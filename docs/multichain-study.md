# Étude — Passage multichaine

Étude exploratoire du passage de la plateforme actuelle (Ethereum testnet
uniquement : Hoodi, Sepolia) vers un support multichaine couvrant
**BSC**, **Base**, **Solana**, **XRPL** et **Bitcoin**.

Cette étude ne tranche pas — elle pose les options et les décisions à
arbitrer. Voir la section [Décisions à arbitrer](#7-décisions-à-arbitrer)
en fin de document.

---

## 1. Point de départ — couplage actuel à Ethereum

Audit du code existant (mai 2026) :

- **Domaine `node-lifecycle`** : couplage **fort**.
  - Enums fermés : `Network` (HOODI, SEPOLIA), `ElClient` (BESU, GETH,
    NETHERMIND, ERIGON), `ClClient` (TEKU, LIGHTHOUSE, PRYSM, NIMBUS,
    LODESTAR).
  - `ClientPair` impose la dualité EL+CL (post-merge Ethereum-only).
  - `ValidatorKey.pubkey` suppose BLS 96 bytes.
  - `NodeOptions` porte `feeRecipient` (validée 42 chars EVM),
    `mevBoost`, `mevMinBid`, `mevBuildFactor`, `graffiti`.
- **Adapters `eth-docker/`** : couplage **fort**.
  - 23 classes dédiées (`EthDockerOrchestrationAdapter`,
    `DepositCliKeyGenerator`, `DepositCliTopupGenerator`,
    `EthdValidatorKeyImporter`, `EthDockerEnvFile`,
    `RepositoryCheckpointSyncSourceLocator`, etc.).
  - Orchestration via `./ethd` ; compose files par EL/CL hardcodés.
- **Schéma DB** : couplage **moyen-fort**.
  - `nodes` : `network` / `el_client` / `cl_client` en enums SQL.
  - `validator_keys.pubkey VARCHAR(98)` (BLS-only).
  - `node_options` : colonnes `validator`, `mev_boost`,
    `fee_recipient`, `mev_min_bid`, `mev_build_factor`.
- **REST API** : couplage **moyen**.
  - Endpoints CRUD `/api/v1/nodes/...` génériques sur le principe mais
    `network`/`executionLayer`/`consensusLayer` sont des enums Ethereum.
  - `/validator/...`, `/mev-boost/...`, `/validator-keys/...`,
    `/topup-deposit-data` sont Ethereum-only.
- **Sync probes** (`HttpBlockchainProbeAdapter`) : couplage **fort**.
  - `eth_syncing`, `net_peerCount` (EVM), `/eth/v1/node/syncing`
    (Beacon REST).
- **BC `rpc-gateway`, `faucet`, `tenant`** : modules **vides**
  (pom.xml seulement). Opportunité pour les designer multichaine
  *by design*.
- **ArchUnit** : règles existantes (pas de Spring/Jakarta/Jackson/
  docker-java dans le domain) ; **aucune** notion de SPI ou de
  Provider pluggable.

**État unique réutilisable tel quel** : la state machine
`REQUESTED → PROVISIONING → SYNCING → READY → DEGRADED/STOPPED/TERMINATED/FAILED`,
qui est intrinsèquement générique.

## 2. Réalité technique par chaîne cible

| Chaîne | Modèle nœud | Composants | Validator en démo ? | Stockage / RAM | Tooling existant |
|---|---|---|---|---|---|
| **BSC** | Single binary EVM (PoSA) | `bsc-geth` (fork geth) | Non — 2000+ BNB, set fermé à ~41 validators | ~2-3 TB SSD / 32 GB RAM | Images Docker officielles, snapshot sync obligatoire |
| **Base** | OP Stack L2 | `op-geth` + `op-node` + RPC L1 (Sepolia/Mainnet) | Non — sequencer = Coinbase | ~1 TB SSD / 16 GB RAM | Docker compose officiel `base-org/node` |
| **Solana** | Single binary, pas d'EL/CL | `agave-validator` (ex-`solana-validator`) | Oui sur testnet (vote + identity accounts) | 1-2 TB NVMe / **256 GB RAM** / 1 Gbit dédié | Binaires officiels, pas idiomatique en Docker |
| **XRPL** | Single binary | `rippled` | "Validator" possible mais sans rewards (UNL) | ~100-500 GB / 16 GB RAM | Image Docker officielle XRPLF |
| **Bitcoin** | Single binary PoW | `bitcoind` (Bitcoin Core) | **Non** (mining ASIC, hors-sujet) | Archive ~700 GB / pruné 10-50 GB / 4-8 GB RAM | Image Docker officielle, JSON-RPC + ZMQ |

### Spécificités structurantes

- **BSC et Base** restent EVM → JSON-RPC, méthodes standard, probes
  largement réutilisables avec l'EL Ethereum.
- **Base** introduit une **dépendance externe** (RPC L1). Choix à
  faire : provider tiers (Infura/Alchemy/Ankr) ou chaîner sur nos
  propres nœuds Sepolia.
- **Solana** casse le modèle EL+CL et explose le profil hardware.
  Décision budgétaire avant tout.
- **XRPL** est techniquement le plus simple — pas de PoS, conteneur
  léger, RPC + WebSocket.
- **Bitcoin** :
  - **PoW pur** → "validator" disparaît (à la limite : mineur
    signet/regtest pour démos locales).
  - **Pruning par nœud** (`-prune=550` à `-prune=550000`) : décision
    *par nœud*, impact stockage 10 GB vs 700 GB → impact pricing
    direct. À exposer dans `CreateNodeRequest`.
  - **Auth RPC** : fichier `.cookie` dans le datadir ou
    `rpcuser`/`rpcpassword` (modèle différent du JWT Engine API).
  - **ZMQ** pour le streaming temps réel (pas de WebSocket).
  - **Lightning** = couche au-dessus (LND, Core Lightning) — service
    distinct à arbitrer.
- **Aucune** des 5 cibles ne reproduit le couple EL+CL d'Ethereum.
  La dualité doit devenir un *cas particulier d'Ethereum*, pas un
  invariant du domaine.

## 3. Options d'architecture

### Option A — Extension à minima (anti-pattern à expliciter)

Ajouter des valeurs aux enums (`Network.BSC_TESTNET`,
`ElClient.BSC_GETH`, `ClClient.NONE`), if/else dans les adapters.

- **Pro** : ~1-2 semaines pour BSC.
- **Con** : on hérite de `feeRecipient`, MEV-Boost, validator-keys
  BLS sur des chaînes où ça n'a pas de sens. ArchUnit ne nous sauve
  pas. Dette qui explose à la 3e chaîne. Schéma DB pollué de
  colonnes nullables sans contraintes claires.
- **Verdict** : à éviter. À considérer **uniquement** si on veut une
  démo BSC en 2 semaines sans plus, sachant qu'on jettera.

### Option B — `ChainProfile` SPI dans le domaine actuel

Introduire dans `node-lifecycle.domain` une interface `ChainProfile`
(slug, displayName, supports(Feature)…) et un `ChainProfileRegistry`.
Les énums deviennent un *défaut Ethereum* ; les features (`validator`,
`mevBoost`, `deposit`, `topup`) sont gardées par capabilities.

- **Pro** : aggregate Node, state machine, ports
  `NodeOrchestrationPort` / `HealthProbePort` restent. Refactor
  centré sur le domaine.
- **Con** : `NodeOrchestrationPort` actuel suppose docker-compose /
  `./ethd`. Il faut le rendre suffisamment générique pour Solana
  (binaire natif, pas de compose) ou accepter une 2e implémentation
  parallèle.
- **Effort** : 3-4 semaines pour le refactor + 1-2 semaines par
  chaîne.

### Option C — Un BC par famille de chaînes

- `bc-node-lifecycle-evm` (Ethereum, BSC, Base)
- `bc-node-lifecycle-solana`
- `bc-node-lifecycle-xrpl`
- `bc-node-lifecycle-bitcoin`

Chacun a son propre aggregate, son schéma, ses adapters. Un BC chapeau
`bc-node-catalog` route les requêtes API par chaîne.

- **Pro** : isolation forte, ArchUnit règne sur chaque silo, on n'a
  pas à généraliser ce qui n'a pas de tronc commun. Cohérent avec le
  DDD strict de CLAUDE.md.
- **Con** : duplication du CRUD nodes, du tenant binding, du polling
  status. ~6-8 semaines pour la première extraction.
- **Verdict** : justifié dès qu'on dépasse 2 familles radicalement
  différentes.

### Option D — Hybride (recommandation à arbitrer)

- `bc-node-lifecycle` actuel devient **EVM** (Ethereum, BSC, Base)
  avec un `ChainProfile` interne.
- `bc-node-lifecycle-solana`, `bc-node-lifecycle-xrpl`,
  `bc-node-lifecycle-bitcoin` en BC séparés.

C'est probablement le bon compromis : on factorise ce qui se
factorise réellement (EVM), on isole ce qui n'a aucun gène commun.

## 4. Impact transverse

### REST API

- `POST /api/v1/nodes` doit accepter `chain` (string validé contre le
  registry) au lieu de `network` enum.
- `/validator-keys/*`, `/topup-deposit-data`, `/validator/*`,
  `/mev-boost/*` → déplacés sous `/api/v1/nodes/{id}/ethereum/...`
  ou exposés conditionnellement selon les capabilities de la chaîne.
- **Versioning** : casse v1 → soit on bump v2, soit on garde v1
  (Ethereum) + on expose v2 (multichaine). Décision à arbitrer.

### Schéma DB

- `network` / `el_client` / `cl_client` enums → colonnes texte +
  lookup table `chain_profiles(slug, family, version, …)`.
- `validator_keys` → soit générique avec `key_format`
  (BLS/ED25519/secp256k1), soit tables séparées par famille. Tabler
  par famille est plus honnête (formats BLS+EIP-2335 vs Solana
  keypairs vs ø Bitcoin).
- Migration : nouvelles colonnes nullables, backfill, puis suppression
  des enums dans une 2e vague.

### `rpc-gateway` (à créer)

Bonne nouvelle : il n'existe pas encore. À designer multichaine
*by design* :

- **EVM** : JSON-RPC over HTTP, méthodes standardisées.
- **Solana** : JSON-RPC over HTTP/WS, méthodes spécifiques
  (`getSlot`, `getHealth`, `getBalance`…).
- **XRPL** : WebSocket + JSON-RPC (`server_info`, `ledger_current`).
- **Bitcoin** : JSON-RPC + auth cookie, optionnel ZMQ streaming.

→ Une abstraction `RpcProtocol` (HTTP-JSON-RPC, WS-JSON-RPC) + une
stratégie de probe par chaîne.

### `faucet` et `tenant`

- `faucet` est vide → à designer multichaine d'emblée (les faucets
  testnet sont chain-specific de toute façon).
