import { sleep } from 'k6';
import { seedShortCodes, realisticIteration } from './lib/workload.js';

/**
 * TIER B — CAPACITY
 *
 * Sustained peak traffic in a production-shaped mix: 99% redirects over a pre-seeded corpus
 * with a long-tail access pattern, 1% creates.
 *
 * Numbers from a GitHub Actions runner describe the runner. That box has 2–4 vCPU and is
 * hosting Postgres, Valkey, every backend JVM AND k6 itself. Real capacity figures need a
 * multi-node cluster driven from a separate machine — see load_tests/BASELINE.md.
 */
export const options = {
  stages: [
    { duration: '30s', target: Number(__ENV.RAMP_VUS || 500) },
    { duration: '2m',  target: Number(__ENV.PEAK_VUS || 2000) },
    { duration: __ENV.HOLD_DURATION || '3m', target: Number(__ENV.PEAK_VUS || 2000) },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    // Separate budgets, because these are separate operations. A redirect is a cache read; a
    // create is a Postgres write. Holding them to one number tells you nothing about either.
    redirect_duration: [__ENV.REDIRECT_P95 || 'p(95)<500'],
    create_duration:   [__ENV.CREATE_P95   || 'p(95)<2000'],
    redirect_failed:   [__ENV.MAX_REDIRECT_ERROR_RATE || 'rate<0.01'],
    create_failed:     [__ENV.MAX_CREATE_ERROR_RATE   || 'rate<0.02'],
    // The architecture's central claim, asserted rather than assumed.
    redirect_cache_hit: [__ENV.MIN_CACHE_HIT || 'rate>0.90'],
  },
};

export function setup() {
  return { codes: seedShortCodes() };
}

export default function (data) {
  realisticIteration(data.codes);
  sleep(Math.random() * 0.3 + 0.2);
}
