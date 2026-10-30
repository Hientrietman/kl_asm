import http from 'k6/http';
import { check, sleep } from 'k6';

// NFR-2: Response time p95 below 500 ms for create, get and cancel at 20 concurrent users
// with at least 10,000 seeded appointments.

export const options = {
  scenarios: {
    mixed_load: {
      executor: 'constant-vus',
      vus: 20,
      duration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'], // p95 response time must be under 500 ms
    'http_req_duration{endpoint:create_booking}': ['p(95)<500'],
    'http_req_duration{endpoint:get_appointment}': ['p(95)<500'],
    'http_req_duration{endpoint:list_appointments}': ['p(95)<500'],
    'http_req_duration{endpoint:cancel_appointment}': ['p(95)<500'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Fixed seed references from V3 seed data
const LONDON_DEALERSHIP = 'd0000000-0000-0000-0000-000000000001';
const OIL_CHANGE_SERVICE = 'c0000000-0000-0000-0000-000000000001';

// Generate a valid 17-char VIN
function randomVin() {
  const chars = '0123456789ABCDEFGHJKLMNPRSTUVWXYZ';
  let suffix = '';
  for (let i = 0; i < 9; i++) {
    suffix += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return '1HGCR2F8' + suffix;
}

// Generate future date on a 15-minute boundary (e.g. 10 to 60 days ahead, between 09:00 and 15:00 UTC)
function randomFutureStartTime() {
  const futureDays = 10 + Math.floor(Math.random() * 50);
  const hour = 9 + Math.floor(Math.random() * 6);
  const minute = [0, 15, 30, 45][Math.floor(Math.random() * 4)];

  const date = new Date(Date.now() + futureDays * 24 * 60 * 60 * 1000);
  // Ensure Monday-Friday (1-5)
  if (date.getUTCDay() === 0) date.setUTCDate(date.getUTCDate() + 1); // skip Sunday
  if (date.getUTCDay() === 6) date.setUTCDate(date.getUTCDate() + 2); // skip Saturday

  date.setUTCHours(hour, minute, 0, 0);
  return date.toISOString();
}

export default function () {
  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  };

  // 1. List appointments with pagination (filtered by dealership)
  const listRes = http.get(
    `${BASE_URL}/api/v1/appointments?dealershipId=${LONDON_DEALERSHIP}&page=0&size=20`,
    { headers, tags: { endpoint: 'list_appointments' } }
  );

  check(listRes, {
    'list status is 200': (r) => r.status === 200,
    'list has items': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body.content);
      } catch (e) {
        return false;
      }
    },
  });

  // Extract an appointment ID from list for read testing, if available
  let existingId = null;
  try {
    const listBody = JSON.parse(listRes.body);
    if (listBody.content && listBody.content.length > 0) {
      existingId = listBody.content[0].id;
    }
  } catch (e) {}

  // 2. Get single appointment
  if (existingId) {
    const getRes = http.get(`${BASE_URL}/api/v1/appointments/${existingId}`, {
      headers,
      tags: { endpoint: 'get_appointment' },
    });
    check(getRes, {
      'get status is 200': (r) => r.status === 200,
    });
  }

  // 3. Create appointment (booking)
  const vin = randomVin();
  const startTime = randomFutureStartTime();
  const bookingPayload = JSON.stringify({
    dealershipId: LONDON_DEALERSHIP,
    serviceTypeId: OIL_CHANGE_SERVICE,
    startTime: startTime,
    customer: {
      fullName: `User ${__VU}_${__ITER}`,
      email: `loadtest_${__VU}_${__ITER}_${Date.now()}@example.com`,
      phone: '+447911123456',
    },
    vehicle: {
      vin: vin,
      make: 'Honda',
      model: 'Civic',
      year: 2021,
    },
  });

  const createRes = http.post(`${BASE_URL}/api/v1/appointments`, bookingPayload, {
    headers,
    tags: { endpoint: 'create_booking' },
  });

  const isCreated = check(createRes, {
    'create status is 201 or 409': (r) => r.status === 201 || r.status === 409,
  });

  // 4. Cancel appointment if successfully created
  if (createRes.status === 201) {
    try {
      const createdBody = JSON.parse(createRes.body);
      const cancelId = createdBody.id;
      if (cancelId) {
        const cancelPayload = JSON.stringify({ reason: 'Load test cleanup' });
        const cancelRes = http.post(
          `${BASE_URL}/api/v1/appointments/${cancelId}/cancel`,
          cancelPayload,
          { headers, tags: { endpoint: 'cancel_appointment' } }
        );
        check(cancelRes, {
          'cancel status is 200': (r) => r.status === 200,
        });
      }
    } catch (e) {}
  }

  sleep(0.1);
}
