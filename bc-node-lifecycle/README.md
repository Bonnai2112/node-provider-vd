# bc-node-lifecycle

Bounded context : provisioning et cycle de vie des nœuds Ethereum testnet
(Hoodi, Sepolia) via [eth-docker](https://github.com/ethstaker/eth-docker).

## Contrat attendu sur l'host

L'adapter d'orchestration (`EthDockerOrchestrationAdapter`) lance des
processus shell sur la machine où tourne le backend. Pré-requis :

| Pré-requis | Détail |
|---|---|
| `git` | sur le `PATH`, utilisé pour `git ls-remote` (résolution tag→SHA) et clone du cache |
| `bash` | shell utilisé par le script `./ethd` d'eth-docker |
| Docker engine | accessible par l'utilisateur du backend (socket ou `DOCKER_HOST`) |
| Docker Compose v2 | invoqué par `./ethd` |
| Connexion réseau | requise au moins une fois pour résoudre le tag eth-docker (fallback cache disque sinon) |
| `sudo` + règle dédiée | requis pour `chown` du datadir EL vers l'UID du conteneur (cf. § ci-dessous) |

### Règle sudoers pour le `chown` du datadir

Le process EL tourne dans le conteneur eth-docker sous un UID non-root (`10000`
pour Geth/Erigon, `10002` pour Nethermind, `12000`, …). Sans alignement de
propriétaire côté hôte, il ne peut pas écrire dans le bind-mount du datadir.
`ProcessEthdShellRunner.ensureDataDir` délègue donc le `chown` via `sudo -n`.

Pré-requis ops (à provisionner avant de démarrer le backend) — `/etc/sudoers.d/node-provider` :

```
<backend-user> ALL=(root) NOPASSWD: /usr/bin/chown -R *\:* /var/lib/platform/nodes/*/data
```

Le périmètre est strictement limité à `/var/lib/platform/nodes/*/data` — aucun
autre `chown` ne peut être lancé via cette règle.

## Configuration applicative

Toutes paramétrables via `application.yml` ou variables d'env, prefix `app.platform.eth-docker` :

| Propriété | Default | Rôle |
|---|---|---|
| `repo-url` | `https://github.com/ethstaker/eth-docker.git` | dépôt source |
| `ref` | `v26.4.1` | tag git pinné — résolu en SHA au runtime |
| `root-dir` | `/var/lib/platform/nodes` | racine des workdirs (`{root-dir}/{nodeId}/eth-docker`) |
| `cache-dir` | `/var/lib/platform/cache` | bare clone partagé du dépôt eth-docker |
| `sha-cache-file` | `/var/lib/platform/cache/eth-docker-sha` | cache disque tag→SHA, fallback si réseau coupé |

Reconciler :

| Propriété | Default | Rôle |
|---|---|---|
| `app.platform.reconciler.fixed-delay-ms` | `30000` | période de tick du reconciler `@Scheduled` |

## Modèle d'exécution

1. `POST /api/v1/nodes` (NodeController) → `ProvisionNodeService.provision()` :
   - Persiste le node en `REQUESTED`, publie `NodeRequested`, retourne 202 immédiatement.
   - Soumet `deploy()` à un `Executor` dédié (cf. § async).
2. Async deploy (`Executor` `node-provision`) :
   - `EthDockerOrchestrationAdapter.deploy(NodeSpec)` :
     a. résout le tag eth-docker → SHA (fresh ou cache)
     b. ensure bare clone `{cacheDir}/eth-docker.git`
     c. `git clone --shared --branch <tag>` → workdir `{rootDir}/{nodeId}/eth-docker`
     d. génère `.env` (cf. § génération `.env`)
     e. `./ethd up --non-interactive`
     f. persiste un `DeploymentPayload` dans `node_deployments` → renvoie `DeploymentRef`
   - succès : `node.startProvisioning(ref)`, sauvegarde, publie `NodeProvisioningStarted`.
   - échec : `node.fail("deploy failed: ...")`, sauvegarde, publie `NodeFailed`.
3. Reconciler (`@Scheduled` toutes les 30s, lock applicatif via ShedLock) :
   - itère sur les nodes non terminaux ayant un `DeploymentRef`
   - combine `getDeploymentStatus()` (docker-java inspect) + `probeSync()` + `probePeers()` (JSON-RPC)
   - applique la table de transition documentée dans le code (`ReconcileNodeStatusService`).

## Génération du `.env`

Pure (`EthDockerEnvFile`), testée unitairement. Variables forcées :

```
NETWORK={hoodi|sepolia}
COMPOSE_FILE={el}.yml:{cl}.yml      # ex: besu.yml:teku.yml
COMPOSE_PROJECT_NAME=node-{uuid8}
EL_HOST=0.0.0.0
EL_RPC_PORT=<dynamique>             # alloué via ServerSocket(0)
EL_WS_PORT=<dynamique>
CL_REST_PORT=<dynamique>
FEE_RECIPIENT=0x0000...0000         # placeholder, validators hors v1
```

`COMPOSE_FILE` n'inclut **jamais** :
- `vc-*.yml`, `validator.yml` — pas de validator client en v1
- `mev-boost.yml` — pas de MEV-Boost en v1

Cette invariance est testée pour toutes les combinaisons `(EL, CL)` dans
`EthDockerEnvFileTest`.

## Volumes & ports

- Workdirs : `{root-dir}/{nodeId}/eth-docker` (~quelques Mo de scripts +
  les volumes Docker créés par eth-docker, qui peuvent grossir à plusieurs
  centaines de Go selon la chain et le client).
- Ports : 3 ports dynamiques par node (alloués via `ServerSocket(0)`).
  Aucun pool fixe — l'OS choisit.

## Sécurité — validators hors périmètre

- Le service `ProvisionNodeService` rejette tout `ClientPair` dont l'un
  des layers a `isValidator() == true`.
- Tous les `ElClient` et `ClClient` actuels ont `isValidator() == false`.
- L'adapter ne génère **jamais** d'entrée `vc-*.yml` ou `validator.yml`
  dans `COMPOSE_FILE` (test garde-fou).
- Aucun code de cette ressource ne touche à des clés de signature de
  validateur.

## Tests

```bash
# Unit (rapide)
./mvnw -pl bc-node-lifecycle test

# Intégration (Testcontainers Postgres + JPA)
./mvnw -pl bc-node-lifecycle verify

# Intégration eth-docker réelle (long, désactivé par défaut)
./mvnw -pl bc-node-lifecycle verify -Dgroups=eth-docker
```
