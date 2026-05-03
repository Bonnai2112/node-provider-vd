# node-provider-vd

Plateforme de démo permettant de provisionner des nœuds Ethereum testnet
(Hoodi, Sepolia) et un faucet PoW, accessibles via JSON-RPC.

Pour le contexte projet complet (architecture, règles de code), voir
[CLAUDE.md](CLAUDE.md).

## Structure du monorepo

```
node-provider-vd/
├── pom.xml                  # platform-parent (parent POM + agrégateur)
├── mvnw, mvnw.cmd, .mvn/    # Maven wrapper (3.9.9)
├── bc-node-lifecycle/       # Bounded context: cycle de vie des nœuds
├── bc-faucet/               # Bounded context: faucet PoW
├── bc-tenant/               # Bounded context: tenants et quotas
├── app-bootstrap/           # Entrée Spring Boot — agrège les BC
├── frontend/                # Nuxt 3 (TypeScript strict, Tailwind, Pinia)
└── .github/workflows/ci.yml # Pipeline CI (backend puis frontend)
```

Chaque BC est un module Maven indépendant. `app-bootstrap` est le seul module
qui dépend des BC et porte le `main` Spring Boot.

## Stack

| Côté        | Techno                                                       |
| ----------- | ------------------------------------------------------------ |
| Backend     | Java 25, Spring Boot 4, Maven (multi-module)                 |
| Frontend    | Nuxt 3, TypeScript strict, Tailwind, Pinia                   |
| Format Java | Spotless + google-java-format AOSP                           |
| Tests arch  | ArchUnit (dépendance de test sur tous les BC)                |

## Prérequis locaux

- JDK 25 (Temurin recommandé).
- Node 22+ (cf. [frontend/.nvmrc](frontend/.nvmrc)).
- pnpm 9+.
- Docker (pour la phase ultérieure : Postgres, nœuds Besu, etc.).

## Commandes

### Backend

```bash
./mvnw verify          # build + tests + spotless:check sur tous les modules
./mvnw -pl app-bootstrap spring-boot:run
```

### Frontend

```bash
cd frontend
pnpm install
pnpm dev               # dev server
pnpm build             # build de production
```

## CI

[.github/workflows/ci.yml](.github/workflows/ci.yml) exécute, sur chaque push
et chaque PR :

1. `./mvnw verify` (job `backend`).
2. `pnpm build` dans `frontend/` (job `frontend`, dépend de `backend`).
