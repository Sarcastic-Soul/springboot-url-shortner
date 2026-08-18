# Baseline

> **Status: no baseline has been recorded yet.**
>
> Everything below describes how to produce one and what to write down. The table at the
> bottom is empty on purpose — filling it in with numbers nobody ran would be worse than
> leaving it blank.

## Why the old numbers are void

The previous benchmark table (throughput of ~1,650 req/s, p95 of 944 ms, and so on) was
produced by a suite that did this, once per iteration:

```js
const createRes = http.post(`${BASE_URL}/api/v1/urls`, payload);
const resolveRes = http.get(`${BASE_URL}/${createRes.json('shortCode')}`);
```

Create a URL, then immediately resolve it. That is:

- a **1:1 write/read ratio**, where a URL shortener is roughly 1000:1,
- against a code that `UrlService.create()` had just written into the cache, so it was a
  **guaranteed hit** — the suite never took a cache miss and so never measured Valkey or
  Postgres on the read path,
- with **one blended `http_req_duration`** covering both a cache read and a Postgres write,
  which hides the behaviour of both,
- and **no hit-ratio measurement at all**, because there was nothing to measure: everything hit.

Roughly half of every run was spent on the one operation that cannot scale horizontally. Those
numbers describe that suite. They do not describe this system, and they are not comparable to
anything produced after the rewrite.

## What the suites do now

Every suite pre-seeds a corpus in `setup()` — outside the measurement window — then runs a
production-shaped mix over it:

| Property | Value | Why |
| :-- | :-- | :-- |
| Read/write mix | 99% redirects, 1% creates | `CREATE_RATIO`, default 0.01 |
| Corpus | 5,000 codes (1,000 for the gate) | `SEED_SIZE` |
| Access pattern | Long tail, `u^2.5` | `ZIPF_SKEW`. A few links get most of the traffic — the shape that makes a two-tier cache worth having |
| Latency | Split: `redirect_duration`, `create_duration` | Separate operations, separate budgets |
| Cache hit ratio | Measured from the `X-Cache` header | Asserted as a threshold, not assumed |
| Rate limiting | On, at production quotas | The generator is allowlisted by shared secret rather than the limits being relaxed |

Raise `SEED_SIZE` above the 50,000-entry Caffeine cap for capacity runs, so the local cache
overflows and Valkey does real work. At the default 5,000 the whole corpus fits in Caffeine
after warm-up, which measures the local cache and not the distributed one.

## The three tiers

| Tier | Suite | Trigger | Scale | What it proves |
| :-- | :-- | :-- | :-- | :-- |
| **A — Gate** | `gate_test.js` | Every PR | 150 VUs, ~2.5 min | No regression. Small enough that the runner is not the bottleneck, so a breach means the code changed |
| **B — Capacity** | `load_test.js`, `spike_test.js`, `soak_test.js` | Manual | 1,000–4,000 VUs | Throughput and scaling behaviour |
| **C — Resilience** | `resilience_test.js` | Manual + nightly | Adversarial | Abuse resistance while real users are unaffected |

## Recording a baseline

Tier A and B on CI hardware:

```bash
make test-up          # KinD + metrics-server + helm install (loadtest overlay)
make test-gate        # Tier A
make test-k6          # Tier B: load, spike, soak
make test-resilience  # Tier C
make db-connections   # confirm the pool/max_connections invariant held
make test-down
```

**A run on this hardware is not a capacity measurement.** One machine hosting Postgres, Valkey,
every backend JVM *and* k6 means the generator competes with the system under test for the same
cores. Treat single-node numbers as a regression signal only, and label them that way.

For a capacity number worth quoting:

1. A cluster of **3+ nodes** — managed Kubernetes, or three cheap VMs with k3s.
2. Load driven from a **separate machine** on the same network. Two small droplets for an
   afternoon is enough.
3. `chart/values-prod.yaml`, not the loadtest overlay.
4. `SEED_SIZE=200000` so the corpus genuinely exceeds the local cache.
5. `make obs-deploy` first, so the run is recorded alongside pool utilisation, stream depth and
   HPA behaviour rather than latency alone.

Record the hardware next to every number. A benchmark without its hardware is a rumour.

## Results

| Date | Commit | Tier | Hardware | Redirect p95 | Create p95 | Cache hit | Throughput | Notes |
| :-- | :-- | :-- | :-- | ---: | ---: | ---: | ---: | :-- |
| _—_ | _—_ | _—_ | _—_ | _—_ | _—_ | _—_ | _—_ | _No run recorded yet_ |
