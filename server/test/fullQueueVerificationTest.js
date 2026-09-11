const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../../.env') });
require('dotenv').config();

const mongoose = require('mongoose');
const connectDB = require('../src/config/db');
const Order = require('../src/models/Order');
const Canteen = require('../src/models/Canteen');

const TEST_CANTEEN_A = 'canteen_test_a';
const TEST_CANTEEN_B = 'canteen_test_b';

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

// Function that mimics the exact router logic in orders.js
async function getOrderQueue(orderIdOrToken, canteenHint = null) {
  const ACTIVE_QUEUE_STATUSES = ['NEW', 'WAITING', 'CONFIRMED', 'PREPARING', 'new', 'waiting', 'confirmed', 'preparing'];

  // 1. Locate order
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
  }).sort({ createdAt: 1, orderId: 1 }).lean();

  const targetIndex = activeOrders.findIndex(o => o.orderId === targetOrder.orderId);
  let ordersAhead = 0;
  let queuePosition = 1;

  function getPrep(o) {
    return o.estimatedPrepMinutes || defaultWait;
  }

  if (targetIndex !== -1) {
    ordersAhead = targetIndex;
    queuePosition = targetIndex + 1;
    let wait = 0;
    for (let i = 0; i < targetIndex; i++) {
      wait += getPrep(activeOrders[i]);
    }
    wait += getPrep(targetOrder);
    var estWaitMinutes = Math.min(120, Math.max(1, wait));
  } else {
    const tTime = targetOrder.createdAt ? new Date(targetOrder.createdAt).getTime() : Date.now();
    ordersAhead = activeOrders.filter(o => new Date(o.createdAt).getTime() < tTime).length;
    queuePosition = ordersAhead + 1;
    var estWaitMinutes = Math.min(120, Math.max(1, (ordersAhead + 1) * defaultWait));
  }

  return {
    orderId: targetOrder.orderId,
    tokenNumber: targetOrder.tokenNumber,
    studentId: targetOrder.studentId,
    canteenId: targetOrder.canteenId,
    status: targetOrder.status,
    queuePosition,
    ordersAhead,
    estWaitMinutes,
  };
}

