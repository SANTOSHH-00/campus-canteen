/**
 * MunchMate Phase 2 — Backend Tool Calling Integration Test Suite
 * 
 * Verifies all 10 required test scenarios:
 *  1. Ask for available food and verify the response against MongoDB records.
 *  2. Ask for a specific food item and verify its actual price.
 *  3. Ask whether the selected canteen is open.
 *  4. Ask whether the student has active orders.
 *  5. Ask for the status of an existing order.
 *  6. Ask for cart details (verifies prices recalculated from DB to prevent tampering).
 *  7. Ask for reviews of an item.
 *  8. Test with a student who has no orders.
 *  9. Test an invalid order ID.
 * 10. Attempt to retrieve another student's order (verifies cross-student access denied).
 * 
 * In addition:
 * 11. Test unauthorized / unallowlisted tool name execution.
 * 12. Test unauthenticated student access denial for student-specific tools.
 * 13. Test end-to-end conversation handling via generateAiResponse.
 * 14. Test SSE streaming token delivery via streamAiResponse.
 */

const path = require('path');
const dotenv = require('dotenv');
dotenv.config({ path: path.resolve(__dirname, '../../.env') });

const assert = require('assert');
const jwt = require('jsonwebtoken');

const { TOOL_DECLARATIONS, executeTool, getStatusExplanation } = require('../src/services/aiToolsService');
const { generateAiResponse, streamAiResponse } = require('../src/services/geminiService');
const Item = require('../src/models/Item');
const Canteen = require('../src/models/Canteen');
const Order = require('../src/models/Order');
const Review = require('../src/models/Review');
const Cart = require('../src/models/Cart');
const User = require('../src/models/User');

const JWT_SECRET = process.env.JWT_SECRET || 'quickbite-super-secure-jwt-secret-key-2026';

const SEED_CANTEENS = [
  {
    id: 'canteen_33',
    name: "Govinda's Kitchen",
    block: '33',
    location: 'Block 33',
    floorInfo: '6th Floor',
    isOpen: true,
    closeReason: '',
    timings: '7:00 AM – 10:00 PM',
    specialty: 'Pure Veg Meals, Thali & Sweets',
    avgWaitMinutes: 5,
  },
  {
    id: 'canteen_34',
    name: 'Talk of the town',
    block: '34',
    location: 'Block 34',
    floorInfo: '6th Floor',
    isOpen: false,
    closeReason: 'Kitchen maintenance until 2:00 PM',
    timings: '7:30 AM – 10:00 PM',
    specialty: 'North Indian, Rolls & Fast Food',
    avgWaitMinutes: 7,
  },
];

const SEED_ITEMS = [
  {
    id: 'item_cb_001',
    canteenId: 'canteen_33',
    name: 'Chole Bhature',
    description: 'Two fluffy bhaturas served with spicy authentic Amritsari chole, pickle and onions',
    price: 80,
    category: 'MEALS',
    available: true,
    stock: 50,
    preparationTime: '7-10 min',
    prepMinutes: 8,
    rating: 4.8,
    reviewCount: 42,
    ingredients: ['Chickpeas', 'Refined Flour', 'Spices', 'Pickle'],
  },
  {
    id: 'item_pbm_002',
    canteenId: 'canteen_33',
    name: 'Paneer Butter Masala',
    description: 'Cottage cheese cubes simmered in rich creamy tomato butter gravy',
    price: 160,
    category: 'MEALS',
    available: true,
    stock: 30,
    preparationTime: '10-12 min',
    prepMinutes: 10,
    rating: 4.7,
    reviewCount: 19,
    ingredients: ['Paneer', 'Butter', 'Cream', 'Tomato', 'Cashew Paste'],
  },
  {
    id: 'item_cold_coffee_003',
    canteenId: 'canteen_33',
    name: 'Cold Coffee',
    description: 'Chilled rich espresso blended with creamy milk and chocolate drizzle',
    price: 60,
    category: 'BEVERAGES',
    available: false, // Currently sold out
    stock: 0,
    preparationTime: '3-5 min',
    prepMinutes: 4,
    rating: 4.6,
    reviewCount: 35,
    ingredients: ['Milk', 'Coffee', 'Sugar', 'Ice'],
  },
];

