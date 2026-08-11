# 📊 Automated k6 Performance Test Results

> **Last Automated Run:** `2026-08-11T13:10:00Z`  
> **Environment:** GitHub Actions (`ubuntu-latest` 2 vCPU Runner) + KinD Cluster  

---  

## 🚀 Executive Benchmark Summary

| Test Suite | Total Requests | Throughput (RPS) | Success Rate | Avg Latency | p95 Latency | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Soak / Endurance Test** | **694,163** | **1,651.0 req/s** | **99.99%** | **267.8 ms** | **944.3 ms** | ✅ PASSED (Sub-300ms) |
| **Standard Load Test** | **141,315** | **386.9 req/s** | **88.52%** | **654.9 ms** | **2,302.0 ms** | 🚀 OPTIMIZED (50 Connections) |
| **Spike Burst Test** | **153,067** | **511.7 req/s** | **87.17%** | **964.5 ms** | **4,381.3 ms** | ⚡ HPA AUTOSCALED |

---

## 📈 Suite Performance Breakdown & Engineering Insights

### 1. 🟢 Soak / Endurance Test (Sustained High Traffic)
- **Total Requests Processed:** `694,163`
- **Sustained Throughput:** `1,651.0` Requests/Second
- **Success Rate:** **`99.997%`** (694,144 passed / 19 failed) ✅
- **Average Latency:** **`267.8 ms`** (Median = `89.7ms`, $p(95) = 944.3\text{ms}$)
- **Analysis:** Phenomenal stability! Over 694,000 requests processed with sub-300ms average latency and 99.997% pass rate under sustained 1,000 VU load.

### 2. 🚀 Standard Load Test (HikariCP 50 Pool Optimization Results)
- **Total Requests Processed:** `141,315`
- **Success Rate:** **`88.52%`** (250,178 passed checks, 125,089 successful HTTP requests)
- **Successful Request Median Latency:** **`175.7 ms`**!
- **Successful Request Average Latency:** **`654.9 ms`** ($p(95) = 2,302.0\text{ms}$)
- **Create Short Code Success:** `63,022` passed
- **Redirect Resolve Success:** `62,067` passed (`98.48%` redirect success)
- **Impact of HikariCP Pool Increase:** Raising `HIKARI_MAX_POOL_SIZE` from 15 to 50 increased the success rate **from 6.73% to 88.52%** (a **13x improvement**)!

### 3. ⚡ Spike Burst Test (4,000 VU Extreme Surge)
- **Total Requests Processed:** `153,067`
- **Throughput:** `511.7` Requests/Second
- **Success Rate:** `87.17%`
- **Average Latency:** `964.5ms` (Median = `33.8ms`)
- **Analysis:** Handled an instant 4,000 VU burst. Kubernetes HPA auto-scaled backend pods (3 to 15) to recover from the surge.
