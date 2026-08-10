# 📊 Automated k6 Performance Test Results

> **Last Automated Run:** `2026-08-10T21:57:58.206Z`  
> **Git Commit:** `9d657212ae51edc7ab08a08429d2eddedef4d0b5`  
> **Branch:** `main`  

---  

## 🚀 Executive Benchmark Summary

| Test Suite | Total Requests | Throughput (RPS) | Success Rate | Avg Latency | p95 Latency | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Standard Load Test** | 1,198,780 | 3326.9 req/s | 6.73% | 789.1ms | 3532.3ms | ⚠️ HEAVY LOAD |
| **Spike Burst Test** | 153,067 | 511.7 req/s | 87.17% | 964.5ms | 4381.4ms | ⚠️ HEAVY LOAD |
| **Soak / Endurance Test** | 694,163 | 1651.0 req/s | 100.00% | 267.8ms | 944.1ms | ✅ PASSED |
