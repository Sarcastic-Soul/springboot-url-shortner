<h1 align="center">⚡ URL Shortener</h1>

<p align="center">
  Shorten a link, protect it, expire it, see who clicked it —<br/>
  on a stack sized to keep serving while it is being abused.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange.svg" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Spring_Boot-4.1-6DB33F.svg" alt="Spring Boot 4.1"/>
  <img src="https://img.shields.io/badge/React-19-61DAFB.svg" alt="React 19"/>
  <img src="https://img.shields.io/badge/PostgreSQL-17-336791.svg" alt="PostgreSQL 17"/>
  <img src="https://img.shields.io/badge/Valkey-8-FF4438.svg" alt="Valkey 8"/>
  <a href="./chart"><img src="https://img.shields.io/badge/Helm-3_environments-0F1689.svg" alt="Helm"/></a>
  <a href="./load_tests"><img src="https://img.shields.io/badge/k6-3_tier_testing-7D64FF.svg" alt="k6"/></a>
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="MIT"/></a>
</p>

| Frontend | OpenAPI (Swagger) | Grafana |
| :---: | :---: | :---: |
| ![App](./assets/app.png) | ![Swagger](./assets/swagger.png) | ![Grafana](./assets/graphana.png) |

---

## What it does

- **Short links** — TSID codes, optional expiry, custom alias
- **Protection** — password-locked links, click limits, unsafe-scheme and domain blocking
- **Analytics** — device, browser, OS and referer per click; IPs stored hashed
- **Accounts** — JWT login, paginated dashboard, edit / delete / bulk-delete
- **Anonymous use** — plain links only, expiry required and capped at 7 days

## Architecture

```mermaid
flowchart TD
    Client["🌐 Client"] --> Ingress["🔀 Nginx Ingress"]

    subgraph K8s["☸️ Kubernetes"]
        Ingress -->|"/"| Frontend["🎨 React SPA on nginx"]
        Ingress -->|"/api, /{shortCode}"| Backend["☕ Spring Boot<br/>HPA 3–15"]

        Backend -->|"hit"| Caffeine["⚡ Caffeine<br/>in-process, 50k"]
        Backend -->|"miss"| Valkey[("⚡ Valkey<br/>cache · rate limit · click stream")]
        Backend -->|"miss, bulkheaded"| Postgres[("🐘 PostgreSQL 17")]

        Backend -->|"async enqueue"| Valkey
        Valkey -->|"consumer group, batched"| Backend
        Backend -->|"batch insert + counter"| Postgres

        Cron["⏱️ CronJobs<br/>cleanup · retention"] --> Postgres
        Migrate["🔧 pre-upgrade Job<br/>Flyway"] --> Postgres

        Backend -.->|"/actuator/prometheus"| Prom["📊 Prometheus → Grafana → Loki"]
    end
```

A redirect reads **Caffeine → Valkey → Postgres**, then publishes the click to a Valkey stream and
returns — no database write on the response path. A consumer group drains that stream and writes
clicks in batches.

Rate limited per identity (20/min anonymous, 200/min authenticated). Database paths sit behind a
bulkhead sized from the connection pool, so overload gets a fast `503` instead of a hung thread.

→ [ARCHITECTURE.md](./ARCHITECTURE.md) for the caching, scaling and load-testing details.

## Quick start

```bash
# Local — dependencies in Docker, app on your host
make dev-up          # postgres :5432, valkey :6379
make dev-backend
make dev-frontend

# Cluster
export POSTGRES_PASSWORD="$(openssl rand -base64 32)"
export JWT_SECRET="$(openssl rand -base64 48)"
make prod-deploy
make obs-deploy      # optional: Prometheus + Grafana + Loki
```

Secrets have no defaults — a missing one fails the install rather than shipping a known password.
Frontend on `http://localhost`, API under `/api/v1`, docs at `/swagger-ui/index.html`, metrics at
`/actuator/prometheus`.

→ [ENVIRONMENTS.md](./ENVIRONMENTS.md) for dev / prod / test setup.

## Benchmarks

Not published yet. The k6 suites (gate / capacity / resilience) run in CI on a constrained
single node, which measures the runner as much as the app — real numbers go in
[`load_tests/BASELINE.md`](load_tests/BASELINE.md) once they are produced on dedicated hardware.

## Stack

| Layer | Technologies |
| :--- | :--- |
| **Backend** | Java 21, Spring Boot 4.1, Spring Security, Spring Data JPA, Flyway, Resilience4j |
| **Data** | PostgreSQL 17, Valkey 8 (cache · rate limit · streams), Caffeine |
| **Frontend** | React 19, TypeScript, Vite, React Query |
| **Infrastructure** | Docker, Kubernetes, Helm (3 environments), HPA, PDB, CronJobs |
| **Observability** | Micrometer/Prometheus, Grafana, Loki |
| **Testing** | JUnit 5, Mockito, k6 |

## Layout

```
backend/               Spring Boot application and Flyway migrations
frontend/              React + Vite SPA, built into an nginx image
chart/                 Helm chart — values-dev / values-loadtest / values-prod
chart/observability/   Prometheus, Grafana, Loki, dashboards
load_tests/            k6 suites: gate, load, spike, soak, resilience
dev/                   docker-compose for local dependencies
.github/workflows/     chart lint, Tier A gate, Tier B capacity, Tier C resilience
```

## License

[MIT](./LICENSE)
