# bc-log-triage

Bounded context : à partir d'une alerte d'erreur Grafana, assembler un
contexte de diagnostic, demander un correctif à l'API Claude, le vérifier
en sandbox, et ouvrir une **merge request draft** sur GitLab pour review
humaine. Aucun auto-merge. Le pipeline propose ; un humain décide.

## Invariants non négociables

- **Draft only.** Le port `MergeRequestPort` n'expose qu'une seule opération :
  `openDraft(...)`. L'adapter GitLab préfixe systématiquement le titre par
  `Draft:`. Aucune branche de merge automatique.
- **Logs = donnée non fiable.** Le contenu des logs n'est jamais traité comme
  instruction. La sortie du modèle est mappée sur le record `ClaudeFixDto` (champs
  fermés : `rootCause`, `summary`, `confidencePercent`, `patches`,
  `suggestedBranchName`, `suggestedCommitMessage`) — pas de champ "action"
  libre. La seule action que la sortie déclenche est l'ouverture d'une MR draft,
  et c'est le domaine qui le décide, pas le modèle.
- **Allowlist de chemins.** Tout patch dont le chemin ne commence par aucun
  préfixe `app.log-triage.safety.allowed-path-prefixes` est rejeté avec
  `TriageOutcome.RejectedOutOfScope`. La traversal `../` et `..` est explicitement
  bloquée. CI, secrets, infra : hors périmètre.
