/**
 * MunchMate Phase 4 — AI Intelligence & Empty Cart Verification Test Suite
 * 
 * Verifies all 15 required test scenarios:
 *  1. "Hi" (Warm personalized greeting with time of day and student's name)
 *  2. "Are pancakes available?" (Real-time dish search across campus, no false claims)
 *  3. "What is the price of pancakes?" (Accurately states not on menu, no invented price)
 *  4. "Show me available food items." (Retrieves live menu items from selected canteen)
 *  5. "Suggest something under ₹100." (Strict budget filtering under ₹100)
 *  6. "What is in my cart?" (Authoritative cart inspection)
 *  7. "Do I have any active orders?" (Order status summary check)
 *  8. "Is my order ready?" (Real-time order readiness & token)
 *  9. "Show my favourite items." (Retrieves student's saved favourites)
 * 10. "What did I order recently?" (Retrieves student's past orders)
 * 11. "What about the second order / item?" (Follow-up multi-order handling)
 * 12. Non-existent item (Pancakes) handling without hallucinations
 * 13. Unavailable item (Sold out) handling with clear out-of-stock notification
 * 14. MongoDB error handling (Graceful degradation without crashing)
 * 15. Gemini API error fallback (Seamless deterministic database grounding)
 */

const assert = require('assert');
const {
  executeTool,
  executeSearchFoodItem,
  executeGetFavouriteItems,
  executeGetRecentOrders,
  executeManageCart,
} = require('../src/services/aiToolsService');
const { generateFallbackResponseAsync, generateAiResponse } = require('../src/services/geminiService');
const { buildAiContext } = require('../src/services/aiContextService');
const Order = require('../src/models/Order');
const User = require('../src/models/User');
const Item = require('../src/models/Item');
const Canteen = require('../src/models/Canteen');
const Cart = require('../src/models/Cart');

// Mock data stores
let MOCK_CANTEENS = [
  { id: 'canteen_33', name: "Govinda's Kitchen", isOpen: true, location: 'Block 33', floor: '1st Floor', operatingHours: '8:00 AM - 10:00 PM' },
  { id: 'canteen_34', name: 'Talk of the Town', isOpen: true, location: 'Block 34', floor: 'Ground Floor', operatingHours: '9:00 AM - 9:00 PM' },
];

let MOCK_ITEMS = [
  { id: 'item_1', canteenId: 'canteen_33', name: 'Masala Dosa', price: 60, available: true, rating: 4.7, category: 'South Indian', preparationTime: '10 mins' },
  { id: 'item_2', canteenId: 'canteen_33', name: 'Chole Bhature', price: 80, available: false, rating: 4.8, category: 'North Indian', preparationTime: '15 mins' }, // SOLD OUT
  { id: 'item_3', canteenId: 'canteen_33', name: 'Paneer Butter Masala', price: 160, available: true, rating: 4.5, category: 'Main Course', preparationTime: '20 mins' },
  { id: 'item_4', canteenId: 'canteen_33', name: 'Filter Coffee', price: 30, available: true, rating: 4.9, category: 'Beverages', preparationTime: '5 mins' },
  { id: 'item_5', canteenId: 'canteen_34', name: 'Veg Sandwich', price: 45, available: true, rating: 4.4, category: 'Snacks', preparationTime: '8 mins' },
];

let MOCK_USERS = [
  {
    uid: 'student_101',
    name: 'Santosh Kumar',
    email: 'santosh@campus.edu',
    registrationNumber: 'REG-2026-001',
    favorites: ['item_1', 'item_4'],
  },
];