- `tenant` est vide → décision à prendre : un tenant peut-il
  provisionner sur N chaînes avec un seul quota, ou un quota par
  chaîne ? Impact pricing.

### Observabilité

Les métriques actuelles (peers EL, slot CL) sont Ethereum-only.
Standardiser un set minimal cross-chain :
`node_healthy`, `sync_progress_percent`, `peer_count`,
`latest_height` — et garder les métriques riches en chain-specific.

## 5. Effort indicatif (option D)

| Lot | Effort | Risque |
|---|---|---|
| Refactor SPI EVM (`ChainProfile` + Capabilities + DB) | 3 sem | Moyen — touche tout `node-lifecycle` |
| Ajout BSC (image, sync probes, snapshot bootstrap) | 1,5 sem | Faible |
| Ajout Base (op-geth + op-node + dépendance L1) | 2,5 sem | Moyen — dépend du choix L1 |
| BC `node-lifecycle-bitcoin` | 2 sem | Faible — chaîne mature, peu de cas particuliers |
| BC `node-lifecycle-xrpl` | 3 sem | Faible — chaîne simple, mais nouveau BC complet |
| BC `node-lifecycle-solana` | 6 sem | **Élevé** — hardware, snapshot, monitoring spécifiques |
| `rpc-gateway` multichaine | 3-4 sem | Moyen — design from scratch |
| Front (sélecteur chaîne, UI capabilities-driven) | 2 sem | Faible |
| (option) Lightning sidecar attaché à bitcoind | +2 sem | Moyen — multi-impls, gestion canaux non triviale |