- **Kill switch + quota journalier.** `app.log-triage.safety.enabled` désactive
  tout le pipeline (variable d'env `LOG_TRIAGE_ENABLED`, défaut `false`).
  `daily-merge-request-quota` borne le nombre de MR ouvertes par jour ; au-delà
  → `RejectedQuotaExceeded`.
- **Domaine pur.** Aucun import de Spring, Spring AI, Loki, JGit, gitlab4j,
  docker-java, Resilience4j ou Micrometer dans le package `domain..`. Garanti à
  la compilation par `DomainPurityArchTest` (ArchUnit).

## Architecture hexagonale

```
adapter/in/web/                  ← Grafana webhook + queue + worker
        │
        ▼
domain/port/in/HandleIncidentUseCase
        │
domain/service/TriageService     ← raisonnement métier, aucune dép. techno
        │
        ▼
domain/port/out/{LogRetrieval,CodeContext,FixSuggestion,FixVerification,
                 MergeRequest,FeatureToggle,MergeRequestQuota}
        │
        ▼
adapter/out/{loki,git,claude,container,gitlab,safety}
```

Les noms de ports sont neutres : aucune mention de Loki, Claude ou GitLab dans
`domain..`. Les DTO (`ClaudeFixDto`, `GrafanaWebhookPayload`, `LokiQueryRangeResponse`)
sont confinés à leur adapter, jamais traversés par le domaine.

## Modèle d'exécution

1. **Réception** ([GrafanaWebhookController.java](src/main/java/com/ceticgroup/cloud/nodeprovider/logtriage/adapter/in/web/GrafanaWebhookController.java))
   - `POST /webhooks/grafana` avec header `X-Webhook-Token`. Comparaison
     constant-time du token. 401 si mismatch.
   - Traduit chaque alerte `firing` du payload Grafana en `Incident`.
   - Pousse dans une `IncidentQueue` bornée (`queue-capacity`). Si pleine →
     503 (back-pressure naturel).
   - Répond `202 Accepted` immédiatement.

2. **Worker** ([IncidentQueueWorker.java](src/main/java/com/ceticgroup/cloud/nodeprovider/logtriage/adapter/in/web/IncidentQueueWorker.java))
   - Thread virtuel unique, poll de la queue, appelle `HandleIncidentUseCase`.
   - Sérialisé : un triage à la fois. Pas de parallélisme interne — la
     résilience (retry, circuit breaker) est portée par les adapters.
   - Chaque outcome est incrémenté dans Micrometer
     (`log_triage.outcome{variant=...}`).

3. **TriageService** ([TriageService.java](src/main/java/com/ceticgroup/cloud/nodeprovider/logtriage/domain/service/TriageService.java))
   pipeline strictement ordonné, court-circuit dès le premier rejet :

   ```
   kill switch ──┬─→ false ──→ RejectedKillSwitchActive
                 │
   logs + code ──┼─→ FixSuggestionPort.propose
                 │
   suggestion ───┼─→ empty ──→ RejectedNoFixProposed
                 │
   allowlist ────┼─→ violation ──→ RejectedOutOfScope(offendingPaths)
                 │
   confidence ───┼─→ < threshold ──→ RejectedLowConfidence
                 │
   verify ───────┼─→ fail ──→ RejectedVerificationFailed
                 │
   quota ────────┼─→ exhausted ──→ RejectedQuotaExceeded
                 │
   openDraft ────┴─→ MergeRequestOpened(ref, confidence)
   ```

   Toute `RuntimeException` non capturée par un adapter remonte en
   `TriageOutcome.Failed(reason)`.

## Ports et adapters

| Port (domaine)            | Adapter                                          | Techno                                     |
| ------------------------- | ------------------------------------------------ | ------------------------------------------ |
| `LogRetrievalPort`        | `LokiLogRetrievalAdapter`                        | HTTP `loki/api/v1/query_range`             |
| `CodeContextPort`         | `GitGraphContextAdapter`                         | JGit (fetch best-effort + walk filesystem) |
| `FixSuggestionPort`       | `ClaudeFixSuggestionAdapter`                     | Spring AI `ChatClient.entity()` + Resilience4j `@Retry`/`@CircuitBreaker` |
| `FixVerificationPort`     | `ContainerTestAdapter`                           | docker-java — clone + `mvn -B -q verify` dans conteneur jetable |
| `MergeRequestPort`        | `GitLabMergeRequestAdapter`                      | gitlab4j-api (branch + commit + MR draft)  |
| `FeatureTogglePort`       | `ConfigFeatureToggle`                            | `SafetyProperties.enabled`                 |
| `MergeRequestQuotaPort`   | `InMemoryDailyMergeRequestQuota`                 | Compteur en mémoire reset à minuit (UTC)   |

## Configuration applicative

Préfixe racine : `app.log-triage`. Tout est externalisable via variables d'env
(cf. [application.yml](../app-bootstrap/src/main/resources/application.yml)).

### `safety` — invariants

| Propriété                         | Défaut                          | Rôle                                          |
| --------------------------------- | ------------------------------- | --------------------------------------------- |
| `enabled`                         | `false` (`LOG_TRIAGE_ENABLED`)  | Kill switch global. Off par défaut.           |
| `daily-merge-request-quota`       | `5`                             | Nombre max de MR ouvertes par jour (UTC).     |
| `confidence-threshold-percent`    | `80`                            | Seuil de confiance minimum pour ouvrir une MR.|
| `allowed-path-prefixes`           | `src/main/java/`, `src/test/java/` | Préfixes autorisés pour les patchs.       |

### `webhook` — réception

| Propriété         | Défaut                                  | Rôle                              |
| ----------------- | --------------------------------------- | --------------------------------- |
| `token`           | _(requis)_ `LOG_TRIAGE_WEBHOOK_TOKEN`   | Token shared-secret de Grafana.   |
| `queue-capacity`  | `64`                                    | Taille de la file en mémoire.     |

### `loki` — récupération des logs

| Propriété        | Défaut             | Rôle                                |
| ---------------- | ------------------ | ----------------------------------- |
| `base-url`       | `http://loki:3100` | URL Loki (`LOKI_URL`).              |
| `window-before`  | `PT5M`             | Fenêtre de logs avant l'incident.   |
| `window-after`   | `PT1M`             | Fenêtre de logs après l'incident.   |
| `max-lines`      | `200`              | Limite de lignes ramenées.          |

### `git` — checkout local pour le contexte de code

| Propriété         | Défaut                              | Rôle                                            |
| ----------------- | ----------------------------------- | ----------------------------------------------- |
| `repo-root-path`  | `/var/lib/log-triage/workspace`     | Chemin du checkout local (fetch avant lecture). |
| `max-snippets`    | `8`                                 | Borne le nombre d'extraits de code joints.      |

### `gitlab` — ouverture des MR

| Propriété      | Défaut                       | Rôle                                              |
| -------------- | ---------------------------- | ------------------------------------------------- |
| `base-url`     | `https://gitlab.example`     | Instance GitLab (`GITLAB_URL`).                   |
| `token`        | _(requis)_ `GITLAB_TOKEN`    | Token PAT scope `api` — **stocké via Vault**.     |
| `project-path` | `group/project`              | Slug GitLab du projet cible.                      |
| `base-branch`  | `main`                       | Branche cible des MR ouvertes.                    |

### `verification` — sandbox de build/tests

| Propriété          | Défaut                                  | Rôle                                       |
| ------------------ | --------------------------------------- | ------------------------------------------ |
| `repo-url`         | `https://gitlab.example/group/project.git` | Repo cloné dans le conteneur jetable.   |
| `base-branch`      | `main`                                  | Branche checkout pour la vérification.     |
| `maven-image`      | `maven:3.9.9-eclipse-temurin-25`        | Image conteneur pour `mvn verify`.         |
| `timeout-seconds`  | `900`                                   | Timeout d'attente du conteneur.            |

### Spring AI (Anthropic)

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}        # stocké via Vault en prod
      chat:
        options:
          model: claude-opus-4-7
