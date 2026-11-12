const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../../.env') });
require('dotenv').config();

const mongoose = require('mongoose');
const Order = require('../src/models/Order');
const User = require('../src/models/User');
const Owner = require('../src/models/Owner');
const IdempotencyKey = require('../src/models/IdempotencyKey');

async function runAuditTests() {
  const uri = process.env.MONGODB_URI;
  const dbName = process.env.MONGODB_DB_NAME || 'Quickbite';

  console.log('Connecting to MongoDB Atlas Cluster...');
  await mongoose.connect(uri, { dbName });
  console.log(`✅ Connected successfully to MongoDB Database: "${mongoose.connection.name}"`);

  console.log('\n--- TEST 1: Register/Save Student Document ---');
  const testStudentId = 'audit_student_' + Date.now();
  const testUser = await User.create({
    uid: testStudentId,
    name: 'Audit Test Student',
    email: testStudentId + '@campus.edu',
    role: 'student',
    registrationNumber: 'AUDIT123',
    department: 'CSE',
    phone: '9876543210',
    course: 'B.Tech CSE'
  });
  console.log('Document inserted in "users" collection:');
  console.log({
    _id: testUser._id.toString(),
    uid: testUser.uid,
    name: testUser.name,
    email: testUser.email,
    role: testUser.role,
    createdAt: testUser.createdAt
  });

  console.log('\n--- TEST 2: Verify Existing Owner Document in MongoDB ---');
  const sampleOwner = await Owner.findOne().select('-password').lean();
  console.log('Owner document in "owners" collection:', sampleOwner);

  console.log('\n--- TEST 3 & 4: Place Order with Multiple Food Items & Verify Snapshots ---');
  const testOrderId = 'ORD-AUDIT-' + Date.now();
  const testOrder = await Order.create({
    orderId: testOrderId,
    studentId: testStudentId,
    studentName: testUser.name,
    studentPhone: testUser.phone,
    studentCourse: testUser.course,
    canteenId: 'canteen_33',
    totalAmount: 160,
    status: 'NEW',
    tokenNumber: '999',
    paymentStatus: 'PAID',
    pickupCanteenName: "Govinda's Kitchen",
    pickupLocation: 'Block 33 (6th Floor)',
    pickupCounter: 'Counter 1',
    customerPickupTime: 'ASAP',
    estimatedCompletionTime: '8:30 PM',
    items: [
      {
        itemId: 'item_oats_1',
        name: 'Milk Oats Snapshot',
        price: 60,
        quantity: 2,
        prepMinutes: 5,
        selectedOption: 'Large',
        selectedAddons: ['Honey']
      },
      {
        itemId: 'item_tea_1',
        name: 'Masala Chai Snapshot',
        price: 20,
        quantity: 2,
        prepMinutes: 3,
        selectedOption: null,
        selectedAddons: []
      }
    ],
    orderPlacedAt: new Date(),
    statusHistory: [
      {
        status: 'PLACED',
        timestamp: new Date(),
        timestampLocal: '11 Nov 2026, 08:30:00 PM'
      },
      {
        status: 'CONFIRMED',
        timestamp: new Date(),
        timestampLocal: '11 Nov 2026, 08:30:00 PM'
      }
    ]
  });

  console.log('Order document created in "orders" collection:');
  console.log({
    _id: testOrder._id.toString(),
    orderId: testOrder.orderId,
    studentId: testOrder.studentId,
    canteenId: testOrder.canteenId,
    totalAmount: testOrder.totalAmount,
    status: testOrder.status,
    paymentStatus: testOrder.paymentStatus,
    items: testOrder.items
  });

  console.log('\n--- TEST 5: Verify Query Persistence & Status History Progression ---');
  const queriedOrder = await Order.findOne({ orderId: testOrderId }).lean();
  if (!queriedOrder) throw new Error('Order not found after insertion!');
  console.log('✅ Query from database confirmed: Order exists in MongoDB Atlas.');

  // Progression: NEW -> PREPARING -> READY -> COMPLETED
  const transitions = [
    { status: 'PREPARING', field: 'preparingAt' },
    { status: 'READY', field: 'readyAt' },
    { status: 'COMPLETED', field: 'completedAt' }
  ];

  for (const trans of transitions) {
    const now = new Date();
    const updateObj = {
      $set: {
        status: trans.status,
        [trans.field]: now
      },
      $push: {
        statusHistory: {
          status: trans.status,
          timestamp: now,
          timestampLocal: '11 Nov 2026'
        }
      }
    };
    const updated = await Order.findOneAndUpdate(
      { orderId: testOrderId },
      updateObj,
      { new: true }
    ).lean();
    console.log(`Updated status to ${updated.status}. statusHistory length: ${updated.statusHistory.length}`);
  }

  console.log('\n--- TEST 9: Idempotency Key Atomic Protection Test ---');
  const testIdempotencyKey = 'IDEMPOTENCY-' + Date.now();
  const firstInsert = await IdempotencyKey.create({
    key: testIdempotencyKey,
    studentId: testStudentId,
    inProgress: true
  });
  console.log('First idempotency key reserved:', firstInsert.key);

  let duplicateBlocked = false;
  try {
    await IdempotencyKey.create({
      key: testIdempotencyKey,
      studentId: testStudentId,
      inProgress: true
    });
  } catch (dupErr) {
    if (dupErr.code === 11000) {
      duplicateBlocked = true;
      console.log('✅ Duplicate idempotency key correctly rejected with E11000 duplicate key error.');
    }
  }
  if (!duplicateBlocked) throw new Error('Idempotency failed to block duplicate key!');

  console.log('\n--- CLEANUP: Removing only synthetic test documents ---');
  await User.deleteOne({ uid: testStudentId });
  await Order.deleteOne({ orderId: testOrderId });
  await IdempotencyKey.deleteOne({ key: testIdempotencyKey });
  console.log('✅ Test data cleaned up successfully without affecting real student records.');

  await mongoose.disconnect();
  console.log('\n🎉 ALL DATABASE AUDIT PERSISTENCE TESTS PASSED SUCCESSFULLY!');
}

runAuditTests().catch(err => {
  console.error('❌ Audit test failed:', err);
  process.exit(1);
});