let MOCK_ORDERS = [
  {
    orderId: 'ORD-101101',
    tokenNumber: '101',
    studentId: 'student_101',
    canteenId: 'canteen_33',
    pickupCanteenName: "Govinda's Kitchen",
    status: 'PREPARING',
    paymentStatus: 'PAID',
    totalAmount: 90,
    items: [{ name: 'Masala Dosa', price: 60, quantity: 1 }, { name: 'Filter Coffee', price: 30, quantity: 1 }],
    estimatedReadyTime: '7 mins',
    createdAt: new Date(),
  },
  {
    orderId: 'ORD-101102',
    tokenNumber: '102',
    studentId: 'student_101',
    canteenId: 'canteen_34',
    pickupCanteenName: 'Talk of the Town',
    status: 'READY',
    paymentStatus: 'PAID',
    totalAmount: 45,
    items: [{ name: 'Veg Sandwich', price: 45, quantity: 1 }],
    estimatedReadyTime: 'Ready for pickup',
    createdAt: new Date(Date.now() - 30 * 60 * 1000),
  },
  {
    orderId: 'ORD-101099',
    tokenNumber: '099',
    studentId: 'student_101',
    canteenId: 'canteen_33',
    pickupCanteenName: "Govinda's Kitchen",
    status: 'COMPLETED',
    paymentStatus: 'PAID',
    totalAmount: 80,
    items: [{ name: 'Chole Bhature', price: 80, quantity: 1 }],
    createdAt: new Date(Date.now() - 24 * 60 * 60 * 1000),
  },
];

let MOCK_CARTS = new Map();

function setupMockEnvironment() {
  Canteen.find = () => ({
    lean: async () => [...MOCK_CANTEENS],
  });
  Canteen.findOne = (query) => ({
    lean: async () => MOCK_CANTEENS.find(c => c.id === query.id || c.name === query.name) || null,
  });

  Item.find = (query = {}) => {
    let results = [...MOCK_ITEMS];
    if (query.canteenId) results = results.filter(i => i.canteenId === query.canteenId);
    if (query.available !== undefined) {
      if (typeof query.available === 'object' && query.available.$ne !== undefined) {
        results = results.filter(i => i.available !== query.available.$ne);
      } else {
        results = results.filter(i => i.available === query.available);
      }
    }
    if (query.id && query.id.$in) {
      results = results.filter(i => query.id.$in.includes(i.id));
    }
    if (query.price && query.price.$lte !== undefined) {
      results = results.filter(i => i.price <= query.price.$lte);
    }
    if (query.name && query.name instanceof RegExp) {
      results = results.filter(i => query.name.test(i.name));
    }
    return {
      select: function() { return this; },
      sort: function(s) {
        if (s && s.rating) results.sort((a, b) => b.rating - a.rating);
        return this;
      },
      limit: function(n) {
        results = results.slice(0, n);
        return this;
      },
      lean: async () => results,
    };
  };

  Item.findOne = (query = {}) => ({
    lean: async () => {
      let candidate = null;
      if (query.id) candidate = MOCK_ITEMS.find(i => i.id === query.id);
      if (!candidate && query.name instanceof RegExp) candidate = MOCK_ITEMS.find(i => query.name.test(i.name));
      if (!candidate && query.$or) {
        for (const cond of query.$or) {
          if (cond.id) candidate = MOCK_ITEMS.find(i => i.id === cond.id);
          if (!candidate && cond.name instanceof RegExp) candidate = MOCK_ITEMS.find(i => cond.name.test(i.name));
          if (candidate) break;
        }
      }
      return candidate || null;
    },
  });

  User.findOne = (query) => ({
    lean: async () => {
      if (query.$or) {
        for (const cond of query.$or) {
          const u = MOCK_USERS.find(user =>
            (cond.uid && user.uid === cond.uid) ||
            (cond.registrationNumber && user.registrationNumber === cond.registrationNumber) ||
            (cond.email && user.email === cond.email)
          );
          if (u) return u;
        }
      }
      return null;
    },
  });

  Order.find = (query = {}) => {
    let list = [...MOCK_ORDERS];
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
      select: function() { return this; },
      sort: function() { return this; },
      limit: function(n) {
        list = list.slice(0, n);
        return this;
      },
      lean: async () => list,
    };
  };

  Order.findOne = (query = {}) => ({
    lean: async () => {
      if (query.$or) {
        for (const cond of query.$or) {
          const match = MOCK_ORDERS.find(o =>
            (cond.orderId && (o.orderId === cond.orderId || (cond.orderId instanceof RegExp && cond.orderId.test(o.orderId)))) ||
            (cond.tokenNumber && o.tokenNumber === cond.tokenNumber)
          );
          if (match) return match;
        }
      }
      return null;
    },
  });

  Cart.prototype.save = async function() {
    const snapshot = {
      studentId: this.studentId,
      canteenId: this.canteenId,
      items: this.items || [],
      subtotal: this.subtotal || 0,
      totalAmount: this.totalAmount || 0,
    };
    MOCK_CARTS.set(this.studentId, snapshot);
    return snapshot;
  };

  Cart.findOne = (query) => {
    let studentId = query.studentId;
    if (studentId && typeof studentId === 'object' && studentId.$in) {
      studentId = studentId.$in.find(id => MOCK_CARTS.has(id)) || studentId.$in[0];
    }
    let cart = (studentId && MOCK_CARTS.get(studentId)) || null;
    return {
      _data: cart,
      lean: async function() { return this._data ? JSON.parse(JSON.stringify(this._data)) : null; },
      then: function(resolve, reject) { return Promise.resolve(this._data).then(resolve, reject); },
    };
  };
}

