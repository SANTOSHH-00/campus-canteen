const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../../.env') });
require('dotenv').config();

const mongoose = require('mongoose');
const connectDB = require('../src/config/db');
const Order = require('../src/models/Order');
const Canteen = require('../src/models/Canteen');
const Item = require('../src/models/Item');

const TEST_CANTEEN_A = 'test_canteen_workload_a';
const TEST_CANTEEN_B = 'test_canteen_workload_b';

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

// Emulate backend orders.js calculateQueueMetrics logic directly for testing
function formatTime12Hour(date) {
  const d = new Date(date);
  let hours = d.getHours();
  const minutes = d.getMinutes();
  const ampm = hours >= 12 ? 'PM' : 'AM';
  hours = hours % 12;
  hours = hours ? hours : 12;
  const minStr = minutes < 10 ? '0' + minutes : minutes;
  return `${hours}:${minStr} ${ampm}`;
}

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
  const targetPlacedAt = targetOrder.orderPlacedAt || targetOrder.createdAt;
  const targetCreatedAt = targetPlacedAt ? new Date(targetPlacedAt).getTime() : now;
  const targetElapsedMin = Math.max(0, (now - targetCreatedAt) / 60000);
  const ownRemaining = Math.max(1, Math.round(ownTotalPrep - targetElapsedMin));

  let aheadOrders = [];
  if (targetIndex === -1) {
    if (targetOrder.orderId === 'CART_PREVIEW' || targetOrder.orderId === 'CART_ITEM_PREVIEW') {
      aheadOrders = [...activeOrders];
    } else {
      aheadOrders = activeOrders.filter(o => {
        const oPlacedAt = o.orderPlacedAt || o.createdAt;
        const oTime = oPlacedAt ? new Date(oPlacedAt).getTime() : 0;
        return oTime < targetCreatedAt;
      });
    }
  } else {
    aheadOrders = activeOrders.slice(0, targetIndex);
  }

  const targetItems = targetOrder.items && Array.isArray(targetOrder.items) ? targetOrder.items : [];
  const hasSpecificItems = targetItems.length > 0;
  const targetItemIds = new Set(targetItems.map(it => it.itemId).filter(Boolean));
  const targetItemNames = new Set(targetItems.map(it => (it.name || '').toLowerCase().trim()).filter(Boolean));

  const primaryItemName = hasSpecificItems ? (targetItems[0].name || 'Item') : 'Item';
  const itemSummary = hasSpecificItems
    ? (targetItems.length === 1
        ? `${targetItems[0].name} × ${targetItems[0].quantity || 1}`
        : `${targetItems[0].name} + ${targetItems.length - 1} other${targetItems.length > 2 ? 's' : ''}`)
    : 'Order Items';

  function orderMatchesTarget(order) {
    if (!hasSpecificItems) return true;
    if (!order.items || !Array.isArray(order.items) || order.items.length === 0) return true;
    return order.items.some(it => {
      if (it.itemId && targetItemIds.has(it.itemId)) return true;
      const n = (it.name || '').toLowerCase().trim();
      return n && targetItemNames.has(n);
    });
  }

  const matchingAheadOrders = aheadOrders.filter(orderMatchesTarget);
  const similarOrdersAhead = matchingAheadOrders.length;
  const ordersAhead = targetIndex === -1 ? aheadOrders.length : targetIndex;

  let similarItemsAhead = 0;
  for (const o of matchingAheadOrders) {
    if (o.items && Array.isArray(o.items) && o.items.length > 0) {
      for (const it of o.items) {
        if (!hasSpecificItems ||
            (it.itemId && targetItemIds.has(it.itemId)) ||
            targetItemNames.has((it.name || '').toLowerCase().trim())) {
          similarItemsAhead += (it.quantity || 1);
        }
      }
    } else {
      similarItemsAhead += 1;
    }
  }

  const distinctCustomersSet = new Set();
  for (const o of matchingAheadOrders) {
    if (o.studentId) distinctCustomersSet.add(o.studentId);
  }
  const distinctCustomersAhead = distinctCustomersSet.size;

  let queuePosition = 1;
  if (similarOrdersAhead > 0) {
    queuePosition = distinctCustomersAhead > 0 ? distinctCustomersAhead + 1 : similarOrdersAhead + 1;
  }

  let sumRemainingAhead = 0;
  for (let i = 0; i < matchingAheadOrders.length; i++) {
    const ahead = matchingAheadOrders[i];
    let aheadPrep = defaultWait;
    if (ahead.items && Array.isArray(ahead.items) && ahead.items.length > 0) {
      const matchingItemPreps = ahead.items
        .filter(it => {
          if (!hasSpecificItems) return true;
          if (it.itemId && targetItemIds.has(it.itemId)) return true;
          const n = (it.name || '').toLowerCase().trim();
          return n && targetItemNames.has(n);
        })
        .map(it => it.prepMinutes || defaultWait);
      if (matchingItemPreps.length > 0) {
        aheadPrep = Math.max(...matchingItemPreps);
      }
    } else {
      aheadPrep = getOrderPrepTime(ahead, defaultWait);
    }

    const aheadPlacedAt = ahead.orderPlacedAt || ahead.createdAt;
    const aheadCreated = aheadPlacedAt ? new Date(aheadPlacedAt).getTime() : now;
    const aheadElapsedMin = Math.max(0, (now - aheadCreated) / 60000);
    const aheadRemaining = Math.max(1, aheadPrep - aheadElapsedMin);
    sumRemainingAhead += aheadRemaining;
  }

  const capacity = kitchenCapacity || Math.min(4, Math.max(2, Math.floor(similarOrdersAhead / 6) + 2));
  let totalWait;
  if (similarOrdersAhead === 0) {
    totalWait = ownRemaining;
  } else if (similarOrdersAhead === 1) {
    totalWait = Math.round(sumRemainingAhead + ownTotalPrep);
  } else {
    const queueWaitAhead = Math.ceil(sumRemainingAhead / capacity);
    totalWait = Math.round(queueWaitAhead + ownTotalPrep);
  }

  const estWaitMinutes = Math.min(120, Math.max(1, totalWait));
  const completionDate = new Date(now + estWaitMinutes * 60000);
  const estimatedCompletionTime = formatTime12Hour(completionDate);
  const estimatedCompletionAt = completionDate.toISOString();

  let workloadSummary = '';
  if (similarOrdersAhead === 0) {
    workloadSummary = `0 ${hasSpecificItems ? primaryItemName + ' ' : ''}orders ahead`;
  } else if (similarOrdersAhead === 1) {
    workloadSummary = `1 ${hasSpecificItems ? primaryItemName + ' ' : ''}order ahead`;
  } else {
    workloadSummary = `${similarOrdersAhead} ${hasSpecificItems ? primaryItemName + ' ' : ''}orders ahead`;
  }

  const message = similarOrdersAhead === 0
    ? 'You are next in line'
    : `You are #${queuePosition} in line (${workloadSummary})`;

  return {
    ordersAhead,
    similarOrdersAhead,
    similarItemsAhead,
    distinctCustomersAhead,
    queuePosition,
    queueNumber: queuePosition,
    estWaitMinutes,
    estimatedCompletionTime,
    estimatedCompletionAt,
    primaryItemName,
    itemSummary,
    workloadSummary,
    message,
  };
}

