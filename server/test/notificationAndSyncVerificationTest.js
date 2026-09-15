const mongoose = require('mongoose');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });

const connectDB = require('../src/config/db');
const Order = require('../src/models/Order');
const Canteen = require('../src/models/Canteen');
const Notification = require('../src/models/Notification');
const { calculateQueueMetrics } = require('../src/routes/orders');

async function runTests() {
  console.log('🚀 Running Notification Format, DB Sync, & Queue Calculation Tests...\n');

  await connectDB();
  console.log('✅ Connected to MongoDB Atlas');

  const testOrderId = 'ORD-NOTIF-TEST-' + Date.now();
  const testStudentId = 'student_test_notif';
  const testCanteenId = 'canteen_33';

  try {
    // 1. Ensure test canteen exists with Block number
    let canteen = await Canteen.findOne({ id: testCanteenId });
    if (!canteen) {
      canteen = await Canteen.create({
        id: testCanteenId,
        name: 'Block 33 Canteen',
        block: '33',
        location: 'Block 33',
        isOpen: true,
      });
    }

    // 2. Create an order placed at this exact moment
    const placedTime = new Date();
    const order = await Order.create({
      orderId: testOrderId,
      studentId: testStudentId,
      studentName: 'Test Student',
      canteenId: testCanteenId,
      totalAmount: 120,
      tokenNumber: '902',
      status: 'PREPARING',
      pickupCounter: 'Counter 1',
      orderPlacedAt: placedTime,
      preparingAt: placedTime,
      statusHistory: [{ status: 'PREPARING', timestamp: placedTime }],
    });
    console.log('✅ TEST 1: Order created with instant server timestamp orderPlacedAt =', order.orderPlacedAt.toISOString());

    // 3. Test Notification Generation & Database Persistence
    const cleanToken = order.tokenNumber.replace(/^#+/, '');
    const canteenName = canteen.name || 'Canteen Kitchen';
    const rawBlock = canteen.block || canteen.location || '';
    const blockNumber = rawBlock.replace(/block\s*/i, '').trim();
    const blockDisplay = blockNumber ? `Block ${blockNumber}` : (rawBlock || 'Campus');
    const counter = order.pickupCounter || 'Counter 1';

    const preparingTitle = `Order #${cleanToken}: Preparing`;
    const preparingBody = `Your meal is being prepared by ${canteenName} at ${blockDisplay}, ${counter}.`;

    // Persist preparing notification
    await Notification.findOneAndUpdate(
      { notificationId: `NOTIF-${order.orderId}-PREPARING` },
      {
        $set: {
          notificationId: `NOTIF-${order.orderId}-PREPARING`,
          userId: order.studentId,
          title: preparingTitle,
          message: preparingBody,
          type: 'ORDER_STATUS',
          orderId: order.orderId,
          canteenId: order.canteenId,
          read: false,
          createdAt: new Date(),
        },
      },
      { upsert: true, new: true }
    );

    const savedNotif = await Notification.findOne({ notificationId: `NOTIF-${order.orderId}-PREPARING` });
    if (!savedNotif) throw new Error('Notification was not saved in MongoDB!');
    if (!savedNotif.message.includes(canteenName) || !savedNotif.message.includes('Block 33')) {
      throw new Error(`Notification message missing canteen or block number! Got: ${savedNotif.message}`);
    }
    console.log('✅ TEST 2: Preparing notification has kitchen name and block number:', savedNotif.message);

    // 4. Update order to READY and persist ready notification
    const readyTime = new Date();
    await Order.updateOne(
      { orderId: testOrderId },
      { $set: { status: 'READY', readyAt: readyTime } }
    );

    const readyTitle = `Order #${cleanToken}: Ready for Pickup`;
    const readyBody = `Your order is ready! Please collect your food at ${counter} from ${canteenName} at ${blockDisplay}.`;

    await Notification.findOneAndUpdate(
      { notificationId: `NOTIF-${order.orderId}-READY` },
      {
        $set: {
          notificationId: `NOTIF-${order.orderId}-READY`,
          userId: order.studentId,
          title: readyTitle,
          message: readyBody,
          type: 'ORDER_STATUS',
          orderId: order.orderId,
          canteenId: order.canteenId,
          read: false,
          createdAt: readyTime,
        },
      },
      { upsert: true, new: true }
    );

    const savedReadyNotif = await Notification.findOne({ notificationId: `NOTIF-${order.orderId}-READY` });
    if (!savedReadyNotif.title.includes('Ready for Pickup')) {
      throw new Error(`Ready notification title incorrect: ${savedReadyNotif.title}`);
    }
    console.log('✅ TEST 3: Ready for Pickup notification verified:', savedReadyNotif.title, '-', savedReadyNotif.message);

    // 5. Verify NO duplicate notifications exist for same milestone
    const notifsForOrder = await Notification.find({ orderId: testOrderId });
    if (notifsForOrder.length !== 2) {
      throw new Error(`Expected exactly 2 notifications (1 PREPARING, 1 READY), but found ${notifsForOrder.length}`);
    }
    console.log('✅ TEST 4: Strict single notification per milestone verified (exactly 1 PREPARING, 1 READY).');

    // 6. Test Queue estimated time calculation from present time with 0, 1, and multiple orders
    const nowMs = Date.now();
    const singleOrderMetrics = calculateQueueMetrics(
      { orderId: 'CART_PREVIEW', canteenId: testCanteenId, items: [{ name: 'Burger', prepMinutes: 8 }] },
      [], // 0 ahead
      7,
      new Date(nowMs)
    );
    if (singleOrderMetrics.estWaitMinutes !== 8) {
      throw new Error(`Expected wait time 8 min with 0 ahead, got ${singleOrderMetrics.estWaitMinutes}`);
    }
    console.log(`✅ TEST 5: Queue calculation with 0 ahead = ${singleOrderMetrics.estWaitMinutes} min (${singleOrderMetrics.estimatedCompletionTime})`);

    const twoOrderMetrics = calculateQueueMetrics(
      { orderId: 'CART_PREVIEW', canteenId: testCanteenId, items: [{ name: 'Burger', prepMinutes: 8 }] },
      [{ orderId: 'ORD-AHEAD-1', studentId: 'student_1', status: 'PREPARING', createdAt: new Date(nowMs - 2 * 60000), items: [{ name: 'Burger', prepMinutes: 6 }] }],
      7,
      new Date(nowMs)
    );
    // 6 - 2 = 4 min remaining ahead + 8 min own = 12 min wait
    if (twoOrderMetrics.estWaitMinutes !== 12) {
      throw new Error(`Expected wait time 12 min with 1 ahead, got ${twoOrderMetrics.estWaitMinutes}`);
    }
    console.log(`✅ TEST 6: Queue calculation with 1 ahead = ${twoOrderMetrics.estWaitMinutes} min (${twoOrderMetrics.estimatedCompletionTime})`);

    console.log('\n🎉 ALL NOTIFICATION, SYNC, AND QUEUE TESTS PASSED SUCCESSFULLY!');
  } finally {
    // Cleanup
    await Order.deleteOne({ orderId: testOrderId });
    await Notification.deleteMany({ orderId: testOrderId });
    await mongoose.disconnect();
  }
}

runTests().catch(err => {
  console.error('❌ Test failed:', err);
  process.exit(1);
});
