import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * SOAK TEST (ENDURANCE TEST)
 * Goal: Verify stability, memory management, database connection health, and Redis cache efficiency over prolonged sustained load.
 */
export const options = {
  stages: [
    { duration: '1m', target: 1000 },
    { duration: __ENV.SOAK_HOLD_DURATION || '5m', target: 1000 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: [__ENV.P95_THRESHOLD || 'p(95)<2000'],
    http_req_failed: [__ENV.MAX_ERROR_RATE || 'rate<0.01'],
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
