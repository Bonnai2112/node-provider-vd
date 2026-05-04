# TODO

## Hors v1 — explicitement non implémenté

- **Validators / VC** : pas de validator client en v1.
  - Ne pas activer `validator.yml` ni `vc-*.yml` dans `COMPOSE_FILE`.
  - Ne pas générer/stocker de clés de signature de validateur.
  - `ProvisionNodeService` rejette tout `ClientPair` dont un layer a `isValidator() == true`.
- **MEV-Boost** : pas d'inclusion `mev-boost.yml` en v1.
- **Mainnet** : seulement Hoodi et Sepolia pour l'instant.

## Dette technique — frontend

- **Auth dev** : le frontend envoie un header `X-Owner-Id` statique
  (configurable via `NUXT_PUBLIC_DEV_OWNER_ID`, valeur par défaut
  `11111111-1111-1111-1111-111111111111`).
  À remplacer par une vraie auth (OIDC / session) dans un ticket dédié.
  Côté backend, le contrôleur ne lit pas encore ce header — il faudra
  faire transiter `ownerId` via header ou JWT plutôt que via le body de
  `POST /api/v1/nodes`.
- **Endpoints REST manquants** (consommés par le frontend mais pas
  encore exposés par `bc-node-lifecycle/NodeController`) :
  - `GET /api/v1/nodes` filtré par `X-Owner-Id` → liste pour `/nodes`.
  - `DELETE /api/v1/nodes/{id}` → bouton « Terminer » sur `/nodes/[id]`.
  Tant que ces endpoints ne sont pas livrés, ces deux features échoueront
  en runtime (404). Le composable `useNodesApi` est déjà typé contre le
  contrat attendu.

## Suivi

- README de référence : [bc-node-lifecycle/README.md](bc-node-lifecycle/README.md).
