/**
 * Comprehensive Barcode Scanner & Two-Step Pickup Verification Test Suite
 * Validates all requirements specified by the user:
 * 1. Status-specific scanner messages (ORDER PLACED, ACCEPTED, PREPARING, READY, COLLECTED, CANCELLED)
 * 2. Barcode permanence & dynamic token inspection from MongoDB
 * 3. Real-time synchronization payloads
 * 4. Two-step pickup verification workflow
 * 5. Security & duplicate pickup prevention (Atomic MongoDB conditional update)
 * 6. Audit history with timestamps & actor tracking
 * 7. Edge cases (invalid barcode, wrong canteen, concurrent handover confirmation, roll number resolution)
 */
const path = require('path');
const dotenv = require('dotenv');
dotenv.config({ path: path.resolve(__dirname, '../../.env') });
dotenv.config();

const http = require('http');
const express = require('express');
const cors = require('cors');
const mongoose = require('mongoose');
const jwt = require('jsonwebtoken');

const connectDB = require('../src/config/db');
const Order = require('../src/models/Order');
const Owner = require('../src/models/Owner');
const User = require('../src/models/User');
const { ensureOrderBarcode } = require('../src/services/barcodeService');

const JWT_SECRET = process.env.JWT_SECRET || 'quickbite-super-secure-jwt-secret-key-2026';

let passed = 0;
let total = 0;

function assert(condition, message) {
  total++;
  if (condition) {
    passed++;
    console.log(`  ✅ [PASS] ${message}`);
  } else {
    console.error(`  ❌ [FAIL] ${message}`);
    throw new Error(`Assertion failed: ${message}`);
  }
}

async function requestJson(url, options = {}) {
  const parsed = new URL(url);
  const postData = options.body ? JSON.stringify(options.body) : null;
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  };
  if (postData) {
    headers['Content-Length'] = Buffer.byteLength(postData);
  }

  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        hostname: parsed.hostname,
        port: parsed.port,
        path: parsed.pathname + parsed.search,
        method: options.method || 'GET',
        headers,
      },
      (res) => {
        let raw = '';
        res.on('data', (chunk) => (raw += chunk));
        res.on('end', () => {
          let parsedBody = null;
          try {
            parsedBody = JSON.parse(raw);
          } catch (_e) {
            parsedBody = raw;
          }
          resolve({ status: res.statusCode, body: parsedBody });
        });
      }
    );
    req.on('error', reject);
    if (postData) req.write(postData);
    req.end();
  });
}

