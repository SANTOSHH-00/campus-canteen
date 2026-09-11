const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../../.env') });
require('dotenv').config();

const mongoose = require('mongoose');
const connectDB = require('../src/config/db');
const Order = require('../src/models/Order');
const Canteen = require('../src/models/Canteen');
const Item = require('../src/models/Item');

const TEST_CANTEEN_A = 'canteen_scale_test_a';
const TEST_CANTEEN_B = 'canteen_scale_test_b';

let passed = 0;
let total = 0;

function assert(condition, message) {
  total++;
  if (!condition) {
    console.error(`❌ FAIL: ${message}`);
    throw new Error(message);
  } else {
    passed++;
    console.log(`✅ PASS: ${message}`);
  }
}

// Emulates the exact orders.js router logic
function getOrderPrepTime(order, defaultWait = 7) {
  if (order && order.estimatedPrepMinutes && order.estimatedPrepMinutes > 0) {
    return order.estimatedPrepMinutes;
  }
  if (order && order.items && Array.isArray(order.items) && order.items.length > 0) {
    const itemPreps = order.items.map(it => it.prepMinutes || 0).filter(p => p > 0);
    if (itemPreps.length > 0) {
      const maxPrep = Math.max(...itemPreps);
      const extraItemsBuffer = Math.min(5, Math.max(0, order.items.length - 1));
      return Math.min(45, maxPrep + extraItemsBuffer);
    }
  }
  return defaultWait;
}

function calculateQueueMetrics(targetOrder, activeOrders, defaultWait = 7, kitchenCapacity = 2) {
  const targetIndex = activeOrders.findIndex(o => o.orderId === targetOrder.orderId);
  const now = Date.now();

  const ownTotalPrep = getOrderPrepTime(targetOrder, defaultWait);
  const targetCreatedAt = targetOrder.createdAt ? new Date(targetOrder.createdAt).getTime() : now;
  const targetElapsedMin = Math.max(0, (now - targetCreatedAt) / 60000);

  if (targetIndex === -1) {
    const earlier = activeOrders.filter(o => {
      const oTime = o.createdAt ? new Date(o.createdAt).getTime() : 0;
      return oTime < targetCreatedAt;
    });
    const ordersAhead = earlier.length;
    const queuePosition = ordersAhead + 1;
    const estWaitMinutes = Math.min(
      120,
      Math.max(
        1,
        Math.round(
          ordersAhead === 0
            ? Math.max(1, ownTotalPrep - targetElapsedMin)
            : (ordersAhead * defaultWait) / kitchenCapacity + ownTotalPrep
        )
      )
    );
    return { ordersAhead, queuePosition, estWaitMinutes };
  }

  const ordersAhead = targetIndex;
  const queuePosition = targetIndex + 1;

  if (ordersAhead === 0) {
    const remainingOwn = Math.max(1, Math.round(ownTotalPrep - targetElapsedMin));
    return {
      ordersAhead: 0,
      queuePosition: 1,
      estWaitMinutes: Math.min(120, remainingOwn),
    };
  }

  const aheadOrders = activeOrders.slice(0, targetIndex);
  let sumRemainingAhead = 0;

  for (let i = 0; i < aheadOrders.length; i++) {
    const ahead = aheadOrders[i];
    const aheadPrep = getOrderPrepTime(ahead, defaultWait);
    const aheadCreated = ahead.createdAt ? new Date(ahead.createdAt).getTime() : now;
    const aheadElapsedMin = Math.max(0, (now - aheadCreated) / 60000);
    const aheadRemaining = Math.max(1, aheadPrep - aheadElapsedMin);
    sumRemainingAhead += aheadRemaining;
  }

  let totalWait;
  if (ordersAhead === 1) {
    totalWait = Math.round(sumRemainingAhead + ownTotalPrep);
  } else {
    const queueWaitAhead = Math.ceil(sumRemainingAhead / kitchenCapacity);
    totalWait = Math.round(queueWaitAhead + ownTotalPrep);
  }

  const estWaitMinutes = Math.min(120, Math.max(1, totalWait));

  return {
    ordersAhead,
    queuePosition,
    estWaitMinutes,
  };
}

