const express = require('express');
const router = express.Router();
const mongoose = require('mongoose');
const Order = require('../models/Order');
const Canteen = require('../models/Canteen');
const { broadcastOrderStatus, broadcastNewOrder, broadcastQueueUpdate } = require('../services/socketService');
const { sendPushToUser } = require('../services/fcmService');

// Active kitchen preparation statuses
const ACTIVE_QUEUE_STATUSES = ['NEW', 'WAITING', 'CONFIRMED', 'PREPARING'];

/**
 * Helper to compute an order's estimated prep time in minutes.
 */
function getOrderPrepTime(order, defaultWait = 7) {
  if (order && order.estimatedPrepMinutes && order.estimatedPrepMinutes > 0) {
    return order.estimatedPrepMinutes;
  }
  if (order && order.items && Array.isArray(order.items) && order.items.length > 0) {
    const itemPreps = order.items.map(it => it.prepMinutes || 0).filter(p => p > 0);
    if (itemPreps.length > 0) {
      return Math.max(...itemPreps);
    }
  }
  return defaultWait;
}

/**
 * Robust helper to locate an order by orderId, MongoDB _id, or tokenNumber.
 * Strips all prefixes (like #, Q, #Q) to reliably match the underlying numeric token.
 */
async function findOrderByIdOrToken(idParam, canteenId = null) {
  if (!idParam) return null;
  const cleanId = decodeURIComponent(idParam).trim();

  // 1. Exact orderId match
  let order = await Order.findOne({ orderId: cleanId }).lean();
  if (order) return order;

  // 2. MongoDB _id match
  if (mongoose.Types.ObjectId.isValid(cleanId)) {
    order = await Order.findOne({ _id: cleanId }).lean();
    if (order) return order;
  }

  // 3. Fallback: match by tokenNumber (strip non-digits like #, Q, #Q)
  const rawNum = cleanId.replace(/^[^0-9]+/g, '').trim();
  const tokenCandidates = [cleanId];
  if (rawNum) {
    tokenCandidates.push(rawNum, `#${rawNum}`, `#Q${rawNum}`, `Q${rawNum}`);
  }

  const query = {
    tokenNumber: { $in: tokenCandidates },
  };
  if (canteenId) {
    query.canteenId = canteenId;
  }

  order = await Order.findOne(query)
    .sort({ createdAt: -1 })
    .lean();

  return order;
}

