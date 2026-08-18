# URL Shortener — Infrastructure & Scalability Improvement Plan

> Derived from a full read of the Spring Boot backend, `k8s/` manifests, Dockerfiles,
> Flyway migrations, and the k6 CI workflow.
>
> **Guiding principle:** the project's thesis is "a backend that handles large scale and
> resists abuse." Every stage below either makes that claim *true* or makes it *provable*.
> Stages are ordered so each one unblocks the next.

---

## Status

Last synced after the Stage 0-7 implementation pass. See [ENVIRONMENTS.md](ENVIRONMENTS.md).

| Stage | State | Notes |
| :-- | :-- | :-- |
| 0 — Measurement integrity | ✅ Done | Suites rewritten: 99:1 mix, seeded corpus, split latency, measured hit ratio. |
| 1 — Data tier | 🔶 Config done, unverified | Everything applied. **Still needs a cluster run** — the one step nobody can do from a keyboard. |
| 2 — Security & abuse | ✅ Done | XFF fix, Lua token bucket, bulkhead, uid claim, allowlist. Tested. |
| 3 — Helm chart | ✅ Done | Flyway pre-upgrade Job added; `k8s/` deleted. |
| 4 — Runtime hardening | ✅ Done | Dockerfile consumes the CI jar; cleanup moved to CronJobs. |
| 5 — Cache & queue | ✅ Done | N+1, PEL orphaning, cache-hit analytics, hot-path write, retention — all closed. |
| 6 — Observability | ✅ Done except step 8 | RequestIdFilter, structured logs, `chart/observability`, dashboards + alerts as code. |
| 7 — Three-tier testing | ✅ Done except Tier B hardware | Tiers split into three workflows. Real capacity numbers need real hardware. |
| 8 — Cloud migration | ⬜ Not started | Deliberately out of scope until a managed cluster is being provisioned. |

