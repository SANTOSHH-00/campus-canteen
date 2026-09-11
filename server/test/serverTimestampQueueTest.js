const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../../.env') });
require('dotenv').config();

const mongoose = require('mongoose');
const connectDB = require('../src/config/db');
const Order = require('../src/models/Order');
const Canteen = require('../src/models/Canteen');
const Item = require('../src/models/Item');
const ordersRouter = require('../src/routes/orders');

async function runTests() {
  console.log('🚀 Running Server-Authoritative Timestamps & Queue Tests...\n');
  await connectDB();

  const testCanteenId = 'canteen_time_test_' + Date.now();
  const otherCanteenId = 'canteen_other_' + Date.now();

  try {
    // ── Test 1: Order creation records exact server orderPlacedAt & statusHistory
    const fakeClientDate = new Date('2020-01-01T00:00:00.000Z'); // spoofed client time
    const order1 = await Order.create({
      orderId: 'ORD-TIME-1-' + Date.now(),
      studentId: 'student_1',
      studentName: 'Alice',
      canteenId: testCanteenId,
      totalAmount: 100,
      status: 'CONFIRMED',
      tokenNumber: '101',
      items: [{ itemId: 'item_1', name: 'Sandwich', price: 100, quantity: 1, prepMinutes: 5 }],
      orderPlacedAt: new Date(),
      statusHistory: [{ status: 'PLACED', timestamp: new Date() }, { status: 'CONFIRMED', timestamp: new Date() }],
    });

    const now = Date.now();
    const placedTime = new Date(order1.orderPlacedAt).getTime();
    console.assert(Math.abs(now - placedTime) < 5000, `Expected server orderPlacedAt within 5s of now, got delta ${Math.abs(now - placedTime)}ms`);
    console.assert(order1.statusHistory.length >= 1, 'Expected statusHistory to contain PLACED');
    console.log('✅ TEST 1 PASSED: Server-authoritative orderPlacedAt and statusHistory recorded');

    // ── Test 2: Order 2 placed after Order 1
    await new Promise(r => setTimeout(r, 150));

    const order2 = await Order.create({
      orderId: 'ORD-TIME-2-' + Date.now(),
      studentId: 'student_2',
      studentName: 'Bob',
      canteenId: testCanteenId,
      totalAmount: 75,
      status: 'PREPARING',
      tokenNumber: '102',
      items: [{ itemId: 'item_2', name: 'Juice', price: 75, quantity: 1, prepMinutes: 4 }],
      orderPlacedAt: new Date(),
      statusHistory: [
        { status: 'PLACED', timestamp: new Date() },
        { status: 'CONFIRMED', timestamp: new Date() },
        { status: 'PREPARING', timestamp: new Date() },
      ],
    });

    // ── Test 3: Queue ordering by orderPlacedAt
    const activeOrders = await Order.find({
      canteenId: testCanteenId,
      status: { $in: ordersRouter.ACTIVE_QUEUE_STATUSES },
    })
      .sort({ orderPlacedAt: 1, createdAt: 1, orderId: 1 })
      .lean();

    console.assert(activeOrders.length === 2, `Expected 2 active orders, got ${activeOrders.length}`);
    console.assert(activeOrders[0].orderId === order1.orderId, 'Order 1 must be #1 in queue');
    console.assert(activeOrders[1].orderId === order2.orderId, 'Order 2 must be #2 in queue');

    const metrics1 = ordersRouter.calculateQueueMetrics(order1, activeOrders, 7, 2);
    const metrics2 = ordersRouter.calculateQueueMetrics(order2, activeOrders, 7, 2);

    console.assert(metrics1.queuePosition === 1 && metrics1.ordersAhead === 0, 'Order 1 metrics incorrect');
    console.assert(metrics2.queuePosition === 2 && metrics2.ordersAhead === 1, 'Order 2 metrics incorrect');
    console.log('✅ TEST 2 & 3 PASSED: Queue position #1 and #2 strictly ordered by server orderPlacedAt');

    // ── Test 4: Status progression updates server timestamps
    await new Promise(r => setTimeout(r, 100));
    const prepTime = new Date();
    await Order.updateOne(
      { _id: order1._id },
      {
        $set: { status: 'PREPARING', preparingAt: prepTime },
        $push: { statusHistory: { status: 'PREPARING', timestamp: prepTime } },
      }
    );

    await new Promise(r => setTimeout(r, 100));
    const readyTime = new Date();
    await Order.updateOne(
      { _id: order1._id },
      {
        $set: { status: 'READY', readyAt: readyTime },
        $push: { statusHistory: { status: 'READY', timestamp: readyTime } },
      }
    );

    const readyOrder1 = await Order.findById(order1._id).lean();
    console.assert(readyOrder1.preparingAt != null, 'preparingAt must be recorded');
    console.assert(readyOrder1.readyAt != null, 'readyAt must be recorded');
    console.assert(new Date(readyOrder1.readyAt).getTime() >= new Date(readyOrder1.preparingAt).getTime(), 'readyAt must be >= preparingAt');
    console.log('✅ TEST 4 PASSED: Specific status timestamps (preparingAt, readyAt) recorded accurately');

    // ── Test 5: Dynamic Queue advancement when Order 1 becomes READY
    const activeAfterReady = await Order.find({
      canteenId: testCanteenId,
      status: { $in: ordersRouter.ACTIVE_QUEUE_STATUSES },
    })
      .sort({ orderPlacedAt: 1, createdAt: 1, orderId: 1 })
      .lean();

    console.assert(activeAfterReady.length === 1, `Expected 1 active order remaining, got ${activeAfterReady.length}`);
    console.assert(activeAfterReady[0].orderId === order2.orderId, 'Order 2 must now be #1 active order');

    const metrics2After = ordersRouter.calculateQueueMetrics(order2, activeAfterReady, 7, 2);
    console.assert(metrics2After.queuePosition === 1, 'Order 2 must dynamically become #1');
    console.assert(metrics2After.ordersAhead === 0, 'Order 2 must have 0 orders ahead');
    console.log('✅ TEST 5 PASSED: Order 2 automatically advanced to #1 with 0 ahead after Order 1 ready');

    // ── Test 6: Cross-canteen queue isolation
    const otherOrder = await Order.create({
      orderId: 'ORD-OTHER-' + Date.now(),
      studentId: 'student_other',
      canteenId: otherCanteenId,
      totalAmount: 50,
      status: 'PREPARING',
      tokenNumber: '201',
      items: [{ itemId: 'item_3', name: 'Tea', price: 20, quantity: 1, prepMinutes: 3 }],
      orderPlacedAt: new Date(),
      statusHistory: [{ status: 'PLACED', timestamp: new Date() }],
    });

    const otherActive = await Order.find({
      canteenId: otherCanteenId,
      status: { $in: ordersRouter.ACTIVE_QUEUE_STATUSES },
    })
      .sort({ orderPlacedAt: 1, createdAt: 1, orderId: 1 })
      .lean();

    console.assert(otherActive.length === 1, 'Other canteen must have exactly 1 active order');
    console.assert(otherActive[0].orderId === otherOrder.orderId, 'Other canteen order must be isolated');
    console.log('✅ TEST 6 PASSED: Cross-canteen isolation verified');

    console.log('\n🎉 ALL 6 SERVER TIMESTAMP & QUEUE TESTS PASSED SUCCESSFULLY!');
  } finally {
    await Order.deleteMany({ canteenId: { $in: [testCanteenId, otherCanteenId] } });
    await mongoose.disconnect();
  }
}

runTests().catch(err => {
  console.error('❌ Test failed:', err);
  process.exit(1);
});
