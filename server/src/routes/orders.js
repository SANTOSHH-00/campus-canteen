const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const mongoose = require('mongoose');
const Order = require('../models/Order');
const Canteen = require('../models/Canteen');
const Item = require('../models/Item');
const IdempotencyKey = require('../models/IdempotencyKey');
const { broadcastOrderStatus, broadcastNewOrder, broadcastQueueUpdate } = require('../services/socketService');
const { sendPushToUser } = require('../services/fcmService');
const { getIstDate, formatIstDateTime, formatIstTime, formatIstDate } = require('../utils/timeHelper');
const { createOrRotateOrderVerification, generateQrDataUrl } = require('../services/qrVerificationService');
const { ensureOrderBarcode, generateCode128BarcodeDataUrl } = require('../services/barcodeService');

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
 * Accurately calculate authoritative queue position, orders ahead, and estimated completion time
 * dynamically from:
 *   CURRENT SERVER TIME
 *   +
 *   TOTAL REMAINING PREPARATION TIME OF ACTIVE QUEUE ITEMS AHEAD
 *   +
 *   CURRENT ORDER'S REQUIRED PREPARATION TIME
 *
 * Scoped strictly to: canteenId + active orders in queue.
 */
function calculateQueueMetrics(targetOrder, activeOrders, defaultWait = 7, serverDate = new Date()) {
  const serverNow = serverDate instanceof Date ? serverDate : new Date(serverDate);
  const nowMs = serverNow.getTime();

  // Find target order's position among active orders
  const targetIndex = activeOrders.findIndex(o => o.orderId === targetOrder.orderId);
  const targetPlacedAt = targetOrder.orderPlacedAt || targetOrder.createdAt;
  const targetCreatedAtMs = targetPlacedAt ? new Date(targetPlacedAt).getTime() : nowMs;

  // Determine ahead orders in FIFO sequence (scoped strictly to this canteen)
  let aheadOrders = [];
  if (targetIndex === -1) {
    if (targetOrder.orderId === 'CART_PREVIEW' || targetOrder.orderId === 'CART_ITEM_PREVIEW') {
      aheadOrders = [...activeOrders];
    } else {
      aheadOrders = activeOrders.filter(o => {
        const oPlacedAt = o.orderPlacedAt || o.createdAt;
        const oTime = oPlacedAt ? new Date(oPlacedAt).getTime() : 0;
        return oTime < targetCreatedAtMs;
      });
    }
  } else {
    aheadOrders = activeOrders.slice(0, targetIndex);
  }

  // Count active orders ahead in canteen
  const ordersAhead = aheadOrders.length;
  const queuePosition = ordersAhead + 1;

  // Distinct customers ahead (do not count multiple orders from the same student as multiple customers)
  const distinctCustomerSet = new Set(
    aheadOrders.map(o => (o.studentId || '').trim().toLowerCase()).filter(id => id && id !== 'student_guest' && !id.includes('guest'))
  );
  const distinctCustomersAhead = distinctCustomerSet.size;

  // Item summaries
  const targetItems = targetOrder.items && Array.isArray(targetOrder.items) ? targetOrder.items : [];
  const primaryItemName = targetItems.length > 0 ? (targetItems[0].name || 'Item') : 'Item';
  const itemSummary = targetItems.length > 0
    ? (targetItems.length === 1
        ? `${targetItems[0].name} × ${targetItems[0].quantity || 1}`
        : `${targetItems[0].name} + ${targetItems.length - 1} other${targetItems.length > 2 ? 's' : ''}`)
    : 'Order Items';

  // Calculate similar items and orders ahead matching this specific food item
  const targetItemIds = new Set(targetItems.map(it => it.itemId).filter(Boolean));
  const targetItemNames = new Set(targetItems.map(it => (it.name || '').toLowerCase().trim()).filter(Boolean));

  let similarItemsAhead = 0;
  let similarOrdersAhead = 0;

  for (const ahead of aheadOrders) {
    let hasSimilarInOrder = false;
    if (Array.isArray(ahead.items)) {
      for (const aItem of ahead.items) {
        const aId = aItem.itemId;
        const aName = (aItem.name || '').toLowerCase().trim();
        const isMatch = (aId && targetItemIds.has(aId)) || (aName && targetItemNames.has(aName));
        if (isMatch) {
          similarItemsAhead += (aItem.quantity || 1);
          hasSimilarInOrder = true;
        }
      }
    }
    if (hasSimilarInOrder) {
      similarOrdersAhead++;
    }
  }

  // Calculate sum of remaining preparation time of active queue items ahead
  let totalRemainingAheadMinutes = 0;
  for (const ahead of aheadOrders) {
    const aheadPrep = getOrderPrepTime(ahead, defaultWait);
    const aheadPlacedAt = ahead.orderPlacedAt || ahead.createdAt;
    const aheadStartMs = aheadPlacedAt ? new Date(aheadPlacedAt).getTime() : nowMs;
    const elapsedMin = Math.max(0, (nowMs - aheadStartMs) / 60000);
    const remainingMin = Math.max(0, Math.round(aheadPrep - elapsedMin));
    totalRemainingAheadMinutes += remainingMin;
  }

  // Current order's required preparation time
  let currentOrderPrepMinutes = getOrderPrepTime(targetOrder, defaultWait);
  if (targetOrder.orderId !== 'CART_PREVIEW' && targetOrder.orderId !== 'CART_ITEM_PREVIEW') {
    if (targetOrder.status === 'PREPARING') {
      const targetStartMs = targetPlacedAt ? new Date(targetPlacedAt).getTime() : nowMs;
      const targetElapsedMin = Math.max(0, (nowMs - targetStartMs) / 60000);
      currentOrderPrepMinutes = Math.max(1, Math.round(currentOrderPrepMinutes - targetElapsedMin));
    }
  }

  // Authoritative total wait time = remaining prep time of orders ahead + current order prep time
  const totalWaitMinutes = Math.max(1, totalRemainingAheadMinutes + currentOrderPrepMinutes);

  // Authoritative estimated completion time based on server time
  const completionDate = new Date(nowMs + totalWaitMinutes * 60000);
  const estimatedCompletionTime = formatIstTime(completionDate);
  const estimatedCompletionAt = completionDate.toISOString();
  const currentServerTime = formatIstTime(serverNow);

  const workloadSummary = ordersAhead === 0
    ? '0 orders ahead'
    : (similarItemsAhead > 0
        ? `${similarItemsAhead} similar item${similarItemsAhead > 1 ? 's' : ''} ahead (${ordersAhead} order${ordersAhead > 1 ? 's' : ''})`
        : `${ordersAhead} order${ordersAhead > 1 ? 's' : ''} ahead (${distinctCustomersAhead} customer${distinctCustomersAhead > 1 ? 's' : ''})`);

  const headline = ordersAhead === 0
    ? "No queue ahead (You're First)"
    : `Queue #${queuePosition} (${workloadSummary})`;

  const message = ordersAhead === 0
    ? 'You are next in line'
    : `You are #${queuePosition} in line (${workloadSummary})`;

  return {
    ordersAhead,
    queuePosition,
    queueNumber: queuePosition,
    distinctCustomersAhead,
    similarItemsAhead,
    similarOrdersAhead,
    totalRemainingAheadMinutes,
    currentOrderPrepMinutes,
    estWaitMinutes: totalWaitMinutes,
    estimatedCompletionTime,
    estimatedCompletionAt,
    currentServerTime,
    primaryItemName,
    itemSummary,
    workloadSummary,
    headline,
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

    const serverNow = new Date();
    const canteen = await Canteen.findOne({ id: canteenId }).select('avgWaitMinutes').lean();
    const defaultWait = (canteen && canteen.avgWaitMinutes) ? canteen.avgWaitMinutes : 7;
    const activeCutoff = new Date(serverNow.getTime() - 24 * 60 * 60 * 1000);

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
      orderPlacedAt: serverNow,
      createdAt: serverNow,
    };

    const metrics = calculateQueueMetrics(mockCartOrder, activeOrders, defaultWait, serverNow);

    // Compute per-item breakdown
    const itemsQueue = (enrichedItems || []).map(cartItem => {
      const singleItemMock = {
        orderId: 'CART_ITEM_PREVIEW',
        canteenId,
        items: [cartItem],
        orderPlacedAt: serverNow,
        createdAt: serverNow,
      };
      const singleMetrics = calculateQueueMetrics(singleItemMock, activeOrders, defaultWait, serverNow);
      return {
        itemId: cartItem.itemId || '',
        name: cartItem.name || '',
        quantity: cartItem.quantity || 1,
        queuePosition: singleMetrics.queuePosition,
        similarOrdersAhead: singleMetrics.similarOrdersAhead,
        similarItemsAhead: singleMetrics.similarItemsAhead,
        distinctCustomersAhead: singleMetrics.distinctCustomersAhead,
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
      currentServerTime: metrics.currentServerTime,
      totalRemainingAheadMinutes: metrics.totalRemainingAheadMinutes,
      cartPrepMinutes: metrics.currentOrderPrepMinutes,
      headline: metrics.headline,
      workloadSummary: metrics.workloadSummary,
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

    const metrics = calculateQueueMetrics(mockCartOrder, activeOrders, prepMinutes, new Date());
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

// GET /api/orders/:id/qr - Dynamic QR Code retrieval & secure rotation for student order verification
router.get('/:id/qr', async (req, res) => {
  try {
    const targetOrder = await findOrderByIdOrToken(req.params.id);
    if (!targetOrder) {
      // Dynamic on-the-fly QR code generation for guest, local, or unpersisted orders
      const rawId = decodeURIComponent(req.params.id).trim();
      const numMatch = rawId.replace(/^[^0-9]+/g, '');
      const tokenNum = numMatch ? numMatch.slice(-4) : '101';
      const syntheticToken = `QB-${crypto.createHash('md5').update(rawId).digest('hex').substring(0, 16).toUpperCase()}`;
      let qrDataUrl = '';
      try {
        qrDataUrl = await generateQrDataUrl(syntheticToken);
      } catch (_) {}

      return res.json({
        success: true,
        code: 'QR_ACTIVE',
        orderId: rawId,
        tokenNumber: tokenNum,
        canteenId: 'canteen_33',
        pickupCanteenName: 'Campus Canteen',
        pickupCounter: 'Counter 1',
        status: 'NEW',
        qrDataUrl: qrDataUrl,
        rawToken: syntheticToken,
        expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
        validityMinutes: 1440,
      });
    }

    if (targetOrder.status === 'CANCELLED') {
      return res.status(400).json({
        success: false,
        code: 'ORDER_CANCELLED',
        error: 'Order has been cancelled.',
        status: 'CANCELLED',
      });
    }

    if (targetOrder.status === 'PICKED_UP' || targetOrder.status === 'COMPLETED') {
      return res.status(200).json({
        success: true,
        code: 'ALREADY_REDEEMED',
        message: 'Order has already been picked up.',
        orderId: targetOrder.orderId,
        tokenNumber: targetOrder.tokenNumber,
        status: targetOrder.status,
        pickedUpAt: targetOrder.pickedUpAt || targetOrder.completedAt,
        pickedUpAtLocal: targetOrder.pickedUpAtLocal || targetOrder.completedAtLocal,
      });
    }

    // Generate or rotate verification token atomically without storing plaintext tokens in DB
    const verification = await createOrRotateOrderVerification({
      orderId: targetOrder.orderId,
      studentId: targetOrder.studentId,
      canteenId: targetOrder.canteenId,
    });

    res.json({
      success: true,
      code: 'QR_ACTIVE',
      orderId: targetOrder.orderId,
      tokenNumber: targetOrder.tokenNumber,
      canteenId: targetOrder.canteenId,
      pickupCanteenName: targetOrder.pickupCanteenName || '',
      pickupCounter: targetOrder.pickupCounter || 'Counter 1',
      status: targetOrder.status,
      qrDataUrl: verification.qrDataUrl,
      rawToken: verification.rawToken,
      expiresAt: verification.expiresAt,
      validityMinutes: verification.validityMinutes,
    });
  } catch (err) {
    console.error('[Orders] Error retrieving QR verification:', err);
    res.status(500).json({
      success: false,
      code: 'SERVER_ERROR',
      error: 'Failed to retrieve order verification QR code.',
    });
  }
});

// GET /api/orders/:id/barcode - Dynamic Code 128 Barcode retrieval & display for student order tracking
router.get('/:id/barcode', async (req, res) => {
  try {
    const targetOrder = await findOrderByIdOrToken(req.params.id);
    if (!targetOrder) {
      // Dynamic on-the-fly Code 128 barcode generation for guest, local, or unpersisted orders
      const rawId = decodeURIComponent(req.params.id).trim();
      const numMatch = rawId.replace(/^[^0-9]+/g, '');
      const tokenNum = numMatch ? numMatch.slice(-4) : '101';
      const syntheticToken = `QB-${crypto.createHash('md5').update(rawId).digest('hex').substring(0, 16).toUpperCase()}`;
      let barcodeDataUrl = '';
      try {
        barcodeDataUrl = await generateCode128BarcodeDataUrl(syntheticToken);
      } catch (_) {}

      const now = new Date();
      return res.json({
        success: true,
        code: 'BARCODE_ACTIVE',
        orderId: rawId,
        tokenNumber: tokenNum,
        verificationToken: syntheticToken,
        barcodeFormat: 'CODE128',
        barcodeStatus: 'ACTIVE',
        barcodeDataUrl: barcodeDataUrl,
        status: 'NEW',
        orderDate: formatIstDate(now),
        orderTime: formatIstTime(now),
        orderPlacedAtLocal: `${formatIstDate(now)}, ${formatIstTime(now)}`,
        items: [],
        totalAmount: 0,
        canteenId: 'canteen_33',
        pickupCanteenName: 'Campus Canteen',
        pickupCounter: 'Counter 1',
        instruction: 'Show this barcode to the canteen owner when collecting your order.',
        collectedAt: null,
        collectedAtLocal: null,
        collectedBy: null,
      });
    }

    if (targetOrder.status === 'CANCELLED') {
      return res.status(400).json({
        success: false,
        code: 'ORDER_CANCELLED',
        error: 'Order has been cancelled.',
        status: 'CANCELLED',
      });
    }

    const isCollected = targetOrder.status === 'PICKED_UP' || targetOrder.status === 'COMPLETED' || targetOrder.barcodeStatus === 'USED';

    // Retrieve or generate persistent Code 128 barcode
    let barcodeData = null;
    if (!isCollected) {
      barcodeData = await ensureOrderBarcode(targetOrder);
    }

    const orderDate = targetOrder.orderDate || formatIstDate(targetOrder.orderPlacedAt || targetOrder.createdAt);
    const orderTime = targetOrder.orderTime || formatIstTime(targetOrder.orderPlacedAt || targetOrder.createdAt);

    return res.json({
      success: true,
      code: isCollected ? 'ALREADY_COLLECTED' : 'BARCODE_ACTIVE',
      orderId: targetOrder.orderId,
      tokenNumber: targetOrder.tokenNumber,
      verificationToken: isCollected ? (targetOrder.verificationToken || '') : (barcodeData?.token || targetOrder.verificationToken || ''),
      barcodeFormat: targetOrder.barcodeFormat || 'CODE128',
      barcodeStatus: isCollected ? 'USED' : (targetOrder.barcodeStatus || 'ACTIVE'),
      barcodeDataUrl: isCollected ? (targetOrder.barcodeDataUrl || '') : (barcodeData?.barcodeDataUrl || targetOrder.barcodeDataUrl || ''),
      status: targetOrder.status,
      orderDate: orderDate,
      orderTime: orderTime,
      orderPlacedAtLocal: targetOrder.orderPlacedAtLocal || `${orderDate}, ${orderTime}`,
      items: targetOrder.items || [],
      totalAmount: targetOrder.totalAmount,
      canteenId: targetOrder.canteenId,
      pickupCanteenName: targetOrder.pickupCanteenName || '',
      pickupCounter: targetOrder.pickupCounter || 'Counter 1',
      instruction: 'Show this barcode to the canteen owner when collecting your order.',
      collectedAt: targetOrder.collectedAt || targetOrder.pickedUpAt,
      collectedAtLocal: targetOrder.pickedUpAtLocal || targetOrder.completedAtLocal,
      collectedBy: targetOrder.collectedBy || targetOrder.verifiedByOwnerId,
    });
  } catch (err) {
    console.error('[Orders] Error retrieving barcode verification:', err);
    res.status(500).json({
      success: false,
      code: 'SERVER_ERROR',
      error: 'Failed to retrieve order verification barcode.',
    });
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

    // Derive local human-readable time strings for frontend consumption
    const orderDate = targetOrder.orderDate || (orderPlacedAt ? formatIstDate(orderPlacedAt) : '');
    const orderTime = targetOrder.orderTime || (orderPlacedAt ? formatIstTime(orderPlacedAt) : '');
    const orderPlacedAtLocal = targetOrder.orderPlacedAtLocal || (orderPlacedAt ? formatIstDateTime(orderPlacedAt) : '');
    const confirmedAtLocal = targetOrder.confirmedAtLocal || (confirmedAt ? formatIstDateTime(confirmedAt) : '');
    const preparingAtLocal = targetOrder.preparingAtLocal || (preparingAt ? formatIstDateTime(preparingAt) : '');
    const readyAtLocal = targetOrder.readyAtLocal || (readyAt ? formatIstDateTime(readyAt) : '');
    const completedAtLocal = targetOrder.completedAtLocal || (completedAt ? formatIstDateTime(completedAt) : '');
    const cancelledAtLocal = targetOrder.cancelledAtLocal || (cancelledAt ? formatIstDateTime(cancelledAt) : '');

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
        orderDate,
        orderTime,
        orderPlacedAtLocal,
        confirmedAtLocal,
        preparingAtLocal,
        readyAtLocal,
        completedAtLocal,
        cancelledAtLocal,
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
        orderDate,
        orderTime,
        orderPlacedAtLocal,
        confirmedAtLocal,
        preparingAtLocal,
        readyAtLocal,
        completedAtLocal,
        cancelledAtLocal,
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
        orderDate,
        orderTime,
        orderPlacedAtLocal,
        confirmedAtLocal,
        preparingAtLocal,
        readyAtLocal,
        completedAtLocal,
        cancelledAtLocal,
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

    const serverNow = new Date();
    // Compute dynamic, server-authoritative queue metrics
    const metrics = calculateQueueMetrics(
      targetOrder,
      activeOrders,
      defaultWait,
      serverNow
    );

    const {
      ordersAhead,
      similarOrdersAhead,
      similarItemsAhead,
      distinctCustomersAhead,
      queuePosition,
      queueNumber,
      totalRemainingAheadMinutes,
      currentOrderPrepMinutes,
      estWaitMinutes,
      estimatedCompletionTime,
      estimatedCompletionAt,
      currentServerTime,
      primaryItemName,
      itemSummary,
      workloadSummary,
      message,
    } = metrics;

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
      totalRemainingAheadMinutes,
      currentOrderPrepMinutes,
      primaryItemName,
      itemSummary,
      workloadSummary,
      estWaitMinutes,
      estimatedCompletionTime,
      estimatedCompletionAt,
      currentServerTime,
      customerPickupTime: targetOrder.customerPickupTime || '',
      pickupPreference: targetOrder.pickupPreference || '',
      message,
      orderDate,
      orderTime,
      orderPlacedAtLocal,
      confirmedAtLocal,
      preparingAtLocal,
      readyAtLocal,
      completedAtLocal,
      cancelledAtLocal,
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

// GET /api/orders/student/:studentId - List orders for student with optional filters
router.get('/student/:studentId', async (req, res) => {
  try {
    const { canteenId, dateOption, startDate, endDate } = req.query;
    const filter = { studentId: req.params.studentId };

    if (canteenId) {
      if (canteenId.includes(',')) {
        filter.canteenId = { $in: canteenId.split(',').map(c => c.trim()).filter(Boolean) };
      } else {
        filter.canteenId = canteenId.trim();
      }
    }

    // Date filtering (aligned with Indian Standard Time / local campus midnight boundaries)
    const now = new Date();
    const IST_OFFSET_MS = 5.5 * 60 * 60 * 1000;
    const istNow = new Date(now.getTime() + IST_OFFSET_MS);

    const istTodayStart = new Date(Date.UTC(istNow.getUTCFullYear(), istNow.getUTCMonth(), istNow.getUTCDate()));
    const todayStartUtc = new Date(istTodayStart.getTime() - IST_OFFSET_MS);
    const todayEndUtc = new Date(todayStartUtc.getTime() + 24 * 60 * 60 * 1000 - 1);

    if (dateOption) {
      const opt = dateOption.toUpperCase().trim();
      if (opt === 'TODAY') {
        filter.$or = [
          { orderPlacedAt: { $gte: todayStartUtc, $lte: todayEndUtc } },
          { orderPlacedAt: null, createdAt: { $gte: todayStartUtc, $lte: todayEndUtc } }
        ];
      } else if (opt === 'YESTERDAY') {
        const yesterdayStartUtc = new Date(todayStartUtc.getTime() - 24 * 60 * 60 * 1000);
        const yesterdayEndUtc = new Date(todayStartUtc.getTime() - 1);
        filter.$or = [
          { orderPlacedAt: { $gte: yesterdayStartUtc, $lte: yesterdayEndUtc } },
          { orderPlacedAt: null, createdAt: { $gte: yesterdayStartUtc, $lte: yesterdayEndUtc } }
        ];
      } else if (opt === 'LAST_7_DAYS' || opt === '7DAYS') {
        const sevenDaysAgoStart = new Date(todayStartUtc.getTime() - 6 * 24 * 60 * 60 * 1000);
        filter.$or = [
          { orderPlacedAt: { $gte: sevenDaysAgoStart, $lte: todayEndUtc } },
          { orderPlacedAt: null, createdAt: { $gte: sevenDaysAgoStart, $lte: todayEndUtc } }
        ];
      } else if (opt === 'LAST_30_DAYS' || opt === '30DAYS') {
        const thirtyDaysAgoStart = new Date(todayStartUtc.getTime() - 29 * 24 * 60 * 60 * 1000);
        filter.$or = [
          { orderPlacedAt: { $gte: thirtyDaysAgoStart, $lte: todayEndUtc } },
          { orderPlacedAt: null, createdAt: { $gte: thirtyDaysAgoStart, $lte: todayEndUtc } }
        ];
      }
    } else if (startDate || endDate) {
      const dateCondition = {};
      if (startDate) {
        const s = new Date(startDate);
        if (!isNaN(s.getTime())) dateCondition.$gte = s;
      }
      if (endDate) {
        const e = new Date(endDate);
        if (!isNaN(e.getTime())) dateCondition.$lte = e;
      }
      if (Object.keys(dateCondition).length > 0) {
        filter.$or = [
          { orderPlacedAt: dateCondition },
          { orderPlacedAt: null, createdAt: dateCondition }
        ];
      }
    }

    const orders = await Order.find(filter)
      .sort({ createdAt: -1 })
      .lean();
    res.json(orders);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// GET /api/orders/canteen/:canteenId - List orders for canteen (excluding guest orders)
router.get('/canteen/:canteenId', async (req, res) => {
  try {
    const orders = await Order.find({
      canteenId: req.params.canteenId,
      studentId: { $ne: 'student_guest', $not: /guest/i },
      studentName: { $not: /guest/i },
    })
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

// POST /api/orders - Place a new order with authoritative server timestamps and idempotency
router.post('/', async (req, res) => {
  const idempotencyKey = req.headers['idempotency-key'] || req.body?.idempotencyKey;
  const rawStudentId = (req.body?.studentId || '').toLowerCase().trim();

  // 1. Check & reserve Idempotency Key atomically if provided
  if (idempotencyKey) {
    try {
      const existingKey = await IdempotencyKey.findOneAndUpdate(
        { key: idempotencyKey },
        { $setOnInsert: { key: idempotencyKey, studentId: rawStudentId || 'unknown', inProgress: true } },
        { upsert: true, new: false }
      );

      if (existingKey) {
        if (existingKey.inProgress) {
          return res.status(409).json({
            error: 'Order is currently being processed. Please wait.',
            status: 'PROCESSING',
          });
        }
        return res.status(existingKey.statusCode || 200).json(existingKey.responseBody);
      }
    } catch (err) {
      // Catch unique index race condition
      const stored = await IdempotencyKey.findOne({ key: idempotencyKey }).lean();
      if (stored && !stored.inProgress) {
        return res.status(stored.statusCode || 200).json(stored.responseBody);
      }
      return res.status(409).json({ error: 'Order request in flight. Please wait.' });
    }
  }

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

    // Determine exact orderPlacedAt: prioritize client placed timestamp or embedded orderId timestamp
    let placedDate = now;
    if (orderData.orderPlacedAt) {
      const parsed = new Date(orderData.orderPlacedAt);
      if (!isNaN(parsed.getTime())) placedDate = parsed;
    } else if (orderData.orderId) {
      const match = orderData.orderId.match(/ORD-(\d{10,14})(?:-\d+)?/);
      if (match && match[1]) {
        const parsed = new Date(parseInt(match[1], 10));
        if (!isNaN(parsed.getTime())) placedDate = parsed;
      }
    }

    const studentId = (orderData.studentId || '').toLowerCase().trim();
    const studentName = (orderData.studentName || '').toLowerCase().trim();
    const isGuest = studentId === 'student_guest' || studentId.includes('guest') || studentName.includes('guest');

    // DO NOT save guest orders to the database or show them on the owner's section
    if (isGuest) {
      console.log(`[Orders] Skipping DB persistence for guest order (${orderData.orderId}, studentId: ${studentId})`);
      return res.status(201).json({
        ...orderData,
        orderId: orderData.orderId,
        status: (orderData.status || 'NEW').toUpperCase().trim(),
        orderPlacedAt: placedDate,
        isGuest: true,
        message: 'Guest order processed locally, not saved to database.'
      });
    }

    const initialStatus = (orderData.status || 'NEW').toUpperCase().trim();
    orderData.status = initialStatus;

    // The backend/server is the sole authority of time: generate true UTC Date
    orderData.orderPlacedAt = now;
    orderData.orderDate = formatIstDate(now);
    orderData.orderTime = formatIstTime(now);
    orderData.orderPlacedAtLocal = formatIstDateTime(now);

    // ConfirmedAt
    orderData.confirmedAt = now;
    orderData.confirmedAtLocal = formatIstDateTime(now);

    // Server-authoritative status history and milestone timestamps
    const statusHistory = [
      { status: 'PLACED', timestamp: now, timestampLocal: formatIstDateTime(now), changedBy: studentId || 'STUDENT', changedByRole: 'STUDENT' },
      { status: 'CONFIRMED', timestamp: now, timestampLocal: formatIstDateTime(now), changedBy: 'SYSTEM', changedByRole: 'SYSTEM' },
    ];
    if (initialStatus === 'PREPARING') {
      orderData.preparingAt = now;
      orderData.preparingAtLocal = formatIstDateTime(now);
      statusHistory.push({ status: 'PREPARING', timestamp: now, timestampLocal: formatIstDateTime(now), changedBy: 'SYSTEM', changedByRole: 'SYSTEM' });
    }
    orderData.statusHistory = statusHistory;

    // Snapshot item subtotals for immutable historical accuracy
    if (orderData.items && Array.isArray(orderData.items)) {
      orderData.items.forEach(it => {
        it.subtotal = (it.price || 0) * (it.quantity || 1);
      });
    }

    // Customer Pickup Time: preserve user's explicit selection
    let customerPickup = (orderData.customerPickupTime || '').trim();
    if (!customerPickup && orderData.pickupPreference) {
      if (/pickup\s+at\s+/i.test(orderData.pickupPreference)) {
        customerPickup = orderData.pickupPreference.replace(/^pickup\s+at\s+/i, '').trim();
      } else if (/pickup\s+asap/i.test(orderData.pickupPreference)) {
        customerPickup = 'ASAP';
      }
    }
    if (!customerPickup) {
      customerPickup = orderData.isPickupAsap === false ? '' : 'ASAP';
    }
    orderData.customerPickupTime = customerPickup;

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

    const canteen = await Canteen.findOne({ id: orderData.canteenId }).select('avgWaitMinutes').lean();
    const defaultWait = (canteen && canteen.avgWaitMinutes) ? canteen.avgWaitMinutes : 7;
    if (!orderData.estimatedPrepMinutes || orderData.estimatedPrepMinutes <= 0) {
      orderData.estimatedPrepMinutes = getOrderPrepTime(orderData, defaultWait);
    }

    // Authoritative Queue Calculation for this new order
    const activeCutoff = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    const activeOrders = await Order.find({
      canteenId: orderData.canteenId,
      status: { $in: ACTIVE_QUEUE_STATUSES },
      createdAt: { $gte: activeCutoff },
    })
      .sort({ orderPlacedAt: 1, createdAt: 1, orderId: 1 })
      .select('orderId studentId canteenId status createdAt orderPlacedAt confirmedAt preparingAt readyAt completedAt estimatedPrepMinutes items')
      .lean();

    const metrics = calculateQueueMetrics(orderData, activeOrders, defaultWait, now);
    orderData.estimatedCompletionTime = metrics.estimatedCompletionTime;
    orderData.estimatedReadyTime = metrics.estimatedCompletionTime;

    const order = await Order.create(orderData);

    // Dynamic Code 128 Barcode generation stored securely in MongoDB
    let barcodePayload = null;
    try {
      barcodePayload = await ensureOrderBarcode(order);
    } catch (bErr) {
      console.warn('[Orders] Barcode generation warning:', bErr.message);
    }

    // Dynamic QR Verification Token generation (persisting only SHA-256 hash in MongoDB)
    let qrPayload = null;
    try {
      qrPayload = await createOrRotateOrderVerification({
        orderId: order.orderId,
        studentId: order.studentId,
        canteenId: order.canteenId,
      });
    } catch (qrErr) {
      console.warn('[Orders] Verification QR generation warning:', qrErr.message);
    }

    const responseOrder = order.toObject ? order.toObject() : { ...order };
    if (barcodePayload) {
      responseOrder.verificationToken = barcodePayload.token;
      responseOrder.barcodeFormat = barcodePayload.format;
      responseOrder.barcodeStatus = barcodePayload.barcodeStatus;
      responseOrder.barcodeDataUrl = barcodePayload.barcodeDataUrl;
      responseOrder.expiresAt = barcodePayload.expiresAt;
    }
    if (qrPayload) {
      responseOrder.qrDataUrl = qrPayload.qrDataUrl;
      if (!responseOrder.verificationToken) {
        responseOrder.verificationToken = qrPayload.rawToken;
      }
      responseOrder.verificationExpiresAt = qrPayload.expiresAt;
    }

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

    // Save final response in Idempotency store
    if (idempotencyKey) {
      await IdempotencyKey.updateOne(
        { key: idempotencyKey },
        { $set: { inProgress: false, statusCode: 201, responseBody: responseOrder } }
      ).catch(() => {});
    }

    res.status(201).json(responseOrder);
  } catch (err) {
    if (idempotencyKey) {
      await IdempotencyKey.deleteOne({ key: idempotencyKey }).catch(() => {});
    }
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

    // Prevent invalid transitions: reject transitions out of terminal states (COMPLETED, CANCELLED, REJECTED)
    const currentStatus = (targetOrder.status || '').toUpperCase().trim();
    if (['COMPLETED', 'CANCELLED', 'REJECTED'].includes(currentStatus)) {
      if (currentStatus !== normalizedStatus) {
        return res.status(400).json({
          error: `Invalid transition: Order ${targetOrder.orderId} is already in terminal state "${currentStatus}".`,
          currentStatus: currentStatus,
        });
      }
    }

    const changedBy = req.body.changedBy || req.headers['x-owner-id'] || 'OWNER';
    const changedByRole = req.body.changedByRole || 'OWNER';

    const now = new Date();
    const nowLocal = formatIstDateTime(now);

    const updateFields = {};
    if (normalizedStatus) {
      updateFields.status = normalizedStatus;
    }
    if (req.body.estimatedPrepMinutes) {
      updateFields.estimatedPrepMinutes = Number(req.body.estimatedPrepMinutes);
    }

    if (normalizedStatus === 'CONFIRMED') {
      updateFields.confirmedAt = now;
      updateFields.confirmedAtLocal = nowLocal;
    }
    if (normalizedStatus === 'PREPARING') {
      updateFields.preparingAt = now;
      updateFields.preparingAtLocal = nowLocal;
    }
    if (normalizedStatus === 'READY') {
      updateFields.readyAt = now;
      updateFields.readyAtLocal = nowLocal;
      updateFields.estimatedCompletionTime = 'Ready now';
      updateFields.estimatedReadyTime = 'Ready now';
    }
    if (normalizedStatus === 'COMPLETED') {
      updateFields.completedAt = now;
      updateFields.completedAtLocal = nowLocal;
      updateFields.estimatedCompletionTime = 'Completed';
      updateFields.estimatedReadyTime = 'Completed';
    }
    if (normalizedStatus === 'CANCELLED') {
      updateFields.cancelledAt = now;
      updateFields.cancelledAtLocal = nowLocal;
      updateFields.estimatedCompletionTime = 'Cancelled';
      updateFields.estimatedReadyTime = 'Cancelled';
    }

    const pushUpdate = normalizedStatus
      ? { $push: { statusHistory: { status: normalizedStatus, timestamp: now, timestampLocal: nowLocal, changedBy, changedByRole } } }
      : {};

    const updated = await Order.findOneAndUpdate(
      { _id: targetOrder._id },
      {
        $set: updateFields,
        ...pushUpdate,
      },
      { new: true }
    );

    // Real-time WebSocket Broadcast & Push Trigger
    try {
      broadcastOrderStatus(updated);
      if (updated.canteenId) {
        broadcastQueueUpdate(updated.canteenId);
      }

      // Only send push notifications for PREPARING, READY, COMPLETED, CANCELLED
      const shouldNotify = ['PREPARING', 'READY', 'COMPLETED', 'CANCELLED'].includes(normalizedStatus);
      const isGuest = (updated.studentId || '').toLowerCase().includes('guest') || (updated.studentName || '').toLowerCase().includes('guest');
      if (!isGuest && shouldNotify && updated.studentId) {
        // Fetch canteen details for kitchen name and block number
        const canteen = await Canteen.findOne({ id: updated.canteenId }).lean();
        const canteenName = (canteen && canteen.name) || updated.pickupCanteenName || 'Canteen Kitchen';
        const rawBlock = (canteen && (canteen.block || canteen.location)) || updated.pickupLocation || '';
        const blockNumber = rawBlock.replace(/block\s*/i, '').trim();
        const blockDisplay = blockNumber ? `Block ${blockNumber}` : (rawBlock || 'Campus');
        const counter = updated.pickupCounter || 'Counter 1';
        const cleanToken = (updated.tokenNumber || updated.orderId.slice(-4)).replace(/^#+/, '');

        const statusTitle = normalizedStatus === 'PREPARING'
          ? `Order #${cleanToken}: Preparing`
          : normalizedStatus === 'READY'
          ? `Order #${cleanToken}: Ready for Pickup`
          : normalizedStatus === 'COMPLETED'
          ? `Order #${cleanToken}: Picked Up`
          : `Order #${cleanToken}: Cancelled`;

        const statusBody = normalizedStatus === 'PREPARING'
          ? `Your meal is being prepared by ${canteenName} at ${blockDisplay}, ${counter}.`
          : normalizedStatus === 'READY'
          ? `Your order is ready! Please collect your food at ${counter} from ${canteenName} at ${blockDisplay}.`
          : normalizedStatus === 'COMPLETED'
          ? 'Your order has been picked up. Enjoy your food!'
          : 'Your order has been cancelled by the canteen kitchen.';

        // Authoritative Database Sync: Save notification in MongoDB at this exact moment
        const Notification = require('../models/Notification');
        await Notification.findOneAndUpdate(
          { notificationId: `NOTIF-${updated.orderId}-${normalizedStatus}` },
          {
            $set: {
              notificationId: `NOTIF-${updated.orderId}-${normalizedStatus}`,
              userId: updated.studentId,
              title: statusTitle,
              message: statusBody,
              type: 'ORDER_STATUS',
              orderId: updated.orderId,
              canteenId: updated.canteenId,
              read: false,
              createdAt: istNow,
            }
          },
          { upsert: true, new: true }
        ).catch(err => console.warn('[Orders] Notification persist warning:', err.message));

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
