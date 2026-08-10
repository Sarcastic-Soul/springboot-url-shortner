import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * SOAK TEST (ENDURANCE TEST)
 * Goal: Verify stability, memory management, database connection health, and Redis cache efficiency over prolonged sustained load.
 */
export const options = {
  stages: [
    { duration: '2m',  target: 1000 },  // Ramp up to 1,000 VUs over 2 minutes
    { duration: '30m', target: 1000 },  // Maintain steady 1,000 VUs for 30 minutes (Expand to 2h+ for deep soaking)
    { duration: '2m',  target: 0 },     // Ramp down to 0
  ],
  thresholds: {
    http_req_duration: ['p(95)<150'],   // 95% of requests must complete under 150ms over long runs
    http_req_failed: ['rate<0.005'],    // Error rate must stay strictly under 0.5%
  },
};

const BASE_URL = __ENV.API_URL || 'http://192.168.49.2:30080';

export default function () {
  const payload = JSON.stringify({
    originalUrl: `https://example.com/soak-test-${Math.floor(Math.random() * 1000000)}`,
    expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString()
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // 1. Create short URL
  const createRes = http.post(`${BASE_URL}/api/v1/urls`, payload, params);
  check(createRes, {
    'create status is 200': (r) => r.status === 200,
  });

  // 2. Resolve short URL
  if (createRes.status === 200) {
    const shortCode = createRes.json('shortCode');
    const resolveRes = http.get(`${BASE_URL}/${shortCode}`, { redirects: 0 });
    check(resolveRes, {
      'redirect status is 302 or 301': (r) => r.status === 302 || r.status === 301,
    });
  }

  // Consistent pacing for sustained throughput
  sleep(0.5);
}
