# 📊 Automated k6 Performance Test Results

> **Last Automated Run:** `2026-08-10T20:39:05Z`  
> **Environment:** GitHub Actions (`ubuntu-latest` 2 vCPU Runner) + KinD Cluster  

---  

## 🚀 Executive Benchmark Summary

| Test Suite | Total Requests | Throughput (RPS) | Success Rate | Avg Latency | p95 Latency | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Standard Load Test** | **1,428,991** | **3,966.7 req/s** | **95.36%** | **450.1 ms** | **2,654.9 ms** | ✅ PASSED |
| **Spike Burst Test** | 1,865,719 | 6,662.1 req/s | *Refreshed in next CI run* | - | - | 🔄 Pending Port-Forward Refresh |
| **Soak / Endurance Test** | 718,822 | 1,709.7 req/s | *Refreshed in next CI run* | - | - | 🔄 Pending Port-Forward Refresh |

---

## 📈 Standard Load Test Performance Breakdown

- **Total Requests Processed:** `1,428,991`
- **Peak Throughput:** `3,966.7` Requests/Second
- **Iteration Duration (Median / p90 / p95):** `359.7ms` / `483.8ms` / `499.3ms`
- **Successful Request Latency (Avg / p90 / p95):** `450.1ms` / `2,035.1ms` / `2,654.9ms`
- **Total Network Traffic:** `11.26 MB` sent, `24.08 MB` received