async function runTests() {
  console.log('=== Starting Quick Bite Realistic Item Queue & Workload Tests ===\n');

  try {
    await connectDB();

    // Clean up test data
    await Order.deleteMany({ canteenId: { $in: [TEST_CANTEEN_A, TEST_CANTEEN_B] } });

    const baseTime = Date.now();

    // ──────────────────────────────────────────────────────────────────────────
    // Test 1: User A places 4 orders (Burger, Pizza, Burger, Juice)
    // ──────────────────────────────────────────────────────────────────────────
    console.log('--- Test 1: User A places 4 orders in Canteen A ---');
    const orderA1 = await Order.create({
      orderId: 'ORD-USER-A-1',
      studentId: 'user_a',
      canteenId: TEST_CANTEEN_A,
      status: 'PREPARING',
      orderPlacedAt: new Date(baseTime - 600000), // 10 mins ago
      totalAmount: 120,
      items: [{ itemId: 'item_burger_1', name: 'Burger', price: 120, quantity: 1, prepMinutes: 8 }],
    });

    const orderA2 = await Order.create({
      orderId: 'ORD-USER-A-2',
      studentId: 'user_a',
      canteenId: TEST_CANTEEN_A,
      status: 'PREPARING',
      orderPlacedAt: new Date(baseTime - 500000), // ~8 mins ago
      totalAmount: 250,
      items: [{ itemId: 'item_pizza_1', name: 'Pizza', price: 250, quantity: 1, prepMinutes: 15 }],
    });

    const orderA3 = await Order.create({
      orderId: 'ORD-USER-A-3',
      studentId: 'user_a',
      canteenId: TEST_CANTEEN_A,
      status: 'PREPARING',
      orderPlacedAt: new Date(baseTime - 300000), // ~5 mins ago
      totalAmount: 120,
      items: [{ itemId: 'item_burger_1', name: 'Burger', price: 120, quantity: 1, prepMinutes: 8 }],
    });

    const orderA4 = await Order.create({
      orderId: 'ORD-USER-A-4',
      studentId: 'user_a',
      canteenId: TEST_CANTEEN_A,
      status: 'PREPARING',
      orderPlacedAt: new Date(baseTime - 100000), // ~1.5 mins ago
      totalAmount: 60,
      items: [{ itemId: 'item_juice_1', name: 'Juice', price: 60, quantity: 1, prepMinutes: 3 }],
    });

    const activeInCanteenA = await Order.find({
      canteenId: TEST_CANTEEN_A,
      status: { $in: ['NEW', 'WAITING', 'CONFIRMED', 'PREPARING'] },
    }).sort({ orderPlacedAt: 1, createdAt: 1, orderId: 1 }).lean();

    assert(activeInCanteenA.length === 4, 'Canteen A has 4 active orders placed by User A');

    // ──────────────────────────────────────────────────────────────────────────
    // Test 2: User B previews Cart for Burger
    // ──────────────────────────────────────────────────────────────────────────
    console.log('\n--- Test 2: User B previews Cart for Burger ---');
    const mockCartBurger = {
      orderId: 'CART_PREVIEW',
      canteenId: TEST_CANTEEN_A,
      items: [{ itemId: 'item_burger_1', name: 'Burger', price: 120, quantity: 1, prepMinutes: 8 }],
      orderPlacedAt: new Date(),
    };

    const cartMetrics = calculateQueueMetrics(mockCartBurger, activeInCanteenA, 7, 2);
    console.log('Cart preview metrics:', cartMetrics);

    assert(cartMetrics.similarOrdersAhead === 2, 'Cart shows 2 similar Burger orders ahead (NOT 4 orders)');
    assert(cartMetrics.similarItemsAhead === 2, 'Cart shows 2 Burger items ahead');
    assert(cartMetrics.distinctCustomersAhead === 1, 'Cart groups User A orders as 1 customer ahead');
    assert(cartMetrics.queuePosition === 2, 'Cart shows Queue #2 for Burger');
    assert(cartMetrics.workloadSummary === '2 Burger orders ahead', 'Workload summary correctly says "2 Burger orders ahead"');

    // ──────────────────────────────────────────────────────────────────────────
    // Test 3: User B places Order 5 (Burger)
    // ──────────────────────────────────────────────────────────────────────────
    console.log('\n--- Test 3: User B places Order 5 (Burger) ---');
    const orderB5 = await Order.create({
      orderId: 'ORD-USER-B-5',
      studentId: 'user_b',
      canteenId: TEST_CANTEEN_A,
      status: 'PREPARING',
      orderPlacedAt: new Date(),
      totalAmount: 120,
      items: [{ itemId: 'item_burger_1', name: 'Burger', price: 120, quantity: 1, prepMinutes: 8 }],
    });

    const activeAfterB = await Order.find({
      canteenId: TEST_CANTEEN_A,
      status: { $in: ['NEW', 'WAITING', 'CONFIRMED', 'PREPARING'] },
    }).sort({ orderPlacedAt: 1, createdAt: 1, orderId: 1 }).lean();

    const orderBMetrics = calculateQueueMetrics(orderB5, activeAfterB, 7, 2);
    console.log('Order B tracking metrics:', orderBMetrics);

    assert(orderBMetrics.similarOrdersAhead === 2, 'User B tracking shows 2 Burger orders ahead (Order 1 & 3)');
    assert(orderBMetrics.distinctCustomersAhead === 1, 'User B sees 1 customer ahead');
    assert(orderBMetrics.queuePosition === 2, 'User B is #2 in line for Burger');
    assert(orderBMetrics.primaryItemName === 'Burger', 'Primary item name is Burger');
    assert(orderBMetrics.estimatedCompletionTime.length > 0, 'Estimated completion time is computed');

    // ──────────────────────────────────────────────────────────────────────────
    // Test 4: User C places Pizza (Different Food Item)
    // ──────────────────────────────────────────────────────────────────────────
    console.log('\n--- Test 4: User C places Pizza (Different Food Item) ---');
    const orderC6 = await Order.create({
      orderId: 'ORD-USER-C-6',
      studentId: 'user_c',
      canteenId: TEST_CANTEEN_A,
      status: 'PREPARING',
      orderPlacedAt: new Date(Date.now() + 1000),
      totalAmount: 250,
      items: [{ itemId: 'item_pizza_1', name: 'Pizza', price: 250, quantity: 1, prepMinutes: 15 }],
    });

    const activeAfterC = await Order.find({
      canteenId: TEST_CANTEEN_A,
      status: { $in: ['NEW', 'WAITING', 'CONFIRMED', 'PREPARING'] },
    }).sort({ orderPlacedAt: 1, createdAt: 1, orderId: 1 }).lean();

    const orderCMetrics = calculateQueueMetrics(orderC6, activeAfterC, 7, 2);
    console.log('Order C tracking metrics:', orderCMetrics);

    // Ahead of User C: User A Order 1 (Burger), User A Order 2 (Pizza), User A Order 3 (Burger), User A Order 4 (Juice), User B Order 5 (Burger)
    // For User C's Pizza: only User A Order 2 is a Pizza!
    assert(orderCMetrics.similarOrdersAhead === 1, 'User C Pizza has only 1 similar Pizza order ahead (Order A2)');
    assert(orderCMetrics.queuePosition === 2, 'User C is #2 in line for Pizza');
    assert(orderCMetrics.workloadSummary === '1 Pizza order ahead', 'Workload summary indicates 1 Pizza order ahead');

    // ──────────────────────────────────────────────────────────────────────────
    // Test 5: Owner completes User A's Order 1 (Burger)
    // ──────────────────────────────────────────────────────────────────────────
    console.log("\n--- Test 5: Owner marks User A's Order 1 as READY ---");
    await Order.findOneAndUpdate({ orderId: 'ORD-USER-A-1' }, { status: 'READY', readyAt: new Date() });

    const activeAfterA1Ready = await Order.find({
      canteenId: TEST_CANTEEN_A,
      status: { $in: ['NEW', 'WAITING', 'CONFIRMED', 'PREPARING'] },
    }).sort({ orderPlacedAt: 1, createdAt: 1, orderId: 1 }).lean();

    const orderBMetricsAfterA1 = calculateQueueMetrics(orderB5, activeAfterA1Ready, 7, 2);
    console.log('Order B metrics after Order A1 ready:', orderBMetricsAfterA1);

    assert(orderBMetricsAfterA1.similarOrdersAhead === 1, 'User B now has only 1 Burger order ahead (Order A3)');
    assert(orderBMetricsAfterA1.queuePosition === 2, 'User B is still #2 in line behind Order A3');

    // ──────────────────────────────────────────────────────────────────────────
    // Test 6: Owner completes User A's Order 3 (Burger)
    // ──────────────────────────────────────────────────────────────────────────
    console.log("\n--- Test 6: Owner marks User A's Order 3 as READY ---");
    await Order.findOneAndUpdate({ orderId: 'ORD-USER-A-3' }, { status: 'READY', readyAt: new Date() });

    const activeAfterA3Ready = await Order.find({
      canteenId: TEST_CANTEEN_A,
      status: { $in: ['NEW', 'WAITING', 'CONFIRMED', 'PREPARING'] },
    }).sort({ orderPlacedAt: 1, createdAt: 1, orderId: 1 }).lean();

    const orderBMetricsAfterA3 = calculateQueueMetrics(orderB5, activeAfterA3Ready, 7, 2);
    console.log('Order B metrics after Order A3 ready:', orderBMetricsAfterA3);

    assert(orderBMetricsAfterA3.similarOrdersAhead === 0, 'User B now has 0 Burger orders ahead');
    assert(orderBMetricsAfterA3.queuePosition === 1, 'User B is now #1 in line ("You are next in line")');
    assert(orderBMetricsAfterA3.message === 'You are next in line', 'User B message is "You are next in line"');

    // ──────────────────────────────────────────────────────────────────────────
    // Test 7: Canteen Isolation
    // ──────────────────────────────────────────────────────────────────────────
    console.log('\n--- Test 7: Strict Canteen Isolation ---');
    const orderCanteenB = await Order.create({
      orderId: 'ORD-CANTEEN-B-1',
      studentId: 'user_x',
      canteenId: TEST_CANTEEN_B,
      status: 'PREPARING',
      orderPlacedAt: new Date(),
      totalAmount: 120,
      items: [{ itemId: 'item_burger_1', name: 'Burger', price: 120, quantity: 1, prepMinutes: 8 }],
    });

    const activeInB = await Order.find({
      canteenId: TEST_CANTEEN_B,
      status: { $in: ['NEW', 'WAITING', 'CONFIRMED', 'PREPARING'] },
    }).lean();

    const canteenBMetrics = calculateQueueMetrics(orderCanteenB, activeInB, 7, 2);
    assert(canteenBMetrics.ordersAhead === 0, 'Canteen B order has 0 orders ahead');
    assert(canteenBMetrics.queuePosition === 1, 'Canteen B order is #1 in line, unaffected by Canteen A');

    // Clean up
    await Order.deleteMany({ canteenId: { $in: [TEST_CANTEEN_A, TEST_CANTEEN_B] } });

    console.log(`\n🎉 ALL ${passed}/${total} ITEM QUEUE WORKLOAD TESTS PASSED!\n`);
    process.exit(0);
  } catch (err) {
    console.error('Test execution failed:', err);
    process.exit(1);
  }
}

runTests();