**Total ordre de grandeur : 4,5-5,5 mois** pour les 5 cibles, dont
~1 mois rien que de refactor préalable. Bitcoin est le moins risqué
de tous ; Solana le plus.

## 6. Position de chaque chaîne dans l'architecture proposée

| Chaîne | BC cible (option D) | Famille | Effort isolé |
|---|---|---|---|
| Ethereum | `bc-node-lifecycle` (EVM, refactoré) | EVM PoS L1 | inclus dans refactor |
| BSC | `bc-node-lifecycle` (EVM) | EVM PoSA L1 | 1,5 sem |
| Base | `bc-node-lifecycle` (EVM) | EVM Optimistic Rollup L2 | 2,5 sem |
| Bitcoin | `bc-node-lifecycle-bitcoin` | UTXO PoW L1 | 2 sem |
| XRPL | `bc-node-lifecycle-xrpl` | UNL consensus L1 | 3 sem |
| Solana | `bc-node-lifecycle-solana` | PoH+PoS L1 | 6 sem |

## 7. Décisions à arbitrer

1. **Stratégie d'architecture** : Option B (SPI dans le BC actuel),
   C (un BC par famille), ou D (hybride EVM mutualisé + non-EVM
   séparés) ?
2. **Versioning API** : v2 cassante, ou cohabitation v1 (Ethereum
   only) + v2 (multichaine) ?
3. **Périmètre démo** : on vise **RPC nodes** partout, ou
   **validators** quand c'est faisable (Solana testnet, XRPL) ?
   Validators non-Ethereum changent radicalement le scope.
4. **Base et la dépendance L1** : on consomme un provider tiers
   (Infura/Alchemy/Ankr) ou on chaîne sur nos propres nœuds Sepolia ?
5. **Solana go/no-go** : profil hardware (256 GB RAM, NVMe, 1 Gbit)
   viable budget démo, ou on l'exclut explicitement comme "Mainnet"
   l'est aujourd'hui ?
6. **Ordre de phasage** : BSC d'abord (proche de l'existant, valide
   le refactor à coût bas) ou XRPL/Bitcoin d'abord (validation de la
   séparation BC sans le bruit EVM) ?
7. **Bitcoin scope** : full node JSON-RPC seul (offre simple, alignée
   Quicknode/Ankr/Getblock), ou Lightning node managé au-dessus ?
   Le second ouvre des questions opérationnelles fortes (custody des
   fonds de canaux, watchtowers, rebalancing) qui dépassent le scope
   démo actuel.

---

*Document vivant — à mettre à jour quand les décisions sont prises.*
