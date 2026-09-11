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
 * Format a Date or timestamp to user-friendly 12-hour local time (e.g. "12:45 PM")
 */
function formatTime12Hour(date) {
  const d = new Date(date);
  let hours = d.getHours();
  const minutes = d.getMinutes();
  const ampm = hours >= 12 ? 'PM' : 'AM';
  hours = hours % 12;
  hours = hours ? hours : 12;
  const minStr = minutes < 10 ? '0' + minutes : minutes;
  return `${hours}:${minStr} ${ampm}`;
}

/**
 * Accurately calculate item-aware queue position, orders ahead, and estimated wait time
 * based on order placement time (orderPlacedAt/createdAt), specific item prep times,
 * elapsed preparation time, kitchen concurrency, and intelligent customer deduplication.
 *
 * Scoped strictly to: same canteen + relevant food item(s) + active orders.
 */
function calculateQueueMetrics(targetOrder, activeOrders, defaultWait = 7, kitchenCapacity = 2) {
  const targetIndex = activeOrders.findIndex(o => o.orderId === targetOrder.orderId);
  const now = Date.now();

  const ownTotalPrep = getOrderPrepTime(targetOrder, defaultWait);
  const targetPlacedAt = targetOrder.orderPlacedAt || targetOrder.createdAt;
  const targetCreatedAt = targetPlacedAt ? new Date(targetPlacedAt).getTime() : now;
  const targetElapsedMin = Math.max(0, (now - targetCreatedAt) / 60000);
  const ownRemaining = Math.max(1, Math.round(ownTotalPrep - targetElapsedMin));

  // Determine ahead orders in FIFO order
  let aheadOrders = [];
  if (targetIndex === -1) {
    if (targetOrder.orderId === 'CART_PREVIEW' || targetOrder.orderId === 'CART_ITEM_PREVIEW') {
      aheadOrders = [...activeOrders];
    } else {
      aheadOrders = activeOrders.filter(o => {
        const oPlacedAt = o.orderPlacedAt || o.createdAt;
        const oTime = oPlacedAt ? new Date(oPlacedAt).getTime() : 0;
        return oTime < targetCreatedAt;
      });
    }
  } else {
    aheadOrders = activeOrders.slice(0, targetIndex);
  }

  // Extract target items for item-specific matching
  const targetItems = targetOrder.items && Array.isArray(targetOrder.items) ? targetOrder.items : [];
  const hasSpecificItems = targetItems.length > 0;
  const targetItemIds = new Set(targetItems.map(it => it.itemId).filter(Boolean));
  const targetItemNames = new Set(targetItems.map(it => (it.name || '').toLowerCase().trim()).filter(Boolean));

  const primaryItemName = hasSpecificItems ? (targetItems[0].name || 'Item') : 'Item';
  const itemSummary = hasSpecificItems
    ? (targetItems.length === 1
        ? `${targetItems[0].name} × ${targetItems[0].quantity || 1}`
        : `${targetItems[0].name} + ${targetItems.length - 1} other${targetItems.length > 2 ? 's' : ''}`)
    : 'Order Items';

  function orderMatchesTarget(order) {
    if (!hasSpecificItems) return true;
    if (!order.items || !Array.isArray(order.items) || order.items.length === 0) return true;
    return order.items.some(it => {
      if (it.itemId && targetItemIds.has(it.itemId)) return true;
      const n = (it.name || '').toLowerCase().trim();
      return n && targetItemNames.has(n);
    });
  }

  // Filter ahead orders that match the target item(s)
  const matchingAheadOrders = aheadOrders.filter(orderMatchesTarget);
  const similarOrdersAhead = matchingAheadOrders.length;
  const ordersAhead = targetIndex === -1 ? aheadOrders.length : targetIndex;

  // Calculate total item quantity ahead
  let similarItemsAhead = 0;
  for (const o of matchingAheadOrders) {
    if (o.items && Array.isArray(o.items) && o.items.length > 0) {
      for (const it of o.items) {
        if (!hasSpecificItems ||
            (it.itemId && targetItemIds.has(it.itemId)) ||
            targetItemNames.has((it.name || '').toLowerCase().trim())) {
          similarItemsAhead += (it.quantity || 1);
        }
      }
    } else {
      similarItemsAhead += 1;
    }
  }

  // Group by studentId to prevent counting multiple orders from the same customer as separate people
  const distinctItemCustomersSet = new Set();
  for (const o of matchingAheadOrders) {
    if (o.studentId) distinctItemCustomersSet.add(o.studentId);
  }
  const distinctCustomersAhead = distinctItemCustomersSet.size;

  const totalCustomersSet = new Set();
  for (const o of aheadOrders) {
    if (o.studentId) totalCustomersSet.add(o.studentId);
  }
  const totalCustomersAhead = totalCustomersSet.size;

  // Realistic queue position:
  // For cart preview: scoped to the item queue (first for item = #1, or ahead item customers + 1)
  // For placed order: customer's position among active customers ahead in the canteen (User A with 4 orders is 1 customer ahead -> User B is #2)
  let queuePosition = 1;
  if (targetOrder.orderId === 'CART_PREVIEW' || targetOrder.orderId === 'CART_ITEM_PREVIEW') {
    queuePosition = similarOrdersAhead > 0 ? (distinctCustomersAhead > 0 ? distinctCustomersAhead + 1 : similarOrdersAhead + 1) : 1;
  } else {
    queuePosition = totalCustomersAhead > 0 ? totalCustomersAhead + 1 : (ordersAhead > 0 ? ordersAhead + 1 : 1);
  }

  // Calculate remaining preparation workload ahead for matching items
  let sumRemainingAhead = 0;
  for (let i = 0; i < matchingAheadOrders.length; i++) {
    const ahead = matchingAheadOrders[i];
    let aheadPrep = defaultWait;
    if (ahead.items && Array.isArray(ahead.items) && ahead.items.length > 0) {
      const matchingItemPreps = ahead.items
        .filter(it => {
          if (!hasSpecificItems) return true;
          if (it.itemId && targetItemIds.has(it.itemId)) return true;
          const n = (it.name || '').toLowerCase().trim();
          return n && targetItemNames.has(n);
        })
        .map(it => it.prepMinutes || defaultWait);
      if (matchingItemPreps.length > 0) {
        aheadPrep = Math.max(...matchingItemPreps);
      }
    } else {
      aheadPrep = getOrderPrepTime(ahead, defaultWait);
    }

    const aheadPlacedAt = ahead.orderPlacedAt || ahead.createdAt;
    const aheadCreated = aheadPlacedAt ? new Date(aheadPlacedAt).getTime() : now;
    const aheadElapsedMin = Math.max(0, (now - aheadCreated) / 60000);
    const aheadRemaining = Math.max(1, aheadPrep - aheadElapsedMin);
    sumRemainingAhead += aheadRemaining;
  }

  const capacity = kitchenCapacity || Math.min(4, Math.max(2, Math.floor(similarOrdersAhead / 6) + 2));
  let totalWait;
  if (similarOrdersAhead === 0) {
    totalWait = ownRemaining;
  } else if (similarOrdersAhead === 1) {
    totalWait = Math.round(sumRemainingAhead + ownTotalPrep);
  } else {
    const queueWaitAhead = Math.ceil(sumRemainingAhead / capacity);
    totalWait = Math.round(queueWaitAhead + ownTotalPrep);
  }

  const estWaitMinutes = Math.min(120, Math.max(1, totalWait));
  const completionDate = new Date(now + estWaitMinutes * 60000);
  const estimatedCompletionTime = formatTime12Hour(completionDate);
  const estimatedCompletionAt = completionDate.toISOString();

  let workloadSummary = '';
  if (similarOrdersAhead === 0) {
    workloadSummary = `0 ${hasSpecificItems ? primaryItemName + ' ' : ''}orders ahead`;
  } else if (similarOrdersAhead === 1) {
    workloadSummary = `1 ${hasSpecificItems ? primaryItemName + ' ' : ''}order ahead`;
  } else {
    workloadSummary = `${similarOrdersAhead} ${hasSpecificItems ? primaryItemName + ' ' : ''}orders ahead`;
  }

  const message = similarOrdersAhead === 0
    ? 'You are next in line'
    : `You are #${queuePosition} in line (${workloadSummary})`;

  return {
    ordersAhead,
    similarOrdersAhead,
    similarItemsAhead,
    distinctCustomersAhead,
    queuePosition,
    queueNumber: queuePosition,
    estWaitMinutes,
    estimatedCompletionTime,
    estimatedCompletionAt,
    primaryItemName,
    itemSummary,
    workloadSummary,
    message,
  };
}

