# Project: Blockchain Node Platform (demo)

Plateforme de démo permettant de provisionner des nœuds Ethereum testnet
(Hoodi, Sepolia) et un faucet PoW, accessibles via JSON-RPC.

## Stack

- Backend: Java 25, Spring Boot 4, Maven multi-module
- Frontend: Nuxt 3, TypeScript, Pinia, Tailwind
- DB: PostgreSQL 16 (Flyway pour migrations)
- Orchestration des nœuds: Docker via docker-java
- Tests: JUnit 5, AssertJ, Testcontainers, ArchUnit
- Front tests: Vitest, Playwright (smoke only)

## Architecture

- Hexagonal strict, un module Maven par bounded context.
- BC actifs: `node-lifecycle`, `rpc-gateway`, `faucet`, `tenant`.
- Validators = HORS PÉRIMÈTRE pour cette phase. Ne jamais générer de code
  touchant des clés de signature de validateur.

## Règles de code non négociables

- Aucune dépendance Spring, Jakarta, Jackson, ou docker-java dans `*.domain..*`.
  Le domain ne dépend que du JDK et de libs pures (java.time, etc.).
- Les ports sont des interfaces dans `domain/port/in` ou `domain/port/out`.
- Les aggregates portent la logique métier. Pas de domain anémique.
- State machine d'un aggregate = sealed interface + records pour les états.
- DTO REST ≠ modèle de domaine. Mapping explicite dans l'adapter.
- Une exception métier = une classe dédiée, mappée en problem+json par un
  `@RestControllerAdvice`.

## Conventions

- Java: records partout où c'est immutable, sealed pour les ADT, pattern matching.
- Nommage tests: `methodName_should_expectedBehavior_when_condition`.
- Commits: conventional commits (feat:, fix:, chore:, test:, refactor:).
- Une PR = un ticket. Diff < 600 lignes idéalement.

## Commandes

- Build + tests: `./mvnw verify`
- Front dev: `cd frontend && pnpm dev`
- Front build: `cd frontend && pnpm build`
- DB locale: `docker compose -f docker/dev.yml up -d postgres`
- Lancer un EL Hoodi en local pour tests d'intégration:
  `docker compose -f docker/dev.yml up -d besu-hoodi`

## Garde-fous automatisés

- ArchUnit teste les règles d'architecture, exécuté dans `verify`.
- Spotless pour le formatage Java.
- ESLint + Prettier côté front.

## Ce que je veux de toi (Claude Code)

- Tu travailles ticket par ticket. Pas de "build the whole thing".
- Tu écris les tests AVANT l'implémentation pour le domain.
- Tu n'introduis pas de nouvelle dépendance sans me demander.
- Si une décision d'architecture émerge en cours de route, tu t'arrêtes
  et tu me poses la question. Tu ne décides pas seul.
- Tu signales explicitement quand tu touches à plus d'un module.
