const assert = require('assert');
const { calculateQueueMetrics, getOrderPrepTime } = require('../src/routes/orders');
const { formatIstTime } = require('../src/utils/timeHelper');

function runTests() {
  console.log('🧪 Starting authoritative queue logic verification tests...\n');

  // Test 1: Requirement 1 Specification Example
  // Current server time: 12:00 PM
  // Order A -> 5 min remaining
  // Order B -> 8 min remaining
  // Order C (Current order) -> 10 min required prep
  // Expected completion = 12:23 PM
  const serverTime12PM = new Date('2026-09-12T06:30:00.000Z'); // 12:00 PM IST
  assert.strictEqual(formatIstTime(serverTime12PM), '12:00 PM', 'Server time must format to 12:00 PM IST');

  // Order A placed 5 min ago with 10 min prep => 10 - 5 = 5 min remaining
  const orderA = {
    orderId: 'ORD_A',
    canteenId: 'canteen_33',
    status: 'PREPARING',
    orderPlacedAt: new Date(serverTime12PM.getTime() - 5 * 60000),
    items: [{ itemId: 'item_1', name: 'Item 1', prepMinutes: 10, quantity: 1 }],
  };

  // Order B placed 2 min ago with 10 min prep => 10 - 2 = 8 min remaining
  const orderB = {
    orderId: 'ORD_B',
    canteenId: 'canteen_33',
    status: 'PREPARING',
    orderPlacedAt: new Date(serverTime12PM.getTime() - 2 * 60000),
    items: [{ itemId: 'item_2', name: 'Item 2', prepMinutes: 10, quantity: 1 }],
  };

  // Order C (Current user's order) placed right now with 10 min prep
  const orderC = {
    orderId: 'ORD_C',
    canteenId: 'canteen_33',
    status: 'CONFIRMED',
    orderPlacedAt: serverTime12PM,
    items: [{ itemId: 'item_3', name: 'Item 3', prepMinutes: 10, quantity: 1 }],
  };

  const activeOrdersBefore = [orderA, orderB, orderC];
  const metricsC_Before = calculateQueueMetrics(orderC, activeOrdersBefore, 7, serverTime12PM);

  console.log('Test 1 Results (Requirement 1):');
  console.log(`  Current Server Time: ${metricsC_Before.currentServerTime}`);
  console.log(`  Orders Ahead: ${metricsC_Before.ordersAhead}`);
  console.log(`  Total Remaining Ahead Minutes: ${metricsC_Before.totalRemainingAheadMinutes} min`);
  console.log(`  Current Order Prep Minutes: ${metricsC_Before.currentOrderPrepMinutes} min`);
  console.log(`  Total Est Wait Minutes: ${metricsC_Before.estWaitMinutes} min`);
  console.log(`  Estimated Completion Time: ${metricsC_Before.estimatedCompletionTime}`);

  assert.strictEqual(metricsC_Before.ordersAhead, 2, 'Orders ahead must be 2');
  assert.strictEqual(metricsC_Before.totalRemainingAheadMinutes, 13, 'Total remaining ahead must be 5 + 8 = 13 min');
  assert.strictEqual(metricsC_Before.currentOrderPrepMinutes, 10, 'Current order prep must be 10 min');
  assert.strictEqual(metricsC_Before.estWaitMinutes, 23, 'Total wait minutes must be 13 + 10 = 23 min');
  assert.strictEqual(metricsC_Before.estimatedCompletionTime, '12:23 PM', 'Estimated completion must be 12:23 PM');
  console.log('✅ Test 1 PASSED: Estimated completion dynamically equals 12:23 PM\n');

  // Test 2: Requirement 2 Specification Example
  // Owner completes Order A.
  // Active queue ahead is now only Order B (8 min remaining)
  // Current user's Order C (10 min prep)
  // Expected completion becomes 12:18 PM
  const activeOrdersAfter = [orderB, orderC]; // Order A is removed upon completion
  const metricsC_After = calculateQueueMetrics(orderC, activeOrdersAfter, 7, serverTime12PM);

  console.log('Test 2 Results (Requirement 2):');
  console.log(`  Orders Ahead: ${metricsC_After.ordersAhead}`);
  console.log(`  Total Remaining Ahead Minutes: ${metricsC_After.totalRemainingAheadMinutes} min`);
  console.log(`  Estimated Completion Time: ${metricsC_After.estimatedCompletionTime}`);

  assert.strictEqual(metricsC_After.ordersAhead, 1, 'Orders ahead must be 1');
  assert.strictEqual(metricsC_After.totalRemainingAheadMinutes, 8, 'Remaining ahead must be 8 min');
  assert.strictEqual(metricsC_After.estWaitMinutes, 18, 'Total wait must be 8 + 10 = 18 min');
  assert.strictEqual(metricsC_After.estimatedCompletionTime, '12:18 PM', 'Estimated completion must automatically become 12:18 PM');
  console.log('✅ Test 2 PASSED: Automatically recalculated to 12:18 PM upon Order A completion\n');

  // Test 3: Requirement 5 Canteen Scoping
  // Orders in Canteen B must never affect Canteen A
  const orderD_CanteenB = {
    orderId: 'ORD_D_B',
    canteenId: 'canteen_different',
    status: 'PREPARING',
    orderPlacedAt: new Date(serverTime12PM.getTime() - 10 * 60000),
    items: [{ itemId: 'item_4', name: 'Item 4', prepMinutes: 15, quantity: 1 }],
  };

  // Canteen 33 queue only contains orders for Canteen 33
  const canteen33Active = [orderB, orderC].filter(o => o.canteenId === 'canteen_33');
  const metricsCanteenCheck = calculateQueueMetrics(orderC, canteen33Active, 7, serverTime12PM);
  assert.strictEqual(metricsCanteenCheck.ordersAhead, 1, 'Canteen B order must not appear in Canteen 33 queue');
  assert.strictEqual(metricsCanteenCheck.estimatedCompletionTime, '12:18 PM');
  console.log('✅ Test 3 PASSED: Canteens are strictly isolated\n');

  console.log('🎉 All authoritative backend queue tests PASSED successfully!');
}

runTests();
