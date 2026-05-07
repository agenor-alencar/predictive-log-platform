import http from 'k6/http';
import { check } from 'k6';

const iterations = Number(__ENV.ITERATIONS || 1000);
const vus = Number(__ENV.VUS || 50);
const loginUrl = __ENV.LOGIN_URL || 'http://java-api:8080/auth/login';
const targetUrl = __ENV.TARGET_URL || 'http://java-api:8080/predict/error';

export const options = {
  scenarios: {
    load_test: {
      executor: 'shared-iterations',
      vus,
      iterations,
      maxDuration: '30m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.9'],
    http_req_duration: ['p(95)<30000'],
  },
};

function randInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

export function setup() {
  const payload = JSON.stringify({ username: 'admin', password: 'admin123' });
  const headers = { 'Content-Type': 'application/json' };
  const res = http.post(loginUrl, payload, { headers });

  check(res, {
    'login status is 200': (r) => r.status === 200,
  });

  let token = '';
  try {
    token = res.json('token') || '';
  } catch (_) {
    token = '';
  }

  return { token };
}

export default function (data) {
  const body = JSON.stringify({
    method: ['GET', 'POST', 'PUT', 'DELETE'][randInt(0, 3)],
    hour: randInt(0, 23),
    historicalAvgResponse: randInt(10, 5000),
    dayOfWeek: randInt(0, 6),
  });

  const headers = {
    'Content-Type': 'application/json',
  };

  if (data && data.token) {
    headers.Authorization = `Bearer ${data.token}`;
  }

  const res = http.post(targetUrl, body, { headers });

  check(res, {
    'request completed': (r) => r.status >= 200 && r.status < 600,
  });
}
