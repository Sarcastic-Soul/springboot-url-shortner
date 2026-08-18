# ⚡ URL Shortener — a backend built to take load and resist abuse

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-green.svg)](https://spring.io/projects/spring-boot)
[![React 19](https://img.shields.io/badge/React-19-blue.svg)](https://react.dev/)
[![Helm](https://img.shields.io/badge/Helm-3_environments-0F1689.svg)](./chart)
[![k6](https://img.shields.io/badge/k6-3_tier_testing-7D64FF.svg)](./load_tests)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A URL shortener with a cache-first redirect path, an off-hot-path analytics pipeline, and a
distributed rate limiter — deployed by one Helm chart across three environments and measured by
a load-test suite that models the traffic a shortener actually gets.

The interesting part of this project is not the feature list. It is that the claims are
checkable: the connection budget is enforced by CI, the cache hit ratio is measured rather than
asserted, and there is a test suite whose job is to attack the service while a second population
of users tries to use it normally.

---

## 📸 Application Showcase

| **Frontend Application** | **Interactive OpenAPI (Swagger)** | **Grafana** |
| :---: | :---: | :---: |
| ![App Screenshot](./assets/app.png) | ![Swagger Screenshot](./assets/swagger.png) | ![Grafana Screenshot](./assets/graphana.png) |

The Grafana view is provisioned from
[`chart/observability/dashboards`](./chart/observability/dashboards) — a ConfigMap in git, not a
dashboard someone built by hand in a local Minikube.

---

## ✨ What it does

- **Cache-first redirects.** Caffeine (in-process) → Valkey (shared) → Postgres. The hit ratio
  is exported as `redirect_cache_lookups_total` and returned per request as `X-Cache`, so it can
  be asserted in a load test instead of assumed.
- **Analytics off the hot path.** A redirect enqueues a click to a Valkey stream and returns.
  A consumer group drains it in batches: one existence query, one batched insert, one batched
  counter update — no per-message lookups, and no synchronous write on the redirect path.
- **Distributed token bucket.** Rate limiting is a single atomic Lua script over Valkey —
  refill arithmetic and state in one round trip, with Redis's own clock so pods with skewed
  clocks cannot disagree.
- **Load shedding.** Database-bound paths sit behind a Resilience4j bulkhead sized from the
  connection pool. Past capacity you get an immediate `503` with `Retry-After` rather than a
  request that occupies a thread for ten seconds and fails anyway.
- **Horizontal autoscaling that the database can survive.** The HPA scales 3→15, and the chart
  *derives* the connection pool from that ceiling so scaling out cannot exhaust Postgres.
- **JWT authentication** with the user id carried as a claim, so an authenticated request costs
  zero database queries to authenticate. (Access tokens only — there is no refresh endpoint.)

---

## 🏗️ Architecture

```mermaid
flowchart TD
    Client["🌐 Client"] --> Ingress["🔀 Nginx Ingress<br/>overwrites X-Forwarded-For"]

    subgraph K8s["☸️ Kubernetes"]
        Ingress -->|"/"| Frontend["🎨 React SPA on nginx"]
        Ingress -->|"/api, /{shortCode}"| Backend["☕ Spring Boot<br/>HPA 3–15"]

        Backend -->|"hit"| Caffeine["⚡ Caffeine<br/>in-process, 50k"]
        Backend -->|"miss"| Valkey[("⚡ Valkey<br/>cache + rate limit + click stream")]
        Backend -->|"miss, bulkheaded"| Postgres[("🐘 PostgreSQL 17")]

        Backend -->|"async enqueue"| Valkey
        Valkey -->|"consumer group, batched"| Backend
        Backend -->|"batch insert + counter"| Postgres

        Cron["⏱️ CronJobs<br/>cleanup · retention"] --> Postgres
        Migrate["🔧 pre-upgrade Job<br/>Flyway"] --> Postgres

        Backend -.->|"/actuator/prometheus"| Prom["📊 Prometheus → Grafana → Loki"]
    end
```

Three details worth calling out, because each replaced something that did not work:

**One Valkey, two workloads.** The redirect cache wants LRU eviction; the click stream must
never be evicted. `maxmemory-policy volatile-lru` evicts only keys that carry a TTL — cache and
rate-limit keys do, the stream does not. One instance serves both safely, with no code aware of
the split.

**Maintenance runs once.** Cleanup and retention used to be `@Scheduled` methods, which means
they ran once *per replica*: fifteen concurrent scans over the same rows every thirty minutes,
competing with live traffic for the connection pool. They are CronJobs now, running the same
image and the same code under a `task` profile.

**The client's IP is not the client's choice.** See below.

---

## 🛡️ Rate limiting, and why it used to do nothing

The limiter read `X-Forwarded-For` unconditionally and took the **leftmost** entry. Both halves
are wrong:

```java
// before
String forwardedFor = request.getHeader("X-Forwarded-For");
if (forwardedFor != null && !forwardedFor.isBlank()) {
    return forwardedFor.split(",")[0].trim();   // client-controlled
}
```

Sending a different `X-Forwarded-For` on every request earned a fresh bucket every request. The
quota was unreachable, and duplicating this logic in two classes meant fixing one would not have
fixed the other.

Now there is one [`ClientIpResolver`](backend/src/main/java/com/anish/url_shortener/common/net/ClientIpResolver.java):

- the header is consulted **only** when `app.client-ip.trust-proxy` is on, which the chart sets
  exactly where the ingress is in the path and overwriting the header;
- when it is consulted, the **rightmost** entry wins — the hop we appended, not the one the
  client chose;
- `server.forward-headers-strategy` is `none`, because the framework strategy would rewrite
  `getRemoteAddr()` from the leftmost entry and quietly reintroduce the same hole.

`chart-lint.yml` fails the build if any environment trusts forwarded headers without an ingress
rendered in front of it, and
[`resilience_test.js`](load_tests/resilience_test.js) floods the service with per-request
spoofed headers and asserts they are refused.

Rate limiting is the *last* line of defence, not the first:

| Layer | Handles | Where |
| :-- | :-- | :-- |
| L3/L4 volumetric | SYN floods, amplification | Provider DDoS scrubbing — not solvable in app code |
| L7 edge | Per-IP request/connection floods | nginx ingress `limit-rps`, `limit-connections` |
| Application | Per-user business quotas | Token bucket over Valkey |
| Load shedding | Graceful degradation past capacity | Resilience4j bulkhead → fast 503 |

---

## 📐 The connection budget

This is the constraint the whole deployment is organised around.

An autoscaler that adds pods adds connection pools. With `maximum-pool-size: 50` and
`maxReplicas: 15`, peak demand is **750** connections against `max_connections=200` — exhausted
at four pods. Past that, HikariCP blocks for the full connection timeout and then throws, which
converts a fast failure into a slow one. Scaling out *caused* the failures the autoscaler
existed to prevent.

The chart derives the pool instead of accepting it:

```
pool_per_pod = (postgres.maxConnections − postgres.reservedConnections) / hpa.maxReplicas
```

| Environment | max_connections | reserved | maxReplicas | pool/pod | peak demand |
| :-- | --: | --: | --: | --: | --: |
| dev | 100 | 10 | 3 | 30 | 96 |
| loadtest | 150 | 20 | 8 | 16 | 134 |
| prod | 200 | 20 | 15 | 12 | 186 |

`helm install` fails if the derived pool falls below 2, and CI fails the build if any
environment oversubscribes. The bulkhead is derived from the same number, so load shedding
begins at a level the pool can sustain.

The real answer at larger scale is PgBouncer in transaction mode, which decouples pod count from
connection count entirely. That is Stage 8 in [plan.md](plan.md) and is not built.

---

## 🧪 Testing: three tiers

| Tier | Suite | Trigger | Scale | Answers |
| :-- | :-- | :-- | :-- | :-- |
| **A — Gate** | [`gate_test.js`](load_tests/gate_test.js) | Every PR | 150 VUs, ~2.5 min | Did this change make it worse? |
| **B — Capacity** | [`load_test.js`](load_tests/load_test.js), `spike_test.js`, `soak_test.js` | Manual | 1,000–4,000 VUs | How much can it take? |
| **C — Resilience** | [`resilience_test.js`](load_tests/resilience_test.js) | Manual + nightly | Adversarial | Does abuse reach real users? |

Every suite pre-seeds a corpus in `setup()`, then runs **99% redirects / 1% creates** over it
with a long-tail access pattern, reporting redirect and create latency separately and asserting
a measured cache hit ratio.

The previous suite created a URL and immediately resolved it — a 1:1 write/read ratio against a
guaranteed cache hit. It spent half its requests on the one operation that cannot scale
horizontally and never took a cache miss.
[`load_tests/BASELINE.md`](load_tests/BASELINE.md) covers what changed and why the old numbers
are not comparable.

### Benchmark results

**None published yet.** The suites were rewritten and no run has been recorded against them.
[`PERFORMANCE_RESULTS.md`](load_tests/PERFORMANCE_RESULTS.md) is regenerated by CI on each
Tier B dispatch.

Numbers from CI will be labelled *constrained single-node* and mean it: one GitHub Actions
runner hosting KinD, Postgres, Valkey, every backend pod **and** k6 measures itself as much as
the application. A capacity claim needs a multi-node cluster with load driven from a separate
machine.

Tier C is the result worth having, and it reads like this:

> *Under sustained attack traffic from spoofed sources, legitimate redirect p95 stayed under
> 150 ms and 99%+ of abusive creates were rejected with 429.*

---

## 🛠️ Stack

| Layer | Technologies |
| :--- | :--- |
| **Backend** | Java 21, Spring Boot 4.1, Spring Security, Spring Data JPA, Flyway, Resilience4j |
| **Data** | PostgreSQL 17, Valkey 8 (cache + rate limit + streams), Caffeine |
| **Frontend** | React 19, TypeScript, Vite, React Query — built and served by nginx |
| **Infrastructure** | Docker, Kubernetes, Helm (3 environments), HPA, PDB, CronJobs |
| **Observability** | Micrometer/Prometheus, Grafana (dashboards as code), Loki, prometheus-adapter |
| **Testing** | JUnit 5, Mockito, k6 (gate / capacity / resilience) |

---

## 🚀 Quickstart

Three ways to run it — see [ENVIRONMENTS.md](./ENVIRONMENTS.md) for the full story.

**Local development** — dependencies in Docker, app on your host:

```bash
make dev-up        # postgres :5432, valkey :6379
make dev-backend   # profile: local
make dev-frontend
```

**A cluster:**

```bash
export POSTGRES_PASSWORD="$(openssl rand -base64 32)"
export JWT_SECRET="$(openssl rand -base64 48)"
make prod-deploy

make obs-deploy    # optional: Prometheus + Grafana + Loki, and turn on the app's metrics
```

Secrets have no defaults — a missing one fails the install rather than shipping a known
password.

**Benchmarks in KinD:**

```bash
make test-up && make test-gate && make test-k6 && make test-down
```

### Endpoints

| | |
| :-- | :-- |
| Frontend | `http://localhost` |
| API | `http://localhost/api/v1` |
| Swagger | `http://localhost/swagger-ui/index.html` |
| Metrics | `http://localhost/actuator/prometheus` |

---

## 📂 Repository

```
backend/            Spring Boot application and Flyway migrations
frontend/           React + Vite SPA, built into an nginx image
chart/              Helm chart -- values-dev / values-loadtest / values-prod
chart/observability/  Prometheus, Grafana, Loki, prometheus-adapter, dashboards
load_tests/         k6 suites: gate, load, spike, soak, resilience
dev/                docker-compose for local dependencies
.github/workflows/  chart lint, Tier A gate, Tier B capacity, Tier C resilience
plan.md             What is done, what is not, and why
```

---

## 📜 License

MIT.
