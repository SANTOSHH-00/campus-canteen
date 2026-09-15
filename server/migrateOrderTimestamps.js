const connectDB = require('./src/config/db');
const Order = require('./src/models/Order');
const { IST_OFFSET_MS, formatIstDateTime, formatIstTime, formatIstDate } = require('./src/utils/timeHelper');

async function migrateTimestamps() {
  console.log('🔄 Starting database timestamp migration to IST and local fields...\n');
  await connectDB();

  const orders = await Order.find();
  console.log(`Found ${orders.length} orders in database to inspect and migrate.`);

  let migratedCount = 0;

  for (const order of orders) {
    let needsUpdate = false;
    const updates = {};

    // Check if order timestamps were stored in raw UTC (prior to IST shift)
    // If orderPlacedAt is present and orderDate or orderTime is missing, or hours differ from estimatedReadyTime
    const rawPlaced = order.orderPlacedAt || order.createdAt;
    
    if (rawPlaced) {
      const placedDate = new Date(rawPlaced);
      
      // Determine if orderPlacedAt was already shifted to IST Date
      // If orderPlacedAtLocal is already set and matches, we don't double shift
      if (!order.orderPlacedAtLocal || !order.orderTime || !order.orderDate) {
        // Shift raw UTC timestamp to IST Date
        const istPlacedDate = new Date(placedDate.getTime() + IST_OFFSET_MS);
        updates.orderPlacedAt = istPlacedDate;
        updates.orderDate = formatIstDate(placedDate);
        updates.orderTime = formatIstTime(placedDate);
        updates.orderPlacedAtLocal = formatIstDateTime(placedDate);
        needsUpdate = true;
      }

      if (order.confirmedAt && !order.confirmedAtLocal) {
        const d = new Date(order.confirmedAt);
        updates.confirmedAt = new Date(d.getTime() + IST_OFFSET_MS);
        updates.confirmedAtLocal = formatIstDateTime(d);
        needsUpdate = true;
      }

      if (order.preparingAt && !order.preparingAtLocal) {
        const d = new Date(order.preparingAt);
        updates.preparingAt = new Date(d.getTime() + IST_OFFSET_MS);
        updates.preparingAtLocal = formatIstDateTime(d);
        needsUpdate = true;
      }

      if (order.readyAt && !order.readyAtLocal) {
        const d = new Date(order.readyAt);
        updates.readyAt = new Date(d.getTime() + IST_OFFSET_MS);
        updates.readyAtLocal = formatIstDateTime(d);
        needsUpdate = true;
      }

      if (order.completedAt && !order.completedAtLocal) {
        const d = new Date(order.completedAt);
        updates.completedAt = new Date(d.getTime() + IST_OFFSET_MS);
        updates.completedAtLocal = formatIstDateTime(d);
        needsUpdate = true;
      }

      if (order.statusHistory && Array.isArray(order.statusHistory) && order.statusHistory.length > 0) {
        const updatedHistory = order.statusHistory.map(sh => {
          if (sh.timestamp && !sh.timestampLocal) {
            const shDate = new Date(sh.timestamp);
            return {
              status: sh.status,
              timestamp: new Date(shDate.getTime() + IST_OFFSET_MS),
              timestampLocal: formatIstDateTime(shDate),
            };
          }
          return sh;
        });
        updates.statusHistory = updatedHistory;
        needsUpdate = true;
      }
    }

    if (needsUpdate) {
      await Order.updateOne({ _id: order._id }, { $set: updates });
      migratedCount++;
      console.log(`✅ Migrated order ${order.orderId} (Token #${order.tokenNumber}): orderTime="${updates.orderTime || order.orderTime}", placed="${updates.orderPlacedAtLocal || order.orderPlacedAtLocal}"`);
    }
  }

  console.log(`\n🎉 Migration complete! Successfully updated ${migratedCount} orders.`);

  // Verify Order #148 specifically
  const order148 = await Order.findOne({ tokenNumber: '148' }).lean();
  if (order148) {
    console.log('\n--- VERIFICATION OF ORDER #148 ---');
    console.log(`Order ID: ${order148.orderId}`);
    console.log(`Token: #${order148.tokenNumber}`);
    console.log(`Status: ${order148.status}`);
    console.log(`orderDate: ${order148.orderDate}`);
    console.log(`orderTime: ${order148.orderTime}`);
    console.log(`orderPlacedAtLocal: ${order148.orderPlacedAtLocal}`);
    console.log(`estimatedReadyTime: ${order148.estimatedReadyTime}`);
    console.log(`orderPlacedAt (ISODate): ${order148.orderPlacedAt}`);
    console.log(`confirmedAt (ISODate): ${order148.confirmedAt}`);
    console.log(`preparingAt (ISODate): ${order148.preparingAt}`);
    console.log(`readyAt (ISODate): ${order148.readyAt}`);
    console.log(`completedAt (ISODate): ${order148.completedAt}`);
    console.log('Status History:', JSON.stringify(order148.statusHistory, null, 2));
  }

  process.exit(0);
}

migrateTimestamps().catch(err => {
  console.error('Migration error:', err);
  process.exit(1);
});
