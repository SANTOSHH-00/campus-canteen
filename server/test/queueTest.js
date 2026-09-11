const path = require('path');
const dotenv = require('dotenv');
dotenv.config({ path: path.resolve(__dirname, '../../.env') });
dotenv.config();

const mongoose = require('mongoose');
const connectDB = require('../src/config/db');
const Order = require('../src/models/Order');
const Canteen = require('../src/models/Canteen');

const TEST_CANTEEN_A = 'canteen_test_queue_a';
const TEST_CANTEEN_B = 'canteen_test_queue_b';

let passedTests = 0;
let totalTests = 0;

function assert(condition, message) {
  totalTests++;
  if (!condition) {
    console.error(`❌ FAIL: ${message}`);
    throw new Error(message);
  } else {
    passedTests++;
    console.log(`✅ PASS: ${message}`);
  }
}

async function runQueueTests() {
  console.log('🚀 Starting Quick Bite Queue System Verification Tests...\n');
  await connectDB();

  try {
    // 0. Clean previous test data
    await Order.deleteMany({ canteenId: { $in: [TEST_CANTEEN_A, TEST_CANTEEN_B] } });
    await Canteen.deleteMany({ id: { $in: [TEST_CANTEEN_A, TEST_CANTEEN_B] } });

    // Seed test canteens with configured avgWaitMinutes
    await Canteen.create({
      id: TEST_CANTEEN_A,
      name: 'Test Canteen A',
      avgWaitMinutes: 5,
    });
    await Canteen.create({
      id: TEST_CANTEEN_B,
      name: 'Test Canteen B',
      avgWaitMinutes: 6,
    });

    // Helper functions mirroring orders.js route logic
    const ACTIVE_QUEUE_STATUSES = ['NEW', 'WAITING', 'CONFIRMED', 'PREPARING'];

    function getOrderPrepTime(order, defaultWait = 7) {
      if (order && order.estimatedPrepMinutes && order.estimatedPrepMinutes > 0) {
        return order.estimatedPrepMinutes;
      }
      return defaultWait;
    }

    async function getCanteenQueueStats(canteenId) {
      const activeOrders = await Order.find({
        canteenId,
        status: { $in: ACTIVE_QUEUE_STATUSES },
      }).sort({ createdAt: 1, orderId: 1 }).lean();

      let totalWait = 0;
      for (const o of activeOrders) {
        totalWait += getOrderPrepTime(o, 7);
      }
      return {
        queueCount: activeOrders.length,
        avgWaitMinutes: activeOrders.length === 0 ? 5 : totalWait + 5,
        activeOrders,
      };
    }

    async function getOrderQueuePosition(orderId) {
      const targetOrder = await Order.findOne({ orderId }).lean();
      if (!targetOrder) return null;

      if (targetOrder.status === 'READY') {
        return { queuePosition: 0, ordersAhead: 0, estWaitMinutes: 0, status: 'READY' };
      }
      if (targetOrder.status === 'COMPLETED') {
        return { queuePosition: 0, ordersAhead: 0, estWaitMinutes: 0, status: 'COMPLETED' };
      }
      if (targetOrder.status === 'CANCELLED') {
        return { queuePosition: 0, ordersAhead: 0, estWaitMinutes: 0, status: 'CANCELLED' };
      }

      const activeOrders = await Order.find({
        canteenId: targetOrder.canteenId,
        status: { $in: ACTIVE_QUEUE_STATUSES },
      }).sort({ createdAt: 1, orderId: 1 }).lean();

      const targetIndex = activeOrders.findIndex(o => o.orderId === targetOrder.orderId);
      if (targetIndex === -1) {
        return { queuePosition: 1, ordersAhead: 0, estWaitMinutes: 7, status: targetOrder.status };
      }

      let cumulativeWait = 0;
      for (let i = 0; i <= targetIndex; i++) {
        cumulativeWait += getOrderPrepTime(activeOrders[i], 7);
      }

      return {
        queuePosition: targetIndex + 1,
        ordersAhead: targetIndex,
        estWaitMinutes: cumulativeWait,
        status: targetOrder.status,
      };
    }

    // ── TEST 1: User A, User B, User C order from Canteen A ────────────────
    console.log('--- TEST 1: Multi-User Shared Queue in Canteen A ---');
    const now = Date.now();

    const orderA = await Order.create({
      orderId: 'ORD-TEST-101',
      studentId: 'student_user_A',
      studentName: 'User A',
      canteenId: TEST_CANTEEN_A,
      tokenNumber: '101',
      totalAmount: 120,
      status: 'PREPARING',
      estimatedPrepMinutes: 8,
      createdAt: new Date(now),
    });

    let qA = await getOrderQueuePosition(orderA.orderId);
    assert(qA.queuePosition === 1, 'User A is #1 in queue');
    assert(qA.ordersAhead === 0, 'User A has 0 orders ahead');
    assert(qA.estWaitMinutes === 8, 'User A estimated wait is 8 minutes');

    const orderB = await Order.create({
      orderId: 'ORD-TEST-102',
      studentId: 'student_user_B',
      studentName: 'User B',
      canteenId: TEST_CANTEEN_A,
      tokenNumber: '102',
      totalAmount: 80,
      status: 'NEW',
      estimatedPrepMinutes: 7,
      createdAt: new Date(now + 1000),
    });

    let qB = await getOrderQueuePosition(orderB.orderId);
    assert(qB.queuePosition === 2, 'User B is #2 in queue');
    assert(qB.ordersAhead === 1, 'User B has 1 order ahead');
    assert(qB.estWaitMinutes === 15, 'User B estimated wait is 15 minutes (8 + 7)');

    const orderC = await Order.create({
      orderId: 'ORD-TEST-103',
      studentId: 'student_user_C',
      studentName: 'User C',
      canteenId: TEST_CANTEEN_A,
      tokenNumber: '103',
      totalAmount: 150,
      status: 'NEW',
      estimatedPrepMinutes: 7,
      createdAt: new Date(now + 2000),
    });

    let qC = await getOrderQueuePosition(orderC.orderId);
    assert(qC.queuePosition === 3, 'User C is #3 in queue');
    assert(qC.ordersAhead === 2, 'User C has 2 orders ahead');
    assert(qC.estWaitMinutes === 22, 'User C estimated wait is 22 minutes (8 + 7 + 7)');

    let canteenAStats = await getCanteenQueueStats(TEST_CANTEEN_A);
    assert(canteenAStats.queueCount === 3, 'Canteen A queue depth is 3');

    // ── TEST 2: Order Completion & Queue Position Advancement ──────────────
    console.log('\n--- TEST 2: Complete User A -> B becomes #1, C becomes #2 ---');
    await Order.findOneAndUpdate({ orderId: orderA.orderId }, { $set: { status: 'COMPLETED' } });

    qB = await getOrderQueuePosition(orderB.orderId);
    assert(qB.queuePosition === 1, 'User B automatically advanced to #1 in queue');
    assert(qB.ordersAhead === 0, 'User B now has 0 orders ahead (next in line)');
    assert(qB.estWaitMinutes === 7, 'User B estimated wait decreased to 7 minutes');

    qC = await getOrderQueuePosition(orderC.orderId);
    assert(qC.queuePosition === 2, 'User C automatically advanced to #2 in queue');
    assert(qC.ordersAhead === 1, 'User C now has 1 order ahead');
    assert(qC.estWaitMinutes === 14, 'User C estimated wait decreased to 14 minutes (7 + 7)');

    console.log('\n--- Complete User B -> C becomes #1 ---');
    await Order.findOneAndUpdate({ orderId: orderB.orderId }, { $set: { status: 'COMPLETED' } });

    qC = await getOrderQueuePosition(orderC.orderId);
    assert(qC.queuePosition === 1, 'User C automatically advanced to #1 in queue (next in line)');
    assert(qC.ordersAhead === 0, 'User C now has 0 orders ahead');
    assert(qC.estWaitMinutes === 7, 'User C estimated wait is 7 minutes');

    // ── TEST 3: Canteen Isolation (Different Canteens -> Independent Queues) ─
    console.log('\n--- TEST 3: Canteen Isolation (Canteen A vs Canteen B) ---');
    const orderD = await Order.create({
      orderId: 'ORD-TEST-201',
      studentId: 'student_user_D',
      studentName: 'User D',
      canteenId: TEST_CANTEEN_B,
      tokenNumber: '201',
      totalAmount: 90,
      status: 'PREPARING',
      estimatedPrepMinutes: 6,
      createdAt: new Date(),
    });

    let qD = await getOrderQueuePosition(orderD.orderId);
    assert(qD.queuePosition === 1, 'User D in Canteen B is #1 in Canteen B');
    assert(qD.ordersAhead === 0, 'User D has 0 orders ahead in Canteen B');
    assert(qD.estWaitMinutes === 6, 'User D estimated wait is 6 minutes based on Canteen B');

    // Verify Canteen A's queue was completely untouched
    qC = await getOrderQueuePosition(orderC.orderId);
    assert(qC.queuePosition === 1, 'User C in Canteen A remains #1 in Canteen A');
    assert(qC.estWaitMinutes === 7, 'User C wait time in Canteen A unaffected by Canteen B');

    // ── TEST 4: Order Cancellation ─────────────────────────────────────────
    console.log('\n--- TEST 4: Order Cancellation ---');
    const orderE = await Order.create({
      orderId: 'ORD-TEST-104',
      studentId: 'student_user_E',
      canteenId: TEST_CANTEEN_A,
      tokenNumber: '104',
      totalAmount: 50,
      status: 'NEW',
      estimatedPrepMinutes: 5,
      createdAt: new Date(now + 10000),
    });

    let qE = await getOrderQueuePosition(orderE.orderId);
    assert(qE.queuePosition === 2, 'User E is #2 behind User C');

    // Cancel User C
    await Order.findOneAndUpdate({ orderId: orderC.orderId }, { $set: { status: 'CANCELLED' } });
    qE = await getOrderQueuePosition(orderE.orderId);
    assert(qE.queuePosition === 1, 'User E advances to #1 after User C cancellation');
    assert(qE.ordersAhead === 0, 'User E has 0 orders ahead');

    // ── TEST 5: Ready for Pickup ───────────────────────────────────────────
    console.log('\n--- TEST 5: Ready for Pickup Status ---');
    await Order.findOneAndUpdate({ orderId: orderE.orderId }, { $set: { status: 'READY' } });
    qE = await getOrderQueuePosition(orderE.orderId);
    assert(qE.queuePosition === 0, 'Ready order has queuePosition: 0');
    assert(qE.ordersAhead === 0, 'Ready order has ordersAhead: 0');
    assert(qE.estWaitMinutes === 0, 'Ready order has estWaitMinutes: 0');
    assert(qE.status === 'READY', 'Ready order status is READY');

    // ── TEST 6: Deterministic Tie-Breaker for Simultaneous Orders ──────────
    console.log('\n--- TEST 6: Deterministic Tie-Breaker for Simultaneous Orders ---');
    const sameTime = new Date();
    await Order.create({
      orderId: 'ORD-TIE-AAA',
      studentId: 'student_tie_1',
      canteenId: TEST_CANTEEN_B,
      totalAmount: 100,
      status: 'NEW',
      createdAt: sameTime,
    });
    await Order.create({
      orderId: 'ORD-TIE-BBB',
      studentId: 'student_tie_2',
      canteenId: TEST_CANTEEN_B,
      totalAmount: 100,
      status: 'NEW',
      createdAt: sameTime,
    });

    const tieAAA = await getOrderQueuePosition('ORD-TIE-AAA');
    const tieBBB = await getOrderQueuePosition('ORD-TIE-BBB');
    assert(tieAAA.queuePosition < tieBBB.queuePosition, 'Simultaneous orders resolved deterministically by unique orderId');
    assert(tieAAA.queuePosition !== tieBBB.queuePosition, 'No two orders share the same queue position');

    // Cleanup
    await Order.deleteMany({ canteenId: { $in: [TEST_CANTEEN_A, TEST_CANTEEN_B] } });
    await Canteen.deleteMany({ id: { $in: [TEST_CANTEEN_A, TEST_CANTEEN_B] } });

    console.log(`\n🎉 All tests completed: ${passedTests}/${totalTests} PASSED!`);
  } catch (err) {
    console.error('💥 Test encountered failure:', err);
    process.exit(1);
  } finally {
    await mongoose.disconnect();
  }
}

runQueueTests();
