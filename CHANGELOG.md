# CHANGELOG

Historique synthétique des contributions, du plus récent au plus ancien.
Pour le détail d'un commit : `git show <sha>`.

---

## 2026-05-23 — Timeout dédié pour l'extraction du tarball template

- **fix(node-lifecycle)** : `extractTarballZstd` a désormais un timeout de
  3600 s au lieu des 600 s par défaut du `ProcessEthdShellRunner.run()`.
  Deux provisionings consécutifs ont failed (`status_reason: deploy failed:
  eth-docker deploy failed`) avec un `data/` partiel (59 GB puis 28 GB sur
  les ~90 GB attendus) : le `tar --use-compress-program=zstd -xf` du tarball
  74 GB compressé était `destroyForcibly` après 10 min, exit non-zéro
  remonté en IOException, et le workdir partiellement préparé restait sur
  disque (sans `el-datadir-bind.yml` puisqu'il est écrit après le chown).
  Mesure : zstd seul fait ~130 MB/s sur cette VM, et le pipeline complet
  (tar + écriture sur /dev/sdb partagé avec un node en cours de fonctionnement)
  passe sous le seuil ~150 MB/s nécessaire pour finir en 10 min. Nouvelle
  surcharge `run(workdir, stdin, timeoutSeconds, command...)` pour cibler ce
  cas sans toucher au fail-safe 600 s des autres ops (ethd up/down/terminate).

## 2026-05-23 — DELETE /nodes idempotent + cleanup du dossier nodes/{nodeId}

- **fix(node-lifecycle)** : `DELETE /api/v1/nodes/{id}` ne renvoie plus 409
  Conflict quand le nœud est déjà dans un état `Terminating`/`Terminated`/`Failed`.
  `Node.terminate()` devient idempotent : no-op sur `Terminating` et
  `Terminated` (ressources déjà libérées, on évite de re-attaquer un workdir
  supprimé) ; transition forcée vers `Terminating` depuis `Failed` pour
  relancer le teardown Docker des ressources résiduelles.
  `TerminateNodeService` skip le teardown asynchrone si le statut entrant
  était déjà terminal, et `tearDownAsync` guard `markTerminated`/`fail` pour
  rester idempotent. Statut de retour inchangé (202 Accepted).
- **feat(node-lifecycle)** : le teardown supprime désormais le dossier parent
  `/var/lib/platform/nodes/{nodeId}/` après `eth-docker/` et `data/`. Nouvelle
  shell op `removeNodeRoot` qui passe par `sudo -n /bin/rm -rf` — symétrique
  au `sudo chown` du déploy, indispensable parce que les fichiers du datadir
  EL sont chownés à l'UID 10000 et que le user backend ne peut pas les
  supprimer en direct. Garde-fou : on n'appelle `removeNodeRoot` que si le
  parent du workdir est un descendant direct de `app.platform.eth-docker.root-dir`.
  **Pré-requis ops** : ajouter au sudoers
  `<backend-user> ALL=(root) NOPASSWD: /bin/rm -rf /var/lib/platform/nodes/*`.

## 2026-05-23 — Job de tarball : ramener le frozen node à Ready

- **fix(ops)** : `produce-el-template.sh` appelle désormais
  `POST {PLATFORM_API}/nodes/{FROZEN_NODE_ID}/restart` (header `X-Owner-Id`)
  dans son trap EXIT, après le redémarrage du conteneur EL. Sans cet appel,
  le reconciler observait l'EL absent pendant le snapshot, basculait
  l'agrégat en `STOPPED`, et aucune branche du `switch` de
  `ReconcileNodeStatusService.applyTransition` ne sortait le nœud de cet
  état même après le redémarrage du conteneur. Opt-in via trois nouvelles
  variables d'env (`PLATFORM_API`, `FROZEN_NODE_ID`, `FROZEN_NODE_OWNER_ID`)
  documentées dans `ops/example-hoodi-geth.env` ; sans elles, le
  comportement précédent est conservé (warning + skip). Une 409 du
  endpoint est traitée comme bénigne (le reconciler n'a pas encore tické,
  le nœud n'est pas encore en STOPPED). Body du Problem Details loggué
  pour tout code non-2xx/409 afin de diagnostiquer rapidement les
  mauvaises configs (UUID malformé, commentaire inline dans le `.env`…).

## 2026-05-17 (suite) — Fix top-up partial-deposit (EIP-55 + lecture récursive)

- **fix(node-lifecycle)**: top-up validateur via `partial-deposit` — encode
  l'adresse de withdrawal en EIP-55 avant de la passer à `deposit-cli` (qui
  rejette les adresses tout en minuscules pour éviter les fautes de frappe),
  via `bouncycastle` (`Keccak.Digest256`). Recherche désormais le
  `deposit_data-*.json` de manière récursive sous `--output_folder` car
  certaines versions de `deposit-cli` écrivent dans un sous-dossier
  `partial_deposits/`. Diagnostic enrichi en cas d'absence de fichier
  (listing du dossier + stdout CLI). Tests unitaires EIP-55 sur les vecteurs
  de référence de l'EIP.

## 2026-05-17 (suite) — Polling frontend de la génération asynchrone

- **feat(front)**: polling pour la génération asynchrone de clés validator —
  le bouton « Générer » déclenche `startGenerateValidatorKeys` (202) puis
  polle `pollValidatorKeyGenerationJob` toutes les secondes (timeout 6 min)
  jusqu'à `SUCCEEDED` ou `FAILED`. UX inchangée (modale, secret affiché une
  fois, ack avant fermeture).

