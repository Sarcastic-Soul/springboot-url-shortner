import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

/**
 * Shared workload model for every suite.
 *
 * WHY THIS EXISTS
 * ---------------
 * Each suite used to create a URL and immediately resolve it. That is a 1:1 write/read ratio
 * against a code the create call had just written into the cache — so the benchmark:
 *
 *   - spent half its requests on the one operation that cannot scale horizontally,
 *   - never took a cache miss, so it never measured Valkey or Postgres on the read path,
 *   - never reported a hit ratio, because every request hit,
 *   - and reported one blended latency for two operations whose costs differ by an order of
 *     magnitude, which hides both.
 *
 * Real shortener traffic is roughly 1000:1 reads to writes over a corpus far larger than any
 * single node's cache, with a long tail: a few links get most of the traffic, most links get
 * almost none. That is what this models.
 */

export const BASE_URL = __ENV.API_URL || 'http://localhost:8080';

/**
 * Lets the generator past the per-IP limiter. k6 drives everything from one or two source
 * addresses, which a correct limiter reads as abuse — so without this the suite measures the
 * rate limiter rather than the service. Quotas stay at production values; the generator is
 * simply allowlisted. Leave it unset to have the limiter apply (the resilience suite does).
 */
const BYPASS_SECRET = __ENV.RATE_LIMIT_BYPASS_SECRET || '';

export const SEED_SIZE = Number(__ENV.SEED_SIZE || 5000);
export const CREATE_RATIO = Number(__ENV.CREATE_RATIO || 0.01);

/**
 * Exponent of the long-tail draw. 1.0 is uniform; higher concentrates traffic on fewer codes.
 * 2.5 puts roughly 60% of requests on the hottest 10% of links, which is the shape that makes
 * a two-tier cache worth having.
 */
export const ZIPF_SKEW = Number(__ENV.ZIPF_SKEW || 2.5);

// Split by operation. One blended http_req_duration cannot tell you whether a p95 moved
// because reads got slower or because writes did.
export const redirectDuration = new Trend('redirect_duration', true);
export const createDuration = new Trend('create_duration', true);

// Measured, not assumed. The backend reports X-Cache on every redirect.
export const cacheHitRate = new Rate('redirect_cache_hit');

export const redirectFailed = new Rate('redirect_failed');
export const createFailed = new Rate('create_failed');
export const rateLimited = new Counter('responses_429');
export const loadShed = new Counter('responses_503');

function headers(extra) {
  const h = { 'Content-Type': 'application/json' };
  if (BYPASS_SECRET) {
    h['X-RateLimit-Bypass'] = BYPASS_SECRET;
  }
  return Object.assign(h, extra || {});
}

function expiryIso(days) {
  return new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString();
}

function createPayload(tag) {
  return JSON.stringify({
    originalUrl: `https://example.com/${tag}-${Math.floor(Math.random() * 1e9)}`,
    // Anonymous links require an expiry inside the allowed window.
    expiresAt: expiryIso(5),
  });
}

/**
 * Pre-seeds the corpus and returns its short codes. Called from a suite's `setup()`, so it runs
 * once, before measurement starts, and its cost is not attributed to the results.
 *
 * Seeded in parallel batches: 5,000 sequential round trips would add minutes of dead time to
 * every run.
 */
export function seedShortCodes(count = SEED_SIZE) {
  const codes = [];
  const batchSize = 50;

  for (let seeded = 0; seeded < count; seeded += batchSize) {
    const requests = [];
    for (let i = 0; i < Math.min(batchSize, count - seeded); i++) {
      requests.push({
        method: 'POST',
        url: `${BASE_URL}/api/v1/urls`,
        body: createPayload('seed'),
        params: { headers: headers() },
      });
    }

    const responses = http.batch(requests);
    for (const response of responses) {
      if (response.status === 200) {
        try {
          codes.push(response.json('shortCode'));
        } catch (e) {
          // A malformed body during seeding is not worth failing the run over.
        }
      }
    }
  }

  if (codes.length === 0) {
    throw new Error(
      `Seeding produced no short codes against ${BASE_URL}. ` +
      `If creates are returning 429, set RATE_LIMIT_BYPASS_SECRET to match the deployment.`
    );
  }

  console.log(`seeded ${codes.length}/${count} short codes`);
  return codes;
}

/**
 * Long-tail draw over the corpus.
 *
 * `u^skew` for u uniform on [0,1) is a cheap power-law approximation — not a true Zipf
 * distribution, but the property that matters here is the shape of the tail, not its exact
 * exponent, and this costs one multiply per iteration instead of a table lookup.
 */
export function pickCode(codes) {
  const index = Math.floor(codes.length * Math.pow(Math.random(), ZIPF_SKEW));
  return codes[Math.min(index, codes.length - 1)];
}

export function redirect(code) {
  const response = http.get(`${BASE_URL}/${code}`, {
    redirects: 0,
    tags: { operation: 'redirect' },
  });

  redirectDuration.add(response.timings.duration);
  redirectFailed.add(response.status !== 302 && response.status !== 301);

  if (response.status === 429) rateLimited.add(1);
  if (response.status === 503) loadShed.add(1);

  const cacheHeader = response.headers['X-Cache'];
  if (cacheHeader) {
    cacheHitRate.add(cacheHeader === 'HIT');
  }

  check(response, {
    'redirect is 302': (r) => r.status === 302,
    'redirect has Location': (r) => r.headers['Location'] !== undefined,
  });

  return response;
}

export function createUrl(tag = 'load') {
  const response = http.post(`${BASE_URL}/api/v1/urls`, createPayload(tag), {
    headers: headers(),
    tags: { operation: 'create' },
  });

  createDuration.add(response.timings.duration);
  createFailed.add(response.status !== 200);

  if (response.status === 429) rateLimited.add(1);
  if (response.status === 503) loadShed.add(1);

  check(response, { 'create is 200': (r) => r.status === 200 });

  return response;
}

/** One iteration of the production-shaped mix: mostly reads, occasionally a write. */
export function realisticIteration(codes) {
  if (Math.random() < CREATE_RATIO) {
    createUrl();
  } else {
    redirect(pickCode(codes));
  }
}
