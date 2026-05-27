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

### Règles sudoers pour le `chown` des volumes

Le process EL tourne dans le conteneur eth-docker sous un UID non-root (`10000`
pour Geth/Erigon, `10002` pour Nethermind, `12000`, …). Sans alignement de
propriétaire côté hôte, il ne peut pas écrire dans le bind-mount du datadir.
`ProcessEthdShellRunner.ensureDataDir` délègue donc le `chown` via `sudo -n`.

De même, le volume Docker nommé `ee-secret` (JWT partagé entre EL et CL) est
créé par défaut avec `root:root`. `ensureVolumeOwnership` pré-crée le volume et
chown son répertoire backing vers l'UID 10000 (EL — c'est Geth qui génère le
JWT au premier boot) avant le premier `ethd up`.

Pré-requis ops (à provisionner avant de démarrer le backend) — `/etc/sudoers.d/node-provider` :

```
<backend-user> ALL=(root) NOPASSWD: /usr/bin/chown -R *\:* /var/lib/platform/nodes/*/data
<backend-user> ALL=(root) NOPASSWD: /usr/bin/chown -R *\:* /var/lib/docker/volumes/node-*
```

Le premier périmètre couvre le datadir EL bind-mounté, le second couvre les
volumes Docker nommés préfixés par le nom de projet Compose (`node-*`).

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

## Tuning I/O disque

Le datadir EL est l'élément le plus sollicité du système : sync initial,
compaction du KV store, et opérations ops concurrentes (extraction du tarball
template — cf. [`ops/RUNBOOK.md`](../ops/RUNBOOK.md)) peuvent saturer un disque
unique (`%util` ≈ 95 %, ~150–200 MB/s sustained en écriture séquentielle, cf.
incident `2026-05-23` dans le `CHANGELOG.md`).

Les leviers ci-dessous se répartissent en deux familles : **host** (que l'ops
applique librement) et **eth-docker `.env`** (aujourd'hui clobberé à chaque
`provision` par `EthDockerEnvFile` — bouger ces knobs demande soit un patch
local hors-platforme, soit une extension du générateur).

### Host (ops, sans modif platform)

| Levier | Effet | Action |
|---|---|---|
| `noatime,nodiratime` sur le mount du datadir | ~5–10 % de writes en moins | `/etc/fstab`, mount option du FS hébergeant `/var/lib/platform/nodes` |
| Disque dédié au datadir EL | supprime la contention avec le tarball template et les autres nodes | bind-mount `{root-dir}` sur un NVMe séparé du disque hébergeant `templates/` |
| Séparer `templates/` des `nodes/` | un `tar+zstd` template ne perturbe plus le sync d'un node existant | déplacer `/var/lib/platform/templates` sur un autre disque |
| Migration HDD → NVMe | plafond ~150 MB/s → ~2–7 GB/s | matériel |

### eth-docker `.env` (non exposé côté platform aujourd'hui)

Knobs documentés ici pour mémoire — si on en a besoin en prod il faudra les
exposer dans `EthDockerEnvFile` (le générateur écrase aujourd'hui tout `.env`
manuel à chaque `deploy`).

| Knob | Client | Effet |
|---|---|---|
| `--state.scheme=path` (PBSS) | Geth | divise la write amplification par ~3–5 vs HBSS legacy |
| `--cache=<MB>` | Geth | gros cache → moins de flushs → moins de writes |
| `--db.cache=<bytes>` | Nethermind | idem |
| `CHECKPOINT_SYNC_URL=<url>` | tous CL | évite l'écriture de l'historique au premier sync |
| `--prune-blobs=true`, `--blob-prune-margin-epochs=<N>` | Lighthouse | borne la rétention blobs post-Dencun (~1 GB/jour) |
| Choix EL Erigon/Reth vs Geth | EL | architecture flat-DB → moins de write amp en steady-state ; sync initial intensif mais borné |

### Limites

Aucun de ces leviers ne réduit le volume *intrinsèque* à écrire pendant un
sync initial (~150–400 Go matérialisés à partir des peers). Ils réduisent
soit la **write amplification**, soit le **steady-state**, soit la
**contention** entre workloads concurrents. Pour diagnostiquer en live :

```sh
iostat -xz 5            # %util et w_await sur le disque suspect
iotop -aoP              # quel process écrit
docker exec <el> geth attach --exec 'eth.syncing'   # phase sync ou steady-state
```

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
