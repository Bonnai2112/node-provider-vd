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

## Suivi

- README de référence : [bc-node-lifecycle/README.md](bc-node-lifecycle/README.md).
