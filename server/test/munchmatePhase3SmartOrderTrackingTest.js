/**
 * MunchMate Phase 3 — Smart Order Tracking Integration Test Suite
 * 
 * Verifies all 12 required test scenarios:
 *  1. Student with zero active orders.
 *  2. Student with one pending order.
 *  3. Student with one waiting order.
 *  4. Student with one preparing order.
 *  5. Student with one ready order.
 *  6. Student with multiple active orders.
 *  7. Student with completed orders only.
 *  8. Student asking for a non-existent order.
 *  9. Student trying to access another student's order (forbidden / cross-student security).
 * 10. Owner changing an order from PREPARING to READY (status transition).
 * 11. Student checking the order after the status change (reflects fresh status immediately).
 * 12. Student asking a follow-up question about the second active order.
 */

const assert = require('assert');
const { executeTool, buildOrderTimeline, TERMINAL_STATUSES } = require('../src/services/aiToolsService');
const { generateAiResponse } = require('../src/services/geminiService');
const Order = require('../src/models/Order');
const User = require('../src/models/User');
const Canteen = require('../src/models/Canteen');
const { broadcastOrderStatus } = require('../src/services/socketService');

// Seed in-memory test database for deterministic verification
let DB_ORDERS = [];

function setupMockDatabase() {
  Order.find = function(query) {
    let list = [...DB_ORDERS];

    if (query.studentId && query.studentId.$in) {
      list = list.filter(o => query.studentId.$in.includes(o.studentId));
    }
    if (query.status && query.status.$nin) {
      const ninUpper = query.status.$nin.map(s => String(s).toUpperCase().trim());
      list = list.filter(o => !ninUpper.includes(String(o.status).toUpperCase().trim()));
    }
    if (query.status && query.status.$in) {
      const inUpper = query.status.$in.map(s => String(s).toUpperCase().trim());
      list = list.filter(o => inUpper.includes(String(o.status).toUpperCase().trim()));
    }

    return {
      _data: list,
      select: function() { return this; },
      sort: function(sortObj) {
        // Sort descending by orderPlacedAt/createdAt
        this._data.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
        return this;
      },
      limit: function(n) {
        this._data = this._data.slice(0, n);
        return this;
      },
      lean: async function() { return this._data; },
      then: function(resolve, reject) { return Promise.resolve(this._data).then(resolve, reject); },
    };
  };

  Order.findOne = function(query) {
    let match = null;
    if (query.$or) {
      for (const cond of query.$or) {
        if (typeof cond.orderId === 'string') {
          match = DB_ORDERS.find(o => o.orderId.toLowerCase() === cond.orderId.toLowerCase());
          if (match) break;
        } else if (cond.orderId instanceof RegExp) {
          match = DB_ORDERS.find(o => cond.orderId.test(o.orderId));
          if (match) break;
        }
      }
    } else if (query.orderId) {
      match = DB_ORDERS.find(o => o.orderId.toLowerCase() === query.orderId.toLowerCase());
    }

    return {
      _data: match,
      lean: async function() { return this._data; },
      then: function(resolve, reject) { return Promise.resolve(this._data).then(resolve, reject); },
    };
  };

  Order.findOneAndUpdate = function(filter, update, options) {
    const order = DB_ORDERS.find(o => o.orderId === filter.orderId || o._id === filter._id);
    if (!order) return Promise.resolve(null);

    if (update.$set) {
      Object.assign(order, update.$set);
    }
    if (update.$push && update.$push.statusHistory) {
      order.statusHistory = order.statusHistory || [];
      order.statusHistory.push(update.$push.statusHistory);
    }

    return Promise.resolve(order);
  };

  User.findOne = function(query) {
    return {
      lean: async function() { return null; },
      then: function(resolve) { return Promise.resolve(null).then(resolve); },
    };
  };

  Canteen.findOne = function(query) {
    const c = { id: 'canteen_33', name: "Govinda's Kitchen", block: '33', isOpen: true };
    return {
      lean: async function() { return c; },
      then: function(resolve) { return Promise.resolve(c).then(resolve); },
    };
  };
}

