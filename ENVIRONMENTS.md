# Environments

One codebase, one pair of images, **three ways to run it**. The modes differ only in
configuration and orchestration — never in application code.

| | **dev** | **prod** | **test** |
| :-- | :-- | :-- | :-- |
| Runs on | Your laptop | Any Kubernetes cluster | KinD, in GitHub Actions |
| Orchestration | `docker compose` (deps only) | Helm chart | The **same** Helm chart |
| Spring profile | `local` | `prod` | `prod` |
| Values file | — | `chart/values-prod.yaml` | `chart/values-loadtest.yaml` |
| App process | On host (`mvnw`, `vite`) | In cluster | In cluster |
| Rate limiting | On, generous | On, strict | On, high capacity |
| Purpose | Iterate fast | Serve traffic | Produce benchmarks |

**The key discipline:** *test* and *prod* run the same chart, the same templates, and the
same Spring profile. The load-test overlay changes replica ceilings, resource sizing and
rate-limit capacity — nothing that alters the code path under test. If those diverged, the
benchmarks would describe a system that never gets deployed.

---

## dev — local development

Dependencies run in Docker; the application runs on your host so the debugger attaches and
hot reload works.

```bash
make dev-up          # postgres :5432, valkey :6379
make dev-backend     # ./mvnw spring-boot:run, profile: local
make dev-frontend    # vite dev server with HMR
```

```
make dev-down        # stop, keep data
make dev-reset       # stop and wipe volumes
```

Config lives in [`backend/src/main/resources/application-local.yml`](backend/src/main/resources/application-local.yml).
Rate limiting stays **on** (with generous quotas) so the filter is exercised during normal
development rather than only discovered in production.

---

## prod — deploy to a cluster

```bash
export POSTGRES_PASSWORD='...'
export JWT_SECRET='...'          # >= 32 bytes for HS256
make prod-deploy
```

or directly:

```bash
helm upgrade --install urlshortener ./chart \
  -f chart/values-prod.yaml \
  --set secrets.postgresPassword="$POSTGRES_PASSWORD" \
  --set secrets.jwtSecret="$JWT_SECRET" \
  --wait --timeout 10m
```

Secrets have **no defaults**. A missing one fails the install rather than silently shipping
a known password — the previous `k8s/secret.yaml` had both committed in plaintext, so
**rotate them**; they remain in git history.

### On a single VPS / EC2

You chose the Helm-only path, so the box needs a Kubernetes API. `k3s` is the least
ceremony:

```bash
curl -sfL https://get.k3s.io | sh -
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
# k3s ships Traefik; either use ingress.className=traefik or install ingress-nginx
make prod-deploy
```

Set `config.appBaseUrl` and `ingress.host` to your domain, and pin `backend.image.tag` to a
git SHA rather than `latest`.

---

## test — benchmarks in CI

Triggered via **workflow_dispatch** on
[`.github/workflows/k6-load-testing.yml`](.github/workflows/k6-load-testing.yml). It builds
both images, creates KinD, deploys the chart with the loadtest overlay, and runs
load → spike → soak.

Locally:

```bash
make test-up          # KinD + metrics-server + helm install
make test-gate        # Tier A -- the fast PR gate
make test-k6          # Tier B -- load, spike, soak
make test-resilience  # Tier C -- attack traffic vs legitimate users
make test-down
```

### Three tiers, three questions

Different questions need different configurations. Making that explicit is what stopped the
hand-tuning that used to happen before benchmarks.

| Tier | Workflow | Trigger | Scale | Answers |
| :-- | :-- | :-- | :-- | :-- |
| **A — Gate** | `k6-gate.yml` | Every PR | 150 VUs, ~2.5 min | Did this change make it worse? |
| **B — Capacity** | `k6-load-testing.yml` | Manual | 1,000–4,000 VUs | How much can it take? |
| **C — Resilience** | `k6-resilience.yml` | Manual + nightly | Adversarial | Does abuse reach real users? |

Tier A fails the build on a threshold breach or any sign of connection-pool exhaustion. Tiers
B and C report rather than gate, because their numbers are hardware-dependent.

Beyond latency, the workflows capture what latency alone cannot explain:

- **Autoscaling evidence** — HPA state and final pod count, so "it scaled" is recorded rather than asserted.
- **Connection usage** — `pg_stat_activity` against `max_connections`, plus a grep for pool
  exhaustion in the backend logs. This is the failure mode that previously capped the
  success rate near 88%.
- **Analytics drain** — stream length at the end of the run against rows persisted, which is
  the difference between "clicks were recorded" and "clicks were accepted and dropped at MAXLEN".

