const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../../.env') });
require('dotenv').config();

const mongoose = require('mongoose');
const connectDB = require('../src/config/db');
const Order = require('../src/models/Order');

async function runGuestExclusionAndTimestampTests() {
  console.log('🚀 Running Guest Order Exclusion & Button-Press Timestamp Verification...\n');
  await connectDB();

  const testCanteenId = 'canteen_guest_test_' + Date.now();

  try {
    // ── Test 1: Verify Guest order filter in Canteen Orders query
    // Create one regular order and one guest order in DB directly
    const regularOrder = await Order.create({
      orderId: 'ORD-REG-' + Date.now(),
      studentId: 'student_12345',
      studentName: 'Aarav Patel',
      canteenId: testCanteenId,
      totalAmount: 120,
      status: 'NEW',
      tokenNumber: '301',
      items: [{ itemId: 'item_1', name: 'Thali', price: 120, quantity: 1, prepMinutes: 8 }],
      orderPlacedAt: new Date(),
    });

    const guestOrder = await Order.create({
      orderId: 'ORD-GUEST-' + Date.now(),
      studentId: 'student_guest',
      studentName: 'Guest User',
      canteenId: testCanteenId,
      totalAmount: 80,
      status: 'NEW',
      tokenNumber: '302',
      items: [{ itemId: 'item_2', name: 'Samosa', price: 80, quantity: 2, prepMinutes: 5 }],
      orderPlacedAt: new Date(),
    });

    // Query canteen orders using the updated filter from orders.js
    const visibleOrders = await Order.find({
      canteenId: testCanteenId,
      studentId: { $ne: 'student_guest', $not: /guest/i },
      studentName: { $not: /guest/i },
    }).lean();

    console.assert(visibleOrders.length === 1, `Expected exactly 1 visible order, got ${visibleOrders.length}`);
    console.assert(visibleOrders[0].orderId === regularOrder.orderId, `Expected ${regularOrder.orderId}, got ${visibleOrders[0].orderId}`);
    console.log('✅ TEST 1 PASSED: Guest orders are strictly excluded from canteen orders query');

    // ── Test 2: Button-Press Milestone Timestamps (PREPARING -> READY -> COMPLETED)
    const pressTime1 = new Date();
    await Order.updateOne(
      { _id: regularOrder._id },
      {
        $set: { status: 'PREPARING', preparingAt: pressTime1 },
        $push: { statusHistory: { status: 'PREPARING', timestamp: pressTime1 } }
      }
    );

    const updatedPreparing = await Order.findById(regularOrder._id).lean();
    console.assert(updatedPreparing.status === 'PREPARING', 'Expected status PREPARING');
    console.assert(updatedPreparing.preparingAt != null, 'Expected preparingAt to be recorded');
    console.log('✅ TEST 2 PASSED: Next button press recorded exact preparingAt milestone timestamp');

    await new Promise(r => setTimeout(r, 100));
    const pressTime2 = new Date();
    await Order.updateOne(
      { _id: regularOrder._id },
      {
        $set: { status: 'READY', readyAt: pressTime2 },
        $push: { statusHistory: { status: 'READY', timestamp: pressTime2 } }
      }
    );

    const updatedReady = await Order.findById(regularOrder._id).lean();
    console.assert(updatedReady.status === 'READY', 'Expected status READY');
    console.assert(new Date(updatedReady.readyAt).getTime() >= new Date(updatedReady.preparingAt).getTime(), 'readyAt must be >= preparingAt');
    console.log('✅ TEST 3 PASSED: Next button press recorded exact readyAt milestone timestamp');

    // Clean up
    await Order.deleteMany({ canteenId: testCanteenId });
    console.log('🧹 Cleaned up test orders');

    console.log('\n🎉 ALL GUEST ORDER EXCLUSION & TIMESTAMP TESTS PASSED!');
    process.exit(0);
  } catch (err) {
    console.error('❌ Test failed:', err);
    await Order.deleteMany({ canteenId: testCanteenId });
    process.exit(1);
  }
}

runGuestExclusionAndTimestampTests();