// GET /api/orders/queue/canteen/:canteenId - Get live queue depth & avg wait time
router.get('/queue/canteen/:canteenId', async (req, res) => {
  try {
    const { canteenId } = req.params;
    const canteen = await Canteen.findOne({ id: canteenId }).select('avgWaitMinutes').lean();
    const defaultWait = (canteen && canteen.avgWaitMinutes) ? canteen.avgWaitMinutes : 5;

    // Filter active orders from the last 24 hours to prevent stale past-day orders
    const activeCutoff = new Date(Date.now() - 24 * 60 * 60 * 1000);

    // Single server-authoritative active queue for this canteen
    const activeOrders = await Order.find({
      canteenId,
      status: { $in: ACTIVE_QUEUE_STATUSES },
      createdAt: { $gte: activeCutoff },
    })
      .sort({ createdAt: 1, orderId: 1 })
      .select('orderId studentId canteenId status createdAt estimatedPrepMinutes items')
      .lean();

    const queueCount = activeOrders.length;

    // Cumulative prep time for all orders ahead + base prep time for next incoming order
    let totalWait = 0;
    for (const order of activeOrders) {
      totalWait += getOrderPrepTime(order, defaultWait);
    }
    const avgWaitMinutes = queueCount === 0 ? defaultWait : Math.min(120, totalWait + defaultWait);

    console.log(`[QUEUE CANTEEN] Canteen ${canteenId} has ${queueCount} active orders, wait: ${avgWaitMinutes} min`);

    res.json({
      canteenId,
      queueCount,
      avgWaitMinutes,
      activeOrdersCount: queueCount,
      ordersAhead: queueCount,
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// GET /api/orders/:id/queue - Get queue position and wait time for a specific order
router.get('/:id/queue', async (req, res) => {
  try {
    const canteenHint = req.query.canteenId || null;
    const targetOrder = await findOrderByIdOrToken(req.params.id, canteenHint);
    if (!targetOrder) {
      console.warn(`[QUEUE] Order not found for query: "${req.params.id}" (canteenHint: ${canteenHint})`);
      return res.status(404).json({ error: 'Order not found' });
    }

    if (targetOrder.status === 'READY') {
      return res.json({
        orderId: targetOrder.orderId,
        tokenNumber: targetOrder.tokenNumber || '',
        status: 'READY',
        canteenId: targetOrder.canteenId,
        queuePosition: 0,
        ordersAhead: 0,
        estWaitMinutes: 0,
        message: 'Ready for Pickup',
      });
    }

    if (targetOrder.status === 'COMPLETED' || targetOrder.status === 'PICKED_UP') {
      return res.json({
        orderId: targetOrder.orderId,
        tokenNumber: targetOrder.tokenNumber || '',
        status: 'COMPLETED',
        canteenId: targetOrder.canteenId,
        queuePosition: 0,
        ordersAhead: 0,
        estWaitMinutes: 0,
        message: 'Picked Up',
      });
    }

    if (targetOrder.status === 'CANCELLED') {
      return res.json({
        orderId: targetOrder.orderId,
        tokenNumber: targetOrder.tokenNumber || '',
        status: 'CANCELLED',
        canteenId: targetOrder.canteenId,
        queuePosition: 0,
        ordersAhead: 0,
        estWaitMinutes: 0,
        message: 'Order Cancelled',
      });
    }

    const canteen = await Canteen.findOne({ id: targetOrder.canteenId }).select('avgWaitMinutes').lean();
    const defaultWait = (canteen && canteen.avgWaitMinutes) ? canteen.avgWaitMinutes : 7;

    // Filter active orders from the last 24 hours to prevent stale past-day orders
    const activeCutoff = new Date(Date.now() - 24 * 60 * 60 * 1000);

    // Fetch all active orders for the SAME canteen, sorted deterministically by createdAt ASC, orderId ASC
    const activeOrders = await Order.find({
      canteenId: targetOrder.canteenId,
      status: { $in: ACTIVE_QUEUE_STATUSES },
      createdAt: { $gte: activeCutoff },
    })
      .sort({ createdAt: 1, orderId: 1 })
      .select('orderId studentId canteenId status createdAt estimatedPrepMinutes items')
      .lean();

    // Find the position of targetOrder in the canteen's active queue
    const targetIndex = activeOrders.findIndex(o => o.orderId === targetOrder.orderId);

    let ordersAhead = 0;
    let queuePosition = 1;
    let estWaitMinutes = getOrderPrepTime(targetOrder, defaultWait);

    if (targetIndex !== -1) {
      ordersAhead = targetIndex;
      queuePosition = targetIndex + 1;

      // Estimated waiting time = sum of prep times of orders ahead + target order's prep time
      let cumulativeWait = 0;
      for (let i = 0; i < targetIndex; i++) {
        cumulativeWait += getOrderPrepTime(activeOrders[i], defaultWait);
      }
      cumulativeWait += getOrderPrepTime(targetOrder, defaultWait);
      estWaitMinutes = Math.min(120, Math.max(1, cumulativeWait));
    } else {
      // Fallback in case targetOrder is in transition or was placed outside 24h window
      const targetTime = targetOrder.createdAt ? new Date(targetOrder.createdAt).getTime() : Date.now();
      const earlier = activeOrders.filter(o => {
        const oTime = o.createdAt ? new Date(o.createdAt).getTime() : 0;
        return oTime < targetTime;
      });
      ordersAhead = earlier.length;
      queuePosition = ordersAhead + 1;
      estWaitMinutes = Math.min(120, Math.max(1, (ordersAhead + 1) * defaultWait));
    }

    // Temporarily inspect/log detailed queue state for debugging as required
    console.log('=== [QUEUE CALCULATION DEBUG] ===');
    console.log(`Current Order ID: ${targetOrder.orderId}`);
    console.log(`Current User ID: ${targetOrder.studentId || 'N/A'}`);
    console.log(`Current Canteen ID: ${targetOrder.canteenId}`);
    console.log(`All active orders for canteen "${targetOrder.canteenId}" (${activeOrders.length}):`);
    activeOrders.forEach((o, i) => {
      console.log(`  [#${i + 1}] Order ID: ${o.orderId}, Status: ${o.status}, Sequence/CreatedAt: ${o.createdAt}`);
    });
    console.log(`Calculated queue position: #${queuePosition}`);
    console.log(`Calculated ordersAhead: ${ordersAhead}`);
    console.log(`Calculated estimated waiting time: ${estWaitMinutes} min`);
    console.log('=================================');

    const message = ordersAhead === 0
      ? 'You are next in line'
      : `You are #${queuePosition} in line (${ordersAhead} order${ordersAhead > 1 ? 's' : ''} ahead)`;

    res.json({
      orderId: targetOrder.orderId,
      tokenNumber: targetOrder.tokenNumber || '',
      status: targetOrder.status,
      canteenId: targetOrder.canteenId,
      queuePosition,
      ordersAhead,
      estWaitMinutes,
      message,
    });
  } catch (err) {
    console.error('[QUEUE] Error calculating queue position:', err);
    res.status(500).json({ error: err.message });
  }
});

// GET /api/orders/student/:studentId - List orders for student
router.get('/student/:studentId', async (req, res) => {
  try {
    const orders = await Order.find({ studentId: req.params.studentId })
      .sort({ createdAt: -1 })
      .lean();
    res.json(orders);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// GET /api/orders/canteen/:canteenId - List orders for canteen
router.get('/canteen/:canteenId', async (req, res) => {
  try {
    const orders = await Order.find({ canteenId: req.params.canteenId })
      .sort({ createdAt: -1 })
      .lean();
    res.json(orders);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// GET /api/orders/:id - Get single order
router.get('/:id', async (req, res) => {
  try {
    const order = await findOrderByIdOrToken(req.params.id);
    if (!order) return res.status(404).json({ error: 'Order not found' });
    res.json(order);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/orders - Place a new order
router.post('/', async (req, res) => {
  try {
    const orderData = req.body;
    if (!orderData.orderId) {
      orderData.orderId = 'ORD-' + Date.now();
    }
    if (!orderData.estimatedPrepMinutes) {
      orderData.estimatedPrepMinutes = getOrderPrepTime(orderData, 7);
    }
    const order = await Order.create(orderData);

    // Real-time WebSocket Broadcast & Push Trigger
    try {
      broadcastNewOrder(order);
      if (order.canteenId) {
        broadcastQueueUpdate(order.canteenId);
        sendPushToUser(order.canteenId, {
          title: `New Order #${order.tokenNumber || order.orderId}`,
          body: `Received ${order.items?.length || 1} items totaling ₹${order.totalPrice || order.totalAmount}.`,
          data: { orderId: order.orderId, type: 'NEW_ORDER' },
        }).catch(() => {});
      }
    } catch (e) {
      console.warn('[Orders] Realtime dispatch warning:', e.message);
    }

    res.status(201).json(order);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// PATCH /api/orders/:id/status - Update order status (NEW, PREPARING, READY, COMPLETED, CANCELLED)
router.patch('/:id/status', async (req, res) => {
  try {
    let normalizedStatus = (req.body.status || '').toUpperCase().trim();
    if (normalizedStatus === 'PICKED_UP' || normalizedStatus === 'PICKED UP' || normalizedStatus === 'DELIVERED') {
      normalizedStatus = 'COMPLETED';
    }

    const targetOrder = await findOrderByIdOrToken(req.params.id);
    if (!targetOrder) {
      console.warn(`[Orders] PATCH status failed - Order not found for query param: ${req.params.id}`);
      return res.status(404).json({ error: 'Order not found' });
    }

    const updated = await Order.findOneAndUpdate(
      { _id: targetOrder._id },
      { $set: { status: normalizedStatus } },
      { new: true }
    );

    // Real-time WebSocket Broadcast & Push Trigger
    try {
      broadcastOrderStatus(updated);
      if (updated.canteenId) {
        broadcastQueueUpdate(updated.canteenId);
      }

      const statusTitle = normalizedStatus === 'PREPARING'
        ? `Order #${updated.tokenNumber || updated.orderId} is being Prepared 🍳`
        : normalizedStatus === 'READY'
        ? `Order #${updated.tokenNumber || updated.orderId} is Ready for Pickup! 🔔`
        : normalizedStatus === 'COMPLETED'
        ? `Order #${updated.tokenNumber || updated.orderId} Completed 🎉`
        : `Order #${updated.tokenNumber || updated.orderId} Status: ${normalizedStatus}`;

      const statusBody = normalizedStatus === 'READY'
        ? `Your meal is hot and ready at Counter ${updated.pickupCounter || '1'}. Please collect your order!`
        : `Current status updated to ${normalizedStatus}.`;

      if (updated.studentId) {
        sendPushToUser(updated.studentId, {
          title: statusTitle,
          body: statusBody,
          data: { orderId: updated.orderId, status: updated.status, type: 'ORDER_STATUS' },
        }).catch(() => {});
      }
    } catch (e) {
      console.warn('[Orders] Realtime dispatch warning:', e.message);
    }

    res.json(updated);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

module.exports = router;
