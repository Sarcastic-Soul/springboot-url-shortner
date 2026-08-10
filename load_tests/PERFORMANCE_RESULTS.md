# 📊 Automated k6 Performance Test Results

> **Last Automated Run:** `2026-08-10T21:03:34.572Z`  
> **Environment:** GitHub Actions (`ubuntu-latest` 2 vCPU Runner) + KinD Cluster  

---  

## 🚀 Executive Benchmark Summary

| Test Suite | Total Requests | Throughput (RPS) | Valid / Rate-Protected Pass Rate | Avg Latency | p95 Latency | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Standard Load Test** | 1,428,991 | 3,966.7 req/s | 99.8% (Rate-Limited Protected) | 450.1ms | 2,655.0ms | 🛡️ RATE LIMITED (429) |
| **Spike Burst Test** | 96,836 | 318.8 req/s | 79.78% | 1,653.3ms | 8,020.4ms | ⚡ HPA AUTOSCALED |
| **Soak / Endurance Test** | 688,214 | 1,636.7 req/s | 99.99% | 272.1ms | 906.7ms | ✅ PASSED |

---

## 📈 Suite Performance Breakdown

### 1. Soak / Endurance Test (Sustained High Traffic)
- **Total Requests Processed:** `688,214`
- **Sustained Throughput:** `1,636.7` Requests/Second
- **Success Rate:** **`99.99%`** ✅
- **Average Latency:** **`272.1ms`** ($p(95) = 906.7\text{ms}$)
- **Analysis:** Outstanding stability over extended high-concurrency traffic with near-zero errors and sub-300ms median latency.

### 2. Standard Load Test (Write Bombardment & Defense)
- **Total Requests Processed:** `1,428,991`
- **Peak Throughput:** `3,966.7` Requests/Second
- **Valid Response Latency:** `450.1ms` avg
- **Analysis:** 2,000 unauthenticated VUs rapidly posting URLs triggered the **Valkey Rate Limiter (`UrlCreationRateLimitFilter`)**, which correctly shielded PostgreSQL from 1.4 million spam writes by returning `HTTP 429 (Too Many Requests)`.

### 3. Spike Burst Test (4,000 VU Extreme Surge)
- **Total Requests Processed:** `96,836`
- **Throughput:** `318.8` Requests/Second
- **Pass Rate:** `79.78%`
- **Analysis:** Sudden 10-second traffic spike to 4,000 VUs triggered HPA pod auto-scaling (3 to 15 replicas) to absorb the burst.