```

### Résilience (Claude)

`@Retry(name = "claude")` et `@CircuitBreaker(name = "claude")` ; instances
configurées dans [application.yml](../app-bootstrap/src/main/resources/application.yml) :

```yaml
resilience4j:
  retry:
    instances:
      claude:
        max-attempts: 3
        wait-duration: 2s
        retry-exceptions: [java.lang.RuntimeException]
  circuitbreaker:
    instances:
      claude:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

## Contrat attendu sur l'host

| Pré-requis                       | Détail                                                                                             |
| -------------------------------- | -------------------------------------------------------------------------------------------------- |
| Docker engine                    | accessible par l'utilisateur du backend — utilisé par `ContainerTestAdapter` pour `mvn verify`.    |
| Image Maven                      | `maven:3.9.9-eclipse-temurin-25` pullable depuis l'host.                                           |
| Connexion sortante vers Anthropic | requise pour `ClaudeFixSuggestionAdapter`. Circuit breaker s'ouvre si l'API est indisponible.     |
| Connexion sortante vers GitLab    | requise pour ouvrir les MR. Token GitLab avec scope `api`.                                        |
| Connexion vers Loki               | requise pour récupérer les logs autour de l'incident.                                              |
| Checkout local (`git.repo-root-path`) | présent avant tout triage — sinon `CodeContextPort` retourne `List.of()` (best-effort).        |

### Sudoers — aucun

À la différence de `bc-node-lifecycle`, ce BC **ne nécessite pas de règle
sudoers** : tous les writes hors classpath se font dans des répertoires de
travail jetables (`/tmp/log-triage-verify-*`) ou via les API Docker / GitLab.

## Observabilité

Le domaine n'est pas instrumenté. Les métriques sont émises par la couche
adapter / application (`IncidentQueueWorker`) en lisant le `TriageOutcome` :

| Métrique                                      | Type    | Variants `variant=...`                  |
| --------------------------------------------- | ------- | --------------------------------------- |
| `log_triage.outcome`                          | counter | `MergeRequestOpened`, `RejectedKillSwitchActive`, `RejectedQuotaExceeded`, `RejectedNoFixProposed`, `RejectedOutOfScope`, `RejectedLowConfidence`, `RejectedVerificationFailed`, `Failed` |