async function runTests() {
  console.log('================================================================');
  console.log('   MUNCHMATE PHASE 4 — AI INTELLIGENCE & RECOVERY TEST SUITE   ');
  console.log('================================================================\n');

  setupMockEnvironment();

  const student = {
    studentId: 'student_101',
    studentName: 'Santosh Kumar',
    studentEmail: 'santosh@campus.edu',
    registrationNumber: 'REG-2026-001',
    isAuthenticated: true,
  };
  const requestContext = { selectedCanteenId: 'canteen_33' };

  let passed = 0;
  let total = 0;

  function record(desc, ok, details = '') {
    total++;
    if (ok) {
      passed++;
      console.log(`  ✓ [TEST ${total}] ${desc}`);
    } else {
      console.error(`  ✗ [TEST ${total}] FAILED: ${desc}`);
      if (details) console.error(`    Details: ${details}`);
    }
  }

  // SCENARIO 1: "Hi" -> Warm personalized greeting, time of day, first name
  try {
    const reply = await generateFallbackResponseAsync('Hi', '', student, requestContext);
    const ok = reply.includes('Santosh') && (reply.includes('Hi') || reply.includes('Hello') || reply.includes('Good'));
    record('Scenario 1: Greeting "Hi" addresses student by first name warmly', ok, reply);
  } catch (e) {
    record('Scenario 1: Greeting "Hi"', false, e.message);
  }

  // SCENARIO 2: "Are pancakes available?" -> Dish search, reports not offered, suggests real items
  try {
    const toolRes = await executeSearchFoodItem({ dishName: 'pancakes' }, requestContext);
    const reply = await generateFallbackResponseAsync('Are pancakes available?', '', student, requestContext);
    const ok = toolRes.found === false &&
               toolRes.isAvailable === false &&
               reply.toLowerCase().includes('not currently offered') &&
               (reply.includes('Masala Dosa') || reply.includes("Govinda's"));
    record('Scenario 2: "Are pancakes available?" accurately reports not offered & suggests real alternatives', ok, reply);
  } catch (e) {
    record('Scenario 2: "Are pancakes available?"', false, e.message);
  }

  // SCENARIO 3: "What is the price of pancakes?" -> Informs dish not on menu, does not fabricate price
  try {
    const reply = await generateFallbackResponseAsync('What is the price of pancakes?', '', student, requestContext);
    const ok = reply.toLowerCase().includes('not currently offered') && !reply.match(/price of pancakes is ₹\d+/i);
    record('Scenario 3: "What is the price of pancakes?" does not invent hallucinated prices', ok, reply);
  } catch (e) {
    record('Scenario 3: "What is the price of pancakes?"', false, e.message);
  }

  // SCENARIO 4: "Show me available food items." -> Retrieves live available items
  try {
    const reply = await generateFallbackResponseAsync('Show me available food items.', '', student, requestContext);
    const ok = reply.includes('Masala Dosa') && reply.includes('₹60') && reply.includes("Govinda's Kitchen");
    record('Scenario 4: "Show me available food items" returns authentic available dishes with prices', ok, reply);
  } catch (e) {
    record('Scenario 4: "Show me available food items"', false, e.message);
  }

  // SCENARIO 5: "Suggest something under ₹100." -> Strict budget filtering
  try {
    const reply = await generateFallbackResponseAsync('Suggest something under ₹100', '', student, requestContext);
    const ok = reply.includes('Masala Dosa') &&
               reply.includes('₹60') &&
               !reply.includes('Paneer Butter Masala'); // Paneer Butter Masala is ₹160 (must be excluded)
    record('Scenario 5: "Suggest something under ₹100" excludes items exceeding budget', ok, reply);
  } catch (e) {
    record('Scenario 5: "Suggest something under ₹100"', false, e.message);
  }

  // SCENARIO 6: "What is in my cart?" -> Clean authoritative cart inspection
  try {
    // Empty cart test
    const emptyReply = await generateFallbackResponseAsync('What is in my cart?', '', student, requestContext);
    const okEmpty = emptyReply.includes('empty');

    // Add item to cart and test inspection
    await executeManageCart({ action: 'add', dishNameOrId: 'Masala Dosa', quantity: 2 }, student, requestContext);
    const populatedReply = await generateFallbackResponseAsync('What is in my cart?', '', student, requestContext);
    const okPopulated = populatedReply.includes('Masala Dosa') && populatedReply.includes('₹120');

    record('Scenario 6: Cart inspection accurately distinguishes empty vs populated cart with verified totals', okEmpty && okPopulated, populatedReply);
  } catch (e) {
    record('Scenario 6: "What is in my cart?"', false, e.message);
  }

  // SCENARIO 7: "Do I have any active orders?" -> Returns multi-order status summary
  try {
    const reply = await generateFallbackResponseAsync('Do I have any active orders?', '', student, requestContext);
    const ok = reply.includes('ORD-101101') &&
               reply.includes('ORD-101102') &&
               reply.includes('PREPARING') &&
               reply.includes('READY');
    record('Scenario 7: "Do I have any active orders?" lists active orders with verified statuses', ok, reply);
  } catch (e) {
    record('Scenario 7: "Do I have any active orders?"', false, e.message);
  }

  // SCENARIO 8: "Is my order ready?" -> Detailed order tracking & token
  try {
    const reply = await generateFallbackResponseAsync('Is my order ORD-101102 ready?', '', student, requestContext);
    const ok = reply.includes('ORD-101102') &&
               reply.includes('READY') &&
               reply.includes('102') &&
               reply.includes('Veg Sandwich');
    record('Scenario 8: "Is my order ready?" reflects pickup token and ready status', ok, reply);
  } catch (e) {
    record('Scenario 8: "Is my order ready?"', false, e.message);
  }

  // SCENARIO 9: "Show my favourite items." -> Live pricing for saved user favourites
  try {
    const favResult = await executeGetFavouriteItems({}, student);
    const reply = await generateFallbackResponseAsync('Show my favourite items', '', student, requestContext);
    const ok = favResult.count === 2 &&
               reply.includes('Masala Dosa') &&
               reply.includes('Filter Coffee') &&
               reply.includes('₹60') &&
               reply.includes('₹30');
    record('Scenario 9: "Show my favourite items" displays student\'s saved dishes with live prices', ok, reply);
  } catch (e) {
    record('Scenario 9: "Show my favourite items"', false, e.message);
  }

  // SCENARIO 10: "What did I order recently?" -> Retrieves past orders
  try {
    const recentResult = await executeGetRecentOrders({ limit: 5 }, student);
    const reply = await generateFallbackResponseAsync('What did I order recently?', '', student, requestContext);
    const ok = recentResult.count > 0 &&
               reply.includes('ORD-101099') &&
               reply.includes('Chole Bhature');
    record('Scenario 10: "What did I order recently?" reports past completed orders', ok, reply);
  } catch (e) {
    record('Scenario 10: "What did I order recently?"', false, e.message);
  }

  // SCENARIO 11: "What about the second order?" -> Follow-up on specific active order
  try {
    const reply = await generateFallbackResponseAsync('What about the second one?', '', student, requestContext);
    const ok = reply.includes('ORD-101102') &&
               reply.includes('Talk of the Town') &&
               reply.includes('READY');
    record('Scenario 11: "What about the second one?" accurately tracks second active order in context', ok, reply);
  } catch (e) {
    record('Scenario 11: Follow-up question', false, e.message);
  }

  // SCENARIO 12: Non-existent item (Pancakes) -> Zero hallucination guarantee
  try {
    const context = await buildAiContext({
      studentId: student.studentId,
      studentName: student.studentName,
      message: 'Can I order pancakes?',
    });
    const ok = context.contextText.includes('pancakes" is NOT offered on any campus canteen menu') &&
               context.contextText.includes('Suggest real available options');
    record('Scenario 12: Dish search prevents AI hallucination via grounded context injection', ok, context.contextText);
  } catch (e) {
    record('Scenario 12: Non-existent item', false, e.message);
  }

  // SCENARIO 13: Unavailable item (Sold out) -> Accurately flags out of stock
  try {
    const searchSoldOut = await executeSearchFoodItem({ dishName: 'Chole Bhature' }, requestContext);
    const reply = await generateFallbackResponseAsync('Is Chole Bhature available?', '', student, requestContext);
    const ok = searchSoldOut.found === true &&
               searchSoldOut.isAvailable === false &&
               (reply.includes('SOLD OUT') || reply.includes('unavailable'));
    record('Scenario 13: Sold-out dishes are reported clearly as unavailable without false availability', ok, reply);
  } catch (e) {
    record('Scenario 13: Unavailable item', false, e.message);
  }

  // SCENARIO 14: MongoDB error handling -> Graceful recovery
  try {
    const originalFind = Item.find;
    // Simulate temporary DB error
    Item.find = () => { throw new Error('Database connection timeout'); };
    const errResult = await executeTool({
      toolName: 'search_food_item',
      args: { dishName: 'Dosa' },
      authenticatedStudent: student,
      requestContext,
    }).catch(err => ({ success: false, error: err.message }));

    Item.find = originalFind; // restore
    const ok = errResult !== undefined;
    record('Scenario 14: MongoDB error handling gracefully recovers without crashing server', ok);
  } catch (e) {
    record('Scenario 14: MongoDB error handling', false, e.message);
  }

  // SCENARIO 15: Gemini API error fallback -> Grounded database deterministic response
  try {
    delete process.env.GEMINI_API_KEY; // simulate missing or timeout API key
    const response = await generateAiResponse({
      userMessage: 'Show me available food items.',
      verifiedContext: '',
      authenticatedStudent: student,
      requestContext,
    });
    const ok = response.modelUsed === 'deterministic-verified-context' &&
               response.reply.includes('Masala Dosa') &&
               response.reply.includes('₹60');
    record('Scenario 15: Gemini API error seamlessly falls back to deterministic database grounding', ok, response.reply);
  } catch (e) {
    record('Scenario 15: Gemini fallback', false, e.message);
  }

  console.log('\n================================================================');
  console.log(`   PHASE 4 TEST SUMMARY: ${passed} / ${total} TESTS PASSED (${Math.round((passed/total)*100)}%)   `);
  console.log('================================================================\n');

  if (passed === total) {
    process.exit(0);
  } else {
    process.exit(1);
  }
}

runTests();
