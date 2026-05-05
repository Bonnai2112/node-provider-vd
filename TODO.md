# TODO

## Hors v1 — explicitement non implémenté

- **Mainnet** : seulement Hoodi et Sepolia pour l'instant.

## Dette technique — frontend

- **Auth dev** : le frontend envoie un header `X-Owner-Id` statique
  (configurable via `NUXT_PUBLIC_DEV_OWNER_ID`, valeur par défaut
  `11111111-1111-1111-1111-111111111111`).
  Le backend lit désormais ce header sur `POST /api/v1/nodes`,
  `GET /api/v1/nodes`, `GET /api/v1/nodes/{id}` et
  `DELETE /api/v1/nodes/{id}`. À remplacer par une vraie auth
  (OIDC / session) dans un ticket dédié.

## Livré — validators & MEV-Boost

- **Chunk 1** : opt-in par nœud via `CreateNodeRequest` (`validator`,
  `mevBoost`, `feeRecipient`, `graffiti`, `mevMinBid`, `mevBuildFactor`).
  `EthDockerEnvFile` sélectionne `<cl>.yml` ou `<cl>-cl-only.yml`,
  ajoute `mev-boost.yml` à `COMPOSE_FILE` quand `mevBoost=true`.
  Réconciliateur : `Ready` = EL synced + peers (CL et validator
  observés mais non bloquants). Inspector : suit le conteneur
  `validator`.
- **Chunk 2** : gestion des clés validator via API.
  - `GET /api/v1/nodes/{id}/validator-keys` — liste pubkeys.
  - `POST /api/v1/nodes/{id}/validator-keys/import` — multipart
    keystores + password, lance `./ethd keys import --non-interactive`.
  - `POST /api/v1/nodes/{id}/validator-keys/generate` — orchestre
    `ghcr.io/ethstaker/ethstaker-deposit-cli` (mnemonic + password
    générés et retournés une fois), puis `./ethd keys import`.
  - `GET /api/v1/nodes/{id}/validator-keys/download` — zip des
    keystores chiffrés.
  - Persistance : table `validator_keys(id, node_id, pubkey UNIQUE,
    imported_at)` (cascade depuis `nodes`).

## Décisions à arbitrer

- **Génération des clés validator** : aujourd'hui via
  `ghcr.io/ethstaker/ethstaker-deposit-cli` orchestré en sous-processus
  Docker (cf. `DepositCliKeyGenerator`). Le coût scrypt EIP-2335
  (`n=2^18`) impose ~1-5 s par clé, plus le pull image et le démarrage
  container — total ~1-2 min pour 32 validators, et l'appel HTTP est
  bloquant. Outil officiel/audité par l'EF, c'est le seul qui produit
  un `deposit_data.json` directement consommable par le launchpad.
  Options à trancher si on veut améliorer l'UX :
  - **(1)** Garder deposit-cli, rendre l'appel **async** (job +
    polling côté UI) et pré-pull l'image au boot. ~2 jours, surface
    crypto inchangée.
  - **(2)** **Génération côté navigateur** (`@chainsafe/bls-keystore`,
    `@chainsafe/bls`, `bip39`). Mnemonic + password ne quittent jamais
    le navigateur, le backend reçoit uniquement les pubkeys + le
    `deposit_data` signé. ~3-4 jours, meilleur modèle de sécurité.
    Préférée à moyen terme.
  - **(3)** BLS Java natif (`tech.pegasys.teku:bls` + EIP-2335 fait
    main). Rapide mais grosse surface d'audit, à maintenir avec les
    updates de spec (Pectra, etc.). Déconseillé sur ce projet.
  - **(4)** Retirer `POST /validator-keys/generate`, ne garder que
    `/import`. L'opérateur génère ses clés en local avec `wagyu` ou
    `staking-deposit-cli`. Le plus défensif côté code mais moins
    user-friendly.

## Suivi

- README de référence : [bc-node-lifecycle/README.md](bc-node-lifecycle/README.md).