async function getOrderQueue(orderIdOrToken, canteenHint = null) {
  const ACTIVE_QUEUE_STATUSES = ['NEW', 'WAITING', 'CONFIRMED', 'PREPARING', 'new', 'waiting', 'confirmed', 'preparing'];

  let cleanId = decodeURIComponent(orderIdOrToken).trim();
  let targetOrder = await Order.findOne({ orderId: cleanId }).lean();
  if (!targetOrder && mongoose.Types.ObjectId.isValid(cleanId)) {
    targetOrder = await Order.findOne({ _id: cleanId }).lean();
  }
  if (!targetOrder) {
    const rawNum = cleanId.replace(/^[^0-9]+/g, '').trim();
    const tokenCandidates = [cleanId];
    if (rawNum) tokenCandidates.push(rawNum, `#${rawNum}`, `#Q${rawNum}`, `Q${rawNum}`);
    const query = { tokenNumber: { $in: tokenCandidates } };
    if (canteenHint) query.canteenId = canteenHint;
    targetOrder = await Order.findOne(query).sort({ createdAt: -1 }).lean();
  }

  if (!targetOrder) return null;

  if (targetOrder.status === 'READY') {
    return { orderId: targetOrder.orderId, queuePosition: 0, ordersAhead: 0, estWaitMinutes: 0, status: 'READY' };
  }
  if (targetOrder.status === 'COMPLETED' || targetOrder.status === 'PICKED_UP') {
    return { orderId: targetOrder.orderId, queuePosition: 0, ordersAhead: 0, estWaitMinutes: 0, status: 'COMPLETED' };
  }
  if (targetOrder.status === 'CANCELLED') {
    return { orderId: targetOrder.orderId, queuePosition: 0, ordersAhead: 0, estWaitMinutes: 0, status: 'CANCELLED' };
  }

  const canteen = await Canteen.findOne({ id: targetOrder.canteenId }).lean();
  const defaultWait = canteen?.avgWaitMinutes || 7;

  const activeCutoff = new Date(Date.now() - 24 * 60 * 60 * 1000);
  const activeOrders = await Order.find({
    canteenId: targetOrder.canteenId,
    status: { $in: ACTIVE_QUEUE_STATUSES },
    createdAt: { $gte: activeCutoff },
  })
    .sort({ createdAt: 1, orderId: 1 })
    .select('orderId studentId canteenId status createdAt estimatedPrepMinutes items')
    .lean();

  const metrics = calculateQueueMetrics(targetOrder, activeOrders, defaultWait, 2);

  return {
    orderId: targetOrder.orderId,
    tokenNumber: targetOrder.tokenNumber,
    studentId: targetOrder.studentId,
    canteenId: targetOrder.canteenId,
    status: targetOrder.status,
    ...metrics,
  };
}