## 2026-05-17 (suite) — Génération de clés validator asynchrone (backend)

- **feat(node-lifecycle)**: génération de clés validator en mode asynchrone
  (202 + polling) — `POST /validator-keys/generate` renvoie désormais
  `202 { jobId }` et un nouveau `GET /validator-keys/generate-jobs/{jobId}`
  expose le statut (`RUNNING` / `SUCCEEDED` / `FAILED`). Le mnemonic et le
  password ne sont jamais persistés : ils vivent uniquement dans
  `InMemoryKeyGenerationJobRegistry` (ConcurrentHashMap + executor + TTL
  30 min), sont effacés à la première lecture authentifiée du propriétaire
  (one-shot), et un poll avec un owner différent renvoie 404 indistinctement
  d'un id inconnu. **Changement d'API : tout consommateur doit s'adapter.**

## 2026-05-17 (suite) — Génération de clés validator : observabilité + déblocage

- **perf(node-lifecycle)**: observabilité + pré-pull image deposit-cli +
  cache UID/GID + déblocage v1.3.0 — log SLF4J avant/après `docker run` avec
  durée, image épinglée à `v1.3.0` (surchargeable via `DEPOSIT_CLI_IMAGE`),
  pré-pull asynchrone au démarrage (`DepositCliImageWarmer`), UID/GID host
  résolu une seule fois, `--amount=32` ajouté à `deposit-cli` pour skipper
  le nouveau prompt interactif introduit par v1.3.0 sur les validators
  compounding.

## 2026-05-17 (suite) — Outillage et règles

- `9d556f9` **chore**: scénarios JSON-RPC consolidation EIP-7251 et CL REST —
  ajoute dans `requests.http` les scénarios pour interroger le beacon node
  directement (lecture des `withdrawal_credentials` 0x00/0x01/0x02), vérifier
  le predeploy de consolidation, lire sa fee, et déclencher une migration
  self-only 0x01 → 0x02.
- `5de435a` **chore**: clarifier les règles d'engineering (branche, CHANGELOG) —
  chaque modification se fait sur une branche tirée de `main` et le CHANGELOG
  est alimenté à chaque commit.

## 2026-05-17 — Reconfiguration validator / MEV-Boost sans redéploiement

- `c22899f` **chore**: notes ops iostat / produce-el-template / df
- `bc0e274` **feat(front)**: activer/désactiver validateur et MEV-Boost depuis le
  détail du nœud — modales `EnableValidatorModal` / `EnableMevBoostModal`,
  `v-if` qui reflète les contraintes d'ordre du back (jamais une combinaison
  qui serait rejetée en 409), affichage du `problem+json.detail` pour la
  raison métier réelle.
- `57c2b7e` **feat**: activer/désactiver validateur et MEV-Boost sur un nœud
  `Ready` — 4 endpoints REST
  (`/validator/{enable,disable}`, `/mev-boost/{enable,disable}`), 5 exceptions
  métier mappées en `problem+json` 409 (Ready requis, MEV-Boost exige
  validator…). Côté orchestration, `applyOptionsChange` ré-écrit `.env` et
  relance `ethd up --remove-orphans` sans perturber EL/CL.

