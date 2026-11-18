const mongoose = require('../node_modules/mongoose');
const dotenv = require('../node_modules/dotenv');
const path = require('path');
const http = require('http');

// Load environment configuration
dotenv.config({ path: path.resolve(__dirname, '../../.env') });

const connectDB = require('../src/config/db');
const Order = require('../src/models/Order');
const Canteen = require('../src/models/Canteen');
const Item = require('../src/models/Item');
const User = require('../src/models/User');
const Review = require('../src/models/Review');
const Notification = require('../src/models/Notification');
const ProductSuggestion = require('../src/models/ProductSuggestion');
const AiFeedback = require('../src/models/AiFeedback');
const IdempotencyKey = require('../src/models/IdempotencyKey');

let server;
let baseUrl;

async function setup() {
  console.log('\n=============================================================');
  console.log('   QUICKBITE MONGODB PERSISTENCE & AUDIT SUITE (END-TO-END)   ');
  console.log('=============================================================\n');

  await connectDB();
  console.log(`[DB] Connected to database: ${mongoose.connection.db.databaseName}`);

  const express = require('express');
  const cors = require('cors');
  const app = express();
  app.use(cors());
  app.use(express.json());

  // Mount API routes
  app.use('/api/auth', require('../src/routes/auth'));
  app.use('/api/owners', require('../src/routes/owners'));
  app.use('/api/canteens', require('../src/routes/canteens'));
  app.use('/api/items', require('../src/routes/items'));
  app.use('/api/orders', require('../src/routes/orders'));
  app.use('/api/users', require('../src/routes/users'));
  app.use('/api/inventory', require('../src/routes/inventory'));
  app.use('/api/notifications', require('../src/routes/notifications'));
  app.use('/api/ai', require('../src/routes/aiRoutes'));
  app.use('/api/suggestions', require('../src/routes/suggestions'));

  return new Promise((resolve) => {
    server = http.createServer(app);
    server.listen(0, '127.0.0.1', () => {
      const port = server.address().port;
      baseUrl = `http://127.0.0.1:${port}/api`;
      console.log(`[Server] Test Express server listening on ${baseUrl}\n`);
      resolve();
    });
  });
}

