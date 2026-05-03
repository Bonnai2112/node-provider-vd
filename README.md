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
- Docker (Postgres pour l'app, Testcontainers pour les tests d'intégration).

### Setup Docker

Sur **Linux** (CI compris) : rien à faire, le daemon Docker est sur
`/var/run/docker.sock`.

Sur **macOS avec colima** : exporter le socket Docker pour Testcontainers
(la détection auto des `DOCKER_HOST` n'est pas fiable) :

```bash
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

`testcontainers.ryuk.disabled=true` est déjà configuré côté projet pour
contourner un problème connu de port-forwarding colima sur le reaper.

## Commandes

### Backend

```bash
docker compose -f docker/dev.yml up -d postgres   # DB locale
./mvnw verify                                     # build + tests (unit + IT)
./mvnw -pl app-bootstrap spring-boot:run          # démarre l'API sur :8080
```

L'API expose :
- `POST /api/v1/nodes` — provisionne un nœud (202 Accepted, `Location` header).
- `GET  /api/v1/nodes/{id}` — récupère un nœud.

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
