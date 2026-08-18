# 📊 Tier B — Capacity

> **No run has been recorded yet.** This file is overwritten by
> [`k6-load-testing.yml`](../.github/workflows/k6-load-testing.yml) on every `workflow_dispatch`.

A published row will carry the hardware above it and these columns:

| Column | Meaning |
| :-- | :-- |
| Redirect p95 | Cache-path latency, reported separately |
| Create p95 | Postgres-write latency, reported separately |
| Cache hit | Measured from the `X-Cache` response header |
| Redirect errors | Anything that was not a 301/302 |
| 503s shed | Requests the database bulkhead rejected rather than queued |

[`BASELINE.md`](BASELINE.md) explains what the suites measure and what a number here does and
does not mean.