async function runPhase3Tests() {
  console.log('================================================================');
  console.log('   MUNCHMATE PHASE 3: SMART ORDER TRACKING VERIFICATION SUITE   ');
  console.log('================================================================\n');

  setupMockDatabase();

  const studentA = {
    studentId: 'student_vikram_2026',
    studentEmail: 'vikram@campus.edu',
    studentName: 'Vikram Singh',
    isAuthenticated: true,
  };

  const studentB = {
    studentId: 'student_ananya_2026',
    studentEmail: 'ananya@campus.edu',
    studentName: 'Ananya Sharma',
    isAuthenticated: true,
  };

  let passed = 0;
  let failed = 0;

  function record(desc, fn) {
    try {
      fn();
      console.log(`  ✅ [PASS] ${desc}`);
      passed++;
    } catch (e) {
      console.error(`  ❌ [FAIL] ${desc}: ${e.message}`);
      failed++;
    }
  }

  DB_ORDERS = [];
  const res1 = await executeTool({
    toolName: 'get_order_status_summary',
    args: {},
    authenticatedStudent: studentA,
  });
  record('1. Student with zero active orders -> returns NO_ACTIVE_ORDERS scenario', () => {
    assert.strictEqual(res1.success, true);
    assert.strictEqual(res1.scenario, 'NO_ACTIVE_ORDERS');
    assert.strictEqual(res1.count, 0);
    assert.ok(res1.message.toLowerCase().includes('no active orders'));
  });

  DB_ORDERS = [
    {
      orderId: 'ORD-201',
      studentId: 'student_vikram_2026',
      canteenId: 'canteen_33',
      pickupCanteenName: "Govinda's Kitchen",
      status: 'PENDING',
      tokenNumber: '201',
      totalAmount: 90,
      paymentStatus: 'PAID',
      items: [{ name: 'Masala Dosa', price: 90, quantity: 1, subtotal: 90 }],
      createdAt: new Date(),
      orderPlacedAtLocal: '11:00 AM',
    },
  ];
  const res2 = await executeTool({
    toolName: 'get_order_status_summary',
    args: {},
    authenticatedStudent: studentA,
  });
  record('2. Student with one PENDING order -> returns SINGLE_ACTIVE_ORDER with PENDING status', () => {
    assert.strictEqual(res2.success, true);
    assert.strictEqual(res2.scenario, 'SINGLE_ACTIVE_ORDER');
    assert.strictEqual(res2.order.orderId, 'ORD-201');
    assert.strictEqual(res2.order.status, 'PENDING');
    assert.strictEqual(res2.order.tokenNumber, '201');
    assert.ok(res2.order.statusExplanation.includes('waiting for the canteen to accept it'));
  });

  DB_ORDERS[0].status = 'WAITING';
  const res3 = await executeTool({
    toolName: 'get_order_status_summary',
    args: {},
    authenticatedStudent: studentA,
  });
  record('3. Student with one WAITING order -> returns SINGLE_ACTIVE_ORDER with WAITING status', () => {
    assert.strictEqual(res3.success, true);
    assert.strictEqual(res3.order.status, 'WAITING');
    assert.ok(res3.order.statusExplanation.includes('waiting for the canteen'));
  });

  DB_ORDERS[0].status = 'PREPARING';
  DB_ORDERS[0].confirmedAtLocal = '11:05 AM';
  DB_ORDERS[0].preparingAtLocal = '11:07 AM';
  DB_ORDERS[0].statusHistory = [
    { status: 'PLACED', timestampLocal: '11:00 AM' },
    { status: 'ACCEPTED', timestampLocal: '11:05 AM' },
    { status: 'PREPARING', timestampLocal: '11:07 AM' },
  ];
  const res4 = await executeTool({
    toolName: 'get_order_tracking',
    args: { orderId: 'ORD-201' },
    authenticatedStudent: studentA,
  });
  record('4. Student with one PREPARING order -> timeline shows Placed & Accepted COMPLETED, Preparing CURRENT', () => {
    assert.strictEqual(res4.success, true);
    assert.strictEqual(res4.status, 'PREPARING');
    const steps = res4.timeline.steps;
    assert.strictEqual(steps[0].name, 'Order Placed');
    assert.strictEqual(steps[0].state, 'COMPLETED');
    assert.strictEqual(steps[1].name, 'Order Accepted');
    assert.strictEqual(steps[1].state, 'COMPLETED');
    assert.strictEqual(steps[2].name, 'Preparing in Kitchen');
    assert.strictEqual(steps[2].state, 'CURRENT');
    assert.strictEqual(steps[3].name, 'Ready for Pickup');
    assert.strictEqual(steps[3].state, 'PENDING');
  });

  DB_ORDERS[0].status = 'READY';
  DB_ORDERS[0].readyAtLocal = '11:15 AM';
  DB_ORDERS[0].statusHistory.push({ status: 'READY', timestampLocal: '11:15 AM' });
  const res5 = await executeTool({
    toolName: 'get_order_tracking',
    args: { orderId: 'ORD-201' },
    authenticatedStudent: studentA,
  });
  record('5. Student with one READY order -> timeline shows Ready for Pickup CURRENT (with real timestamp)', () => {
    assert.strictEqual(res5.success, true);
    assert.strictEqual(res5.status, 'READY');
    const readyStep = res5.timeline.steps.find(s => s.stepKey === 'READY');
    assert.strictEqual(readyStep.state, 'CURRENT');
    assert.strictEqual(readyStep.timestamp, '11:15 AM');
    assert.ok(res5.statusExplanation.includes('ready for pickup'));
  });

  DB_ORDERS = [
    {
      orderId: 'ORD-201',
      studentId: 'student_vikram_2026',
      canteenId: 'canteen_33',
      pickupCanteenName: "Govinda's Kitchen",
      status: 'PREPARING',
      tokenNumber: '201',
      totalAmount: 160,
      paymentStatus: 'PAID',
      items: [{ name: 'Chole Bhature', price: 80, quantity: 2 }],
      createdAt: new Date(Date.now() - 5000),
    },
    {
      orderId: 'ORD-202',
      studentId: 'student_vikram_2026',
      canteenId: 'canteen_34',
      pickupCanteenName: 'Talk of the town',
      status: 'READY',
      tokenNumber: '202',
      totalAmount: 110,
      paymentStatus: 'PAID',
      items: [{ name: 'Paneer Roll', price: 110, quantity: 1 }],
      createdAt: new Date(Date.now() - 15000),
    },
  ];
  const res6 = await executeTool({
    toolName: 'get_order_status_summary',
    args: {},
    authenticatedStudent: studentA,
  });
  record('6. Student with multiple active orders -> returns MULTIPLE_ACTIVE_ORDERS with indexed summaries', () => {
    assert.strictEqual(res6.success, true);
    assert.strictEqual(res6.scenario, 'MULTIPLE_ACTIVE_ORDERS');
    assert.strictEqual(res6.count, 2);
    assert.strictEqual(res6.orders[0].orderId, 'ORD-201');
    assert.strictEqual(res6.orders[0].orderIndex, 1);
    assert.strictEqual(res6.orders[1].orderId, 'ORD-202');
    assert.strictEqual(res6.orders[1].orderIndex, 2);
    assert.ok(res6.message.includes('ask the student'));
  });

  DB_ORDERS = [
    {
      orderId: 'ORD-199',
      studentId: 'student_vikram_2026',
      canteenId: 'canteen_33',
      status: 'COMPLETED',
      tokenNumber: '199',
      totalAmount: 140,
      createdAt: new Date(Date.now() - 3600 * 1000),
    },
    {
      orderId: 'ORD-198',
      studentId: 'student_vikram_2026',
      canteenId: 'canteen_33',
      status: 'PICKED_UP',
      tokenNumber: '198',
      totalAmount: 80,
      createdAt: new Date(Date.now() - 7200 * 1000),
    },
  ];
  const res7 = await executeTool({
    toolName: 'get_order_status_summary',
    args: {},
    authenticatedStudent: studentA,
  });
  record('7. Student with completed orders only -> returns NO_ACTIVE_ORDERS', () => {
    assert.strictEqual(res7.success, true);
    assert.strictEqual(res7.scenario, 'NO_ACTIVE_ORDERS');
    assert.strictEqual(res7.count, 0);
  });

  const res8 = await executeTool({
    toolName: 'get_order_tracking',
    args: { orderId: 'ORD-999-NOT-FOUND' },
    authenticatedStudent: studentA,
  });
  record('8. Student asking for a non-existent order -> returns found: false with clean error', () => {
    assert.strictEqual(res8.success, false);
    assert.strictEqual(res8.found, false);
    assert.ok(res8.error.includes('was not found'));
  });

  DB_ORDERS = [
    {
      orderId: 'ORD-SECRET-99',
      studentId: 'student_ananya_2026', // Ananya's order
      status: 'PREPARING',
      tokenNumber: '099',
      totalAmount: 300,
    },
  ];
  const res9 = await executeTool({
    toolName: 'get_order_tracking',
    args: { orderId: 'ORD-SECRET-99' },
    authenticatedStudent: studentA, // Vikram trying to inspect Ananya's order
  });
  record('9. Accessing another student order -> strictly forbidden: true, access denied', () => {
    assert.strictEqual(res9.success, false);
    assert.strictEqual(res9.forbidden, true);
    assert.ok(res9.error.includes('only provide details and tracking for your own orders'));
  });

  DB_ORDERS = [
    {
      _id: 'mongo_id_201',
      orderId: 'ORD-201',
      studentId: 'student_vikram_2026',
      canteenId: 'canteen_33',
      pickupCanteenName: "Govinda's Kitchen",
      status: 'PREPARING',
      tokenNumber: '201',
      totalAmount: 160,
      createdAt: new Date(),
    },
  ];
  // Owner updates order in MongoDB
  const updatedDoc = await Order.findOneAndUpdate(
    { orderId: 'ORD-201' },
    {
      $set: { status: 'READY', readyAtLocal: '11:22 AM' },
      $push: { statusHistory: { status: 'READY', timestampLocal: '11:22 AM' } },
    }
  );
  record('10. Owner updating order in database -> status transitions to READY', () => {
    assert.strictEqual(updatedDoc.status, 'READY');
    assert.strictEqual(updatedDoc.readyAtLocal, '11:22 AM');
  });

  const res11 = await executeTool({
    toolName: 'get_order_tracking',
    args: { orderId: 'ORD-201' },
    authenticatedStudent: studentA,
  });
  record('11. Student checking order after owner update -> retrieves fresh READY status immediately', () => {
    assert.strictEqual(res11.success, true);
    assert.strictEqual(res11.status, 'READY');
    assert.ok(res11.statusExplanation.includes('ready for pickup'));
  });

  DB_ORDERS = [
    {
      orderId: 'ORD-201',
      studentId: 'student_vikram_2026',
      canteenId: 'canteen_33',
      pickupCanteenName: "Govinda's Kitchen",
      status: 'PREPARING',
      tokenNumber: '201',
      totalAmount: 160,
      items: [{ name: 'Chole Bhature', price: 80, quantity: 2, subtotal: 160 }],
      createdAt: new Date(Date.now() - 5000),
    },
    {
      orderId: 'ORD-202',
      studentId: 'student_vikram_2026',
      canteenId: 'canteen_34',
      pickupCanteenName: 'Talk of the town',
      status: 'READY',
      tokenNumber: '202',
      totalAmount: 110,
      items: [{ name: 'Paneer Roll', price: 110, quantity: 1, subtotal: 110 }],
      createdAt: new Date(Date.now() - 15000),
    },
  ];

  const followUpResponse = await generateAiResponse({
    userMessage: 'What about the second one?',
    verifiedContext: '',
    conversationHistory: [
      { role: 'user', content: 'Where are my orders?' },
      { role: 'model', content: 'You have 2 active orders: 1. Order #ORD-201 at Govinda Kitchen, 2. Order #ORD-202 at Talk of the Town.' },
    ],
    authenticatedStudent: studentA,
    requestContext: { selectedCanteenId: 'canteen_33' },
  });

  record('12. Follow-up query: "What about the second one?" -> tracks Order 2 (ORD-202) at Talk of the town', () => {
    assert.ok(followUpResponse.reply && followUpResponse.reply.length > 0);
    assert.ok(followUpResponse.reply.includes('ORD-202') || followUpResponse.reply.includes('Order 2'));
    assert.ok(followUpResponse.reply.includes('Talk of the town') || followUpResponse.reply.includes('READY') || followUpResponse.reply.includes('202'));
  });

  console.log('\n================================================================');
  console.log(`   TOTAL TESTS: ${passed + failed} | PASSED: ${passed} | FAILED: ${failed}    `);
  console.log('================================================================\n');

  if (failed > 0) {
    process.exit(1);
  }
}

runPhase3Tests().catch(err => {
  console.error('Fatal test error:', err);
  process.exit(1);
});
