const connectDB = require('./src/config/db');
const Order = require('./src/models/Order');
const { formatIstDateTime, formatIstTime, formatIstDate, IST_OFFSET_MS } = require('./src/utils/timeHelper');

async function normalizeDatabaseTimestamps() {
  console.log('🔄 Starting database timestamp normalization to true UTC...\n');
  await connectDB();

  const orders = await Order.find();
  console.log(`Found ${orders.length} orders in database to inspect and normalize.`);

  let normalizedCount = 0;

  for (const order of orders) {
    let needsUpdate = false;
    const updates = {};

    const created = order.createdAt ? new Date(order.createdAt).getTime() : null;
    let placed = order.orderPlacedAt ? new Date(order.orderPlacedAt) : (order.createdAt ? new Date(order.createdAt) : new Date());

    // Check if orderPlacedAt was artificially shifted by ~5.5 hours ahead of createdAt
    if (created && placed) {
      const diffMs = placed.getTime() - created;
      // If diff is between 5 hours and 6 hours ahead of createdAt, it was double-shifted with IST_OFFSET_MS
      if (diffMs >= 5 * 60 * 60 * 1000 && diffMs <= 6 * 60 * 60 * 1000) {
        placed = new Date(placed.getTime() - IST_OFFSET_MS);
        updates.orderPlacedAt = placed;
        needsUpdate = true;
        console.log(`  [Order ${order.orderId}] Unshifting orderPlacedAt back to true UTC (${placed.toISOString()})`);
      }
    }

    // Check status milestone dates for artificial 5.5 hour shifts
    const milestoneKeys = ['confirmedAt', 'preparingAt', 'readyAt', 'completedAt', 'cancelledAt'];
    for (const key of milestoneKeys) {
      if (order[key]) {
        let mDate = new Date(order[key]);
        if (created) {
          const diff = mDate.getTime() - created;
          if (diff >= 5 * 60 * 60 * 1000 && diff <= 6 * 60 * 60 * 1000) {
            mDate = new Date(mDate.getTime() - IST_OFFSET_MS);
            updates[key] = mDate;
            needsUpdate = true;
            console.log(`  [Order ${order.orderId}] Unshifting ${key} back to true UTC (${mDate.toISOString()})`);
          }
        }
        const localKey = `${key}Local`;
        const correctLocal = formatIstDateTime(updates[key] || mDate);
        if (order[localKey] !== correctLocal) {
          updates[localKey] = correctLocal;
          needsUpdate = true;
        }
      }
    }

    // Normalize statusHistory
    if (order.statusHistory && Array.isArray(order.statusHistory) && order.statusHistory.length > 0) {
      let historyChanged = false;
      const normalizedHistory = order.statusHistory.map(sh => {
        if (!sh.timestamp) return sh;
        let shDate = new Date(sh.timestamp);
        if (created) {
          const diff = shDate.getTime() - created;
          if (diff >= 5 * 60 * 60 * 1000 && diff <= 6 * 60 * 60 * 1000) {
            shDate = new Date(shDate.getTime() - IST_OFFSET_MS);
            historyChanged = true;
          }
        }
        const correctShLocal = formatIstDateTime(shDate);
        if (sh.timestampLocal !== correctShLocal || historyChanged) {
          historyChanged = true;
          return {
            status: sh.status,
            timestamp: shDate,
            timestampLocal: correctShLocal,
          };
        }
        return sh;
      });
      if (historyChanged) {
        updates.statusHistory = normalizedHistory;
        needsUpdate = true;
      }
    }

    // Refresh human-readable strings from true UTC placed date
    const finalPlaced = updates.orderPlacedAt || placed;
    const correctDateStr = formatIstDate(finalPlaced);
    const correctTimeStr = formatIstTime(finalPlaced);
    const correctPlacedLocal = formatIstDateTime(finalPlaced);

    if (order.orderDate !== correctDateStr) {
      updates.orderDate = correctDateStr;
      needsUpdate = true;
    }
    if (order.orderTime !== correctTimeStr) {
      updates.orderTime = correctTimeStr;
      needsUpdate = true;
    }
    if (order.orderPlacedAtLocal !== correctPlacedLocal) {
      updates.orderPlacedAtLocal = correctPlacedLocal;
      needsUpdate = true;
    }

    // Ensure customerPickupTime and estimatedCompletionTime
    if (!order.customerPickupTime) {
      let custPickup = '';
      if (order.pickupPreference) {
        if (/pickup\s+at\s+/i.test(order.pickupPreference)) {
          custPickup = order.pickupPreference.replace(/^pickup\s+at\s+/i, '').trim();
        } else if (/pickup\s+asap/i.test(order.pickupPreference)) {
          custPickup = 'ASAP';
        }
      }
      updates.customerPickupTime = custPickup || 'ASAP';
      needsUpdate = true;
    }

    if (!order.estimatedCompletionTime) {
      updates.estimatedCompletionTime = order.estimatedReadyTime || correctTimeStr;
      needsUpdate = true;
    }

    if (needsUpdate) {
      await Order.updateOne({ _id: order._id }, { $set: updates });
      normalizedCount++;
      console.log(`✅ Normalized order ${order.orderId} (Token #${order.tokenNumber})`);
    }
  }

  console.log(`\n🎉 Normalization complete! Processed ${normalizedCount} orders.`);
  process.exit(0);
}

normalizeDatabaseTimestamps().catch(err => {
  console.error('Normalization error:', err);
  process.exit(1);
});