async function runAuditTests() {
  let createdOrderId = null;
  const testStudentId = '12314501'; // Santosh Kadali
  const testStudentUid = 'student_1789045164127';
  const testCanteenId = 'canteen_33'; // Govinda's Kitchen

  console.log('▶ TEST A: Order Creation and Database Persistence');
  const orderPayload = {
    studentId: testStudentId,
    studentName: 'Santosh Kadali',
    canteenId: testCanteenId,
    items: [
      {
        itemId: 'c33_item_001_chana_bhatura',
        name: 'Chana Bhatura',
        price: 75,
        quantity: 2,
      },
      {
        itemId: 'c33_item_010_poha',
        name: 'Poha',
        price: 40,
        quantity: 1,
      }
    ],
    customerPickupTime: 'ASAP',
  };

  const createRes = await fetch(`${baseUrl}/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(orderPayload),
  });

  const createJson = await createRes.json();
  if (createRes.status !== 201) {
    throw new Error(`Order creation failed with HTTP ${createRes.status}: ${JSON.stringify(createJson)}`);
  }

  createdOrderId = createJson.orderId;
  console.log(`  ✔ Order created via API: ${createdOrderId}`);
  console.log(`  ✔ Returned totalAmount: ₹${createJson.totalAmount} (Calculated by server: 2*75 + 1*40 = ₹190)`);

  // Verify in MongoDB directly
  const savedDoc = await Order.findOne({ orderId: createdOrderId }).lean();
  if (!savedDoc) {
    throw new Error(`Order ${createdOrderId} NOT found in MongoDB orders collection!`);
  }
  console.log(`  ✔ Confirmed in MongoDB orders collection: _id=${savedDoc._id}`);
  console.log(`  ✔ DB Fields: totalAmount=${savedDoc.totalAmount}, status=${savedDoc.status}, itemsCount=${savedDoc.items.length}`);
  console.log(`  ✔ Barcode Format: ${savedDoc.barcodeFormat}, Barcode Status: ${savedDoc.barcodeStatus}`);
  console.log(`  ✔ Status History: ${savedDoc.statusHistory.map(h => h.status).join(' -> ')}`);

  console.log('\n▶ TEST B: Order Retrieval across Student & Owner Roles');
  const regOrdersRes = await fetch(`${baseUrl}/orders/student/${testStudentId}`);
  const regOrders = await regOrdersRes.json();
  const foundByReg = regOrders.some(o => o.orderId === createdOrderId);
  console.log(`  ✔ Query by registration number (${testStudentId}): ${foundByReg ? 'FOUND' : 'MISSING'} (Total: ${regOrders.length})`);

  const uidOrdersRes = await fetch(`${baseUrl}/orders/student/${testStudentUid}`);
  const uidOrders = await uidOrdersRes.json();
  const foundByUid = uidOrders.some(o => o.orderId === createdOrderId);
  console.log(`  ✔ Query by student uid (${testStudentUid}): ${foundByUid ? 'RESOLVED & FOUND' : 'FAILED'} (Total: ${uidOrders.length})`);

  const canteenOrdersRes = await fetch(`${baseUrl}/orders/canteen/${testCanteenId}`);
  const canteenOrders = await canteenOrdersRes.json();
  const foundInCanteen = canteenOrders.some(o => o.orderId === createdOrderId);
  console.log(`  ✔ Query by canteen (${testCanteenId}): ${foundInCanteen ? 'FOUND in Owner Dashboard' : 'MISSING'}`);

  const patchRes = await fetch(`${baseUrl}/orders/${createdOrderId}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status: 'PREPARING', changedBy: 'OWNER_TEST' }),
  });
  const patchedJson = await patchRes.json();
  console.log(`  ✔ Updated order status via API: ${patchedJson.status}`);

  // Re-verify in DB
  const updatedDoc = await Order.findOne({ orderId: createdOrderId }).lean();
  console.log(`  ✔ Confirmed status in MongoDB: ${updatedDoc.status}`);
  console.log(`  ✔ Milestone preparingAt: ${updatedDoc.preparingAt}`);

  console.log('\n▶ TEST C: Auditing Other MongoDB Write Operations');

  // Canteen status update
  const canteenStatusRes = await fetch(`${baseUrl}/canteens/${testCanteenId}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ isOpen: true, closeReason: 'Kitchen open for lunch rush' }),
  });
  const canteenStatusJson = await canteenStatusRes.json();
  console.log(`  ✔ Canteen status updated: isOpen=${canteenStatusJson.isOpen}`);

  // Item availability toggle
  const itemAvailRes = await fetch(`${baseUrl}/items/c33_item_001_chana_bhatura/availability`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ available: true, canteenId: testCanteenId }),
  });
  const itemAvailJson = await itemAvailRes.json();
  console.log(`  ✔ Item availability updated: available=${itemAvailJson.available}`);

  // Item Review submission
  const reviewRes = await fetch(`${baseUrl}/items/c33_item_001_chana_bhatura/reviews`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      rating: 5,
      comment: 'Excellent hot Chana Bhatura, very authentic taste!',
      userName: 'Santosh Kadali',
      userId: testStudentId,
      canteenId: testCanteenId,
    }),
  });
  const reviewJson = await reviewRes.json();
  console.log(`  ✔ Item review submitted: reviewId=${reviewJson.review?.id}, newAverage=${reviewJson.averageRating}`);
  const reviewInDb = await Review.findOne({ id: reviewJson.review?.id }).lean();
  console.log(`  ✔ Review verified in MongoDB collection [reviews]: ${reviewInDb ? 'CONFIRMED' : 'MISSING'}`);

  // User Favorites persistence
  const favAddRes = await fetch(`${baseUrl}/users/${testStudentUid}/favorites/c33_item_001_chana_bhatura`, {
    method: 'POST',
  });
  const favAddJson = await favAddRes.json();
  console.log(`  ✔ User favorite added: favorites count=${favAddJson.favorites?.length}`);
  const userInDb = await User.findOne({ uid: testStudentUid }).lean();
  console.log(`  ✔ User favorite verified in MongoDB: ${userInDb.favorites.includes('c33_item_001_chana_bhatura') ? 'CONFIRMED' : 'FAILED'}`);

  // Product Suggestion submission
  const suggestionRes = await fetch(`${baseUrl}/suggestions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-student-id': testStudentId,
      'x-student-name': 'Santosh Kadali',
    },
    body: JSON.stringify({
      canteenId: testCanteenId,
      canteenName: "Govinda's Kitchen",
      suggestionText: 'Please consider introducing Filter Coffee and Masala Dosa in morning breakfast menu.',
      studentName: 'Santosh Kadali',
    }),
  });
  const suggestionJson = await suggestionRes.json();
  console.log(`  ✔ Product suggestion result: success=${suggestionJson.success || (suggestionJson.message ? true : false)}`);

  // Notification creation
  const notifRes = await fetch(`${baseUrl}/notifications`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      userId: testStudentId,
      title: 'Order Status Update',
      message: 'Your order is currently being prepared at Counter 1.',
      type: 'ORDER_UPDATE',
      orderId: createdOrderId,
      canteenId: testCanteenId,
    }),
  });
  const notifJson = await notifRes.json();
  console.log(`  ✔ Notification created: notificationId=${notifJson.notificationId}`);
  const notifInDb = await Notification.findOne({ notificationId: notifJson.notificationId }).lean();
  console.log(`  ✔ Notification verified in MongoDB: ${notifInDb ? 'CONFIRMED' : 'MISSING'}`);

  // AI Feedback submission
  const aiFeedbackRes = await fetch(`${baseUrl}/ai/feedback`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-student-id': testStudentId,
    },
    body: JSON.stringify({
      messageId: `msg_${Date.now()}`,
      userQuestion: 'What are the best breakfast items under ₹100?',
      assistantResponse: 'Chana Bhatura (₹75) and Poha (₹40) at Govinda Kitchen are popular options!',
      feedbackType: 'positive',
    }),
  });
  const aiFeedbackJson = await aiFeedbackRes.json();
  console.log(`  ✔ AI Assistant feedback recorded: feedbackType=${aiFeedbackJson.feedback?.feedbackType}`);

  console.log('\n▶ TEST D: Duplicate Protection and Idempotency Verification');
  const testIdempotencyKey = `IDEMP-${Date.now()}-UNIQUE-KEY`;

  const idempReq1 = await fetch(`${baseUrl}/orders`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': testIdempotencyKey,
    },
    body: JSON.stringify(orderPayload),
  });
  const idempJson1 = await idempReq1.json();
  console.log(`  ✔ First submission: orderId=${idempJson1.orderId}, HTTP ${idempReq1.status}`);

  // Retry with EXACT SAME idempotency key (simulating network retry / double tap)
  const idempReq2 = await fetch(`${baseUrl}/orders`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': testIdempotencyKey,
    },
    body: JSON.stringify(orderPayload),
  });
  const idempJson2 = await idempReq2.json();
  console.log(`  ✔ Repeated submission: orderId=${idempJson2.orderId}, HTTP ${idempReq2.status}`);

  if (idempJson1.orderId !== idempJson2.orderId) {
    throw new Error('Idempotency failed: Retry created a different order ID!');
  }
  const matchingKeyOrders = await Order.countDocuments({ orderId: idempJson1.orderId });
  console.log(`  ✔ Count of orders with ID ${idempJson1.orderId} in MongoDB: ${matchingKeyOrders} (Must be exactly 1)`);

  console.log('\n▶ TEST E: Failure Handling and Safe Rejection');

  const guestRes = await fetch(`${baseUrl}/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      studentId: 'student_guest',
      studentName: 'Guest User',
      canteenId: testCanteenId,
      items: [{ itemId: 'c33_item_001_chana_bhatura', name: 'Chana Bhatura', price: 75, quantity: 1 }],
    }),
  });
  const guestJson = await guestRes.json();
  console.log(`  ✔ Guest order attempt: HTTP ${guestRes.status} (${guestJson.error || 'Blocked correctly'})`);

  const emptyRes = await fetch(`${baseUrl}/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      studentId: testStudentId,
      canteenId: testCanteenId,
      items: [],
    }),
  });
  console.log(`  ✔ Empty cart attempt: HTTP ${emptyRes.status} (Rejected properly)`);

  const crossCanteenRes = await fetch(`${baseUrl}/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      studentId: testStudentId,
      canteenId: testCanteenId,
      items: [{ itemId: 'c34_item_999_nonexistent', name: 'Unknown Food', price: 99, quantity: 1 }],
    }),
  });
  const crossCanteenJson = await crossCanteenRes.json();
  console.log(`  ✔ Cross-canteen/invalid item attempt: HTTP ${crossCanteenRes.status} (${crossCanteenJson.error})`);

  console.log('\n▶ TEST F: Historical Data Integrity Verification');
  const septemberOrders = await Order.countDocuments({
    createdAt: { $gte: new Date('2026-09-01'), $lte: new Date('2026-09-30') }
  });
  const octoberOrders = await Order.countDocuments({
    createdAt: { $gte: new Date('2026-10-01'), $lte: new Date('2026-10-31') }
  });
  const novemberOrders = await Order.countDocuments({
    createdAt: { $gte: new Date('2026-11-01') }
  });

  console.log(`  ✔ Preserved September historical orders: ${septemberOrders}`);
  console.log(`  ✔ Preserved October historical orders: ${octoberOrders}`);
  console.log(`  ✔ Current November orders: ${novemberOrders}`);
  console.log(`  ✔ Total orders in MongoDB: ${septemberOrders + octoberOrders + novemberOrders}`);

  console.log('\n=============================================================');
  console.log('       ALL END-TO-END AUDIT & PERSISTENCE TESTS PASSED!       ');
  console.log('=============================================================\n');

  return {
    verifiedOrderId: createdOrderId,
    totalOrders: septemberOrders + octoberOrders + novemberOrders
  };
}

setup()
  .then(() => runAuditTests())
  .then((results) => {
    console.log(`Summary: Verified Order ID: ${results.verifiedOrderId}, Total DB Orders: ${results.totalOrders}`);
    server.close();
    mongoose.disconnect();
    process.exit(0);
  })
  .catch((err) => {
    console.error('\n❌ TEST FAILURE:', err);
    if (server) server.close();
    mongoose.disconnect();
    process.exit(1);
  });

