const connectDB = require('./src/config/db');
const Order = require('./src/models/Order');
const Notification = require('./src/models/Notification');
const User = require('./src/models/User');

async function cleanGuestData() {
  console.log('🧹 Cleaning all guest data from MongoDB Atlas...\n');
  await connectDB();

  // 1. Delete Guest Orders
  const guestOrderQuery = {
    $or: [
      { studentId: { $regex: 'guest', $options: 'i' } },
      { studentName: { $regex: 'guest', $options: 'i' } },
      { studentId: 'student_guest' }
    ]
  };
  const deletedOrders = await Order.deleteMany(guestOrderQuery);
  console.log(`✅ Deleted ${deletedOrders.deletedCount} guest orders from Orders collection.`);

  // 2. Delete Guest Notifications
  const guestNotifQuery = {
    $or: [
      { userId: { $regex: 'guest', $options: 'i' } },
      { userId: 'student_guest' },
      { title: { $regex: 'guest', $options: 'i' } }
    ]
  };
  const deletedNotifs = await Notification.deleteMany(guestNotifQuery);
  console.log(`✅ Deleted ${deletedNotifs.deletedCount} guest notifications from Notifications collection.`);

  // 3. Delete Guest Users if any exist
  const guestUserQuery = {
    $or: [
      { uid: { $regex: 'guest', $options: 'i' } },
      { name: { $regex: 'guest', $options: 'i' } },
      { email: { $regex: 'guest', $options: 'i' } },
      { registrationNumber: { $regex: 'guest', $options: 'i' } }
    ]
  };
  const deletedUsers = await User.deleteMany(guestUserQuery);
  console.log(`✅ Deleted ${deletedUsers.deletedCount} guest users from Users collection.`);

  // Verify counts remaining
  const remainingGuestOrders = await Order.countDocuments(guestOrderQuery);
  const remainingGuestNotifs = await Notification.countDocuments(guestNotifQuery);
  console.log(`\nVerification: Remaining guest orders: ${remainingGuestOrders}, Remaining guest notifs: ${remainingGuestNotifs}`);

  process.exit(0);
}

cleanGuestData().catch(err => {
  console.error('Error cleaning guest data:', err);
  process.exit(1);
});