### The load generator and the rate limiter

k6 drives all traffic from one or two source addresses, which a per-IP limiter correctly reads
as abuse. Rather than raising quotas to a fictional number for benchmark runs — which would
change what the benchmark measures — the generator presents a shared secret in
`X-RateLimit-Bypass` and is skipped. Quotas stay at their production values, the filter and its
Valkey round trip stay in the request path, and traffic *without* the secret is still limited,
which is exactly what Tier C checks.

The secret is empty by default, so nothing can bypass the limiter in production.

### Reading these numbers honestly

A GitHub Actions runner hosts Postgres, Valkey, every backend JVM **and k6 itself** on a
handful of vCPUs. Results are a solid *regression* signal and a poor *capacity* claim. Real
capacity numbers need a multi-node cluster with load driven from a separate machine — see
[load_tests/BASELINE.md](load_tests/BASELINE.md).

---

## The connection-budget invariant

The chart derives the HikariCP pool size instead of accepting it as a free parameter:

```
pool_per_pod = (postgres.maxConnections − postgres.reservedConnections) / hpa.maxReplicas
```

| Environment | max_connections | reserved | maxReplicas | pool/pod | peak demand |
| :-- | --: | --: | --: | --: | --: |
| dev | 100 | 10 | 3 | 30 | 96 |
| loadtest | 150 | 20 | 8 | 16 | 134 |
| prod | 200 | 20 | 15 | 12 | 186 |

Peak demand includes the one-shot task pods (the migration Job and the two maintenance
CronJobs), each capped at 2 connections. The database bulkhead is derived from the same
number — `2 x pool` per pod — so load shedding kicks in at a level the pool can actually
sustain rather than at an arbitrary constant.

This encodes the defect that was capping throughput: a pool of 50 across `maxReplicas: 15`
demands **750** connections against `max_connections=200`, exhausting the database at four
pods — so scaling out *caused* the failures the autoscaler existed to prevent.

`helm install` fails if the derived pool drops below 2, and
[`.github/workflows/chart-lint.yml`](.github/workflows/chart-lint.yml) fails the build if any
environment oversubscribes. Set `config.hikariMaxPoolSize` explicitly only when something
else owns the budget — e.g. PgBouncer (plan.md Stage 8).

---

## Secrets

The chart ships **no** credential defaults. A missing `secrets.postgresPassword` or
`secrets.jwtSecret` fails `helm install` rather than silently deploying a known password.

### Rotate the old ones

`k8s/secret.yaml` used to carry `POSTGRES_PASSWORD` and `JWT_SECRET` in plaintext. That
directory is gone, but **the values remain in git history**, so treat both as compromised:

```bash
# New values
export POSTGRES_PASSWORD="$(openssl rand -base64 32)"
export JWT_SECRET="$(openssl rand -base64 48)"      # >= 32 bytes for HS256

make prod-deploy
```

Rotating `JWT_SECRET` invalidates every issued access token, so all users are signed out at
once. Access tokens live 15 minutes; rotating during a quiet period keeps that cheap.

Rotating `POSTGRES_PASSWORD` on an existing volume needs the database updated too — the
`POSTGRES_PASSWORD` environment variable only takes effect on first initialisation:

```bash
kubectl exec urlshortener-postgres-0 -- \
  psql -U postgres -c "ALTER USER postgres WITH PASSWORD '$POSTGRES_PASSWORD';"
```

History was left intact deliberately: rewriting it rewrites every commit SHA on a public repo
and breaks every clone, which is a poor trade for credentials that only ever protected a
self-hosted database on a local cluster. If this ever holds a real credential, rotate first and
rewrite second.

---

## Observability

Prometheus, Grafana, Loki and prometheus-adapter live in a separate chart
([`chart/observability`](chart/observability)) rather than as a dependency of the application
chart. Two reasons: the app chart stays installable and lintable with no network and no CRDs,
and the monitoring stack has a different lifecycle from the service it watches.

```bash
make obs-deploy    # installs the stack, then enables the app's ServiceMonitor and alert rules
```

Dashboards are ConfigMaps in
[`chart/observability/dashboards`](chart/observability/dashboards), provisioned by the Grafana
sidecar — versioned with the code rather than clicked together and lost on the next
`minikube delete`.

The application exports two things nothing else can supply: `redirect_cache_lookups_total`
(so the hit ratio is measured, not assumed) and `analytics_stream_length` / `_pending` (so a
queue backing up is visible before it hits Valkey's memory ceiling).
