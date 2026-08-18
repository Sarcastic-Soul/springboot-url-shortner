import { sleep } from 'k6';
import { seedShortCodes, realisticIteration } from './lib/workload.js';

/**
 * TIER B — SPIKE
 *
 * A 40x traffic burst in ten seconds, then a sustained hold long enough for the HPA to react,
 * then a drop. Answers: does it survive the burst, does it scale, and does it recover — with
 * latency still split by operation so "it got slower" can be attributed.
 */
export const options = {
  stages: [
    { duration: '10s', target: 100 },
    { duration: '1m',  target: 100 },
    { duration: '10s', target: Number(__ENV.SPIKE_VUS || 4000) },
    { duration: '2m',  target: Number(__ENV.SPIKE_VUS || 4000) },
    { duration: '10s', target: 100 },
    { duration: '1m',  target: 100 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    redirect_duration:  [__ENV.REDIRECT_P95 || 'p(95)<1500'],
    create_duration:    [__ENV.CREATE_P95   || 'p(95)<3000'],
    // A spike is allowed to shed load — that is the bulkhead doing its job — but the cache
    // path should not be failing.
    redirect_failed:    [__ENV.MAX_REDIRECT_ERROR_RATE || 'rate<0.05'],
    redirect_cache_hit: [__ENV.MIN_CACHE_HIT || 'rate>0.90'],
  },
};

export function setup() {
  return { codes: seedShortCodes() };
}

export default function (data) {
  realisticIteration(data.codes);
  sleep(0.1);
}