async function run() {
  console.log('🚀 Running Comprehensive Quick Bite Queue System Verification...\n');
  await connectDB();

  try {
    // 0. Clean test data
    await Order.deleteMany({ canteenId: { $in: [TEST_CANTEEN_A, TEST_CANTEEN_B] } });
    await Canteen.deleteMany({ id: { $in: [TEST_CANTEEN_A, TEST_CANTEEN_B] } });

    await Canteen.create({ id: TEST_CANTEEN_A, name: 'Canteen A', avgWaitMinutes: 7 });
    await Canteen.create({ id: TEST_CANTEEN_B, name: 'Canteen B', avgWaitMinutes: 10 });

    const t0 = new Date(Date.now() - 30000);
    const t1 = new Date(Date.now() - 20000);
    const t2 = new Date(Date.now() - 10000);

    // 1. Create User 1, User 2, User 3 in Canteen A
    console.log('--- Step 1: Placing Orders for User 1, User 2, User 3 in Canteen A ---');
    await Order.create({
      orderId: 'ORD-A-01',
      studentId: 'user_1',
      canteenId: TEST_CANTEEN_A,
      tokenNumber: '043',
      status: 'PREPARING',
      totalAmount: 40,
      estimatedPrepMinutes: 7,
      createdAt: t0,
    });

    await Order.create({
      orderId: 'ORD-A-02',
      studentId: 'user_2',
      canteenId: TEST_CANTEEN_A,
      tokenNumber: '045',
      status: 'WAITING',
      totalAmount: 75,
      estimatedPrepMinutes: 8,
      createdAt: t1,
    });

    await Order.create({
      orderId: 'ORD-A-03',
      studentId: 'user_3',
      canteenId: TEST_CANTEEN_A,
      tokenNumber: '046',
      status: 'NEW',
      totalAmount: 50,
      estimatedPrepMinutes: 5,
      createdAt: t2,
    });

    // Verify User 1, User 2, User 3
    const q1 = await getOrderQueue('ORD-A-01');
    const q2 = await getOrderQueue('ORD-A-02');
    const q3 = await getOrderQueue('ORD-A-03');

    assert(q1.queuePosition === 1 && q1.ordersAhead === 0, 'User 1 is #1 with 0 orders ahead');
    assert(q1.estWaitMinutes === 7, 'User 1 estWait is 7 min');

    assert(q2.queuePosition === 2 && q2.ordersAhead === 1, 'User 2 is #2 with 1 order ahead (User 1)');
    assert(q2.estWaitMinutes === 15, 'User 2 estWait is 15 min (7 + 8)');

    assert(q3.queuePosition === 3 && q3.ordersAhead === 2, 'User 3 is #3 with 2 orders ahead (User 1 + User 2)');
    assert(q3.estWaitMinutes === 20, 'User 3 estWait is 20 min (7 + 8 + 5)');

    // 2. Verify token lookup flexibility (#Q043, #Q045, 046)
    console.log('\n--- Step 2: Testing Token Prefix Resolution ---');
    const q1Token = await getOrderQueue('#Q043', TEST_CANTEEN_A);
    assert(q1Token.orderId === 'ORD-A-01' && q1Token.queuePosition === 1, '#Q043 resolves correctly to User 1 at #1');

    const q2Token = await getOrderQueue('#Q045', TEST_CANTEEN_A);
    assert(q2Token.orderId === 'ORD-A-02' && q2Token.queuePosition === 2, '#Q045 resolves correctly to User 2 at #2');

    // 3. Complete Order 1 -> User 2 becomes #1 (0 ahead), User 3 becomes #2 (1 ahead)
    console.log('\n--- Step 3: Kitchen Completes Order 1 ---');
    await Order.updateOne({ orderId: 'ORD-A-01' }, { $set: { status: 'COMPLETED' } });

    const q1After = await getOrderQueue('ORD-A-01');
    assert(q1After.status === 'COMPLETED' && q1After.ordersAhead === 0 && q1After.queuePosition === 0, 'User 1 is marked COMPLETED with 0 ahead');

    const q2After1 = await getOrderQueue('ORD-A-02');
    assert(q2After1.queuePosition === 1 && q2After1.ordersAhead === 0, 'User 2 advances to #1 with 0 orders ahead');
    assert(q2After1.estWaitMinutes === 8, 'User 2 wait reduced to own prep (8 min)');

    const q3After1 = await getOrderQueue('ORD-A-03');
    assert(q3After1.queuePosition === 2 && q3After1.ordersAhead === 1, 'User 3 advances to #2 with 1 order ahead (User 2)');
    assert(q3After1.estWaitMinutes === 13, 'User 3 wait reduced to 13 min (8 + 5)');

    // 4. Complete Order 2 -> User 3 becomes #1 (0 ahead)
    console.log('\n--- Step 4: Kitchen Completes Order 2 ---');
    await Order.updateOne({ orderId: 'ORD-A-02' }, { $set: { status: 'COMPLETED' } });

    const q3After2 = await getOrderQueue('ORD-A-03');
    assert(q3After2.queuePosition === 1 && q3After2.ordersAhead === 0, 'User 3 advances to #1 with 0 orders ahead');
    assert(q3After2.estWaitMinutes === 5, 'User 3 wait is now 5 min');

    // 5. Verify Cancellation updates queue correctly
    console.log('\n--- Step 5: Order Cancellation ---');
    await Order.create({
      orderId: 'ORD-A-04',
      studentId: 'user_4',
      canteenId: TEST_CANTEEN_A,
      tokenNumber: '047',
      status: 'NEW',
      totalAmount: 30,
      createdAt: new Date(),
    });
    await Order.create({
      orderId: 'ORD-A-05',
      studentId: 'user_5',
      canteenId: TEST_CANTEEN_A,
      tokenNumber: '048',
      status: 'NEW',
      totalAmount: 30,
      createdAt: new Date(Date.now() + 1000),
    });

    let q5BeforeCancel = await getOrderQueue('ORD-A-05');
    // Active line: ORD-A-03 (#1), ORD-A-04 (#2), ORD-A-05 (#3)
    assert(q5BeforeCancel.queuePosition === 3 && q5BeforeCancel.ordersAhead === 2, 'User 5 is #3 with 2 ahead before cancellation');

    await Order.updateOne({ orderId: 'ORD-A-04' }, { $set: { status: 'CANCELLED' } });
    let q4Cancel = await getOrderQueue('ORD-A-04');
    assert(q4Cancel.status === 'CANCELLED' && q4Cancel.queuePosition === 0, 'User 4 is marked CANCELLED with queue 0');

    let q5AfterCancel = await getOrderQueue('ORD-A-05');
    assert(q5AfterCancel.queuePosition === 2 && q5AfterCancel.ordersAhead === 1, 'User 5 advances to #2 with 1 ahead after User 4 cancelled');

    // 6. Verify Isolation Between Different Canteens
    console.log('\n--- Step 6: Different Canteen Queue Isolation ---');
    await Order.create({
      orderId: 'ORD-B-01',
      studentId: 'user_6',
      canteenId: TEST_CANTEEN_B,
      tokenNumber: '201',
      status: 'PREPARING',
      totalAmount: 100,
      estimatedPrepMinutes: 10,
      createdAt: new Date(Date.now() - 50000), // earlier than all Canteen A orders
    });

    const qB1 = await getOrderQueue('ORD-B-01');
    assert(qB1.canteenId === TEST_CANTEEN_B && qB1.queuePosition === 1 && qB1.ordersAhead === 0, 'Canteen B order is #1 with 0 ahead despite earlier timestamp');

    const q5Still = await getOrderQueue('ORD-A-05');
    assert(q5Still.canteenId === TEST_CANTEEN_A && q5Still.queuePosition === 2 && q5Still.ordersAhead === 1, 'Canteen A queue is completely unaffected by Canteen B');

    // Clean up test data
    await Order.deleteMany({ canteenId: { $in: [TEST_CANTEEN_A, TEST_CANTEEN_B] } });
    await Canteen.deleteMany({ id: { $in: [TEST_CANTEEN_A, TEST_CANTEEN_B] } });

    console.log(`\n🎉 ALL TESTS PASSED: ${passed}/${total} assertions successful!`);
  } finally {
    await mongoose.disconnect();
  }
}

run().catch(err => {
  console.error(err);
  process.exit(1);
});