// POST /api/orders/queue/cart - Get item-specific queue and wait time preview for Cart
router.post('/queue/cart', async (req, res) => {
  try {
    const { canteenId, items = [] } = req.body;
    if (!canteenId) {
      return res.status(400).json({ error: 'canteenId is required' });
    }

    const canteen = await Canteen.findOne({ id: canteenId }).select('avgWaitMinutes').lean();
    const defaultWait = (canteen && canteen.avgWaitMinutes) ? canteen.avgWaitMinutes : 7;
    const activeCutoff = new Date(Date.now() - 24 * 60 * 60 * 1000);

    const activeOrders = await Order.find({
      canteenId,
      status: { $in: ACTIVE_QUEUE_STATUSES },
      createdAt: { $gte: activeCutoff },
    })
      .sort({ orderPlacedAt: 1, createdAt: 1, orderId: 1 })
      .select('orderId studentId canteenId status createdAt orderPlacedAt confirmedAt preparingAt readyAt completedAt estimatedPrepMinutes items')
      .lean();

    // Auto-populate prepMinutes from Item collection if not provided in cart items
    let enrichedItems = items;
    if (Array.isArray(items) && items.length > 0) {
      const itemIds = items.map(it => it.itemId).filter(Boolean);
      const itemNames = items.map(it => it.name).filter(Boolean);
      const dbItems = await Item.find({
        $or: [
          { id: { $in: itemIds } },
          { name: { $in: itemNames } },
        ],
      }).select('id name prepMinutes').lean();
      const prepMapById = new Map(dbItems.map(d => [d.id, d.prepMinutes]));
      const prepMapByName = new Map(dbItems.map(d => [(d.name || '').toLowerCase().trim(), d.prepMinutes]));
      enrichedItems = items.map(it => ({
        ...it,
        prepMinutes: it.prepMinutes || prepMapById.get(it.itemId) || prepMapByName.get((it.name || '').toLowerCase().trim()) || defaultWait,
      }));
    }

    const mockCartOrder = {
      orderId: 'CART_PREVIEW',
      canteenId,
      items: enrichedItems,
      orderPlacedAt: new Date(),
      createdAt: new Date(),
    };

    const metrics = calculateQueueMetrics(mockCartOrder, activeOrders, defaultWait, 2);

    // Compute per-item breakdown
    const itemsQueue = (enrichedItems || []).map(cartItem => {
      const singleItemMock = {
        orderId: 'CART_ITEM_PREVIEW',
        canteenId,
        items: [cartItem],
        orderPlacedAt: new Date(),
        createdAt: new Date(),
      };
      const singleMetrics = calculateQueueMetrics(singleItemMock, activeOrders, defaultWait, 2);
      return {
        itemId: cartItem.itemId || '',
        name: cartItem.name || '',
        quantity: cartItem.quantity || 1,
        queuePosition: singleMetrics.queuePosition,
        similarOrdersAhead: singleMetrics.similarOrdersAhead,
        similarItemsAhead: singleMetrics.similarItemsAhead,
        estWaitMinutes: singleMetrics.estWaitMinutes,
        estimatedReadyTime: singleMetrics.estimatedCompletionTime,
      };
    });

    res.json({
      canteenId,
      primaryItemName: metrics.primaryItemName,
      queuePosition: metrics.queuePosition,
      queueNumber: metrics.queuePosition,
      ordersAhead: metrics.ordersAhead,
      similarOrdersAhead: metrics.similarOrdersAhead,
      similarItemsAhead: metrics.similarItemsAhead,
      distinctCustomersAhead: metrics.distinctCustomersAhead,
      estWaitMinutes: metrics.estWaitMinutes,
      estimatedCompletionTime: metrics.estimatedCompletionTime,
      estimatedCompletionAt: metrics.estimatedCompletionAt,
      headline: metrics.similarOrdersAhead === 0 ? "No Queue (You're First)" : `Queue #${metrics.queuePosition}`,
      workloadSummary: metrics.similarOrdersAhead === 0 ? '0 similar orders ahead' : `${metrics.similarOrdersAhead} similar order${metrics.similarOrdersAhead > 1 ? 's' : ''} ahead`,
      itemsQueue,
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// GET /api/orders/queue/item/:itemId - Direct single item queue preview
router.get('/queue/item/:itemId', async (req, res) => {
  try {
    const { itemId } = req.params;
    const canteenId = req.query.canteenId;
    if (!canteenId) return res.status(400).json({ error: 'canteenId query param required' });

    const item = await Item.findOne({ id: itemId }).lean();
    const itemName = item ? item.name : itemId;
    const prepMinutes = item?.prepMinutes || 7;

    const mockCartOrder = {
      orderId: 'CART_ITEM_PREVIEW',
      canteenId,
      items: [{ itemId, name: itemName, prepMinutes, quantity: 1 }],
      orderPlacedAt: new Date(),
      createdAt: new Date(),
    };

    const activeCutoff = new Date(Date.now() - 24 * 60 * 60 * 1000);
    const activeOrders = await Order.find({
      canteenId,
      status: { $in: ACTIVE_QUEUE_STATUSES },
      createdAt: { $gte: activeCutoff },
    })
      .sort({ orderPlacedAt: 1, createdAt: 1, orderId: 1 })
      .select('orderId studentId canteenId status createdAt orderPlacedAt confirmedAt preparingAt readyAt completedAt estimatedPrepMinutes items')
      .lean();

    const metrics = calculateQueueMetrics(mockCartOrder, activeOrders, prepMinutes, 2);
    res.json(metrics);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

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
    const firstItemName = targetOrder.items?.[0]?.name || 'Item';
    const singleItemSummary = targetOrder.items?.length === 1
      ? `${targetOrder.items[0].name} × ${targetOrder.items[0].quantity || 1}`
      : `${firstItemName}`;

    if (targetOrder.status === 'READY') {
      return res.json({
        orderId: targetOrder.orderId,
        tokenNumber: targetOrder.tokenNumber || '',
        status: 'READY',
        canteenId: targetOrder.canteenId,
        queuePosition: 0,
        queueNumber: 0,
        ordersAhead: 0,
        similarOrdersAhead: 0,
        similarItemsAhead: 0,
        distinctCustomersAhead: 0,
        primaryItemName: firstItemName,
        itemSummary: singleItemSummary,
        workloadSummary: '0 orders ahead',
        estWaitMinutes: 0,
        estimatedCompletionTime: 'Ready now',
        estimatedCompletionAt: new Date().toISOString(),
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
        similarOrdersAhead: 0,
        similarItemsAhead: 0,
        distinctCustomersAhead: 0,
        primaryItemName: firstItemName,
        itemSummary: singleItemSummary,
        workloadSummary: '0 orders ahead',
        estWaitMinutes: 0,
        estimatedCompletionTime: 'Completed',
        estimatedCompletionAt: new Date().toISOString(),
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
        similarOrdersAhead: 0,
        similarItemsAhead: 0,
        distinctCustomersAhead: 0,
        primaryItemName: firstItemName,
        itemSummary: singleItemSummary,
        workloadSummary: '0 orders ahead',
        estWaitMinutes: 0,
        estimatedCompletionTime: 'Cancelled',
        estimatedCompletionAt: new Date().toISOString(),
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

    // Compute dynamic, item-aware queue metrics with user deduplication and kitchen concurrency
    const metrics = calculateQueueMetrics(
      targetOrder,
      activeOrders,
      defaultWait,
      2
    );

    const {
      ordersAhead,
      similarOrdersAhead,
      similarItemsAhead,
      distinctCustomersAhead,
      queuePosition,
      queueNumber,
      estWaitMinutes,
      estimatedCompletionTime,
      estimatedCompletionAt,
      primaryItemName,
      itemSummary,
      workloadSummary,
      message,
    } = metrics;

    // Inspect/log detailed queue state for debugging as required
    console.log('=== [ITEM-AWARE QUEUE CALCULATION DEBUG] ===');
    console.log(`Current Order ID: ${targetOrder.orderId}`);
    console.log(`Current User ID: ${targetOrder.studentId || 'N/A'}`);
    console.log(`Current Canteen ID: ${targetOrder.canteenId}`);
    console.log(`Primary Item: ${primaryItemName}, Item Summary: ${itemSummary}`);
    console.log(`All active orders for canteen "${targetOrder.canteenId}" (${activeOrders.length}):`);
    activeOrders.forEach((o, i) => {
      console.log(`  [#${i + 1}] Order ID: ${o.orderId}, User: ${o.studentId}, Status: ${o.status}, Items: ${o.items?.map(it => it.name).join(', ')}`);
    });
    console.log(`Calculated item queue position: #${queuePosition}`);
    console.log(`Calculated similar orders ahead: ${similarOrdersAhead} (${workloadSummary})`);
    console.log(`Calculated distinct customers ahead: ${distinctCustomersAhead}`);
    console.log(`Calculated total orders ahead: ${ordersAhead}`);
    console.log(`Calculated estimated waiting time: ${estWaitMinutes} min (${estimatedCompletionTime})`);
    console.log('============================================');

    res.json({
      orderId: targetOrder.orderId,
      tokenNumber: targetOrder.tokenNumber || '',
      status: targetOrder.status,
      canteenId: targetOrder.canteenId,
      queuePosition,
      queueNumber: queuePosition,
      ordersAhead,
      similarOrdersAhead,
      similarItemsAhead,
      distinctCustomersAhead,
      primaryItemName,
      itemSummary,
      workloadSummary,
      estWaitMinutes,
      estimatedCompletionTime,
      estimatedCompletionAt,
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

    const initialStatus = (orderData.status || 'NEW').toUpperCase().trim();
    orderData.status = initialStatus;
    orderData.orderPlacedAt = now;
    orderData.confirmedAt = now;

    // Server-authoritative status history and milestone timestamps
    const statusHistory = [
      { status: 'PLACED', timestamp: now },
      { status: 'CONFIRMED', timestamp: now },
    ];
    if (initialStatus === 'PREPARING') {
      orderData.preparingAt = now;
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