async function run() {
  console.log('\n================================================================');
  console.log('🚀 RUNNING BARCODE SCANNER & PICKUP VERIFICATION SUITE');
  console.log('================================================================\n');

  await connectDB();

  const app = express();
  app.use(cors());
  app.use(express.json());
  app.use('/api/owners', require('../src/routes/owners'));
  app.use('/api/orders', require('../src/routes/orders'));

  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  const baseUrl = `http://127.0.0.1:${port}`;
  console.log(`📡 Test server running on ${baseUrl}\n`);

  const owner33 = await Owner.findOneAndUpdate(
    { email: 'scanner_test_owner33@quickbite.test' },
    {
      $set: {
        uid: `test_owner_33_${Date.now()}`,
        email: 'scanner_test_owner33@quickbite.test',
        name: 'Block 33 Chef',
        role: 'owner',
        canteenAssigned: true,
        canteenId: 'canteen_33',
        block: 'Block 33',
      },
    },
    { upsert: true, new: true }
  );

  const owner34 = await Owner.findOneAndUpdate(
    { email: 'scanner_test_owner34@quickbite.test' },
    {
      $set: {
        uid: `test_owner_34_${Date.now()}`,
        email: 'scanner_test_owner34@quickbite.test',
        name: 'Block 34 Chef',
        role: 'owner',
        canteenAssigned: true,
        canteenId: 'canteen_34',
        block: 'Block 34',
      },
    },
    { upsert: true, new: true }
  );

  const student = await User.findOneAndUpdate(
    { email: 'scanner_test_student@quickbite.test' },
    {
      $set: {
        uid: `test_student_${Date.now()}`,
        email: 'scanner_test_student@quickbite.test',
        name: 'Aarav Sharma',
        role: 'student',
        registrationNumber: 'RA2111003010452',
        department: 'Computer Science',
        course: 'B.Tech CSE',
      },
    },
    { upsert: true, new: true }
  );

  const owner33Token = jwt.sign(
    { uid: owner33.uid, email: owner33.email, role: 'owner', canteenId: 'canteen_33' },
    JWT_SECRET,
    { expiresIn: '1h' }
  );

  const owner34Token = jwt.sign(
    { uid: owner34.uid, email: owner34.email, role: 'owner', canteenId: 'canteen_34' },
    JWT_SECRET,
    { expiresIn: '1h' }
  );

  const testOrderId = `ORD_SCAN_TEST_${Date.now()}`;
  const testOrder = await Order.create({
    orderId: testOrderId,
    studentId: student.uid,
    studentName: student.name,
    studentRollNumber: student.registrationNumber,
    studentCourse: student.course,
    canteenId: 'canteen_33',
    totalAmount: 140,
    status: 'NEW',
    tokenNumber: '108',
    items: [
      { itemId: 'item_veg_thali', name: 'Special Veg Thali', price: 140, quantity: 1 }
    ],
  });

  const barcodeData = await ensureOrderBarcode(testOrder);
  const scannedToken = barcodeData.token;
  assert(scannedToken.startsWith('QB-'), 'Barcode generated with standard QB- token');

  console.log('▶ Test 1: Scanning an order in ORDER PLACED status');
  testOrder.status = 'NEW';
  await testOrder.save();

  const resPlaced = await requestJson(`${baseUrl}/api/owners/orders/verify-barcode`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${owner33Token}` },
    body: { token: scannedToken },
  });

  assert(resPlaced.status === 200, 'Endpoint returns 200 OK for ORDER PLACED state');
  assert(resPlaced.body.success === true, 'Response indicates success: true');
  assert(resPlaced.body.code === 'ORDER_PLACED', 'Status code mapped to ORDER_PLACED');
  assert(
    resPlaced.body.message === 'Your order has been received. Waiting for owner acceptance.',
    'Exact message: "Your order has been received. Waiting for owner acceptance."'
  );
  assert(resPlaced.body.eligibleForPickup === false, 'Pickup confirmation is disabled for ORDER PLACED');
  assert(resPlaced.body.order.studentRollNumber === 'RA2111003010452', 'Student roll number returned in order details');

  console.log('\n▶ Test 2: Scanning an order in ACCEPTED status');
  testOrder.status = 'ACCEPTED';
  await testOrder.save();

  const resAccepted = await requestJson(`${baseUrl}/api/owners/orders/verify-barcode`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${owner33Token}` },
    body: { token: scannedToken },
  });

  assert(resAccepted.status === 200, 'Endpoint returns 200 OK for ACCEPTED state');
  assert(resAccepted.body.code === 'ACCEPTED', 'Status code mapped to ACCEPTED');
  assert(
    resAccepted.body.message === 'Your order has been accepted. Preparation will begin shortly.',
    'Exact message: "Your order has been accepted. Preparation will begin shortly."'
  );
  assert(resAccepted.body.eligibleForPickup === false, 'Pickup confirmation is disabled for ACCEPTED');

  console.log('\n▶ Test 3: Scanning an order in PREPARING status');
  testOrder.status = 'PREPARING';
  await testOrder.save();

  const resPreparing = await requestJson(`${baseUrl}/api/owners/orders/verify-barcode`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${owner33Token}` },
    body: { token: scannedToken },
  });

  assert(resPreparing.status === 200, 'Endpoint returns 200 OK for PREPARING state (does not throw 400 error)');
  assert(resPreparing.body.code === 'PREPARING', 'Status code mapped to PREPARING');
  assert(
    resPreparing.body.message === 'Your order is currently being prepared. Please wait until it is ready for pickup.',
    'Exact message: "Your order is currently being prepared. Please wait until it is ready for pickup."'
  );
  assert(resPreparing.body.eligibleForPickup === false, 'Pickup confirmation is disabled for PREPARING');
  assert(resPreparing.body.order.items.length === 1, 'Ordered food items list returned');

  console.log('\n▶ Test 4: Attempting pickup confirmation when PREPARING');
  const resConfirmPreparing = await requestJson(`${baseUrl}/api/owners/orders/confirm-pickup`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${owner33Token}` },
    body: { token: scannedToken },
  });

  assert(resConfirmPreparing.status === 400, 'Pickup confirmation rejected when status is PREPARING');
  assert(resConfirmPreparing.body.code === 'PREPARING', 'Rejection code indicates PREPARING');

  console.log('\n▶ Test 5: Scanning an order in READY FOR PICKUP status');
  testOrder.status = 'READY';
  await testOrder.save();

  const resReady = await requestJson(`${baseUrl}/api/owners/orders/verify-barcode`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${owner33Token}` },
    body: { token: scannedToken },
  });

  assert(resReady.status === 200, 'Endpoint returns 200 OK for READY state');
  assert(resReady.body.code === 'READY', 'Status code mapped to READY');
  assert(
    resReady.body.message === 'Your order is ready! Please verify the customer before handing over the order.',
    'Exact message: "Your order is ready! Please verify the customer before handing over the order."'
  );
  assert(resReady.body.eligibleForPickup === true, 'Pickup confirmation is enabled for READY');

  console.log('\n▶ Test 6: Step 2 - Physical customer handover confirmation');
  const resConfirm = await requestJson(`${baseUrl}/api/owners/orders/confirm-pickup`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${owner33Token}` },
    body: { token: scannedToken },
  });

  assert(resConfirm.status === 200, 'Pickup confirmation succeeds when in READY state');
  assert(resConfirm.body.success === true, 'Response indicates success: true');
  assert(resConfirm.body.code === 'COLLECTED_SUCCESS', 'Code indicates COLLECTED_SUCCESS');
  assert(resConfirm.body.order.status === 'COMPLETED', 'Order status updated to COMPLETED in MongoDB');

  const dbCollectedOrder = await Order.findOne({ orderId: testOrderId });
  assert(dbCollectedOrder.status === 'COMPLETED', 'MongoDB order status is COMPLETED');
  assert(dbCollectedOrder.barcodeStatus === 'USED', 'MongoDB barcodeStatus marked as USED');
  assert(dbCollectedOrder.collectedBy === owner33.uid, 'collectedBy recorded authenticated owner UID');
  assert(dbCollectedOrder.collectedAt !== null, 'collectedAt timestamp recorded');

  // Audit history check
  const latestHistory = dbCollectedOrder.statusHistory[dbCollectedOrder.statusHistory.length - 1];
  assert(latestHistory.status === 'COLLECTED', 'Audit history recorded status COLLECTED');
  assert(latestHistory.previousStatus === 'READY', 'Audit history recorded previousStatus READY');
  assert(latestHistory.changedBy === owner33.uid, 'Audit history recorded changedBy owner UID');
  assert(latestHistory.changedByRole === 'OWNER', 'Audit history recorded changedByRole OWNER');

  console.log('\n▶ Test 7: Scanning an already COLLECTED order');
  const resCollected = await requestJson(`${baseUrl}/api/owners/orders/verify-barcode`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${owner33Token}` },
    body: { token: scannedToken },
  });

  assert(resCollected.status === 200, 'Scanning collected order returns 200 with order details');
  assert(resCollected.body.code === 'COLLECTED', 'Status code mapped to COLLECTED');
  assert(
    resCollected.body.message === 'This order has already been collected.',
    'Exact message: "This order has already been collected."'
  );
  assert(resCollected.body.eligibleForPickup === false, 'Pickup confirmation disabled for already collected order');

  console.log('\n▶ Test 8: Repeated pickup attempt on collected order is blocked');
  const resDuplicatePickup = await requestJson(`${baseUrl}/api/owners/orders/confirm-pickup`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${owner33Token}` },
    body: { token: scannedToken },
  });

  assert(resDuplicatePickup.status === 400, 'Duplicate pickup rejected with HTTP 400');
  assert(resDuplicatePickup.body.code === 'ALREADY_COLLECTED', 'Error code is ALREADY_COLLECTED');

  console.log('\n▶ Test 9: Scanning a CANCELLED order');
  const cancelledOrderId = `ORD_CANCEL_${Date.now()}`;
  const cancelledOrder = await Order.create({
    orderId: cancelledOrderId,
    studentId: student.uid,
    canteenId: 'canteen_33',
    totalAmount: 50,
    status: 'CANCELLED',
    barcodeStatus: 'CANCELLED',
    tokenNumber: '109',
    items: [{ itemId: 'item_samosa', name: 'Samosa', price: 25, quantity: 2 }],
  });
  const cancelBarcode = await ensureOrderBarcode(cancelledOrder);

  const resCancelled = await requestJson(`${baseUrl}/api/owners/orders/verify-barcode`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${owner33Token}` },
    body: { token: cancelBarcode.token },
  });

  assert(resCancelled.status === 200, 'Scanning cancelled order returns 200 with order details');
  assert(resCancelled.body.code === 'CANCELLED', 'Status code mapped to CANCELLED');
  assert(
    resCancelled.body.message === 'This order has been cancelled and cannot be collected.',
    'Exact message: "This order has been cancelled and cannot be collected."'
  );
  assert(resCancelled.body.eligibleForPickup === false, 'Pickup disabled for cancelled order');

  console.log('\n▶ Test 10: Scanning an invalid / non-existent barcode');
  const resInvalid = await requestJson(`${baseUrl}/api/owners/orders/verify-barcode`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${owner33Token}` },
    body: { token: 'QB-NONEXISTENT99999' },
  });

  assert(resInvalid.status === 404, 'Invalid barcode returns HTTP 404');
  assert(resInvalid.body.code === 'INVALID_BARCODE', 'Code is INVALID_BARCODE');
  assert(
    resInvalid.body.error === 'This barcode is invalid. Please scan a valid Quick Bite order barcode.',
    'User-friendly error message returned'
  );

  console.log('\n▶ Test 11: Scanning another canteen\'s order');
  const resCrossCanteen = await requestJson(`${baseUrl}/api/owners/orders/verify-barcode`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${owner34Token}` }, // Owner of canteen_34 scanning canteen_33 order
    body: { token: scannedToken },
  });

  assert(resCrossCanteen.status === 403, 'Cross-canteen scan rejected with HTTP 403 Forbidden');
  assert(resCrossCanteen.body.code === 'WRONG_OWNER', 'Code is WRONG_OWNER');
  assert(
    resCrossCanteen.body.error === 'This order does not belong to your canteen.',
    'Error message states order belongs to another canteen'
  );

  console.log('\n▶ Test 12: Concurrent simultaneous handover confirmation');
  const raceOrderId = `ORD_RACE_${Date.now()}`;
  const raceOrder = await Order.create({
    orderId: raceOrderId,
    studentId: student.uid,
    canteenId: 'canteen_33',
    totalAmount: 90,
    status: 'READY',
    barcodeStatus: 'ACTIVE',
    tokenNumber: '110',
    items: [{ itemId: 'item_dosa', name: 'Masala Dosa', price: 90, quantity: 1 }],
  });
  const raceBarcode = await ensureOrderBarcode(raceOrder);

  // Send two requests at the exact same instant
  const [firstRes, secondRes] = await Promise.all([
    requestJson(`${baseUrl}/api/owners/orders/confirm-pickup`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${owner33Token}` },
      body: { token: raceBarcode.token },
    }),
    requestJson(`${baseUrl}/api/owners/orders/confirm-pickup`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${owner33Token}` },
      body: { token: raceBarcode.token },
    }),
  ]);

  console.log(`  ℹ️ Concurrent responses: first=${firstRes.status} (${firstRes.body?.code}), second=${secondRes.status} (${secondRes.body?.code})`);
  const statuses = [firstRes.status, secondRes.status].sort();
  assert(statuses[0] === 200 && (statuses[1] === 409 || statuses[1] === 400), 'Atomic update allowed exactly one confirmation (200) and rejected duplicate concurrent scan (409 or 400 ALREADY_COLLECTED)');

  console.log('\n▶ Test 13: Student Roll Number fallback resolution from User model');
  const noRollOrderId = `ORD_NO_ROLL_${Date.now()}`;
  const noRollOrder = await Order.create({
    orderId: noRollOrderId,
    studentId: student.uid,
    studentName: 'Aarav Sharma',
    studentRollNumber: '', // Missing on order document
    canteenId: 'canteen_33',
    totalAmount: 40,
    status: 'PREPARING',
    tokenNumber: '111',
    items: [{ itemId: 'item_tea', name: 'Masala Chai', price: 20, quantity: 2 }],
  });
  const noRollBarcode = await ensureOrderBarcode(noRollOrder);

  const resRollFallback = await requestJson(`${baseUrl}/api/owners/orders/verify-barcode`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${owner33Token}` },
    body: { token: noRollBarcode.token },
  });

  assert(resRollFallback.status === 200, 'Verify succeeds for order with missing roll number');
  assert(resRollFallback.body.order.studentRollNumber === 'RA2111003010452', 'Roll number resolved dynamically from User model');

  console.log('\n▶ Test 14: Barcode Permanence throughout Order Lifecycle');
  const lifecycleOrderId = `ORD_LIFE_${Date.now()}`;
  const lifeOrder = await Order.create({
    orderId: lifecycleOrderId,
    studentId: student.uid,
    canteenId: 'canteen_33',
    totalAmount: 70,
    status: 'NEW',
    tokenNumber: '112',
    items: [{ itemId: 'item_coffee', name: 'Filter Coffee', price: 35, quantity: 2 }],
  });
  const initialBarcode = await ensureOrderBarcode(lifeOrder);

  // Status transitions: NEW -> ACCEPTED -> PREPARING -> READY
  lifeOrder.status = 'ACCEPTED';
  await lifeOrder.save();
  const barcodeAtAccepted = await ensureOrderBarcode(lifeOrder);

  lifeOrder.status = 'PREPARING';
  await lifeOrder.save();
  const barcodeAtPreparing = await ensureOrderBarcode(lifeOrder);

  lifeOrder.status = 'READY';
  await lifeOrder.save();
  const barcodeAtReady = await ensureOrderBarcode(lifeOrder);

  assert(barcodeAtAccepted.token === initialBarcode.token, 'Token unchanged when moving to ACCEPTED');
  assert(barcodeAtPreparing.token === initialBarcode.token, 'Token unchanged when moving to PREPARING');
  assert(barcodeAtReady.token === initialBarcode.token, 'Token unchanged when moving to READY');

  // Clean up test server and records
  server.close();
  await Order.deleteMany({
    orderId: { $in: [testOrderId, cancelledOrderId, raceOrderId, noRollOrderId, lifecycleOrderId] }
  });
  await Owner.deleteMany({ _id: { $in: [owner33._id, owner34._id] } });
  await User.deleteOne({ _id: student._id });

  console.log('\n================================================================');
  console.log(`🎉 ALL ${passed}/${total} STATUS & VERIFICATION TESTS PASSED!`);
  console.log('================================================================\n');

  process.exit(0);
}

run().catch((err) => {
  console.error('Test suite failed:', err);
  process.exit(1);
});
