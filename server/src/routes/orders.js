const express = require('express');
const router = express.Router();
const mongoose = require('mongoose');
const Order = require('../models/Order');
const Canteen = require('../models/Canteen');
const Item = require('../models/Item');
const { broadcastOrderStatus, broadcastNewOrder, broadcastQueueUpdate } = require('../services/socketService');
const { sendPushToUser } = require('../services/fcmService');

// Active kitchen preparation statuses (case-insensitive fallback coverage)
const ACTIVE_QUEUE_STATUSES = ['NEW', 'WAITING', 'CONFIRMED', 'PREPARING', 'new', 'waiting', 'confirmed', 'preparing'];

/**
 * Helper to compute an order's estimated prep time in minutes based on its individual items.
 * Prioritizes actual item prepMinutes, accounting for item quantity and packaging overhead.
 */
function getOrderPrepTime(order, defaultWait = 7) {
  if (order && order.items && Array.isArray(order.items) && order.items.length > 0) {
    const itemPreps = order.items.map(it => it.prepMinutes || 0).filter(p => p > 0);
    if (itemPreps.length > 0) {
      const maxPrep = Math.max(...itemPreps);
      const totalItemCount = order.items.reduce((acc, it) => acc + (it.quantity || 1), 0);
      const extraItemsBuffer = Math.min(10, Math.max(0, (totalItemCount - 1) * 1.5));
      return Math.min(60, Math.round(maxPrep + extraItemsBuffer));
    }
  }
  if (order && order.estimatedPrepMinutes && order.estimatedPrepMinutes > 0) {
    return order.estimatedPrepMinutes;
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

/**
 * Accurately calculate queue position, orders ahead, and estimated wait time
 * based on order placement time (createdAt), individual item prep times,
 * elapsed cooking time, and dynamic kitchen preparation concurrency.
 *
 * Supports single-user, multi-user, and large-scale (50+ orders) rush hours.
 */
function calculateQueueMetrics(targetOrder, activeOrders, defaultWait = 7, kitchenCapacity = 2) {
  const targetIndex = activeOrders.findIndex(o => o.orderId === targetOrder.orderId);
  const now = Date.now();

  const ownTotalPrep = getOrderPrepTime(targetOrder, defaultWait);
  const targetPlacedAt = targetOrder.orderPlacedAt || targetOrder.createdAt;
  const targetCreatedAt = targetPlacedAt ? new Date(targetPlacedAt).getTime() : now;
  const targetElapsedMin = Math.max(0, (now - targetCreatedAt) / 60000);
  const ownRemaining = Math.max(1, Math.round(ownTotalPrep - targetElapsedMin));

  if (targetIndex === -1) {
    // Target order is in transition or was placed outside current active window
    const earlier = activeOrders.filter(o => {
      const oPlacedAt = o.orderPlacedAt || o.createdAt;
      const oTime = oPlacedAt ? new Date(oPlacedAt).getTime() : 0;
      return oTime < targetCreatedAt;
    });
    const ordersAhead = earlier.length;
    const queuePosition = ordersAhead + 1;
    const capacity = kitchenCapacity || Math.min(4, Math.max(2, Math.floor(ordersAhead / 6) + 2));
    const estWaitMinutes = Math.min(
      120,
      Math.max(
        1,
        Math.round(
          ordersAhead === 0
            ? ownRemaining
            : (ordersAhead * defaultWait) / capacity + ownRemaining
        )
      )
    );
    return { ordersAhead, queuePosition, queueNumber: queuePosition, estWaitMinutes };
  }

  const ordersAhead = targetIndex;
  const queuePosition = targetIndex + 1;

  if (ordersAhead === 0) {
    // First in line - wait time is remaining own prep time
    return {
      ordersAhead: 0,
      queuePosition: 1,
      queueNumber: 1,
      estWaitMinutes: Math.min(120, ownRemaining),
    };
  }

  // Calculate remaining preparation time for all orders ahead based on each order's items & placement time
  const aheadOrders = activeOrders.slice(0, targetIndex);
  let sumRemainingAhead = 0;

  for (let i = 0; i < aheadOrders.length; i++) {
    const ahead = aheadOrders[i];
    const aheadPrep = getOrderPrepTime(ahead, defaultWait);
    const aheadPlacedAt = ahead.orderPlacedAt || ahead.createdAt;
    const aheadCreated = aheadPlacedAt ? new Date(aheadPlacedAt).getTime() : now;
    const aheadElapsedMin = Math.max(0, (now - aheadCreated) / 60000);
    // An active unfulfilled order requires at least 1 minute until kitchen completes/readies it
    const aheadRemaining = Math.max(1, aheadPrep - aheadElapsedMin);
    sumRemainingAhead += aheadRemaining;
  }

  const capacity = kitchenCapacity || Math.min(4, Math.max(2, Math.floor(ordersAhead / 6) + 2));
  let totalWait;
  if (ordersAhead === 1) {
    // Exactly 1 order ahead: wait for Order 1's remaining time + own order's preparation
    totalWait = Math.round(sumRemainingAhead + ownTotalPrep);
  } else {
    // 2 or more orders ahead: kitchen prepares across parallel cooking counters/stations
    const queueWaitAhead = Math.ceil(sumRemainingAhead / capacity);
    totalWait = Math.round(queueWaitAhead + ownTotalPrep);
  }

  const estWaitMinutes = Math.min(120, Math.max(1, totalWait));

  return {
    ordersAhead,
    queuePosition,
    queueNumber: queuePosition,
    estWaitMinutes,
  };
}

// GET /api/orders/queue/canteen/:canteenId - Get live queue depth & avg wait time
router.get('/queue/canteen/:canteenId', async (req, res) => {
  try {
    const { canteenId } = req.params;
    const canteen = await Canteen.findOne({ id: canteenId }).select('avgWaitMinutes').lean();
    const defaultWait = (canteen && canteen.avgWaitMinutes) ? canteen.avgWaitMinutes : 5;

    // Filter active orders from the last 24 hours to prevent stale past-day orders
    const activeCutoff = new Date(Date.now() - 24 * 60 * 60 * 1000);

    // Single server-authoritative active queue for this canteen sorted by server placement time
    const activeOrders = await Order.find({
      canteenId,
      status: { $in: ACTIVE_QUEUE_STATUSES },
      createdAt: { $gte: activeCutoff },
    })
      .sort({ orderPlacedAt: 1, createdAt: 1, orderId: 1 })
      .select('orderId studentId canteenId status createdAt orderPlacedAt confirmedAt preparingAt readyAt completedAt estimatedPrepMinutes items')
      .lean();

    const queueCount = activeOrders.length;
    let avgWaitMinutes = defaultWait;

    if (queueCount > 0) {
      const now = Date.now();
      let sumRemaining = 0;
      for (const order of activeOrders) {
        const prep = getOrderPrepTime(order, defaultWait);
        const orderPlaced = order.orderPlacedAt || order.createdAt;
        const created = orderPlaced ? new Date(orderPlaced).getTime() : now;
        const elapsed = Math.max(0, (now - created) / 60000);
        sumRemaining += Math.max(1, prep - elapsed);
      }
      const capacity = Math.min(4, Math.max(2, Math.floor(queueCount / 6) + 2));
      const queueWait = Math.ceil(sumRemaining / capacity);
      avgWaitMinutes = Math.min(120, Math.max(defaultWait, queueWait + defaultWait));
    }

    console.log(`[QUEUE CANTEEN] Canteen "${canteenId}" has ${queueCount} active orders, wait: ${avgWaitMinutes} min`);

    res.json({
      canteenId,
      queueCount,
      queueNumber: queueCount + 1,
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

    const orderPlacedAt = targetOrder.orderPlacedAt || targetOrder.createdAt || null;
    const confirmedAt = targetOrder.confirmedAt || null;
    const preparingAt = targetOrder.preparingAt || null;
    const readyAt = targetOrder.readyAt || null;
    const completedAt = targetOrder.completedAt || null;
    const cancelledAt = targetOrder.cancelledAt || null;
    const statusHistory = targetOrder.statusHistory || [];

    if (targetOrder.status === 'READY') {
      return res.json({
        orderId: targetOrder.orderId,
        tokenNumber: targetOrder.tokenNumber || '',
        status: 'READY',
        canteenId: targetOrder.canteenId,
        queuePosition: 0,
        queueNumber: 0,
        ordersAhead: 0,
        estWaitMinutes: 0,
        message: 'Ready for Pickup',
        orderPlacedAt,
        confirmedAt,
        preparingAt,
        readyAt,
        completedAt,
        cancelledAt,
        statusHistory,
      });
    }

    if (targetOrder.status === 'COMPLETED' || targetOrder.status === 'PICKED_UP') {
      return res.json({
        orderId: targetOrder.orderId,
        tokenNumber: targetOrder.tokenNumber || '',
        status: 'COMPLETED',
        canteenId: targetOrder.canteenId,
        queuePosition: 0,
        queueNumber: 0,
        ordersAhead: 0,
        estWaitMinutes: 0,
        message: 'Picked Up',
        orderPlacedAt,
        confirmedAt,
        preparingAt,
        readyAt,
        completedAt,
        cancelledAt,
        statusHistory,
      });
    }

    if (targetOrder.status === 'CANCELLED') {
      return res.json({
        orderId: targetOrder.orderId,
        tokenNumber: targetOrder.tokenNumber || '',
        status: 'CANCELLED',
        canteenId: targetOrder.canteenId,
        queuePosition: 0,
        queueNumber: 0,
        ordersAhead: 0,
        estWaitMinutes: 0,
        message: 'Order Cancelled',
        orderPlacedAt,
        confirmedAt,
        preparingAt,
        readyAt,
        completedAt,
        cancelledAt,
        statusHistory,
      });
    }

    const canteen = await Canteen.findOne({ id: targetOrder.canteenId }).select('avgWaitMinutes').lean();
    const defaultWait = (canteen && canteen.avgWaitMinutes) ? canteen.avgWaitMinutes : 7;

    // Filter active orders from the last 24 hours to prevent stale past-day orders
    const activeCutoff = new Date(Date.now() - 24 * 60 * 60 * 1000);

    // Fetch all active orders for the SAME canteen, sorted deterministically by orderPlacedAt ASC, createdAt ASC, orderId ASC
    const activeOrders = await Order.find({
      canteenId: targetOrder.canteenId,
      status: { $in: ACTIVE_QUEUE_STATUSES },
      createdAt: { $gte: activeCutoff },
    })
      .sort({ orderPlacedAt: 1, createdAt: 1, orderId: 1 })
      .select('orderId studentId canteenId status createdAt orderPlacedAt confirmedAt preparingAt readyAt completedAt estimatedPrepMinutes items')
      .lean();

    // Compute dynamic, time-based queue metrics with kitchen concurrency
    const { ordersAhead, queuePosition, queueNumber, estWaitMinutes } = calculateQueueMetrics(
      targetOrder,
      activeOrders,
      defaultWait,
      2
    );

    // Inspect/log detailed queue state for debugging as required
    console.log('=== [QUEUE CALCULATION DEBUG] ===');
    console.log(`Current Order ID: ${targetOrder.orderId}`);
    console.log(`Current User ID: ${targetOrder.studentId || 'N/A'}`);
    console.log(`Current Canteen ID: ${targetOrder.canteenId}`);
    console.log(`All active orders for canteen "${targetOrder.canteenId}" (${activeOrders.length}):`);
    activeOrders.forEach((o, i) => {
      console.log(`  [#${i + 1}] Order ID: ${o.orderId}, Status: ${o.status}, Sequence/PlacedAt: ${o.orderPlacedAt || o.createdAt}`);
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
      queueNumber: queuePosition,
      ordersAhead,
      estWaitMinutes,
      message,
      orderPlacedAt,
      confirmedAt,
      preparingAt,
      readyAt,
      completedAt,
      cancelledAt,
      statusHistory,
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

// POST /api/orders - Place a new order with authoritative server timestamps
router.post('/', async (req, res) => {
  try {
    const orderData = req.body;
    const now = new Date();

    // The backend/server is the authoritative source of time: discard client timestamps
    delete orderData._id;
    delete orderData.createdAt;
    delete orderData.updatedAt;

    if (!orderData.orderId) {
      orderData.orderId = 'ORD-' + now.getTime() + '-' + Math.floor(1000 + Math.random() * 9000);
    }

    const initialStatus = (orderData.status || 'PREPARING').toUpperCase().trim();
    orderData.status = initialStatus;
    orderData.orderPlacedAt = now;

    // Server-authoritative status history and milestone timestamps
    const statusHistory = [{ status: 'PLACED', timestamp: now }];
    if (initialStatus === 'CONFIRMED') {
      orderData.confirmedAt = now;
      statusHistory.push({ status: 'CONFIRMED', timestamp: now });
    } else if (initialStatus === 'PREPARING') {
      orderData.confirmedAt = now;
      orderData.preparingAt = now;
      statusHistory.push({ status: 'CONFIRMED', timestamp: now });
      statusHistory.push({ status: 'PREPARING', timestamp: now });
    }
    orderData.statusHistory = statusHistory;

    // Auto-populate item prepMinutes from Item collection if missing or default
    if (orderData.items && Array.isArray(orderData.items) && orderData.items.length > 0) {
      const itemIds = orderData.items.map(it => it.itemId).filter(Boolean);
      const itemNames = orderData.items.map(it => it.name).filter(Boolean);
      if (itemIds.length > 0 || itemNames.length > 0) {
        const dbItems = await Item.find({
          $or: [
            { id: { $in: itemIds } },
            { name: { $in: itemNames } },
          ],
        }).select('id name prepMinutes').lean();
        const prepMapById = new Map(dbItems.map(d => [d.id, d.prepMinutes]));
        const prepMapByName = new Map(dbItems.map(d => [(d.name || '').toLowerCase().trim(), d.prepMinutes]));
        for (const it of orderData.items) {
          if (!it.prepMinutes || it.prepMinutes === 7) {
            if (it.itemId && prepMapById.has(it.itemId)) {
              it.prepMinutes = prepMapById.get(it.itemId);
            } else if (it.name && prepMapByName.has((it.name || '').toLowerCase().trim())) {
              it.prepMinutes = prepMapByName.get((it.name || '').toLowerCase().trim());
            }
          }
        }
      }
    }

    if (!orderData.estimatedPrepMinutes || orderData.estimatedPrepMinutes <= 0) {
      const canteen = await Canteen.findOne({ id: orderData.canteenId }).select('avgWaitMinutes').lean();
      const defaultWait = (canteen && canteen.avgWaitMinutes) ? canteen.avgWaitMinutes : 7;
      orderData.estimatedPrepMinutes = getOrderPrepTime(orderData, defaultWait);
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

// PATCH /api/orders/:id/status - Update order status with server-authoritative timestamps
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

    const now = new Date();
    const updateFields = { status: normalizedStatus };

    if (normalizedStatus === 'CONFIRMED') updateFields.confirmedAt = now;
    if (normalizedStatus === 'PREPARING') updateFields.preparingAt = now;
    if (normalizedStatus === 'READY') updateFields.readyAt = now;
    if (normalizedStatus === 'COMPLETED') updateFields.completedAt = now;
    if (normalizedStatus === 'CANCELLED') updateFields.cancelledAt = now;

    const updated = await Order.findOneAndUpdate(
      { _id: targetOrder._id },
      {
        $set: updateFields,
        $push: { statusHistory: { status: normalizedStatus, timestamp: now } },
      },
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

router.calculateQueueMetrics = calculateQueueMetrics;
router.getOrderPrepTime = getOrderPrepTime;
router.findOrderByIdOrToken = findOrderByIdOrToken;
router.ACTIVE_QUEUE_STATUSES = ACTIVE_QUEUE_STATUSES;

module.exports = router;