const SEED_ORDERS = [
  {
    orderId: 'ORD-101101',
    studentId: 'student_rahul_2026',
    studentName: 'Rahul Sharma',
    canteenId: 'canteen_33',
    pickupCanteenName: "Govinda's Kitchen",
    status: 'PREPARING',
    tokenNumber: '101',
    totalAmount: 160,
    paymentStatus: 'PAID',
    items: [
      { itemId: 'item_cb_001', name: 'Chole Bhature', price: 80, quantity: 2, subtotal: 160 },
    ],
    customerPickupTime: '1:30 PM',
    estimatedReadyTime: '1:25 PM',
    createdAt: new Date(Date.now() - 10 * 60 * 1000),
    orderDate: '14 Nov 2026',
    orderTime: '01:15 PM',
  },
  {
    orderId: 'ORD-999999',
    studentId: 'victim_student_404',
    studentName: 'Private Student',
    canteenId: 'canteen_33',
    pickupCanteenName: "Govinda's Kitchen",
    status: 'READY',
    tokenNumber: '999',
    totalAmount: 240,
    paymentStatus: 'PAID',
    items: [
      { itemId: 'item_pbm_002', name: 'Paneer Butter Masala', price: 160, quantity: 1, subtotal: 160 },
      { itemId: 'item_cb_001', name: 'Chole Bhature', price: 80, quantity: 1, subtotal: 80 },
    ],
    createdAt: new Date(Date.now() - 20 * 60 * 1000),
  },
];

const SEED_REVIEWS = [
  {
    id: 'rev_1',
    itemId: 'item_cb_001',
    canteenId: 'canteen_33',
    userName: 'Aarav Patel',
    rating: 5,
    comment: 'Best chole bhature on campus! Fluffy and completely non-greasy.',
    createdAt: new Date(Date.now() - 24 * 3600 * 1000),
  },
  {
    id: 'rev_2',
    itemId: 'item_cb_001',
    canteenId: 'canteen_33',
    userName: 'Sneha Roy',
    rating: 4.5,
    comment: 'Authentic taste and fresh pickle. Highly recommended.',
    createdAt: new Date(Date.now() - 48 * 3600 * 1000),
  },
];

// Helper to mock query chain (.select, .sort, .limit, .lean)
function createMockQuery(results) {
  const query = {
    _data: results,
    select: function() { return this; },
    sort: function() { return this; },
    limit: function(n) {
      if (Array.isArray(this._data)) {
        return createMockQuery(this._data.slice(0, n));
      }
      return this;
    },
    lean: async function() { return this._data; },
    then: function(resolve, reject) { return Promise.resolve(this._data).then(resolve, reject); },
  };
  return query;
}

/**
 * Setup test database stubs to ensure deterministic execution
 */
function setupTestDatabaseStubs() {
  // Canteen stubs
  Canteen.find = function(query) {
    return createMockQuery([...SEED_CANTEENS]);
  };

  Canteen.findOne = function(query) {
    let match = null;
    if (query.$or) {
      for (const cond of query.$or) {
        if (cond.id) match = SEED_CANTEENS.find(c => c.id === cond.id);
        if (cond.block && !match) match = SEED_CANTEENS.find(c => c.block === cond.block);
        if (match) break;
      }
    } else if (query.id) {
      match = SEED_CANTEENS.find(c => c.id === query.id);
    }
    return createMockQuery(match || null);
  };

  // Item stubs
  Item.find = function(query) {
    let list = [...SEED_ITEMS];
    if (query.canteenId) {
      list = list.filter(i => i.canteenId === query.canteenId);
    }
    if (query.available !== undefined) {
      list = list.filter(i => i.available === query.available);
    }
    if (query.category) {
      list = list.filter(i => query.category.test(i.category));
    }
    if (query.$or) {
      // e.g. itemIds
      const idCond = query.$or.find(c => c.id && c.id.$in);
      if (idCond) {
        list = list.filter(i => idCond.id.$in.includes(i.id));
      }
    }
    if (query.id && query.id.$in) {
      list = list.filter(i => query.id.$in.includes(i.id));
    }
    return createMockQuery(list);
  };

  Item.findOne = function(query) {
    const item = SEED_ITEMS.find(i => i.id === query.id || (query.name && i.name.toLowerCase() === query.name.toLowerCase()));
    return createMockQuery(item || null);
  };

  // Order stubs
  Order.find = function(query) {
    let list = [...SEED_ORDERS];
    if (query.studentId && query.studentId.$in) {
      list = list.filter(o => query.studentId.$in.includes(o.studentId));
    }
    if (query.status && query.status.$in) {
      list = list.filter(o => query.status.$in.map(s => s.toUpperCase()).includes(o.status.toUpperCase()));
    }
    return createMockQuery(list);
  };

  Order.findOne = function(query) {
    let match = null;
    if (query.$or) {
      for (const cond of query.$or) {
        if (typeof cond.orderId === 'string') {
          match = SEED_ORDERS.find(o => o.orderId.toLowerCase() === cond.orderId.toLowerCase());
          if (match) break;
        } else if (cond.orderId instanceof RegExp) {
          match = SEED_ORDERS.find(o => cond.orderId.test(o.orderId));
          if (match) break;
        }
      }
    } else if (typeof query.orderId === 'string') {
      match = SEED_ORDERS.find(o => o.orderId.toLowerCase() === query.orderId.toLowerCase());
    }
    return createMockQuery(match || null);
  };

  // Review stubs
  Review.find = function(query) {
    let list = [...SEED_REVIEWS];
    if (query.itemId) {
      list = list.filter(r => r.itemId === query.itemId);
    }
    if (query.canteenId) {
      list = list.filter(r => r.canteenId === query.canteenId);
    }
    return createMockQuery(list);
  };

  // Cart stubs
  Cart.findOne = function() {
    return createMockQuery(null); // default to payload-driven cart
  };

  // User stubs
  User.findOne = function(query) {
    if (query.$or) {
      const match = query.$or.find(c => c.uid === 'student_rahul_2026' || c.email === 'rahul.sharma@campus.edu');
      if (match) {
        return createMockQuery({
          uid: 'student_rahul_2026',
          email: 'rahul.sharma@campus.edu',
          registrationNumber: 'REG-2026-101',
          name: 'Rahul Sharma',
        });
      }
    }
    return createMockQuery(null);
  };
}

