# Architecture

Diagram in the [README](./README.md#architecture).

## Request paths

**A redirect** checks Caffeine, then Valkey, then Postgres, populating each layer on the way
back. It publishes the click to a Valkey stream and returns — no database write on the response
path. An `X-Cache` header reports which layer answered.

**A click** is drained from that stream by a consumer group: one existence query, one batched
insert, one batched counter update per batch. Unacknowledged messages are reclaimed by another
pod if a consumer dies mid-batch.

**Maintenance** runs as CronJobs, not `@Scheduled` methods — expired anonymous links every 30
minutes, click-history retention nightly. Migrations run as a pre-upgrade Helm hook, so an
upgrade never starts a pod against a schema it does not expect; a fresh install has no old schema
to break, and the backend's own Flyway creates it under an advisory lock.

## How it scales

**One Valkey, two workloads.** `maxmemory-policy volatile-lru` evicts only keys carrying a TTL.
Cache and rate-limit keys have one; the click stream does not — so memory pressure drops cache
entries and never analytics.

**Rate limiting is a single Lua script.** Refill arithmetic and bucket state in one atomic round
trip, timestamped by Redis's own clock, so pods with drifting clocks cannot disagree about a
quota.

**The client's IP is not the client's choice.**
[`ClientIpResolver`](backend/src/main/java/com/anish/url_shortener/common/net/ClientIpResolver.java)
reads `X-Forwarded-For` only where the chart puts an ingress in front, and takes the **rightmost**
hop — the one we appended, not one the caller supplied. CI fails the build if any environment
trusts the header without an ingress.

**Load shedding.** Database-bound paths sit behind a Resilience4j bulkhead sized from the
connection pool. Past capacity a request gets an immediate `503` with `Retry-After` rather than
occupying a thread until the pool times out.

**The connection budget.** An autoscaler that adds pods adds connection pools, so the chart
derives the pool from the replica ceiling rather than accepting a number:

```
pool_per_pod = (postgres.maxConnections − reservedConnections) / hpa.maxReplicas
```

| Environment | max_connections | reserved | maxReplicas | pool/pod | peak demand |
| :-- | --: | --: | --: | --: | --: |
| dev | 100 | 10 | 3 | 30 | 96 |
| loadtest | 150 | 20 | 8 | 16 | 134 |
| prod | 200 | 20 | 15 | 12 | 186 |

`helm install` fails if the derived pool drops below 2, and CI fails the build if any environment
oversubscribes. The bulkhead derives from the same number, so shedding begins where the pool gives
out. Past this, the answer is PgBouncer in transaction mode — not built.

## Load testing

| Tier | Suite | Trigger | Scale | Answers |
| :-- | :-- | :-- | :-- | :-- |
| **A — Gate** | [`gate_test.js`](load_tests/gate_test.js) | Every PR | 150 VUs, ~2.5 min | Did this change make it worse? |
| **B — Capacity** | [`load_test.js`](load_tests/load_test.js) · `spike` · `soak` | Manual | 1,000–4,000 VUs | How much can it take? |
| **C — Resilience** | [`resilience_test.js`](load_tests/resilience_test.js) | Manual + nightly | Adversarial | Does abuse reach real users? |

Every suite seeds a corpus in `setup()`, then runs **99% redirects / 1% creates** over it with a
long-tail access pattern, reporting redirect and create latency separately. Tier C runs a
legitimate user population alongside spoofed-header floods and cache-miss storms, and asserts the
legitimate traffic stays fast.

```bash
make test-up && make test-gate && make test-k6 && make test-down   # needs kind
```

> **No capacity numbers are published.** CI results are labelled *constrained single-node* and
> mean it — one runner hosting KinD, Postgres, Valkey, every pod **and** k6 measures itself as
> much as the app. See [`BASELINE.md`](load_tests/BASELINE.md).