Verified by a local integration run against real Postgres and Valkey: all eight migrations
apply, `ddl-auto: validate` accepts the schema, the token bucket refuses the 6th of 5 requests
with `Retry-After`, a spoofed `X-Forwarded-For` earns no fresh bucket, cached redirects record
clicks, and all three tasks exit cleanly. That run found two defects neither compilation nor
`helm template` could — see [Cross-Cutting Cleanups](#also-fixed-along-the-way-not-in-the-original-plan).

**Two things remain, and both need hardware rather than code:** a benchmark run to close
Stage 1, and a multi-node cluster to make Tier B numbers mean anything.

---

## Current State — What the Code Actually Does

| Path | Behaviour | Bottleneck |
| :--- | :--- | :--- |
| `GET /{shortCode}` | Caffeine (50k, 5min) → Valkey → Postgres, DB miss bulkheaded | Cache-dominated; fast |
| `POST /api/v1/urls` | TSID gen + `saveAndFlush` + cache write, bulkheaded | Postgres write |
| Authenticated API | JWT verify, principal built from claims — **no database read** | — |
| Click analytics | `@Async` enrich → Valkey stream → batched insert + batched counter | Off hot path, no N+1 |
| Maintenance | CronJobs under the `task` profile | One execution, not one per replica |

The application is stateless and scales horizontally. **Postgres is still the only true
bottleneck** — but the configuration no longer makes it worse, and the paths that reach it
now shed load instead of queueing into a timeout.

### Blocking Defects Found

| # | Defect | Impact | State |
| :- | :--- | :--- | :--- |
| D1 | 15 pods × 50 pool = **750 connections** vs `max_connections=200` | Scaling out *causes* failures | ✅ Pool derived in chart; CI enforces the budget |
| D2 | Postgres capped at 500m CPU / 512Mi for 200 connections | Cannot fit the connections it advertises | ✅ 1-2 CPU / 2-4Gi, StatefulSet + PVC |
| D3 | Valkey: no `maxmemory`, no eviction policy, no persistence | OOMKill drops cache **and** unprocessed clicks | ✅ `maxmemory` + `volatile-lru` + AOF |
| D4 | `X-Forwarded-For` trusted blindly, leftmost entry | Rate limiting provides **zero** protection | ✅ `ClientIpResolver`; rightmost hop, gated on trust-proxy; CI invariant; resilience test |
| D5 | Frontend ran the Vite dev server in production | No gzip/caching, on-demand transpile | ✅ Multi-stage nginx, 48.6MB |
| D6 | No graceful shutdown anywhere | Scale-down kills in-flight requests | ✅ Graceful shutdown + preStop + PDB |
| D7 | Cache-hit redirect recorded **no** click | Analytics lost for exactly the busiest links | ✅ `urlId` in the cache entry; click published on the hit path |
| D8 | Consumer did `findById` **per message** | N+1 across a batch of 1000 | ✅ One projection query, one batched insert, one batched counter |
| D9 | Random UUID consumer name, no reclaim | Messages orphaned in the PEL forever on restart | ✅ Name from `HOSTNAME` + XCLAIM reclaim pass |
| D10 | Load test used a **1:1 write/read** ratio, 100% cache hits | Benchmark measured neither the cache nor the architecture | ✅ Seeded corpus, 99:1 mix, long tail, split latency, measured hit ratio |
| D11 | GeoIP database path empty in all profiles | Country always `"Unknown"` while the README claimed otherwise | ✅ Removed end to end — dependency, column, DTO, UI field, claim |
| D12 | Both schedulers fanned out to all 15 replicas | 15 concurrent scans + batch deletes | ✅ CronJobs under the `task` profile |
| D13 | HPA declared `http_requests_per_second` with no provider | `FailedGetPodsMetric`; blocks scale-**down** | ✅ Off by default; prometheus-adapter serves it when observability is installed |
| D14 | Secrets committed in plaintext | Credentials in git history | 🔶 `k8s/` deleted, chart requires them, rotation documented. **The old values are still in history — rotate them** |
| D15 | 8 zero-byte Java files incl. `RequestIdFilter` | No correlation IDs for log aggregation | ✅ `RequestIdFilter` implemented; the other seven deleted |
| D16 | `backend/.gitignore` patterns unanchored | Profile configs were never committed | ✅ Fixed |
| D17 | `package-lock.json` out of sync | `npm ci` refused | ✅ Regenerated. **`npm audit` still reports 5 vulnerabilities (1 moderate, 4 high) — unaddressed** |
| D18 | Fixed-window `INCR` with a non-atomic `EXPIRE` | 2× capacity across a window boundary; a dying pod leaked a TTL-less key | ✅ Single atomic Lua token bucket using Redis's own clock |
| D19 | Synchronous `urlRepository.save` per redirect | A blocking Postgres write on the hot path, for a counter nobody read synchronously | ✅ Folded into the batched consumer update |
| D20 | Two disagreeing click counters | `COUNT(*)` in one screen, `urls.click_count` in another | ✅ `urls.click_count` is authoritative |
| D21 | `url_clicks` grew without bound | A soak run adds ~700k rows | ✅ Retention CronJob, 90 days by default |

## Stage 0 — Measurement Integrity  ✅ *done*

**Goal:** make the benchmark measure the system that was actually built, so every
later stage has a credible before/after.

**Why:** `load_test.js` created a URL then immediately resolved it. That is a 1:1
write/read ratio (real shortener traffic is ~1000:1 reads) and a guaranteed cache hit,
because `UrlService.create()` calls `putForAnonymousRedirect()`. The suite therefore
never measured cache-miss behaviour, never measured hit ratio, never touched analytics,
and spent ~50% of its time on the one operation that cannot scale horizontally.

### Steps

1. ✅ **Seeded corpus.** `seedShortCodes()` in `load_tests/lib/workload.js`, called from each
   suite's `setup()` so its cost is outside the measurement window. Seeds in parallel batches
   of 50 — 5,000 sequential round trips would have added minutes of dead time to every run.
   `SEED_SIZE` is an environment variable; raise it above the 50k Caffeine cap for capacity
   runs so Valkey does real work.
2. ✅ **99% redirects / 1% creates**, selecting from the pool with a long-tail draw
   (`u^2.5`, tunable via `ZIPF_SKEW`). Not a true Zipf distribution — the shape of the tail is
   what matters here, and this costs one multiply per iteration.
3. ✅ **Custom metrics.** `redirect_duration` and `create_duration` as separate trends,
   `redirect_cache_hit` as a Rate derived from a new `X-Cache` response header, plus
   `responses_429` / `responses_503` counters. The backend also exports
   `redirect_cache_lookups_total{result}` for the Grafana side of the same question.
4. ✅ **`load_tests/BASELINE.md`** — written, explaining why the old numbers are void, what the
   suites do now, and how to record a real baseline. **The results table is deliberately empty:
   no run has happened.** `PERFORMANCE_RESULTS.md` was reset for the same reason.

**Files:** `load_tests/lib/workload.js` (new), `load_test.js`, `spike_test.js`, `soak_test.js`,
`gate_test.js` (new), `resilience_test.js` (new), `BASELINE.md` (new)

**Exit criteria:** ✅ Suites report redirect p95 and create p95 separately and assert a measured
cache hit ratio. 🔶 A documented baseline exists as a *procedure*; the numbers need a run.

---

## Stage 1 — Unblock the Data Tier  🔶 *config applied, not yet verified*

**Goal:** stop the autoscaler from exhausting Postgres. This is the single highest-value change.

**Why (D1):** `maximum-pool-size: 50` × `maxReplicas: 15` demands 750 connections against
`max_connections=200`. The ceiling is crossed at **4 pods**. Past that, HikariCP blocks
for the full 10s `connection-timeout` and then throws — which is the mechanism behind the
88.52% success rate and the 2.3s p95. Raising the pool from 15 to 50 made this *worse*,
not better: it converted fast failures into slow ones.

### Steps

1. ✅ **Pool math.** Implemented *better than planned*: rather than hardcoding 12, the chart
   **derives** it in `urlshortener.hikariPoolSize` (`chart/templates/_helpers.tpl`):
   ```
   pool_per_pod = (postgres.maxConnections − postgres.reservedConnections) / hpa.maxReplicas
   ```
   Rendered: dev 30, loadtest 16, **prod 12**. The install fails if the result drops below 2,
   and `chart-lint.yml` fails the build if any environment oversubscribes — so the two values
   cannot silently drift apart again.
2. ✅ **Postgres sizing.** `requests: 1 CPU / 2Gi`, `limits: 2 CPU / 4Gi` in `values-prod.yaml`.
   Also moved Deployment → StatefulSet with `volumeClaimTemplates`.
3. ✅ **Indexes** added as `V7__performance_indexes.sql` (both indexes as specified).
4. ✅ **Tomcat threads** 1000 → 200, `accept-count` 2000 → 500, both env-driven.
5. ⬜ **Re-run the Stage 0 suites and record the delta.** ← *the only remaining step. Stage 0
   has landed, so nothing blocks it but a cluster:*

   ```bash
   make test-up && make test-gate && make test-k6
   make db-connections          # the invariant, live
   make test-down
   ```

   The Tier A workflow now fails the build on any `SQLTransientConnectionException`, so the
   regression cannot come back silently even before anyone reads a chart.

**Files (as built):** `backend/src/main/resources/application.yml`,
`chart/values*.yaml`, `chart/templates/_helpers.tpl`, `chart/templates/postgres.yaml`,
`backend/src/main/resources/db/migration/V7__performance_indexes.sql`

**Exit criteria (still unmet — no benchmark has been run):** No `SQLTransientConnectionException` in logs at peak; success rate >99%
on the standard load suite; total connections at max scale verified under 200 via
`SELECT count(*) FROM pg_stat_activity`.

---

## Stage 2 — Security & Abuse Resistance  ✅ *done*

**Goal:** make rate limiting real, then turn it back on permanently.

**Why (D4):** `resolveIpAddress()` took the first entry of `X-Forwarded-For` with no
trusted-proxy validation. Any client could send a random `X-Forwarded-For` per request and
receive a fresh rate-limit bucket every time. The limiter was trivially bypassable, so the
feature had never been validated under load — and the logic was duplicated in two classes, so
fixing one would not have fixed the other.

### Steps

1. ✅ **Client-IP resolution.** One `common/net/ClientIpResolver`, used by both the controller
   and the filter. The header is read **only** when `app.client-ip.trust-proxy` is on, and then
   the **rightmost** entry wins — the hop our proxy appended, not the one the client chose.
   `server.forward-headers-strategy` is `none`, because `framework` would rewrite
   `getRemoteAddr()` from the leftmost entry and reintroduce the hole underneath us. The
   ingress overwrites the header (`use-forwarded-headers: false`,
   `compute-full-forwarded-for: false`), and `chart-lint.yml` fails the build if any
   environment trusts the header with no ingress rendered in front of it.
2. ✅ **Real token bucket.** `scripts/token_bucket.lua` — refill arithmetic and state in one
   atomic script, so there is no INCR/EXPIRE pair to die between and no window boundary to
   spend twice across. Time comes from `redis.call('TIME')` rather than the caller, so pods
   with skewed clocks cannot disagree about how much a bucket has refilled. Fails **open** on a
   Valkey outage, logged: a cache failure should degrade abuse protection, not availability.
3. ✅ **Realistic quotas** — anonymous 20/min, authenticated 200/min.
4. ✅ **Enabled by default, and the allowlist is built.** `values-loadtest.yaml` no longer
   raises capacity to 1,000,000: quotas are the production ones and the generator presents a
   shared secret in `X-RateLimit-Bypass`. Constant-time comparison, empty by default, so
   nothing can bypass it in production. This matters for measurement integrity — the benchmark
   now exercises the configuration that actually ships.
5. ✅ **Load shedding.** A Resilience4j semaphore bulkhead named `database`, sized at 2× the
   derived pool, on `UrlService.create` and the new `UrlLookupService` (the cache-miss path).
   Past capacity: immediate `503` with `Retry-After`, not a thread parked on the connection
   pool for ten seconds before failing anyway.
6. ✅ **Per-request DB lookup removed.** A `uid` claim in the token; the principal is built from
   the signed claims. Tokens issued before the claim existed fall back to the lookup until they
   expire. Also collapsed `isValid()` + `extractEmail()` into one `parse()` — the signature was
   being verified twice per authenticated request.

**Tests:** `ClientIpResolverTest` (3), `UrlCreationRateLimitFilterTest` (4) — including
`spoofedForwardedForDoesNotEarnAFreshBucket`, which sends 50 requests with 50 different
forwarded headers and asserts they all map to one bucket.

**Exit criteria:** ✅ all three. Spoofing grants no extra quota (unit-tested, and attacked end
to end by `resilience_test.js`); rate limiting is on by default; overload returns 503.

---

## Stage 3 — Helm Chart  ✅ *done*

**Goal:** one chart, three environments. Every subsequent stage becomes a values entry.

### Delivered

```
chart/
  Chart.yaml  values.yaml
  values-dev.yaml       HPA 1-3,  NodePort, pullPolicy Never
  values-loadtest.yaml  HPA 2-8,  runner-sized, CI overlay
  values-prod.yaml      HPA 3-15, registry, limits enforced, secrets required
  templates/
    _helpers.tpl              labels, image ref, POOL DERIVATION, secret guard
    backend.yaml              Service + Deployment + HPA + PDB
    frontend.yaml             Service + Deployment
    postgres.yaml             Service + StatefulSet
    valkey.yaml               Service + StatefulSet
    valkey-configmap.yaml     valkey.conf (maxmemory / eviction policy)
    configmap.yaml  secret.yaml  ingress.yaml  NOTES.txt
```

Verified: `helm lint` and `helm template` clean on all three values files; a prod install
**fails** when secrets are absent; `chart-lint.yml` enforces the connection-budget invariant
on every PR.

### Deviations from the original plan — all deliberate

| Planned | Built | Why |
| :-- | :-- | :-- |
| `values-staging.yaml` | omitted | No staging environment exists. Add when one does. |
| Split `backend-deployment` / `backend-service` files | one `backend.yaml` per component | Fewer files, each holding one component's full set of objects. |
| `valkey-cache` + `valkey-stream` | single Valkey | Replaced by the `volatile-lru` fix — see Stage 5. |
| `migrate-job.yaml` (Flyway pre-upgrade hook) | ✅ **built** | See below. |
| `cleanup-cronjob.yaml` | ✅ **built** as `maintenance-cronjobs.yaml` | Covers both cleanup and click retention. |

### Since delivered

- ✅ **Flyway pre-upgrade Job** (`templates/migrate-job.yaml`). Runs the service image with
  `SPRING_PROFILES_ACTIVE=prod,task` and `APP_TASK=migrate`, so the migrations applied are
  necessarily the ones the application expects — no second copy in a ConfigMap to drift.
  Named per release revision, because a Job's pod template is immutable and reusing the name
  makes the *second* upgrade fail rather than migrate. Capped at 2 connections so it cannot
  push the cluster over budget during a rollout. Flyway still runs on app startup as well; the
  advisory lock makes that safe, and the Job simply front-loads it and fails the release on a
  bad migration instead of crash-looping the new pods.
- ✅ **`k8s/` deleted.** The chart had parity and the manifests were already drifting.

### Also added here

- **`templates/servicemonitor.yaml`** and **`templates/prometheusrule.yaml`**, both off by
  default: they need the Prometheus Operator CRDs, and a chart that fails to install on a bare
  cluster is worse than one that needs a flag.
- **A second CI invariant** — forwarded headers may only be trusted where an Ingress renders.

---

## Stage 4 — Runtime Hardening  ✅ *done*

**Goal:** make pods survivable, right-sized, and safe to scale down.

### Steps

1. ✅ **Frontend production build.** Multi-stage → nginx. **48.6MB** image (was `node:22-alpine`
   plus `node_modules` running a Vite dev server). Verified serving: `/healthz`, SPA history
   fallback, gzip, and a single immutable `Cache-Control` on hashed assets.
2. ✅ **Graceful shutdown.** `server.shutdown: graceful`,
   `spring.lifecycle.timeout-per-shutdown-phase: 30s`, `terminationGracePeriodSeconds: 45`,
   `preStop: sleep 5`, `maxUnavailable: 0`.
3. ✅ **Resource limits** on every workload, plus
   `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError`.
4. ✅ **Probes.** `startupProbe` replaces the 70s/90s delays; liveness and readiness split
   onto `/actuator/health/{liveness,readiness}` with `probes.enabled: true`.
5. ✅ **Backend Dockerfile.** Now `ARG JAR_FILE=target/*.jar` — the image packages the artifact
   Maven already built rather than recompiling from source, so CI compiles once and the image
   is demonstrably the jar the tests passed against. A `.dockerignore` keeps the build context
   to the jar alone. Layered extraction and the non-root user are unchanged.
6. ✅ **PodDisruptionBudget** + pod anti-affinity.
7. ✅ **Maintenance moved to CronJobs (D12).** Introduced a `task` profile and a `TaskRunner`
   that executes one named job and exits: `migrate`, `cleanup-anonymous`, `clicks-retention`.
   Under that profile the app starts no web server and schedules nothing, so a one-shot pod
   cannot duplicate the work it was created to do exactly once. `MaintenanceScheduler` still
   exists for running without an orchestrator, but is `@ConditionalOnProperty` and **off by
   default** — the dev profile turns it on so a laptop still cleans up after itself.
   The cleanup query is now a single `DELETE`, not load-then-`deleteAllInBatch`.

**Exit criteria:** Rolling deploy with zero failed requests under sustained load *(unverified
— needs a cluster run)*; frontend serves static assets with cache headers *(verified)*.

---

## Stage 5 — Cache & Queue Correctness  ✅ *done*

**Goal:** remove the OOM cliff, and make analytics actually correct.

**Why (D3):** one Valkey serves two workloads with **incompatible** memory policies. The
redirect cache wants LRU eviction; the analytics stream must never be evicted. With no
`maxmemory` set, 24h-TTL redirect keys accumulate until the 256Mi container is OOMKilled —
taking the cache *and* every unprocessed click with it, silently.

### Steps

1. ✅ **~~Split Valkey into two instances.~~ Superseded by a simpler fix.**

   The split was proposed because the cache wants LRU eviction while the stream must never be
   evicted — policies a single instance appeared unable to reconcile. It can:

   > **`maxmemory-policy volatile-lru` evicts only keys that carry a TTL.**
   > `redirect:code:*` (24h TTL) and `rl:create:*` (window TTL) are evictable and rebuild
   > themselves. `analytics:click:stream` has **no TTL**, so click data is never evicted to
   > make room for cache entries.

   Now shipped in `chart/templates/valkey-configmap.yaml` with bounded `maxmemory`, AOF
   persistence, and a PVC. This closes D3 with **zero code changes** — the split would have
   required rewiring `RedirectCacheService` and `AsyncAnalyticsService` onto separate
   connection factories.

   **The split is now an optimisation, not a correctness fix.** Do it only when the two
   workloads demonstrably contend — e.g. stream depth growth degrades redirect p95 (Stage 6
   dashboards will show this). Until then it is one less stateful service to operate.
2. ✅ **Consumer N+1 (D8) fixed.** The per-message `findById` is gone. `ClickBatchWriter` now
   does one projection query (`select u.id from Url u where u.id in :ids`) to learn which ids
   still exist, one JDBC batch insert, and one JDBC batch of counter increments — a fixed
   number of statements whatever the batch size. Deliberately a projection, not entities: the
   batch only needs to know a row exists, and loading whole urls to learn that was the
   expensive half. Clicks whose url was deleted mid-flight are skipped rather than aborting
   the batch on a foreign key violation.
3. ✅ **PEL orphaning (D9) fixed.** Consumer name comes from `HOSTNAME`, and a
   `reclaimOrphaned()` pass every 60s lists the group's pending entries, filters those idle
   beyond `app.analytics.reclaim-idle-ms`, XCLAIMs them, and drains them by reading its own
   PEL at offset `0`. At-least-once, never zero.
4. ✅ **Group creation moved to `@PostConstruct`**, catching only BUSYGROUP. The old
   `catch (Exception e) {}` on every poll made an unreachable Valkey look exactly like a group
   that already existed. A `groupReady` flag retries lazily, so Valkey being down at boot no
   longer stops the application from starting.
5. ✅ **Stream capped with `MAXLEN ~` on write**, replacing the consumer's XDEL. XDEL was both
   a second round trip and no protection: if the consumer fell behind or died, nothing trimmed.
   Producer-side trimming bounds the stream regardless of consumer health.
6. ✅ **Cache-hit analytics gap (D7) closed.** `CachedRedirectEntry` now carries `urlId`, so the
   cache-hit path publishes a click without a database read. The `url.getUser() != null` gate
   is gone — it was silently discarding every click on an anonymous link, which is most of
   them. Entries cached before `urlId` existed simply record nothing rather than failing.
7. ✅ **`clickCount` write moved off the hot path.** `completeRedirect` no longer saves. The
   consumer folds increments into one batched `UPDATE urls SET click_count = click_count + ?`.
   Consequence, documented in the code: a `maxClicks` ceiling is now enforced within a batch
   interval rather than instantly. Links with `maxClicks` are never cached, so every one of
   their redirects still reaches the check.
8. ✅ **Counters reconciled.** `urls.click_count` is authoritative; `AnalyticsService` reports it
   instead of `COUNT(*)` over `url_clicks`. They were always going to disagree once retention
   started deleting rows.
9. ✅ **`url_clicks` retention.** A `clicks-retention` task and CronJob, 90 days by default
   (7 in loadtest, and disabled there for the capacity suites — a delete sweep during a
   benchmark is noise in the measurement). Retention rather than partitioning: partitioning is
   the better answer at a volume this table is nowhere near, and it is a migration that cannot
   be undone casually.

**Metrics added here:** `analytics_stream_length` and `analytics_stream_pending` gauges. Depth
growing while pending stays flat means the consumer is too slow; pending growing means messages
are being stranded. Neither was observable before — the queue could back up all the way to
Valkey's memory ceiling in silence.

**Note:** RabbitMQ was evaluated and **rejected**. Redis Streams with consumer groups,
explicit acks and a PEL is the same primitive class; the defects above are implementation
bugs that a broker swap would not fix (the N+1 would carry over). Revisit only if fan-out to
multiple independent consumers (webhooks, notifications) is needed.

**Exit criteria:** ✅ Valkey memory bounded and observable (gauges + alert); a pod kill
mid-batch loses no clicks (reclaim pass, unacked entries survive); cached redirects record
analytics; no synchronous Postgres write on the redirect path.

---

## Stage 6 — Observability  ✅ *done except step 8*

**Goal:** measure and prove the previous five stages.

**Why:** the app exported Prometheus metrics but **no Prometheus server or Grafana existed in
the repo** — only in a local Minikube, so it vanished on `minikube delete` and never ran in CI.

### Steps

1. ✅ **`RequestIdFilter` implemented** (it was a zero-byte file). Accepts an inbound
   `X-Request-Id` or generates one, puts it in the MDC, echoes it on the response, and clears
   it in a `finally` — threads are pooled, and a leftover value would stamp the next, unrelated
   request. Registered at `HIGHEST_PRECEDENCE`, because an id that appears halfway down the
   filter chain cannot correlate the lines emitted before it. The inbound value is
   client-controlled and ends up in log output, so it is length-capped and character-restricted
   to something that cannot forge a log line.
2. ✅ **Structured logging.** `logging.structured.format.console: ecs` in the prod profile —
   Spring Boot 4's built-in support, so no new dependency. MDC entries, including the request
   id, become fields. Prod previously ran at Spring defaults because `logging:` existed only in
   `application-local.yml`.
3. ✅ **`chart/observability`** — a separate umbrella chart with `kube-prometheus-stack`,
   `prometheus-adapter` and `loki-stack` as dependencies, plus a `ServiceMonitor` in the app
   chart. Deliberately *not* a dependency of the application chart: the app chart stays
   installable and lintable with no network and no CRDs, and the monitoring stack has a
   different lifecycle from the service it watches.
   Note `serviceMonitorSelectorNilUsesHelmValues: false` — the default only matches the
   operator's own release label, which silently ignores the application's ServiceMonitor and
   leaves you staring at an empty dashboard.
4. ✅ **`prometheus-adapter` (D13).** A custom rule maps `http_server_requests_seconds_count`
   to `http_requests_per_second` as a Pods metric. The HPA's `requestsPerSecond` block stays
   opt-in: an unservable metric produces `FailedGetPodsMetric` and blocks scale-**down**, which
   is worse than not declaring it.
5. ✅ **Dashboards as code.** `chart/observability/dashboards/url-shortener.json`, 19 panels,
   provisioned as a ConfigMap via the Grafana sidecar: redirect p50/p95/p99, cache hit ratio,
   cache lookups by source, create-vs-redirect p95, status codes, HikariCP active/idle/pending
   plus acquire time, analytics stream depth and pending, Valkey memory, JVM heap, HPA replicas
   and CPU. CI asserts each file parses — a dashboard that fails to parse is silently ignored
   by the sidecar, which looks exactly like one that was never provisioned.
6. ✅ **`loki-stack`** with Promtail, wired as a Grafana data source with a `derivedFields` rule
   that extracts `requestId` from the JSON log line. This is the payoff for step 1: without a
   correlation id there is nothing to derive a field from.
7. ✅ **Alerts** (`templates/prometheusrule.yaml`): pool saturation, redirect p95 breach, stream
   backlog, stranded messages, Valkey memory, `ScalingActive=false`, and sustained load
   shedding. Also enabled server-side histogram buckets for `http.server.requests` — without
   them `histogram_quantile()` has nothing to work from and every latency alert has to be built
   on client-side numbers.
8. ⬜ **Export dashboard snapshots as CI artifacts.** Not built. It requires standing up the
   whole observability stack inside the CI run to render images from, which roughly doubles an
   already heavy job to produce a screenshot. The dashboards themselves are in git, which was
   the actual problem — reproducibility, not screenshots.

**New application metrics:** `redirect_cache_lookups_total{result}` (local_hit / remote_hit /
miss) and `analytics_stream_length` / `analytics_stream_pending`. Nothing else could supply
these, and without them the hit ratio and the queue depth were both unknowable.

**Exit criteria:** ✅ A single Grafana view correlating a k6 run's latency spike with HPA
scaling, pool saturation and stream depth; logs queryable by request ID. *(Renders and lints;
the correlation itself is unobserved until someone runs a load test against a live stack.)*

---

## Stage 7 — Three-Tier Test Strategy  ✅ *tiers split; Tier B still needs real hardware*

**Goal:** resolve the tension that caused hand-tuning before benchmarks. Different questions
need different configs — made explicit and version-controlled rather than edited ad hoc.

| Tier | Workflow | When | Scale | Config | Proves |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **A. CI gate** | `k6-gate.yml` | Every PR | 150 VUs, ~2.5 min | rate limiting **on**, generator allowlisted | No regressions |
| **B. Capacity** | `k6-load-testing.yml` | Manual | 1,000–4,000 VUs | loadtest overlay | Throughput |
| **C. Resilience** | `k6-resilience.yml` | Manual + nightly | Adversarial | production abuse defaults | Abuse resistance |

### Steps

1. ✅ **Tier A split out.** `k6-gate.yml` runs on every PR: backend unit tests, frontend
   typecheck and build, jar packaged once and passed to the image build as an artifact, then
   KinD + Helm + `gate_test.js`. **No `|| true`** — a threshold breach fails the pull request,
   which is the entire point. It also asserts the migration hook ran and hard-fails on any
   `SQLTransientConnectionException`, the exact defect that capped success at 88%.
   Deliberately small: at 150 VUs the runner is not the bottleneck, so a breach means the code
   changed rather than that the hardware was busy.
2. 🔶 **Tier B — honest about the runner.** The workflow now prints the runner's core count
   next to the results, labels the output "constrained single-node CI", and the generated
   `PERFORMANCE_RESULTS.md` says in its own header that these are regression numbers, not
   capacity numbers. The stale table of inflated figures has been removed rather than left to
   be quoted.
   **Still outstanding, and it is hardware, not code:** a 3+ node cluster with load driven from
   a separate machine. Two cheap droplets for an afternoon. Procedure is in
   `load_tests/BASELINE.md`.
3. ✅ **Tier C — the differentiator.** `load_tests/resilience_test.js`, four concurrent
   scenarios: a legitimate-user population running the whole time (so the comparison is against
   this run's own baseline), a single-IP create flood, the same flood with a spoofed
   `X-Forwarded-For` per request, and a cache-miss storm on non-existent codes. Success is
   inverted — attack traffic should be *refused*, and refusal should be cheap enough not to
   move legitimate latency. Thresholds: legit p95 < 150ms, ≥95% of both floods blocked.
   **Not covered: slowloris.** Holding a request open with a trickle of bytes needs
   socket-level control k6 does not expose, and faking it with slow full requests would prove
   nothing. Said so in the file rather than claiming coverage.
4. ✅ **README benchmark table rewritten** — no numbers, because none have been produced against
   the new suites, and an explanation of what a number from each tier will and will not mean.

---

## Stage 8 — Cloud Migration  ⬜ *not started — deliberately*

**Goal:** leave KinD. Terraform owns infrastructure; Helm owns the application.

**Only start once a managed cluster is actually being provisioned.** Scoped out of the
Stage 0-7 pass on that basis: Terraform modules for infrastructure nobody is provisioning are
untested scaffolding that will be wrong by the time they are used.

Two pieces of it did land early, because they were cheap and useful without a cloud account:
the layered abuse-defence model is documented in the README, and the ingress already carries
optional `limit-rps` / `limit-connections` annotations.

### Steps

1. `terraform/` with modules: `network` (VPC/subnets), `cluster` (node pool sized for 15
   backend pods plus overhead), `database` (managed Postgres, replacing `postgres.yaml`).
2. Remote state in object storage with locking, from day one.
3. **PgBouncer — the real answer to "handles large scale."** It decouples pod count from
   connection count:
   ```
   50 pods × pool of 5  →  PgBouncer (transaction mode)  →  ~40 real Postgres connections
   ```
   The HPA can then scale without Postgres noticing, retiring the Stage 1 arithmetic entirely.
   *Caveat:* transaction pooling requires `prepareThreshold=0` on the JDBC URL, since
   Hibernate relies on prepared statements.
4. **Edge protection.** Cloudflare (or provider DDoS scrubbing) in front of the ingress —
   L3/L4 volumetric attacks are not solvable in application code. Add nginx ingress
   `limit_req` / `limit_conn` annotations so floods are rejected at the proxy before consuming
   a Tomcat thread, a Redis round-trip, or a DB connection.

   **Layered model — app-level rate limiting is the *last* line of defence, not the first:**
   | Layer | Handles | Where |
   | :-- | :-- | :-- |
   | L3/L4 volumetric | SYN floods, amplification | Cloudflare / provider — not in-app |
   | L7 edge | Per-IP request/connection floods | nginx ingress `limit_req`, `limit_conn` |
   | Application | Per-user business quotas | Stage 2 filter |
   | Load shedding | Graceful degradation past capacity | Resilience4j bulkhead → fast 503 |
5. Terraform outputs the DB endpoint; it feeds `values-prod.yaml`. That boundary is the point
   of the exercise.
6. Optional: read replica for analytics queries, keeping the primary write-only for the hot path.

---

## Cross-Cutting Cleanups

- ✅ **Zero-byte Java files deleted.** `common/entity/BaseEntity`, `AuditableEntity`,
  `common/exception/*`, `common/response/*`, `config/JpaConfig` — dead scaffolding, removed.
  `common/filter/RequestIdFilter` was implemented instead (Stage 6).
- ✅ **The duplicate empty `GlobalExceptionHandler`** in `common/exception/` is gone; the real
  one in `exception/` gained a `ServiceOverloadedException` handler that emits `Retry-After`.
- ✅ **GeoIP removed (D11).** `app.geoip.database-path` was empty in every profile, so
  `resolveCountry()` always returned `"Unknown"` while the README advertised geographic
  analytics. Rather than ship a MaxMind database and a licence-key rotation job for a feature
  nothing depended on, it was removed end to end: the `com.maxmind.geoip2` dependency, the
  reader, the `country` column (`V8__drop_click_country.sql`), the field in
  `EnrichedClickContext` and `ClickHistoryResponse`, the TypeScript interface, and the claim.
- ✅ **`backend/.gitignore` anchoring fixed (D16).**
- ✅ **`package-lock.json` regenerated (D17).** ⬜ `npm audit` still reports 5 vulnerabilities
  (1 moderate, 4 high). Not addressed in this pass — `npm audit fix` on a transitive Vite
  dependency chain wants review, not a reflex.
- 🔶 **Rotate `POSTGRES_PASSWORD` and `JWT_SECRET`.** `k8s/` is deleted and the chart requires
  both with no defaults, but the old values remain in git history. Rotation procedure —
  including the `ALTER USER` that the `POSTGRES_PASSWORD` env var will *not* do on an existing
  volume — is in [ENVIRONMENTS.md](ENVIRONMENTS.md#secrets). History was left intact on
  purpose: rewriting it rewrites every commit SHA on a public repo and breaks every clone,
  which is a poor trade for credentials that only ever protected a local database.
- ✅ **README accuracy pass.** "Access/refresh token rotation" removed — `AuthController`
  exposes only register and login, and no refresh endpoint exists. "Distributed token bucket"
  kept, because it is now true. "Sub-millisecond redirection" replaced with a measured cache
  hit ratio. The benchmark table is empty rather than stale.

### Also fixed along the way, not in the original plan

The first four were found by reading; the last two only by booting the application
against a real Postgres and Valkey, which is an argument for doing that before a
cluster run rather than after.

- **The redirect cache was never populated on a miss.** `putForAnonymousRedirect` was
  called only from `create()` and `update()`. Any link not created by recent activity —
  and *every* link after a Valkey eviction, a restart, or a TTL expiry — missed on every
  single request and went to Postgres forever. The cache-first redirect path, which is the
  premise the whole architecture rests on, only held for links the same deployment had
  recently created. `resolveRedirect` now populates on the way back from a database hit.
  Note the seeded load test would have hidden this: seeding creates the corpus, which warms
  the cache, so the hit ratio would have looked perfect.
- **Task pods could not start.** `SecurityConfig` declares a bean taking `HttpSecurity`,
  which does not exist without a servlet web context — so the `task` profile, and therefore
  the migration Job and both maintenance CronJobs, failed at startup. `SecurityConfig` is
  now `@Profile("!task")`, with `PasswordEncoder` moved to `AppConfig` because `AuthService`
  needs it in every context. This would have surfaced as a crash-looping Job on the first
  `helm upgrade`.
- **JWT signature verified twice per request** — `isValid()` then `extractEmail()` both parsed
  the token. Collapsed into one `parse()` returning `Optional<Claims>`.
- **Hibernate batching was never enabled**, so the consumer's "batch" insert was a thousand
  individual round trips. `jdbc.batch_size: 100` plus ordered inserts and updates.
- **Redis stream payloads could contain nulls** — a missing `Referer` is normal, and stream
  fields cannot be null. Empty strings on write, normalised back to null on read.
- **`AuthService.register` generated a token from the pre-save instance** rather than the
  entity returned by `save()`.

## Sequencing Summary

```
Stage 0  Measurement integrity      ✅  suites now model a shortener's traffic
Stage 1  Data tier unblock          🔶  config applied; needs one benchmark run
Stage 2  Security & abuse           ✅  XFF fixed, real token bucket, load shedding
Stage 3  Helm chart                 ✅  migrate Job added, k8s/ deleted
Stage 4  Runtime hardening          ✅  CronJobs, jar-based image
Stage 5  Cache & queue correctness  ✅  N+1, PEL, cache-hit analytics, retention
Stage 6  Observability              ✅  request ids → Prometheus → Grafana → Loki
Stage 7  Three-tier testing         ✅  three workflows; Tier B needs real hardware
Stage 8  Cloud + Terraform + PgBouncer   ⬜  gated on a managed cluster
```

### What to do next

Everything that can be done from a keyboard is done. What remains needs machines:

1. **Run the suites.** This closes Stage 1 and produces the first real before/after.
   ```bash
   make test-up && make test-gate && make test-k6 && make test-resilience
   make db-connections
   make test-down
   ```
   Record the results in `load_tests/BASELINE.md`. Tier C's output is the one worth putting in
   front of people.
2. **Rotate the two secrets** still sitting in git history
   ([procedure](ENVIRONMENTS.md#secrets)).
3. **Tier B on real hardware** — a 3+ node cluster with load driven from a separate machine.
   Until then, every number this project produces is a regression signal and should be labelled
   as one. It now is, everywhere.
4. **Decide on the 5 npm vulnerabilities.**
5. **Stage 8**, when there is a cluster to migrate to. PgBouncer is the change that retires the
   Stage 1 arithmetic entirely.

**The framing holds.** The README's old headline — the HPA scaling 3→15 under load — described
the *mechanism producing the failures* in the project's own benchmark table. The chart now makes
that arithmetically impossible, the limiter it claimed to have actually exists, and the suite
that measures it resembles a URL shortener. The last honest gap is that nobody has run it yet.
