import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * STANDARD LOAD TEST
 * Goal: Verify system performance, throughput, and autoscaling response under expected peak load.
 */
export const options = {
  stages: [
    { duration: '30s', target: 500 },   // Ramp up to 500 VUs over 30s
    { duration: '2m',  target: 2000 },  // Ramp up to 2,000 VUs (Peak steady load)
    { duration: '3m',  target: 2000 },  // Stay at 2,000 VUs for 3 minutes
    { duration: '30s', target: 0 },     // Ramp down to 0 VUs
  ],
  thresholds: {
    http_req_duration: [__ENV.P95_THRESHOLD || 'p(95)<2500'], // 95% of requests complete in under 2.5s on single-node runner
    http_req_failed: [__ENV.MAX_ERROR_RATE || 'rate<0.02'],    // Error rate under 2%
  },
};

const BASE_URL = __ENV.API_URL || 'http://192.168.49.2:30080';

export default function () {
  // 1. Create a short URL (Tests Write Performance)
  const payload = JSON.stringify({
    originalUrl: `https://example.com/page-${Math.floor(Math.random() * 1000000)}`,
    expiresAt: new Date(Date.now() + 5 * 24 * 60 * 60 * 1000).toISOString()
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const createRes = http.post(`${BASE_URL}/api/v1/urls`, payload, params);

  check(createRes, {
    'create status is 200': (r) => r.status === 200,
    'has short code': (r) => {
      if (r.status !== 200) return false;
      try {
        return r.json('shortCode') !== undefined;
      } catch (e) {
        return false;
      }
    }
  });

  // 2. Resolve the short URL (Tests Read & Redirect Performance / Redis caching)
  if (createRes.status === 200) {
    const shortCode = createRes.json('shortCode');
    const resolveRes = http.get(`${BASE_URL}/${shortCode}`, { redirects: 0 });
    
    check(resolveRes, {
      'redirect status is 302 or 301': (r) => r.status === 302 || r.status === 301,
      'has location header': (r) => r.headers['Location'] !== undefined,
    });
  }

  // Pacing between requests (0.2 - 0.5s)
  sleep(Math.random() * 0.3 + 0.2);
}