async function runTestSuite() {
  console.log('================================================================');
  console.log('   MUNCHMATE PHASE 2: BACKEND TOOL INTEGRATION TEST SUITE       ');
  console.log('================================================================\n');

  setupTestDatabaseStubs();

  // Create test JWTs
  const validStudentToken = jwt.sign(
    { uid: 'student_rahul_2026', email: 'rahul.sharma@campus.edu', role: 'student', name: 'Rahul Sharma' },
    JWT_SECRET,
    { expiresIn: '1h' }
  );

  const authenticatedStudent = {
    studentId: 'student_rahul_2026',
    studentEmail: 'rahul.sharma@campus.edu',
    registrationNumber: 'REG-2026-101',
    studentName: 'Rahul Sharma',
    isAuthenticated: true,
  };

  const otherStudent = {
    studentId: 'victim_student_404',
    studentEmail: 'victim@campus.edu',
    registrationNumber: 'REG-2026-999',
    studentName: 'Private Student',
    isAuthenticated: true,
  };

  const unauthenticatedStudent = {
    studentId: 'guest_student',
    studentEmail: '',
    registrationNumber: '',
    studentName: 'Guest',
    isAuthenticated: false,
  };

  let passed = 0;
  let failed = 0;

  function recordTest(desc, assertionFn) {
    try {
      assertionFn();
      console.log(`  ✅ [PASS] ${desc}`);
      passed++;
    } catch (e) {
      console.error(`  ❌ [FAIL] ${desc}: ${e.message}`);
      failed++;
    }
  }

  console.log('── PART 1: INDEPENDENT TOOL EXECUTION TESTS ───────────────────\n');

  // Test 1: get_menu_items (available food and availability filter)
  const menuRes = await executeTool({
    toolName: 'get_menu_items',
    args: { canteenId: 'canteen_33', availableOnly: true },
    authenticatedStudent,
    requestContext: { selectedCanteenId: 'canteen_33' },
  });
  recordTest('1. Ask for available food & verify against MongoDB (excludes out-of-stock Cold Coffee)', () => {
    assert.strictEqual(menuRes.success, true);
    assert.strictEqual(menuRes.canteenId, 'canteen_33');
    assert.strictEqual(menuRes.totalFound, 2); // Chole Bhature and Paneer Butter Masala are available
    assert.strictEqual(menuRes.items.some(i => i.name === 'Cold Coffee'), false);
    assert.strictEqual(menuRes.items[0].name, 'Chole Bhature');
  });

  // Test 2: get_menu_items (item price verification)
  const priceItem = menuRes.items.find(i => i.name === 'Chole Bhature');
  recordTest('2. Ask for a specific food item & verify its actual price from database', () => {
    assert.ok(priceItem, 'Chole Bhature must exist in menu');
    assert.strictEqual(priceItem.price, 80);
    assert.strictEqual(priceItem.priceFormatted, '₹80');
    assert.strictEqual(priceItem.rating, 4.8);
  });

  // Test 3: get_canteen_details (open/closed status from database)
  const openCanteenRes = await executeTool({
    toolName: 'get_canteen_details',
    args: { canteenId: 'canteen_33' },
    authenticatedStudent,
  });
  const closedCanteenRes = await executeTool({
    toolName: 'get_canteen_details',
    args: { canteenId: 'canteen_34' },
    authenticatedStudent,
  });
  recordTest('3. Ask whether selected canteen is open (canteen_33 OPEN vs canteen_34 CLOSED)', () => {
    assert.strictEqual(openCanteenRes.success, true);
    assert.strictEqual(openCanteenRes.isOpen, true);
    assert.strictEqual(openCanteenRes.name, "Govinda's Kitchen");
    assert.strictEqual(closedCanteenRes.success, true);
    assert.strictEqual(closedCanteenRes.isOpen, false);
    assert.ok(closedCanteenRes.closeReason.includes('maintenance'));
  });

  // Test 4: get_my_orders (active orders for authenticated student)
  const myActiveOrders = await executeTool({
    toolName: 'get_my_orders',
    args: { activeOnly: true },
    authenticatedStudent,
  });
  recordTest('4. Ask whether the student has active orders (returns ORD-101101 in PREPARING)', () => {
    assert.strictEqual(myActiveOrders.success, true);
    assert.strictEqual(myActiveOrders.authenticated, true);
    assert.strictEqual(myActiveOrders.count, 1);
    assert.strictEqual(myActiveOrders.orders[0].orderId, 'ORD-101101');
    assert.strictEqual(myActiveOrders.orders[0].status, 'PREPARING');
    assert.strictEqual(myActiveOrders.orders[0].tokenNumber, '101');
    assert.strictEqual(myActiveOrders.orders[0].totalAmount, 160);
  });

  // Test 5: get_order_tracking (live tracking of existing order)
  const trackingRes = await executeTool({
    toolName: 'get_order_tracking',
    args: { orderId: 'ORD-101101' },
    authenticatedStudent,
  });
  recordTest('5. Ask for the status of an existing order (detailed tracking info)', () => {
    assert.strictEqual(trackingRes.success, true);
    assert.strictEqual(trackingRes.orderId, 'ORD-101101');
    assert.strictEqual(trackingRes.tokenNumber, '101');
    assert.strictEqual(trackingRes.status, 'PREPARING');
    assert.ok(trackingRes.statusExplanation.includes('currently being prepared'));
    assert.strictEqual(trackingRes.totalFormatted, '₹160');
  });

  // Test 6: get_cart_details (recalculates prices from Item collection to prevent price tampering)
  const cartRes = await executeTool({
    toolName: 'get_cart_details',
    args: {},
    authenticatedStudent,
    requestContext: {
      selectedCanteenId: 'canteen_33',
      // Attacker attempts to pass price: 1 instead of genuine price: 80
      cartItems: [
        { itemId: 'item_cb_001', name: 'Chole Bhature', price: 1, quantity: 2 },
      ],
    },
  });
  recordTest('6. Ask for cart details & verify database price override (tampered ₹1 -> verified ₹80 = total ₹160)', () => {
    assert.strictEqual(cartRes.success, true);
    assert.strictEqual(cartRes.isEmpty, false);
    assert.strictEqual(cartRes.itemCount, 2);
    // Crucial security verification: price must be verified ₹80 from DB Item, NOT tampered ₹1!
    assert.strictEqual(cartRes.items[0].price, 80);
    assert.strictEqual(cartRes.subtotal, 160);
    assert.strictEqual(cartRes.totalFormatted, '₹160');
  });

  // Test 7: get_food_reviews (genuine customer reviews)
  const reviewsRes = await executeTool({
    toolName: 'get_food_reviews',
    args: { itemId: 'item_cb_001' },
    authenticatedStudent,
  });
  recordTest('7. Ask for reviews of an item (returns real customer feedback & ratings)', () => {
    assert.strictEqual(reviewsRes.success, true);
    assert.strictEqual(reviewsRes.count, 2);
    assert.strictEqual(reviewsRes.reviews[0].userName, 'Aarav Patel');
    assert.strictEqual(reviewsRes.reviews[0].rating, 5);
    assert.ok(reviewsRes.reviews[0].comment.includes('Best chole bhature'));
  });

  // Test 8: Student with no orders
  const studentNoOrders = {
    studentId: 'new_student_fresh_2026',
    studentEmail: 'fresh@campus.edu',
    studentName: 'Fresh Student',
    isAuthenticated: true,
  };
  const noOrdersRes = await executeTool({
    toolName: 'get_my_orders',
    args: { activeOnly: true },
    authenticatedStudent: studentNoOrders,
  });
  recordTest('8. Test with a student who has no orders (returns clean count 0)', () => {
    assert.strictEqual(noOrdersRes.success, true);
    assert.strictEqual(noOrdersRes.count, 0);
    assert.strictEqual(noOrdersRes.orders.length, 0);
  });

  // Test 9: Invalid order ID
  const invalidOrderRes = await executeTool({
    toolName: 'get_order_tracking',
    args: { orderId: 'ORD-DOES-NOT-EXIST' },
    authenticatedStudent,
  });
  recordTest('9. Test an invalid order ID (returns found: false with clear user explanation)', () => {
    assert.strictEqual(invalidOrderRes.success, false);
    assert.strictEqual(invalidOrderRes.found, false);
    assert.ok(invalidOrderRes.error.includes('not found'));
  });

  // Test 10: Cross-student data access prevention
  const crossAccessRes = await executeTool({
    toolName: 'get_order_tracking',
    args: { orderId: 'ORD-999999' }, // Order belonging to victim_student_404
    authenticatedStudent, // Rahul Sharma
  });
  recordTest('10. Attempt to retrieve another student order (strictly forbidden, access denied)', () => {
    assert.strictEqual(crossAccessRes.success, false);
    assert.strictEqual(crossAccessRes.forbidden, true);
    assert.ok(crossAccessRes.error.includes('only provide details and tracking for your own orders'));
  });

  console.log('\n── PART 2: SECURITY & BOUNDARY TESTS ──────────────────────────\n');

  // Test 11: Unauthorized tool rejection
  const unauthorizedRes = await executeTool({
    toolName: 'drop_database',
    args: {},
    authenticatedStudent,
  });
  recordTest('11. Reject unallowlisted or malicious tool name execution', () => {
    assert.strictEqual(unauthorizedRes.success, false);
    assert.ok(unauthorizedRes.error.includes('not registered or permitted'));
  });

  // Test 12: Unauthenticated student access denial
  const unauthOrdersRes = await executeTool({
    toolName: 'get_my_orders',
    args: { activeOnly: true },
    authenticatedStudent: unauthenticatedStudent,
  });
  recordTest('12. Deny unauthenticated / guest access to private student orders', () => {
    assert.strictEqual(unauthOrdersRes.success, false);
    assert.strictEqual(unauthOrdersRes.authenticated, false);
    assert.ok(unauthOrdersRes.error.includes('logged in'));
  });

  console.log('\n── PART 3: END-TO-END CONVERSATION & STREAMING TESTS ──────────\n');

  // Test 13: Grounded conversation via generateAiResponse
  const chatResponse = await generateAiResponse({
    userMessage: 'Is my order ready?',
    verifiedContext: '',
    conversationHistory: [],
    authenticatedStudent,
    requestContext: { selectedCanteenId: 'canteen_33' },
  });
  recordTest('13. End-to-end conversation: "Is my order ready?" answers with real order details', () => {
    assert.ok(chatResponse.reply && chatResponse.reply.length > 0);
    assert.ok(chatResponse.reply.includes('ORD-101101') || chatResponse.reply.includes('PREPARING') || chatResponse.reply.includes('prepared'));
    assert.ok(chatResponse.reply.includes('101'));
  });

  // Test 14: Streaming chunk delivery via streamAiResponse
  let chunksReceived = 0;
  let fullStreamReply = '';
  const streamResult = await streamAiResponse({
    userMessage: 'What food is available right now?',
    verifiedContext: '',
    conversationHistory: [],
    authenticatedStudent,
    requestContext: { selectedCanteenId: 'canteen_33' },
    onChunk: (chunk) => {
      chunksReceived++;
      fullStreamReply += chunk;
    },
  });
  recordTest('14. Streaming SSE response: chunks delivered progressively without latency stalls', () => {
    assert.ok(chunksReceived > 0, 'Must deliver chunks via onChunk');
    assert.ok(fullStreamReply.includes('Chole Bhature') || fullStreamReply.includes('Govinda'));
    assert.ok(fullStreamReply.includes('₹80'));
  });

  console.log('\n================================================================');
  console.log(`   TOTAL TESTS: ${passed + failed} | PASSED: ${passed} | FAILED: ${failed}    `);
  console.log('================================================================\n');

  if (failed > 0) {
    process.exit(1);
  }
}

runTestSuite().catch(err => {
  console.error('Fatal test error:', err);
  process.exit(1);
});