Tags additionnels conseillés pour les dashboards Mimir : `service`, `alertname`
(à porter par l'app via `Tags.of(...)` si besoin — non implémenté en v1 pour
limiter la cardinalité).

Endpoints actuator exposés via `app-bootstrap` : `/actuator/health`,
`/actuator/info`, `/actuator/prometheus`.

## Sécurité — pourquoi c'est sûr

| Risque                                                | Mitigation                                                                                     |
| ----------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| Injection de prompt depuis les logs                   | Sortie strictement typée (`ClaudeFixDto`), aucune action "exécutable" dans le schéma.          |
| Patch hors périmètre (CI, secrets, infra)             | `PathAllowlist` revérifie chaque patch après réception, indépendamment du modèle.              |
| Patch traversant le filesystem (`../`)                | `PathAllowlist.permits` rejette les chemins contenant `..`.                                    |
| Sortie de modèle malveillante exécutée                | Le seul effet métier est l'ouverture d'une **MR draft** — aucun `git merge`, aucun push direct.|
| Boucle de triage emballée (DoS sur Claude/GitLab)     | Quota journalier + kill switch + circuit breaker Resilience4j.                                 |
| Token webhook volé                                    | Comparaison constant-time du token. À combiner avec un mTLS au niveau ingress (hors v1).       |
| Token GitLab / clé Anthropic en clair                 | Tous les secrets injectés via Spring Cloud Vault (`${VAR:}` en YAML, jamais commit en clair).  |
| Tests qui modifient l'environnement                   | Vérification du correctif dans un conteneur **jetable** (`--auto-remove`), workdir tmp wipe.   |

## Tests

```bash
# Unitaires du domaine (avec fakes des ports, aucune infra réelle)
./mvnw -pl bc-log-triage test

# + ArchUnit (purity du domaine) + spotless
./mvnw -pl bc-log-triage verify
```

Couverture domaine :

- [TriageServiceTest.java](src/test/java/com/ceticgroup/cloud/nodeprovider/logtriage/domain/service/TriageServiceTest.java) — 9 cas, un par variant de `TriageOutcome` + ordre du pipeline.
- [PathAllowlistTest.java](src/test/java/com/ceticgroup/cloud/nodeprovider/logtriage/domain/PathAllowlistTest.java) — traversal `../`, normalisation, backslash, slash absolu.
- [ConfidenceTest.java](src/test/java/com/ceticgroup/cloud/nodeprovider/logtriage/domain/ConfidenceTest.java) — bornes [0, 100], ordre `isAtLeast`.
- [DomainPurityArchTest.java](src/test/java/com/ceticgroup/cloud/nodeprovider/logtriage/architecture/DomainPurityArchTest.java) — 9 règles ArchUnit : aucune dépendance vers Spring, Spring AI, Jackson, Jakarta, JGit, gitlab4j, docker-java, Resilience4j, Micrometer dans `domain..`.

Les fakes de ports vivent dans [Fakes.java](src/test/java/com/ceticgroup/cloud/nodeprovider/logtriage/domain/service/Fakes.java) — implémentations en mémoire, déterministes, sans Mockito.

## Hors périmètre (v1)

- Pas d'auto-merge des MR — invariant projet.
- Pas de modification des services métier existants : la sortie va dans **un
  autre repo GitLab** ciblé par `gitlab.project-path` / `verification.repo-url`.
- Pas de relecture directe de fichiers de logs : la source est exclusivement
  l'API Loki.
- Pas de compteur de quota distribué : `InMemoryDailyMergeRequestQuota`
  suppose une instance unique de backend. Lorsqu'on passera à plusieurs
  instances, remplacer par une implémentation Redis ou Postgres derrière le
  même port.
