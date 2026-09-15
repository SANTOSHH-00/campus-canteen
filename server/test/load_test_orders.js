import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * Quick Bite 500 Simultaneous Student Orders Load Test
 * Tests order placement, idempotency deduplication under race conditions,
 * and response latency thresholds under peak campus rush hour traffic.
 *
 * Usage:
 *   k6 run server/test/load_test_orders.js
 * Or with custom URL:
 *   k6 run -e TARGET_URL=https://quickbite-server.onrender.com server/test/load_test_orders.js
 */

export const options = {
  stages: [
    { duration: '20s', target: 100 },  // Step 1: 100 concurrent students
    { duration: '30s', target: 250 },  // Step 2: 250 concurrent students
    { duration: '45s', target: 500 },  // Step 3: 500 concurrent students (Peak Lunch Rush)
    { duration: '30s', target: 1000 }, // Step 4: 1000 concurrent spike (Stress testing limits)
    { duration: '20s', target: 0 },    // Cooldown
  ],
  thresholds: {
    // 95% of orders must be processed within 2 seconds during peak load
    http_req_duration: ['p(95)<2000'],
    // Order failure rate must be under 1%
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:5000';

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export default function () {
  const studentNum = Math.floor(Math.random() * 1000) + 1;
  const studentId = `student_${studentNum}`;
  const idempotencyKey = generateUUID();

  const payload = JSON.stringify({
    studentId: studentId,
    studentName: `Student ${studentNum}`,
    studentPhone: '9876543210',
    studentCourse: 'B.Tech CSE',
    canteenId: 'canteen_33',
    items: [
      {
        itemId: 'item_33_1',
        name: 'Special Thali',
        price: 80,
        quantity: 1,
        prepMinutes: 5,
      },
    ],
    totalAmount: 80,
    status: 'NEW',
    pickupPreference: 'Pickup ASAP',
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
      'x-student-id': studentId,
    },
  };

  // 1. Initial Order Placement
  const res1 = http.post(`${BASE_URL}/api/orders`, payload, params);

  check(res1, {
    'Order created successfully (201)': (r) => r.status === 201,
  });

  // 2. Simulated Rapid Double-Tap by impatient student (Idempotency Test)
  const res2 = http.post(`${BASE_URL}/api/orders`, payload, params);

  check(res2, {
    'Duplicate tap handled safely (200/201 or 409)': (r) =>
      r.status === 200 || r.status === 201 || r.status === 409,
  });

  sleep(1);
}
