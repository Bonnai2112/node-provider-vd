# TODO

## Hors v1 — explicitement non implémenté

- **Validators / VC** : pas de validator client en v1.
  - Ne pas activer `validator.yml` ni `vc-*.yml` dans `COMPOSE_FILE`.
  - Ne pas générer/stocker de clés de signature de validateur.
  - `ProvisionNodeService` rejette tout `ClientPair` dont un layer a `isValidator() == true`.
- **MEV-Boost** : pas d'inclusion `mev-boost.yml` en v1.
- **Mainnet** : seulement Hoodi et Sepolia pour l'instant.

## Suivi

- README de référence : [bc-node-lifecycle/README.md](bc-node-lifecycle/README.md).
