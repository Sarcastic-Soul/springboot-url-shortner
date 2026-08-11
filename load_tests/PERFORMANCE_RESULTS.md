# 📊 Automated k6 Performance Test Results

> **Last Automated Run:** `2026-08-11T05:58:00Z`  
> **Environment:** GitHub Actions (`ubuntu-latest` 2 vCPU Runner) + KinD Cluster  

---  

## 🚀 Executive Benchmark Summary

| Test Suite | Total Requests | Throughput (RPS) | Success Rate | Avg Latency | p95 Latency | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Soak / Endurance Test** | **694,163** | **1,651.0 req/s** | **99.99%** | **267.8 ms** | **944.3 ms** | ✅ PASSED (Sub-300ms) |
| **Spike Burst Test** | **153,067** | **511.7 req/s** | **87.17%** | **964.5 ms** | **4,381.3 ms** | ⚡ HPA AUTOSCALED |
| **Standard Load Test** | **1,198,780** | **3,326.9 req/s** | **6.73%** | **450.1 ms** | **2,654.9 ms** | ⚠️ DB POOL BOUND (15 Connections) |

---

## 📈 Suite Performance Breakdown & Engineering Insights

### 1. 🟢 Soak / Endurance Test (Sustained High Traffic)
- **Total Requests Processed:** `694,163`
- **Sustained Throughput:** `1,651.0` Requests/Second
- **Success Rate:** **`99.997%`** (694,144 passed / 19 failed) ✅
- **Average Latency:** **`267.8 ms`** (Median = `89.7ms`, $p(95) = 944.3\text{ms}$)
- **Analysis:** Phenomenal stability! Over 694,000 requests processed with sub-300ms average latency and 99.997% pass rate under sustained 1,000 VU load.

### 2. ⚡ Spike Burst Test (4,000 VU Extreme Surge)
- **Total Requests Processed:** `153,067`
- **Throughput:** `511.7` Requests/Second
- **Success Rate:** `87.17%`
- **Average Latency:** `964.5ms` (Median = `33.8ms`)
- **Analysis:** Handled an instant 4,000 VU burst. Kubernetes HPA auto-scaled backend pods (3 to 15) to recover from the surge.

### 3. ⚠️ Standard Load Test (HikariCP Connection Pool Analysis)
- **Total Requests Processed:** `1,198,780`
- **Throughput:** `3,326.9` Requests/Second
- **Success Rate:** `6.73%` (80,716 successful HTTP 200/302 requests)
- **Average Latency for 200 OK:** `450.1ms`
- **Root Cause Analysis:** 2,000 VUs hit `POST /api/v1/urls` (synchronous database writes) with rate limiting disabled. 2,000 threads competed for **15 HikariCP database connections** (`HIKARI_MAX_POOL_SIZE: 15`). Threads timed out waiting for database connections after 3,000ms, proving that **rate-limiting is essential to protect database pools from write starvation** under high concurrency.
