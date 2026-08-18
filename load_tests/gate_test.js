import { sleep } from 'k6';
import { seedShortCodes, realisticIteration } from './lib/workload.js';

/**
 * TIER A — CI GATE
 *
 * Runs on every pull request. Small enough to finish in a couple of minutes on a shared
 * runner, strict enough to catch a regression. It is deliberately NOT a capacity test: at
 * this size the runner is not the bottleneck, so a threshold breach means the code changed,
 * not that the hardware was busy.
 */
export const options = {
  stages: [
    { duration: '30s', target: Number(__ENV.GATE_VUS || 150) },
    { duration: '90s', target: Number(__ENV.GATE_VUS || 150) },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    redirect_duration:  ['p(95)<300'],
    create_duration:    ['p(95)<1000'],
    redirect_failed:    ['rate<0.005'],
    create_failed:      ['rate<0.01'],
    redirect_cache_hit: ['rate>0.90'],
    // No load shedding at this level. If the bulkhead trips at 150 VUs, something regressed.
    responses_503:      ['count<10'],
  },
};

export function setup() {
  return { codes: seedShortCodes(Number(__ENV.SEED_SIZE || 1000)) };
}

export default function (data) {
  realisticIteration(data.codes);
  sleep(0.2);
}
