import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    constant_request_rate: {
      executor: 'constant-arrival-rate',
      rate: 10000,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 1000,
      maxVUs: 3000,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<200'], // 95% of requests should complete within 200ms
    http_req_failed: ['rate<0.01'],   // Error rate should be less than 1%
  },
};

const BASE_URL = __ENV.API_URL || 'http://localhost:8080';

export default function () {
  // 1. Create a short URL (Tests Write Performance)
  const payload = JSON.stringify({
    originalUrl: `https://example.com/page-${Math.floor(Math.random() * 100000)}`,
    expiresAt: new Date(Date.now() + 5 * 24 * 60 * 60 * 1000).toISOString()
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const createRes = http.post(`${BASE_URL}/api/v1/urls`, payload, params);

  if (createRes.status !== 200) {
    console.log(`Status: ${createRes.status}`);
    console.log(`Body: ${createRes.body}`);
  }

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
    
    // We set redirects: 0 so k6 doesn't automatically follow the redirect to example.com.
    // We just want to measure how fast our backend replies with a 302 Found.
    const resolveRes = http.get(`${BASE_URL}/${shortCode}`, { redirects: 0 });
    
    check(resolveRes, {
      'redirect status is 302 Found': (r) => r.status === 302 || r.status === 301,
      'has location header': (r) => r.headers['Location'] !== undefined,
    });
  }

  // Small sleep to simulate realistic user pacing
  sleep(1);
}
