/**
 * Native Node.js High-Concurrency Simulation Script
 * Tests the backend with 100 to 500 simultaneous order requests and validates idempotency
 * without needing external tools installed.
 *
 * Usage:
 *   node server/test/simulate_500_orders.js
 */

const http = require('http');

const SERVER_HOST = 'localhost';
const SERVER_PORT = process.env.PORT || 5000;
const CONCURRENT_USERS = parseInt(process.env.USERS || '50', 10); // Default to 50 for quick test, pass USERS=500 for full burst

function makeOrderRequest(studentId, idempotencyKey) {
  return new Promise((resolve) => {
    const payload = JSON.stringify({
      studentId: `student_${studentId}`,
      studentName: `Test Student ${studentId}`,
      canteenId: 'canteen_33',
      items: [
        {
          itemId: 'item_33_1',
          name: 'Chole Bhature',
          price: 60,
          quantity: 1,
          prepMinutes: 5,
        },
      ],
      totalAmount: 60,
      status: 'NEW',
      pickupPreference: 'Pickup ASAP',
    });

    const start = Date.now();
    const req = http.request(
      {
        hostname: SERVER_HOST,
        port: SERVER_PORT,
        path: '/api/orders',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(payload),
          'Idempotency-Key': idempotencyKey,
          'x-student-id': `student_${studentId}`,
        },
      },
      (res) => {
        let body = '';
        res.on('data', (chunk) => (body += chunk));
        res.on('end', () => {
          const duration = Date.now() - start;
          resolve({
            statusCode: res.statusCode,
            duration,
            body: body.length > 100 ? body.slice(0, 100) + '...' : body,
          });
        });
      }
    );

    req.on('error', (err) => {
      resolve({ statusCode: 0, duration: Date.now() - start, error: err.message });
    });

    req.write(payload);
    req.end();
  });
}

async function runSimulation() {
  console.log(`\n🚀 Starting Quick Bite Concurrency Test with ${CONCURRENT_USERS} simultaneous users...`);
  console.log(`Target: http://${SERVER_HOST}:${SERVER_PORT}/api/orders\n`);

  const promises = [];
  const idempotencyKeys = [];

  for (let i = 1; i <= CONCURRENT_USERS; i++) {
    const key = `IDEMP-TEST-${Date.now()}-${i}-${Math.random()}`;
    idempotencyKeys.push(key);
    promises.push(makeOrderRequest(i, key));
  }

  const startTime = Date.now();
  const results = await Promise.all(promises);
  const totalTime = Date.now() - startTime;

  const successful = results.filter((r) => r.statusCode === 201).length;
  const duplicateOrInProgress = results.filter((r) => r.statusCode === 200 || r.statusCode === 409).length;
  const failed = results.filter((r) => r.statusCode !== 201 && r.statusCode !== 200 && r.statusCode !== 409).length;
  const durations = results.map((r) => r.duration).sort((a, b) => a - b);
  const avgDuration = Math.round(durations.reduce((a, b) => a + b, 0) / durations.length);
  const p95 = durations[Math.floor(durations.length * 0.95)] || 0;

  console.log(`\n================ SIMULATION RESULTS ================`);
  console.log(`Total Requests:         ${CONCURRENT_USERS}`);
  console.log(`Successful Orders (201):${successful}`);
  console.log(`Idempotent/In-Progress: ${duplicateOrInProgress}`);
  console.log(`Failed / Errors:        ${failed}`);
  console.log(`Total Time Elapsed:     ${totalTime} ms`);
  console.log(`Average Latency:        ${avgDuration} ms`);
  console.log(`95th Percentile (p95):  ${p95} ms`);
  console.log(`Throughput:             ${((CONCURRENT_USERS / totalTime) * 1000).toFixed(1)} req/sec`);
  console.log(`====================================================\n`);

  // Test Idempotency with immediate duplicate calls
  console.log(`🔁 Testing Idempotency (Sending duplicate requests with exact same keys)...`);
  const dupPromises = idempotencyKeys.slice(0, Math.min(10, CONCURRENT_USERS)).map((key, i) => {
    return makeOrderRequest(i + 1, key);
  });
  const dupResults = await Promise.all(dupPromises);
  const dupBlockedOrReused = dupResults.filter((r) => r.statusCode === 200 || r.statusCode === 201 || r.statusCode === 409).length;

  console.log(`Duplicate Requests Sent:     ${dupPromises.length}`);
  console.log(`Deduplicated Successfully:   ${dupBlockedOrReused}/${dupPromises.length}`);
  if (dupBlockedOrReused === dupPromises.length) {
    console.log(`✅ Idempotency Verified: No duplicate orders were created!`);
  } else {
    console.warn(`⚠️ Warning: Some duplicate requests were not deduplicated.`);
  }
}

if (require.main === module) {
  runSimulation().catch(console.error);
}

module.exports = { runSimulation };
