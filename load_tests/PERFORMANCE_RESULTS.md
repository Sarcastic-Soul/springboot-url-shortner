# 📊 Tier B — Capacity suite (constrained single-node CI)

> **Last run:** `2026-08-19T20:20:03.020Z`  
> **Commit:** `bf2abd8eae1f223ba0a15bd37c17ace53924f752`  
> **Branch:** `main`  
> **Hardware:** one GitHub Actions runner (2–4 vCPU) hosting KinD, Postgres, Valkey,
> every backend pod **and** k6 itself.

**These are regression numbers, not capacity numbers.** The generator competes with the
system under test for the same handful of cores, so absolute latency here is dominated by
the runner. Compare them across commits, never against a real deployment.

| Test Suite | Requests | Throughput | Redirect p95 | Create p95 | Cache hit | Redirect errors | 503s shed | |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | :-: |
| **Standard Load Test** | 828,717 | 2180.5 req/s | 646.7 ms | 4622.2 ms | 100.00% | 0.00% | 3301 | ✅ |
| **Spike Burst Test** | 198,732 | 665.9 req/s | 30006.1 ms | 30000.9 ms | 100.00% | 7.81% | 462 | ⚠️ |
| **Soak / Endurance Test** | 636,252 | 1457.9 req/s | 185.2 ms | 284.1 ms | 100.00% | 0.00% | 0 | ✅ |

Redirect and create latency are reported separately because they are different
operations: one is a cache read, the other a Postgres write. A single blended p95 hides
both. Cache hit ratio is read from the `X-Cache` response header, not assumed.
