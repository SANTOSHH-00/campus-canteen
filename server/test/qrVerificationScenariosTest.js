/**
 * Comprehensive verification scenarios test script
 * Tests all required states and security validations for Dynamic QR Order Verification
 */
const path = require('path');
const dotenv = require('dotenv');
dotenv.config({ path: path.resolve(__dirname, '../../.env') });
dotenv.config();

const mongoose = require('mongoose');
const jwt = require('jsonwebtoken');
const connectDB = require('../src/config/db');
const Order = require('../src/models/Order');
const Owner = require('../src/models/Owner');
const OrderVerification = require('../src/models/OrderVerification');
const { createOrRotateOrderVerification, hashVerificationToken } = require('../src/services/qrVerificationService');

const JWT_SECRET = process.env.JWT_SECRET || 'quickbite-super-secure-jwt-secret-key-2026';

let passedTests = 0;
let totalTests = 0;

function assert(condition, message) {
  totalTests++;
  if (condition) {
    passedTests++;
    console.log(`  ✅ [PASS] ${message}`);
  } else {
    console.error(`  ❌ [FAIL] ${message}`);
    throw new Error(`Assertion failed: ${message}`);
  }
}

async function runTests() {
  console.log('\n======================================================');
  console.log('🚀 RUNNING DYNAMIC QR ORDER VERIFICATION TEST SUITE');
  console.log('======================================================\n');

  await connectDB();

  // Create mock owner 1 (assigned to canteen_33)
  const testOwner1Uid = `test_owner_33_${Date.now()}`;
  const testOwner1 = await Owner.findOneAndUpdate(
    { email: 'test_owner_33@quickbite.test' },
    {
      $set: {
        uid: testOwner1Uid,
        email: 'test_owner_33@quickbite.test',
        name: 'Test Owner 33',
        role: 'owner',
        canteenAssigned: true,
        canteenId: 'canteen_33',
        block: 'Block 33',
      },
    },
    { upsert: true, new: true }
  );

  const owner1Token = jwt.sign(
    { uid: testOwner1.uid, email: testOwner1.email, role: 'owner', canteenId: testOwner1.canteenId },
    JWT_SECRET,
    { expiresIn: '1h' }
  );

  // Create mock owner 2 (assigned to canteen_34)
  const testOwner2Uid = `test_owner_34_${Date.now()}`;
  const testOwner2 = await Owner.findOneAndUpdate(
    { email: 'test_owner_34@quickbite.test' },
    {
      $set: {
        uid: testOwner2Uid,
        email: 'test_owner_34@quickbite.test',
        name: 'Test Owner 34',
        role: 'owner',
        canteenAssigned: true,
        canteenId: 'canteen_34',
        block: 'Block 34',
      },
    },
    { upsert: true, new: true }
  );

  const owner2Token = jwt.sign(
    { uid: testOwner2.uid, email: testOwner2.email, role: 'owner', canteenId: testOwner2.canteenId },
    JWT_SECRET,
    { expiresIn: '1h' }
  );

  // Import Express app for supertest-like internal invocation or direct route simulation
  const express = require('express');
  const app = express();
  app.use(express.json());
  app.use('/api/owner', require('../src/routes/owners'));
  app.use('/api/orders', require('../src/routes/orders'));

  // Simple fetch simulator using express app handle
  const http = require('http');
  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, resolve));
  const port = server.address().port;
  const baseUrl = `http://127.0.0.1:${port}`;

  async function postVerify(token, authToken) {
    const headers = { 'Content-Type': 'application/json' };
    if (authToken) headers['Authorization'] = `Bearer ${authToken}`;
    const res = await fetch(`${baseUrl}/api/owner/orders/verify-qr`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ token }),
    });
    const data = await res.json();
    return { status: res.status, data };
  }

  try {
    // ---------------------------------------------------------
    // Test 1: Unauthorized Owner (Missing Auth Token)
    // ---------------------------------------------------------
    console.log('\n--- Scenario 1: Unauthorized Owner ---');
    const res1 = await postVerify('some_dummy_token', null);
    assert(res1.status === 401, 'Returns 401 for missing authorization header');
    assert(res1.data.code === 'UNAUTHORIZED', 'Response code is UNAUTHORIZED');

    // ---------------------------------------------------------
    // Test 2: Invalid QR Token (Not Found)
    // ---------------------------------------------------------
    console.log('\n--- Scenario 2: Invalid QR Code ---');
    const res2 = await postVerify('non_existent_fake_token_hex_123456789', owner1Token);
    assert(res2.status === 404, 'Returns 404 for unknown token hash');
    assert(res2.data.code === 'INVALID_QR', 'Response code is INVALID_QR');

    // ---------------------------------------------------------
    // Test 3: Order Not Ready (Order in PREPARING status)
    // ---------------------------------------------------------
    console.log('\n--- Scenario 3: Order Not Ready ---');
    const orderPrepId = `ORD-TEST-PREP-${Date.now()}`;
    await Order.create({
      orderId: orderPrepId,
      studentId: 'student_test_1',
      studentName: 'Alice Test',
      canteenId: 'canteen_33',
      totalAmount: 120,
      status: 'PREPARING',
      tokenNumber: '801',
    });
    const verPrep = await createOrRotateOrderVerification({
      orderId: orderPrepId,
      studentId: 'student_test_1',
      canteenId: 'canteen_33',
    });

    const res3 = await postVerify(verPrep.rawToken, owner1Token);
    assert(res3.status === 400, 'Returns 400 when order is not READY');
    assert(res3.data.code === 'ORDER_NOT_READY', 'Response code is ORDER_NOT_READY');

    // ---------------------------------------------------------
    // Test 4: Wrong Canteen (Order belongs to canteen_34, verified by owner of canteen_33)
    // ---------------------------------------------------------
    console.log('\n--- Scenario 4: Wrong Canteen Verification ---');
    const orderWrongCanteenId = `ORD-TEST-CANTEEN34-${Date.now()}`;
    await Order.create({
      orderId: orderWrongCanteenId,
      studentId: 'student_test_2',
      studentName: 'Bob Test',
      canteenId: 'canteen_34',
      totalAmount: 180,
      status: 'READY',
      tokenNumber: '802',
    });
    const verWrongCanteen = await createOrRotateOrderVerification({
      orderId: orderWrongCanteenId,
      studentId: 'student_test_2',
      canteenId: 'canteen_34',
    });

    const res4 = await postVerify(verWrongCanteen.rawToken, owner1Token);
    assert(res4.status === 403, 'Returns 403 for wrong canteen attempt');
    assert(res4.data.code === 'WRONG_CANTEEN', 'Response code is WRONG_CANTEEN');

    // ---------------------------------------------------------
    // Test 5: Expired QR Code
    // ---------------------------------------------------------
    console.log('\n--- Scenario 5: Expired QR Code ---');
    const orderExpiredId = `ORD-TEST-EXPIRED-${Date.now()}`;
    await Order.create({
      orderId: orderExpiredId,
      studentId: 'student_test_3',
      studentName: 'Charlie Test',
      canteenId: 'canteen_33',
      totalAmount: 90,
      status: 'READY',
      tokenNumber: '803',
    });
    // Create verification that expired 10 minutes ago
    const expiredRawToken = 'expired_raw_token_' + Date.now();
    const expiredTokenHash = hashVerificationToken(expiredRawToken);
    await OrderVerification.create({
      orderId: orderExpiredId,
      studentId: 'student_test_3',
      canteenId: 'canteen_33',
      tokenHash: expiredTokenHash,
      status: 'ACTIVE',
      expiresAt: new Date(Date.now() - 10 * 60 * 1000),
    });

    const res5 = await postVerify(expiredRawToken, owner1Token);
    assert(res5.status === 400, 'Returns 400 for expired QR code');
    assert(res5.data.code === 'EXPIRED_QR', 'Response code is EXPIRED_QR');

    // ---------------------------------------------------------
    // Test 6: Cancelled Order
    // ---------------------------------------------------------
    console.log('\n--- Scenario 6: Cancelled Order ---');
    const orderCancelledId = `ORD-TEST-CANCELLED-${Date.now()}`;
    await Order.create({
      orderId: orderCancelledId,
      studentId: 'student_test_4',
      studentName: 'Dave Test',
      canteenId: 'canteen_33',
      totalAmount: 150,
      status: 'CANCELLED',
      tokenNumber: '804',
    });
    const verCancelled = await createOrRotateOrderVerification({
      orderId: orderCancelledId,
      studentId: 'student_test_4',
      canteenId: 'canteen_33',
    });

    const res6 = await postVerify(verCancelled.rawToken, owner1Token);
    assert(res6.status === 400, 'Returns 400 for cancelled order');
    assert(res6.data.code === 'ORDER_CANCELLED', 'Response code is ORDER_CANCELLED');

    // ---------------------------------------------------------
    // Test 7: Successful Order Verification & Redemption
    // ---------------------------------------------------------
    console.log('\n--- Scenario 7: Successful Verification & Status Transition ---');
    const orderSuccessId = `ORD-TEST-SUCCESS-${Date.now()}`;
    await Order.create({
      orderId: orderSuccessId,
      studentId: 'student_test_5',
      studentName: 'Eva Test',
      canteenId: 'canteen_33',
      totalAmount: 250,
      status: 'READY',
      tokenNumber: '805',
      items: [{ itemId: 'item_1', name: 'Veg Sandwich', price: 125, quantity: 2 }],
    });
    const verSuccess = await createOrRotateOrderVerification({
      orderId: orderSuccessId,
      studentId: 'student_test_5',
      canteenId: 'canteen_33',
    });

    const res7 = await postVerify(verSuccess.rawToken, owner1Token);
    assert(res7.status === 200, 'Returns 200 for valid QR code');
    assert(res7.data.success === true, 'Success is true');
    assert(res7.data.code === 'VERIFIED_SUCCESS', 'Code is VERIFIED_SUCCESS');
    assert(res7.data.order.status === 'PICKED_UP', 'Order status updated to PICKED_UP');
    assert(res7.data.verification.verifiedBy === testOwner1.uid, 'Verification recorded owner UID');

    // Verify DB state
    const dbOrder = await Order.findOne({ orderId: orderSuccessId }).lean();
    assert(dbOrder.status === 'PICKED_UP', 'Database Order record is PICKED_UP');
    assert(dbOrder.pickedUpAt != null, 'Database Order record has pickedUpAt timestamp');

    const dbVer = await OrderVerification.findOne({ _id: verSuccess.verificationId }).lean();
    assert(dbVer.status === 'REDEEMED', 'Database OrderVerification record is REDEEMED');
    assert(dbVer.usedAt != null, 'Database OrderVerification record has usedAt timestamp');

    // ---------------------------------------------------------
    // Test 8: Already-Used / Reused QR Code (Re-scan attempt)
    // ---------------------------------------------------------
    console.log('\n--- Scenario 8: Reused QR Code ---');
    const res8 = await postVerify(verSuccess.rawToken, owner1Token);
    assert(res8.status === 400, 'Returns 400 for already redeemed QR code');
    assert(res8.data.code === 'ALREADY_REDEEMED', 'Response code is ALREADY_REDEEMED');

    // ---------------------------------------------------------
    // Test 9: Student Dynamic QR Retrieval & Rotation (GET /api/orders/:id/qr)
    // ---------------------------------------------------------
    console.log('\n--- Scenario 9: Student Dynamic QR Retrieval & Rotation ---');
    const orderRotateId = `ORD-TEST-ROT-${Date.now()}`;
    await Order.create({
      orderId: orderRotateId,
      studentId: 'student_test_rot',
      studentName: 'Frank Test',
      canteenId: 'canteen_33',
      totalAmount: 100,
      status: 'READY',
      tokenNumber: '806',
    });

    const qrRes = await fetch(`${baseUrl}/api/orders/${orderRotateId}/qr`);
    const qrData = await qrRes.json();
    assert(qrRes.status === 200, 'GET /api/orders/:id/qr returns 200');
    assert(qrData.success === true, 'Returns success true');
    assert(qrData.qrDataUrl && qrData.qrDataUrl.startsWith('data:image/png;base64,'), 'Returns valid PNG data URL');
    assert(qrData.rawToken != null, 'Returns fresh rawToken payload');

    // Verify that the newly rotated token can be redeemed by the owner
    const resRotVerify = await postVerify(qrData.rawToken, owner1Token);
    assert(resRotVerify.status === 200, 'Rotated token can be redeemed successfully');
    assert(resRotVerify.data.order.status === 'PICKED_UP', 'Rotated order is now PICKED_UP');

    // Clean up test data
    await Order.deleteMany({ orderId: { $regex: '^ORD-TEST-' } });
    await OrderVerification.deleteMany({ studentId: { $regex: '^student_test_' } });
    await Owner.deleteMany({ email: { $in: ['test_owner_33@quickbite.test', 'test_owner_34@quickbite.test'] } });

    console.log('\n======================================================');
    console.log(`🎉 ALL ${passedTests} OF ${totalTests} TESTS PASSED SUCCESSFULLY!`);
    console.log('======================================================\n');
  } finally {
    server.close();
    await mongoose.connection.close();
  }
}

runTests().catch((err) => {
  console.error('\n❌ Test execution failed:', err);
  process.exit(1);
});

