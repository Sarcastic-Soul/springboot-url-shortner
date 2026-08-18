import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { BASE_URL, seedShortCodes, pickCode } from './lib/workload.js';

/**
 * TIER C — RESILIENCE
 *
 * Every portfolio project has a throughput screenshot. Almost none show the defences working
 * under attack while real users carry on unaffected. That is what this measures, and the
 * success metric is inverted: attack traffic SHOULD be refused, and refusal should be cheap
 * enough that it does not touch legitimate latency.
 *
 * The target result reads:
 *
 *   "Under sustained attack traffic from spoofed sources, legitimate redirect p95 stayed
 *    under 150ms and 99%+ of abusive creates were rejected with 429."
 *
 * Run it WITHOUT the bypass secret for the attack scenarios — that is the point. Seeding uses
 * it, because seeding is setup, not measurement.
 *
 *   API_URL=http://localhost:8080 RATE_LIMIT_BYPASS_SECRET=... k6 run resilience_test.js
 *
 * NOT COVERED: slowloris. Holding a request open with a trickle of bytes needs socket-level
 * control that k6 does not expose, and faking it with slow full requests would prove nothing.
 * Tomcat's connectionTimeout and the ingress are the answer there; measure it with a tool
 * built for it rather than claiming coverage here.
 */

const legitRedirect = new Trend('legit_redirect_duration', true);
const legitFailed = new Rate('legit_redirect_failed');

const floodBlocked = new Rate('flood_blocked');
const spoofBlocked = new Rate('spoof_blocked');
const missStormFailed = new Rate('cache_miss_storm_unexpected');
const attackServed = new Counter('attack_requests_served');

const ATTACK_RATE = Number(__ENV.ATTACK_RATE || 400);
const ATTACK_DURATION = __ENV.ATTACK_DURATION || '90s';

export const options = {
  scenarios: {
    // Runs the whole time, including before and after the attacks, so the comparison is
    // against this run's own baseline rather than a different run's.
    legitimate: {
      executor: 'constant-vus',
      vus: Number(__ENV.LEGIT_VUS || 20),
      duration: '3m',
      exec: 'legitimateUser',
      startTime: '0s',
    },
    // One source hammering create. The per-IP token bucket should absorb this outright.
    single_ip_flood: {
      executor: 'constant-arrival-rate',
      rate: ATTACK_RATE,
      timeUnit: '1s',
      duration: ATTACK_DURATION,
      preAllocatedVUs: 200,
      maxVUs: 600,
      exec: 'singleIpFlood',
      startTime: '30s',
    },
    // The same flood wearing a different X-Forwarded-For on every request. Before the Stage 2
    // fix this earned a fresh bucket each time and sailed through; it must now be refused
    // exactly like the un-spoofed flood.
    spoofed_flood: {
      executor: 'constant-arrival-rate',
      rate: ATTACK_RATE,
      timeUnit: '1s',
      duration: ATTACK_DURATION,
      preAllocatedVUs: 200,
      maxVUs: 600,
      exec: 'spoofedHeaderFlood',
      startTime: '30s',
    },
    // Codes that do not exist, so every request misses both cache tiers and reaches Postgres.
    // This is the scenario the bulkhead exists for.
    cache_miss_storm: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.MISS_RATE || 300),
      timeUnit: '1s',
      duration: ATTACK_DURATION,
      preAllocatedVUs: 200,
      maxVUs: 600,
      exec: 'cacheMissStorm',
      startTime: '45s',
    },
  },
  thresholds: {
    // THE headline. Legitimate users must not be able to tell an attack is happening.
    legit_redirect_duration: [__ENV.LEGIT_P95 || 'p(95)<150'],
    legit_redirect_failed: ['rate<0.01'],

    // Refusal must be near-total, and spoofing must buy nothing.
    flood_blocked: [__ENV.MIN_BLOCK_RATE || 'rate>0.95'],
    spoof_blocked: [__ENV.MIN_BLOCK_RATE || 'rate>0.95'],

    // A miss storm may be shed (503) or answered (404). What it must not do is 500.
    cache_miss_storm_unexpected: ['rate<0.01'],
  },
};

export function setup() {
  return { codes: seedShortCodes(Number(__ENV.SEED_SIZE || 1000)) };
}

/**
 * A real user following links. Redirects are not rate limited by design — throttling reads
 * would break the product — so this traffic has no protection of its own. It stays fast only
 * because the abuse is stopped before it reaches shared resources.
 */
export function legitimateUser(data) {
  const response = http.get(`${BASE_URL}/${pickCode(data.codes)}`, {
    redirects: 0,
    tags: { traffic: 'legitimate' },
  });

  legitRedirect.add(response.timings.duration);
  legitFailed.add(response.status !== 302);

  check(response, { 'legitimate user still gets a redirect': (r) => r.status === 302 });
}

function attackPayload() {
  return JSON.stringify({
    originalUrl: `https://example.com/abuse-${Math.floor(Math.random() * 1e9)}`,
    expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
  });
}

function recordAttack(response, metric) {
  const blocked = response.status === 429 || response.status === 503;
  metric.add(blocked);
  if (response.status === 200) {
    attackServed.add(1);
  }
}

export function singleIpFlood() {
  const response = http.post(`${BASE_URL}/api/v1/urls`, attackPayload(), {
    headers: { 'Content-Type': 'application/json' },
    tags: { traffic: 'flood' },
  });
  recordAttack(response, floodBlocked);
}

export function spoofedHeaderFlood() {
  const spoofed = `${randomOctet()}.${randomOctet()}.${randomOctet()}.${randomOctet()}`;
  const response = http.post(`${BASE_URL}/api/v1/urls`, attackPayload(), {
    headers: {
      'Content-Type': 'application/json',
      // The whole exploit, in one header.
      'X-Forwarded-For': spoofed,
    },
    tags: { traffic: 'spoofed' },
  });
  recordAttack(response, spoofBlocked);
}

export function cacheMissStorm() {
  const response = http.get(`${BASE_URL}/zz${Math.floor(Math.random() * 1e12).toString(36)}`, {
    redirects: 0,
    tags: { traffic: 'miss-storm' },
  });

  // 404 means it reached the database and found nothing; 503 means the bulkhead shed it before
  // it could. Both are correct. A 500, or a request that hangs to timeout, is not.
  missStormFailed.add(!(response.status === 404 || response.status === 503 || response.status === 429));
}

function randomOctet() {
  return Math.floor(Math.random() * 254) + 1;
}
