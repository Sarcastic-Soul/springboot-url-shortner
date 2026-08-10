import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * SPIKE TEST
 * Goal: Test system behavior, dynamic HPA autoscaling response, and recovery under sudden, extreme traffic spikes.
 */
export const options = {
  stages: [
    { duration: '10s', target: 100 },   // Normal baseline load
    { duration: '1m',  target: 100 },   // Maintain baseline
    { duration: '10s', target: 4000 },  // SPIKE: Rapid burst to 4,000 VUs in 10 seconds
    { duration: '2m',  target: 4000 },  // Hold spike load to trigger HPA autoscaling
    { duration: '10s', target: 100 },   // Drop rapidly back to 100 VUs
    { duration: '1m',  target: 100 },   // Recovery observation period
    { duration: '10s', target: 0 },     // Ramp down completely
  ],
  thresholds: {
    // Allow slightly higher latency under extreme sudden spike, but error rate must stay below 5%
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.05'],
  },
};

const BASE_URL = __ENV.API_URL || 'http://192.168.49.2:30080';

export default function () {
  const payload = JSON.stringify({
    originalUrl: `https://example.com/spike-test-${Math.floor(Math.random() * 1000000)}`,
    expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString()
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // 1. Create short URL
  const createRes = http.post(`${BASE_URL}/api/v1/urls`, payload, params);
  check(createRes, {
    'create status 200 or 429': (r) => r.status === 200 || r.status === 429,
  });

  // 2. Resolve short URL if creation succeeded
  if (createRes.status === 200) {
    const shortCode = createRes.json('shortCode');
    const resolveRes = http.get(`${BASE_URL}/${shortCode}`, { redirects: 0 });
    check(resolveRes, {
      'redirect status is 302 or 301': (r) => r.status === 302 || r.status === 301,
    });
  }

  sleep(0.1);
}