## 2026-05-16 — STOPPED / Restart, fixes reconciler, exposition publique

- `2bd9568` **fix**: sonder l'EL/CL via loopback (pas l'IP publique de la VM) —
  les clouds sans hairpin NAT (Cetic) faisaient time-out tous les probes.
  Sépare `endpointFor()` (public, persisté) de `internalEndpointFor()`
  (loopback, pour le reconciler).
- `61bc896` **fix**: ne pas annuler un `PROVISIONING` en cours via le
  reconciler — un tick tombant avant la création des conteneurs re-basculait
  en `STOPPED` et invalidait le restart utilisateur. Le reconciler reste
  patient en PROVISIONING ; les services async escaladent eux-mêmes en
  `FAILED` si `runEthdUp` jette.
- `ed30c94` **feat(front)**: bouton « Relancer » pour les nœuds `STOPPED` —
  badge dédié, action store optimiste qui passe en `PROVISIONING`.
- `30b64be` **feat**: permettre de relancer un nœud arrêté sans le redéployer —
  ajoute l'état intermédiaire `STOPPED` (workdir intact sur disque) avec
  `POST /restart`. `FAILED` reste réservé aux échecs irrécupérables.
- `9990a4b` **chore**: notes ops disque et IO dans brouillons.
- `7c72d6a` **feat**: exposer la JSON-RPC URL sur l'IP publique de la VM —
  propriété `publicHost` (`PLATFORM_PUBLIC_HOST`), bind sur `0.0.0.0` via
  `HOST_IP`.
- `b922d22` **chore**: basculer les fichiers ops de besu vers geth.

## 2026-05-15 — Mise en service sur VM Cetic Cloud

- `e94208f` **chore**: lier postgres dev sur 127.0.0.1.
- `bdcd514` **fix**: provisionner `/var/lib/platform` dans cloud-init.
- `eb65692` **fix**: update cloud-init.
- `2ff1c9e` Remove user 'ccp' configuration from cloud-init.
- `7ce5cda` **feat**: use vm from cetic cloud.

## 2026-05-12 — Provisioning rapide via tarball template EL

- `6bf0a9c` **feat**: job ops de production des tarballs templates EL depuis un
  nœud frozen.
- `b3cb6e1` **feat**: restaurer le datadir EL depuis un tarball template au
  provisioning — supprime des heures de sync à froid pour chaque nouveau nœud.
- `5c1f449` **feat**: bind-mount du datadir EL sur
  `/var/lib/platform/nodes/{nodeId}/data`.
- `8a87f62` **docs**: superseder la décision btrfs par tarball template ext4.

## 2026-05-09 — Checkpoint sync

- `5bf17a3` **feat**: utiliser un beacon LAN comme checkpoint sync source —
  raccourcit la synchronisation initiale du consensus layer.

## 2026-05-06 — Correctif ports

- `3192bd6` **fix**: ne plus écraser les ports internes `EL_RPC` / `EL_WS` /
  `CL_REST`.

## 2026-05-05 — Clés validator, faucet de scénarios, UI keystore

- `4183fdf` **feat**: scénarios JSON-RPC dans `requests.http` + UI validator keys.
- `52cc620` **feat**: téléchargement keystore et `deposit_data` par validator.
- `2c0f893` **fix**: ignore `claude.md` et `todo.md`.
- `9948c71` **feat**: add validator client.

## 2026-05-04 — Orchestration eth-docker + frontend Nuxt minimal

- `4fb0727` **feat**: frontend Nuxt minimal pour provisioning de nœuds.
- `59d7376` **fix**: add app conf.
- `8302d20` **feat**: adapter eth-docker pour orchestration (suite).

## 2026-05-03 — Bootstrap

- `9aacede` **feat**: adapter eth-docker pour orchestration.
- `f74bc01` **feat**: aggregate `Node` + state machine, en mémoire.

---

## Conventions

- Format des messages : [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`), avec scope
  optionnel (`feat(front):`, etc.).
- Une PR = un ticket, diff < 600 lignes idéalement (voir `CLAUDE.md`).
- Le détail technique vit dans le corps du commit, pas dans ce fichier ;
  utiliser `git show <sha>` pour le contexte complet.
- Chaque nouveau commit ajoute une ligne en tête de ce CHANGELOG sous la
  rubrique de date du jour.
