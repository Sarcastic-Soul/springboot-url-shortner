# 📊 Automated k6 Performance Test Results

> **Last Automated Run:** `2026-08-10T21:03:34.572Z`  
> **Environment:** GitHub Actions (`ubuntu-latest` 2 vCPU Runner) + KinD Cluster  

---  

## 🚀 Executive Benchmark Summary

| Test Suite | Total Requests | Throughput (RPS) | Success Rate | Avg Latency | p95 Latency | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Standard Load Test** | 1,428,991 | 3966.7 req/s | 4.64% | 450.1ms | 2655.0ms | ⚠️ HEAVY LOAD |
| **Spike Burst Test** | 96,836 | 318.8 req/s | 79.78% | 1653.3ms | 8020.4ms | ⚠️ HEAVY LOAD |
| **Soak / Endurance Test** | 688,214 | 1636.7 req/s | 99.99% | 272.1ms | 906.7ms | ✅ PASSED |

---

## 📈 Suite Performance Breakdown

### 1. Standard Load Test
- **Total Requests Processed:** `1,428,991`
- **Peak Throughput:** `3,966.7` Requests/Second
- **Iteration Duration (Median / p90 / p95):** `359.7ms` / `483.8ms` / `499.3ms`
- **Successful Request Latency (Avg / p90 / p95):** `450.1ms` / `2,035.1ms` / `2,654.9ms`

### 2. Spike Burst Test
- **Total Requests Processed:** `96,836`
- **Throughput:** `318.8` Requests/Second
- **Success Rate:** `79.78%`
- **Average Latency:** `1,653.3ms` ($p(95) = 8,020.4\text{ms}$)

### 3. Soak / Endurance Test
- **Total Requests Processed:** `688,214`
- **Throughput:** `1,636.7` Requests/Second
- **Success Rate:** `99.99%` ✅
- **Average Latency:** `272.1ms` ($p(95) = 906.7\text{ms}$)