async function run() {
  console.log('🚀 Running High-Scale Queue & Time-Basis Calculation Tests...\n');
  await connectDB();

  try {
    // 0. Clean test data
    await Order.deleteMany({ canteenId: { $in: [TEST_CANTEEN_A, TEST_CANTEEN_B] } });
    await Canteen.deleteMany({ id: { $in: [TEST_CANTEEN_A, TEST_CANTEEN_B] } });

    await Canteen.create({ id: TEST_CANTEEN_A, name: 'Scale Canteen A', avgWaitMinutes: 5 });
    await Canteen.create({ id: TEST_CANTEEN_B, name: 'Scale Canteen B', avgWaitMinutes: 8 });

    // ── TEST 1: Time-Basis & Item Prep for 3 Users ─────────────────────────
    console.log('--- TEST 1: Time-Basis Queue Calculation for Users 1, 2, 3 ---');
    const baseTime = Date.now() - 60000; // 1 minute ago

    // User 1: Ordered Poha (prepMinutes = 5) 1 minute ago
    await Order.create({
      orderId: 'ORD-SCALE-01',
      studentId: 'user_1',
      canteenId: TEST_CANTEEN_A,
      tokenNumber: '101',
      status: 'PREPARING',
      totalAmount: 40,
      estimatedPrepMinutes: 5,
      items: [{ itemId: 'item_poha', name: 'Poha', price: 40, quantity: 1, prepMinutes: 5 }],
      createdAt: new Date(baseTime),
    });

    // User 2: Ordered Chana Kulcha (prepMinutes = 6) 30 seconds ago
    await Order.create({
      orderId: 'ORD-SCALE-02',
      studentId: 'user_2',
      canteenId: TEST_CANTEEN_A,
      tokenNumber: '102',
      status: 'NEW',
      totalAmount: 75,
      estimatedPrepMinutes: 6,
      items: [{ itemId: 'item_kulcha', name: 'Chana Kulcha', price: 75, quantity: 1, prepMinutes: 6 }],
      createdAt: new Date(baseTime + 30000),
    });

    // User 3: Ordered Thali (prepMinutes = 10) 10 seconds ago
    await Order.create({
      orderId: 'ORD-SCALE-03',
      studentId: 'user_3',
      canteenId: TEST_CANTEEN_A,
      tokenNumber: '103',
      status: 'NEW',
      totalAmount: 120,
      estimatedPrepMinutes: 10,
      items: [{ itemId: 'item_thali', name: 'Special Thali', price: 120, quantity: 1, prepMinutes: 10 }],
      createdAt: new Date(baseTime + 50000),
    });

    const q1 = await getOrderQueue('ORD-SCALE-01');
    const q2 = await getOrderQueue('ORD-SCALE-02');
    const q3 = await getOrderQueue('ORD-SCALE-03');

    assert(q1.queuePosition === 1 && q1.ordersAhead === 0, 'User 1 is #1 with 0 ahead');
    assert(q1.estWaitMinutes <= 5 && q1.estWaitMinutes >= 1, `User 1 wait time is ${q1.estWaitMinutes}m (elapsed time deducted from 5m prep)`);

    assert(q2.queuePosition === 2 && q2.ordersAhead === 1, 'User 2 is #2 with 1 order ahead');
    assert(q2.estWaitMinutes >= 7 && q2.estWaitMinutes <= 11, `User 2 wait time is ${q2.estWaitMinutes}m (Order 1 remaining + Order 2 6m prep)`);

    assert(q3.queuePosition === 3 && q3.ordersAhead === 2, 'User 3 is #3 with 2 orders ahead');
    assert(q3.estWaitMinutes >= 12 && q3.estWaitMinutes <= 18, `User 3 wait time is ${q3.estWaitMinutes}m (parallel kitchen prep + 10m Thali)`);

    // ── TEST 2: Kitchen Advancement ────────────────────────────────────────
    console.log('\n--- TEST 2: Advancing Queue when Kitchen Completes Order 1 ---');
    await Order.updateOne({ orderId: 'ORD-SCALE-01' }, { $set: { status: 'COMPLETED' } });

    const q2Advanced = await getOrderQueue('ORD-SCALE-02');
    const q3Advanced = await getOrderQueue('ORD-SCALE-03');

    assert(q2Advanced.queuePosition === 1 && q2Advanced.ordersAhead === 0, 'User 2 dynamically became #1 in line (0 ahead)');
    assert(q3Advanced.queuePosition === 2 && q3Advanced.ordersAhead === 1, 'User 3 dynamically became #2 in line (1 ahead)');

    // ── TEST 3: High Scale Simulation (25 Concurrent Orders) ──────────────
    console.log('\n--- TEST 3: Massive Rush Hour Scale Test (25 Concurrent Orders) ---');
    await Order.deleteMany({ canteenId: TEST_CANTEEN_A });

    const numOrders = 25;
    const orderDocs = [];
    const startTime = Date.now() - 120000; // spread over 2 minutes

    for (let i = 1; i <= numOrders; i++) {
      const pad = String(i).padStart(2, '0');
      orderDocs.push({
        orderId: `ORD-RUSH-${pad}`,
        studentId: `student_${pad}`,
        canteenId: TEST_CANTEEN_A,
        tokenNumber: `5${pad}`,
        status: i === 1 ? 'PREPARING' : 'NEW',
        totalAmount: 50 + i * 5,
        estimatedPrepMinutes: 5 + (i % 4), // varies between 5, 6, 7, 8 minutes
        createdAt: new Date(startTime + i * 2000), // strictly 2 seconds apart
      });
    }
    await Order.insertMany(orderDocs);

    // Verify all 25 users in parallel
    for (let i = 1; i <= numOrders; i++) {
      const pad = String(i).padStart(2, '0');
      const q = await getOrderQueue(`ORD-RUSH-${pad}`);
      assert(q.queuePosition === i, `Rush User ${i} is accurately position #${i}`);
      assert(q.ordersAhead === i - 1, `Rush User ${i} has accurately ${i - 1} orders ahead`);
      assert(q.estWaitMinutes >= 1 && q.estWaitMinutes <= 120, `Rush User ${i} estWait (${q.estWaitMinutes}m) is realistic and bounded`);
    }

    // ── TEST 4: Batch Completion of First 5 Orders ─────────────────────────
    console.log('\n--- TEST 4: Kitchen Completes First 5 Orders (Batch Advancement) ---');
    await Order.updateMany(
      { orderId: { $in: ['ORD-RUSH-01', 'ORD-RUSH-02', 'ORD-RUSH-03', 'ORD-RUSH-04', 'ORD-RUSH-05'] } },
      { $set: { status: 'COMPLETED' } }
    );

    // Order 6 was #6 (5 ahead), now should be #1 (0 ahead)
    const q6 = await getOrderQueue('ORD-RUSH-06');
    assert(q6.queuePosition === 1 && q6.ordersAhead === 0, 'Order 6 is now #1 in line with 0 orders ahead');

    // Order 25 was #25 (24 ahead), now should be #20 (19 ahead)
    const q25 = await getOrderQueue('ORD-RUSH-25');
    assert(q25.queuePosition === 20 && q25.ordersAhead === 19, 'Order 25 is now #20 in line with 19 orders ahead');

    // ── TEST 5: Cancellation in Middle of Queue ────────────────────────────
    console.log('\n--- TEST 5: Mid-Queue Cancellation Test ---');
    // Cancel Order 10 (which is currently #5 in active line)
    const q10Before = await getOrderQueue('ORD-RUSH-10');
    const pos10 = q10Before.queuePosition;

    await Order.updateOne({ orderId: 'ORD-RUSH-10' }, { $set: { status: 'CANCELLED' } });

    const q10After = await getOrderQueue('ORD-RUSH-10');
    assert(q10After.status === 'CANCELLED' && q10After.queuePosition === 0, 'Cancelled order has queue 0');

    // Orders before #10 (e.g. Order 6) unaffected
    const q6Check = await getOrderQueue('ORD-RUSH-06');
    assert(q6Check.queuePosition === 1, 'Orders ahead of cancelled order unaffected');

    // Order 11 should shift forward by 1
    const q11 = await getOrderQueue('ORD-RUSH-11');
    assert(q11.queuePosition === pos10, `Order 11 shifted into position #${pos10} after Order 10 cancellation`);

    // ── TEST 6: Simultaneous Order Placement (Sub-millisecond Tie-Breaker) ──
    console.log('\n--- TEST 6: Simultaneous Placement Deterministic Tie-Breaker ---');
    const exactSameTime = new Date();
    await Order.create([
      {
        orderId: 'ORD-TIE-1',
        studentId: 'tie_user_1',
        canteenId: TEST_CANTEEN_A,
        tokenNumber: '801',
        status: 'NEW',
        totalAmount: 40,
        createdAt: exactSameTime,
      },
      {
        orderId: 'ORD-TIE-2',
        studentId: 'tie_user_2',
        canteenId: TEST_CANTEEN_A,
        tokenNumber: '802',
        status: 'NEW',
        totalAmount: 40,
        createdAt: exactSameTime,
      },
    ]);

    const tie1 = await getOrderQueue('ORD-TIE-1');
    const tie2 = await getOrderQueue('ORD-TIE-2');
    assert(tie1.queuePosition !== tie2.queuePosition, 'Simultaneous orders never share identical queue positions');
    assert(tie2.queuePosition === tie1.queuePosition + 1, 'Deterministic tie-breaker places ORD-TIE-1 ahead of ORD-TIE-2');

    // ── TEST 7: Independent Canteen Queues ──────────────────────────────────
    console.log('\n--- TEST 7: Strict Canteen Queue Isolation ---');
    await Order.create({
      orderId: 'ORD-CANTEEN-B-FIRST',
      studentId: 'user_b1',
      canteenId: TEST_CANTEEN_B,
      tokenNumber: '901',
      status: 'PREPARING',
      totalAmount: 60,
      estimatedPrepMinutes: 8,
      createdAt: new Date(Date.now() - 100000),
    });

    const qBFirst = await getOrderQueue('ORD-CANTEEN-B-FIRST');
    assert(qBFirst.canteenId === TEST_CANTEEN_B && qBFirst.queuePosition === 1 && qBFirst.ordersAhead === 0, 'Canteen B order is #1 in Canteen B');

    // Clean test data
    await Order.deleteMany({ canteenId: { $in: [TEST_CANTEEN_A, TEST_CANTEEN_B] } });
    await Canteen.deleteMany({ id: { $in: [TEST_CANTEEN_A, TEST_CANTEEN_B] } });

    console.log(`\n🎉 ALL SCALE & ACCURACY TESTS PASSED: ${passed}/${total} assertions successful!`);
  } finally {
    await mongoose.disconnect();
  }
}

run().catch(err => {
  console.error(err);
  process.exit(1);
});
