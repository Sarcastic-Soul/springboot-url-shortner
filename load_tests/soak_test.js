import { sleep } from 'k6';
import { seedShortCodes, realisticIteration } from './lib/workload.js';

/**
 * TIER B — SOAK
 *
 * Steady load held long enough for slow failures to show: connection leaks, heap growth, an
 * analytics stream that drains slower than it fills. Watch `analytics_stream_length` and
 * `hikaricp_connections_pending` in Grafana alongside this run — a soak that only reports
 * latency cannot distinguish "stable" from "degrading slowly".
 */
export const options = {
  stages: [
    { duration: '1m', target: Number(__ENV.SOAK_VUS || 1000) },
    { duration: __ENV.SOAK_HOLD_DURATION || '5m', target: Number(__ENV.SOAK_VUS || 1000) },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    redirect_duration:  [__ENV.REDIRECT_P95 || 'p(95)<400'],
    create_duration:    [__ENV.CREATE_P95   || 'p(95)<1500'],
    redirect_failed:    [__ENV.MAX_REDIRECT_ERROR_RATE || 'rate<0.01'],
    create_failed:      [__ENV.MAX_CREATE_ERROR_RATE   || 'rate<0.01'],
    redirect_cache_hit: [__ENV.MIN_CACHE_HIT || 'rate>0.90'],
  },
};

export function setup() {
  return { codes: seedShortCodes() };
}

export default function (data) {
  realisticIteration(data.codes);
  sleep(0.5);
}
