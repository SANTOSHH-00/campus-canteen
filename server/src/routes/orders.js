// Order Management Routes: FIFO queue calculation, Code 128 barcode & QR generation, order placement, and status tracking
const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const mongoose = require('mongoose');
const Order = require('../models/Order');
const Canteen = require('../models/Canteen');
const Item = require('../models/Item');
const User = require('../models/User');
const IdempotencyKey = require('../models/IdempotencyKey');
const { broadcastOrderStatus, broadcastNewOrder, broadcastQueueUpdate } = require('../services/socketService');
const { sendPushToUser } = require('../services/fcmService');
const { getIstDate, formatIstDateTime, formatIstTime, formatIstDate } = require('../utils/timeHelper');
const { createOrRotateOrderVerification, generateQrDataUrl } = require('../services/qrVerificationService');
const { ensureOrderBarcode, generateCode128BarcodeDataUrl } = require('../services/barcodeService');
const { processRefund } = require('./payments');

const ACTIVE_QUEUE_STATUSES = ['PENDING', 'ACCEPTED', 'NEW', 'WAITING', 'CONFIRMED', 'PREPARING', 'pending', 'accepted', 'new', 'waiting', 'confirmed', 'preparing'];

/**
 * Validates a scheduled pickup time string against current campus time and canteen operating hours.
 */
function validatePickupSchedule(pickupTimeStr, canteenTimings, now = new Date()) {
  if (!pickupTimeStr || /asap/i.test(pickupTimeStr)) {
    return { valid: true };
  }

  const match = pickupTimeStr.match(/(\d{1,2}):(\d{2})\s*(AM|PM)/i);
  if (!match) {
    return { valid: true }; // non-standard string, allow gracefully
  }

  let hours = parseInt(match[1], 10);
  const minutes = parseInt(match[2], 10);
  const ampm = match[3].toUpperCase();
  if (ampm === 'PM' && hours < 12) hours += 12;
  if (ampm === 'AM' && hours === 12) hours = 0;

  // Convert current time to IST minutes since midnight
  const istNow = new Date(now.getTime() + (5.5 * 60 * 60 * 1000));
  const nowHours = istNow.getUTCHours();
  const nowMinutes = istNow.getUTCMinutes();
  const requestedTotalMinutes = hours * 60 + minutes;
  const currentTotalMinutes = nowHours * 60 + nowMinutes;

  // Do not allow times in the past (with a 2-minute clock skew grace window)
  if (requestedTotalMinutes < currentTotalMinutes - 2) {
    return {
      valid: false,
      error: `Requested pickup time (${pickupTimeStr}) cannot be in the past. Please select an upcoming pickup time.`,
    };
  }

  // Validate against canteen closing time (e.g., "7:00 AM – 10:00 PM")
  if (canteenTimings) {
    const parts = canteenTimings.split(/[–\-—]/);
    if (parts.length >= 2) {
      const closingStr = parts[1].trim();
      const closeMatch = closingStr.match(/(\d{1,2}):(\d{2})\s*(AM|PM)/i);
      if (closeMatch) {
        let closeHours = parseInt(closeMatch[1], 10);
        const closeMinutes = parseInt(closeMatch[2], 10);
        if (closeMatch[3].toUpperCase() === 'PM' && closeHours < 12) closeHours += 12;
        if (closeMatch[3].toUpperCase() === 'AM' && closeHours === 12) closeHours = 0;
        const closeTotalMinutes = closeHours * 60 + closeMinutes;

        if (requestedTotalMinutes > closeTotalMinutes) {
          return {
            valid: false,
            error: `Requested pickup time (${pickupTimeStr}) is after the canteen closes at ${closingStr}.`,
          };
        }
      }
    }
  }

  return { valid: true };
}

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

  let order = await Order.findOne({ orderId: cleanId }).lean();
  if (order) return order;

  if (mongoose.Types.ObjectId.isValid(cleanId)) {
    order = await Order.findOne({ _id: cleanId }).lean();
    if (order) return order;
  }

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

  const ordersAhead = aheadOrders.length;
  const queuePosition = ordersAhead + 1;

  const distinctCustomerSet = new Set(
    aheadOrders.map(o => (o.studentId || '').trim().toLowerCase()).filter(id => id && id !== 'student_guest' && !id.includes('guest'))
  );
  const distinctCustomersAhead = distinctCustomerSet.size;

  const targetItems = targetOrder.items && Array.isArray(targetOrder.items) ? targetOrder.items : [];
  const primaryItemName = targetItems.length > 0 ? (targetItems[0].name || 'Item') : 'Item';
  const itemSummary = targetItems.length > 0
    ? (targetItems.length === 1
        ? `${targetItems[0].name} × ${targetItems[0].quantity || 1}`
        : `${targetItems[0].name} + ${targetItems.length - 1} other${targetItems.length > 2 ? 's' : ''}`)
    : 'Order Items';

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

  let totalRemainingAheadMinutes = 0;
  for (const ahead of aheadOrders) {
    const aheadPrep = getOrderPrepTime(ahead, defaultWait);
    const aheadPlacedAt = ahead.orderPlacedAt || ahead.createdAt;
    const aheadStartMs = aheadPlacedAt ? new Date(aheadPlacedAt).getTime() : nowMs;
    const elapsedMin = Math.max(0, (nowMs - aheadStartMs) / 60000);
    const remainingMin = Math.max(0, Math.round(aheadPrep - elapsedMin));
    totalRemainingAheadMinutes += remainingMin;
  }

  let currentOrderPrepMinutes = getOrderPrepTime(targetOrder, defaultWait);
  if (targetOrder.orderId !== 'CART_PREVIEW' && targetOrder.orderId !== 'CART_ITEM_PREVIEW') {
    if (targetOrder.status === 'PREPARING') {
      const targetStartMs = targetPlacedAt ? new Date(targetPlacedAt).getTime() : nowMs;
      const targetElapsedMin = Math.max(0, (nowMs - targetStartMs) / 60000);
      currentOrderPrepMinutes = Math.max(1, Math.round(currentOrderPrepMinutes - targetElapsedMin));
    }
  }

  const totalWaitMinutes = Math.max(1, totalRemainingAheadMinutes + currentOrderPrepMinutes);

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
// Cart Queue Preview: Computes estimated wait time and items ahead for items currently in cart
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
// Live Canteen Queue Depth: Retrieves active queue count and average preparation time
router.get('/queue/canteen/:canteenId', async (req, res) => {
  try {
    const { canteenId } = req.params;
    const canteen = await Canteen.findOne({ id: canteenId }).select('avgWaitMinutes').lean();
    const defaultWait = (canteen && canteen.avgWaitMinutes) ? canteen.avgWaitMinutes : 5;

    const activeCutoff = new Date(Date.now() - 24 * 60 * 60 * 1000);

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
// Dynamic QR Code: Generates and returns rotating QR verification token for student order
router.get('/:id/qr', async (req, res) => {
  try {
    const targetOrder = await findOrderByIdOrToken(req.params.id);
    if (!targetOrder) {
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
// Dynamic 1D Barcode: Generates Code 128 barcode for counter scanner verification
router.get('/:id/barcode', async (req, res) => {
  try {
    const targetOrder = await findOrderByIdOrToken(req.params.id);
    if (!targetOrder) {
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
// Order Queue Tracking: Returns real-time queue position and estimated completion time
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

    const activeCutoff = new Date(Date.now() - 24 * 60 * 60 * 1000);

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

/**
 * Helper to resolve all associated student IDs (uid, registrationNumber, email)
 * given any single identifier passed by the client.
 */
async function resolveStudentIdentities(studentIdParam) {
  if (!studentIdParam) return [];
  const clean = String(studentIdParam).trim();
  if (!clean || clean.toLowerCase() === 'student_guest' || clean.toLowerCase().includes('guest')) {
    return [];
  }

  const ids = new Set([clean]);
  try {
    const user = await User.findOne({
      $or: [
        { uid: clean },
        { registrationNumber: clean },
        { email: clean.toLowerCase() },
      ],
    }).lean();

    if (user) {
      if (user.uid) ids.add(user.uid);
      if (user.registrationNumber) ids.add(user.registrationNumber);
      if (user.email) ids.add(user.email);
    }
  } catch (err) {
    console.warn('[Orders] Student resolution warning:', err.message);
  }
  return Array.from(ids);
}

// GET /api/orders/student/:studentId - List orders for student with multi-identifier resolution
// Student Order History: Retrieves order history across student IDs and registration numbers
router.get('/student/:studentId', async (req, res) => {
  try {
    const rawStudentId = req.params.studentId ? req.params.studentId.trim() : '';
    const { canteenId, dateOption, startDate, endDate } = req.query;

    if (!rawStudentId) {
      return res.status(400).json({ error: 'studentId is required' });
    }

    const resolvedIds = await resolveStudentIdentities(rawStudentId);
    const idList = resolvedIds.length > 0 ? resolvedIds : [rawStudentId];

    const andConditions = [
      { studentId: { $in: idList } }
    ];

    if (canteenId) {
      if (canteenId.includes(',')) {
        andConditions.push({ canteenId: { $in: canteenId.split(',').map(c => c.trim()).filter(Boolean) } });
      } else {
        andConditions.push({ canteenId: canteenId.trim() });
      }
    }

    const now = new Date();
    const IST_OFFSET_MS = 5.5 * 60 * 60 * 1000;
    const istNow = new Date(now.getTime() + IST_OFFSET_MS);

    const istTodayStart = new Date(Date.UTC(istNow.getUTCFullYear(), istNow.getUTCMonth(), istNow.getUTCDate()));
    const todayStartUtc = new Date(istTodayStart.getTime() - IST_OFFSET_MS);
    const todayEndUtc = new Date(todayStartUtc.getTime() + 24 * 60 * 60 * 1000 - 1);

    if (dateOption) {
      const opt = dateOption.toUpperCase().trim();
      if (opt === 'TODAY') {
        andConditions.push({
          $or: [
            { orderPlacedAt: { $gte: todayStartUtc, $lte: todayEndUtc } },
            { orderPlacedAt: null, createdAt: { $gte: todayStartUtc, $lte: todayEndUtc } }
          ]
        });
      } else if (opt === 'YESTERDAY') {
        const yesterdayStartUtc = new Date(todayStartUtc.getTime() - 24 * 60 * 60 * 1000);
        const yesterdayEndUtc = new Date(todayStartUtc.getTime() - 1);
        andConditions.push({
          $or: [
            { orderPlacedAt: { $gte: yesterdayStartUtc, $lte: yesterdayEndUtc } },
            { orderPlacedAt: null, createdAt: { $gte: yesterdayStartUtc, $lte: yesterdayEndUtc } }
          ]
        });
      } else if (opt === 'LAST_7_DAYS' || opt === '7DAYS') {
        const sevenDaysAgoStart = new Date(todayStartUtc.getTime() - 6 * 24 * 60 * 60 * 1000);
        andConditions.push({
          $or: [
            { orderPlacedAt: { $gte: sevenDaysAgoStart, $lte: todayEndUtc } },
            { orderPlacedAt: null, createdAt: { $gte: sevenDaysAgoStart, $lte: todayEndUtc } }
          ]
        });
      } else if (opt === 'LAST_30_DAYS' || opt === '30DAYS') {
        const thirtyDaysAgoStart = new Date(todayStartUtc.getTime() - 29 * 24 * 60 * 60 * 1000);
        andConditions.push({
          $or: [
            { orderPlacedAt: { $gte: thirtyDaysAgoStart, $lte: todayEndUtc } },
            { orderPlacedAt: null, createdAt: { $gte: thirtyDaysAgoStart, $lte: todayEndUtc } }
          ]
        });
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
        andConditions.push({
          $or: [
            { orderPlacedAt: dateCondition },
            { orderPlacedAt: null, createdAt: dateCondition }
          ]
        });
      }
    }

    const query = andConditions.length === 1 ? andConditions[0] : { $and: andConditions };

    const orders = await Order.find(query)
      .sort({ createdAt: -1 })
      .lean();
    res.json(orders);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// GET /api/orders - List all orders campus-wide (for admin portal & reporting)
router.get('/', async (req, res) => {
  try {
    const limit = parseInt(req.query.limit, 10) || 150;
    const filter = {
      studentId: { $ne: 'student_guest', $not: /guest/i },
      studentName: { $not: /guest/i },
    };
    if (req.query.canteenId && req.query.canteenId !== 'all') {
      filter.canteenId = req.query.canteenId;
    }
    if (req.query.status && req.query.status !== 'all') {
      filter.status = req.query.status;
    }
    const orders = await Order.find(filter)
      .sort({ createdAt: -1 })
      .limit(limit)
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

// POST /api/orders - Place a new order with authoritative server timestamps, validation, and idempotency
// Order Placement: Validates items, calculates server-authoritative timestamps, and creates order with barcode/QR
router.post('/', async (req, res) => {
  const correlationId = 'ORD-REQ-' + Date.now() + '-' + Math.floor(1000 + Math.random() * 9000);
  const idempotencyKey = req.headers['idempotency-key'] || req.body?.idempotencyKey;
  const rawStudentId = (req.body?.studentId || req.headers['x-student-id'] || req.headers['x-student-reg'] || '').toLowerCase().trim();

  console.log(`[${new Date().toISOString()}] [INFO] [Orders:${correlationId}] Order submission started for studentId="${rawStudentId}", canteenId="${req.body?.canteenId}"`);

  if (idempotencyKey) {
    try {
      const existingKey = await IdempotencyKey.findOneAndUpdate(
        { key: idempotencyKey },
        { $setOnInsert: { key: idempotencyKey, studentId: rawStudentId || 'unknown', inProgress: true } },
        { upsert: true, new: false }
      );

      if (existingKey) {
        if (existingKey.inProgress) {
          console.warn(`[${new Date().toISOString()}] [WARN] [Orders:${correlationId}] Order request with idempotencyKey="${idempotencyKey}" already in progress`);
          return res.status(409).json({
            error: 'Order is currently being processed. Please wait.',
            status: 'PROCESSING',
          });
        }
        console.log(`[${new Date().toISOString()}] [INFO] [Orders:${correlationId}] Returning cached idempotent response for key="${idempotencyKey}"`);
        return res.status(existingKey.statusCode || 200).json(existingKey.responseBody);
      }
    } catch (err) {
      const stored = await IdempotencyKey.findOne({ key: idempotencyKey }).lean();
      if (stored && !stored.inProgress) {
        return res.status(stored.statusCode || 200).json(stored.responseBody);
      }
      return res.status(409).json({ error: 'Order request in flight. Please wait.' });
    }
  }

  try {
    const orderData = req.body || {};
    const now = new Date();

    delete orderData._id;
    delete orderData.createdAt;
    delete orderData.updatedAt;

    const isGuest = !rawStudentId || rawStudentId === 'student_guest' || rawStudentId.includes('guest') || (orderData.studentName || '').toLowerCase().includes('guest');
    if (isGuest) {
      console.warn(`[${new Date().toISOString()}] [WARN] [Orders:${correlationId}] Order rejected: Guest orders are not permitted without student login`);
      if (idempotencyKey) await IdempotencyKey.deleteOne({ key: idempotencyKey }).catch(() => {});
      return res.status(401).json({
        error: 'Please log in as a student to place an order that saves permanently to your account.',
        requiresLogin: true,
      });
    }

    const studentUser = await User.findOne({
      $or: [
        { registrationNumber: rawStudentId },
        { uid: rawStudentId },
        { email: rawStudentId.toLowerCase() },
      ],
    }).lean();

    const studentUid = studentUser ? (studentUser.registrationNumber || studentUser.uid) : rawStudentId;
    orderData.studentId = studentUid;
    if (studentUser) {
      orderData.studentName = orderData.studentName || studentUser.name || 'Student';
      orderData.studentPhone = orderData.studentPhone || studentUser.phone || '';
      orderData.studentCourse = orderData.studentCourse || studentUser.course || '';
      orderData.studentRollNumber = studentUser.registrationNumber || '';
    } else {
      orderData.studentRollNumber = orderData.studentRollNumber || '';
    }

    if (!orderData.canteenId) {
      if (idempotencyKey) await IdempotencyKey.deleteOne({ key: idempotencyKey }).catch(() => {});
      return res.status(400).json({ error: 'canteenId is required.' });
    }

    const canteen = await Canteen.findOne({ id: orderData.canteenId }).select('id name isOpen closeReason avgWaitMinutes betweenBlocks floorInfo location').lean();
    if (!canteen) {
      console.warn(`[${new Date().toISOString()}] [WARN] [Orders:${correlationId}] Canteen ${orderData.canteenId} not found`);
      if (idempotencyKey) await IdempotencyKey.deleteOne({ key: idempotencyKey }).catch(() => {});
      return res.status(404).json({ error: 'Selected canteen not found.' });
    }
    if (canteen.isOpen === false) {
      console.warn(`[${new Date().toISOString()}] [WARN] [Orders:${correlationId}] Canteen ${canteen.name} is closed: ${canteen.closeReason}`);
      if (idempotencyKey) await IdempotencyKey.deleteOne({ key: idempotencyKey }).catch(() => {});
      return res.status(400).json({
        error: `${canteen.name || 'Canteen'} is currently closed. ${canteen.closeReason || 'New orders are paused.'}`,
        canteenClosed: true,
      });
    }

    orderData.pickupCanteenName = orderData.pickupCanteenName || canteen.name;
    if (!orderData.pickupLocation) {
      orderData.pickupLocation = canteen.betweenBlocks ? `${canteen.betweenBlocks} (${canteen.floorInfo})` : (canteen.location || '');
    }

    if (!orderData.items || !Array.isArray(orderData.items) || orderData.items.length === 0) {
      if (idempotencyKey) await IdempotencyKey.deleteOne({ key: idempotencyKey }).catch(() => {});
      return res.status(400).json({ error: 'Order must contain at least one food item.' });
    }

    const itemIds = orderData.items.map(it => it.itemId).filter(Boolean);
    const itemNames = orderData.items.map(it => it.name).filter(Boolean);

    const dbItems = await Item.find({
      canteenId: orderData.canteenId,
      $or: [
        { id: { $in: itemIds } },
        { name: { $in: itemNames } },
      ],
    }).select('id name price prepMinutes available stock canteenId').lean();

    const prepMapById = new Map(dbItems.map(d => [d.id, d]));
    const prepMapByName = new Map(dbItems.map(d => [(d.name || '').toLowerCase().trim(), d]));

    let computedTotal = 0;
    const validatedItems = [];

    for (const it of orderData.items) {
      const q = Math.max(1, parseInt(it.quantity, 10) || 1);
      const matchItem = (it.itemId && prepMapById.get(it.itemId)) ||
                        (it.name && prepMapByName.get((it.name || '').toLowerCase().trim()));

      if (!matchItem) {
        console.warn(`[${new Date().toISOString()}] [WARN] [Orders:${correlationId}] Item "${it.name || it.itemId}" does not belong to ${canteen.name}`);
        if (idempotencyKey) await IdempotencyKey.deleteOne({ key: idempotencyKey }).catch(() => {});
        return res.status(400).json({
          error: `Item "${it.name || it.itemId}" does not belong to ${canteen.name || 'this canteen'} or is not on the menu.`,
        });
      }

      if (matchItem.available === false) {
        console.warn(`[${new Date().toISOString()}] [WARN] [Orders:${correlationId}] Item "${matchItem.name}" is marked unavailable`);
        if (idempotencyKey) await IdempotencyKey.deleteOne({ key: idempotencyKey }).catch(() => {});
        return res.status(400).json({
          error: `"${matchItem.name}" is currently unavailable in the kitchen.`,
        });
      }

      if (typeof matchItem.stock === 'number' && matchItem.stock > 0 && matchItem.stock < q) {
        console.warn(`[${new Date().toISOString()}] [WARN] [Orders:${correlationId}] Insufficient stock for "${matchItem.name}"`);
        if (idempotencyKey) await IdempotencyKey.deleteOne({ key: idempotencyKey }).catch(() => {});
        return res.status(400).json({
          error: `Insufficient stock for "${matchItem.name}". Only ${matchItem.stock} item(s) remaining.`,
        });
      }

      const authoritativePrice = typeof matchItem.price === 'number' ? matchItem.price : (Number(it.price) || 0);
      const subtotal = authoritativePrice * q;
      computedTotal += subtotal;

      validatedItems.push({
        itemId: matchItem.id,
        name: matchItem.name,
        price: authoritativePrice,
        quantity: q,
        subtotal: subtotal,
        prepMinutes: matchItem.prepMinutes || it.prepMinutes || 7,
        selectedOption: it.selectedOption || null,
        selectedAddons: Array.isArray(it.selectedAddons) ? it.selectedAddons : [],
      });
    }

    // Authoritative pricing breakdown: Items Subtotal, Conditional Packaging, and GST Taxes
    const rawFulfillment = (orderData.fulfillmentType || '').trim().toUpperCase();
    const fulfillmentType = rawFulfillment === 'DINE_IN' ? 'DINE_IN' : 'TAKEAWAY';
    orderData.fulfillmentType = fulfillmentType;

    const itemsSubtotal = computedTotal;
    orderData.itemsSubtotal = itemsSubtotal;

    // Packaging charge is strictly ₹0 for Dine-in; applied only for Takeaway orders
    let packagingCharge = 0;
    if (fulfillmentType === 'TAKEAWAY') {
      packagingCharge = typeof canteen.packagingCharge === 'number' ? canteen.packagingCharge : 10;
    }
    orderData.packagingCharge = packagingCharge;

    // GST Tax calculation (5% food GST or canteen-configured rate)
    const taxRate = typeof canteen.taxRate === 'number' ? canteen.taxRate : 0.05;
    const taxAmount = Math.round(itemsSubtotal * taxRate);
    orderData.taxAmount = taxAmount;

    // Authoritative grand total calculated on backend
    const finalCalculatedTotal = itemsSubtotal + packagingCharge + taxAmount;
    orderData.items = validatedItems;
    orderData.totalAmount = finalCalculatedTotal;
    orderData.totalPrice = finalCalculatedTotal;

    if (!orderData.orderId) {
      orderData.orderId = 'ORD-' + now.getTime() + '-' + Math.floor(1000 + Math.random() * 9000);
    }

    // Student orders MUST enter initial 'PENDING' state awaiting owner confirmation.
    orderData.status = 'PENDING';

    orderData.orderPlacedAt = now;
    orderData.orderDate = formatIstDate(now);
    orderData.orderTime = formatIstTime(now);
    orderData.orderPlacedAtLocal = formatIstDateTime(now);
    orderData.confirmedAt = null;
    orderData.confirmedAtLocal = '';
    orderData.acceptedAt = null;
    orderData.acceptedAtLocal = '';
    orderData.preparingAt = null;
    orderData.preparingAtLocal = '';

    const statusHistory = [
      {
        status: 'PENDING',
        previousStatus: '',
        timestamp: now,
        timestampLocal: formatIstDateTime(now),
        changedBy: studentUid,
        changedByRole: 'STUDENT',
      },
    ];
    orderData.statusHistory = statusHistory;

    let customerPickup = (orderData.customerPickupTime || orderData.scheduledPickupTime || '').trim();
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

    // Validate scheduled pickup time against canteen operating hours and past times
    const isScheduledOrder = Boolean(customerPickup && !customerPickup.toUpperCase().includes('ASAP'));
    if (isScheduledOrder) {
      const timeValidation = validatePickupSchedule(customerPickup, canteen.timings, now);
      if (!timeValidation.valid) {
        if (idempotencyKey) await IdempotencyKey.deleteOne({ key: idempotencyKey }).catch(() => {});
        return res.status(400).json({ error: timeValidation.error });
      }
      orderData.isScheduled = true;
      orderData.scheduledPickupTime = customerPickup;
      orderData.pickupPreference = `Pickup at ${customerPickup}`;
    } else {
      orderData.isScheduled = false;
      orderData.scheduledPickupTime = '';
      orderData.pickupPreference = 'Pickup ASAP';
    }
    orderData.customerPickupTime = customerPickup;

    const defaultWait = (canteen && canteen.avgWaitMinutes) ? canteen.avgWaitMinutes : 7;
    if (!orderData.estimatedPrepMinutes || orderData.estimatedPrepMinutes <= 0) {
      orderData.estimatedPrepMinutes = getOrderPrepTime(orderData, defaultWait);
    }

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

    console.log(`[${new Date().toISOString()}] [INFO] [Orders:${correlationId}] Persisting order ${orderData.orderId} (₹${orderData.totalAmount}) to MongoDB...`);
    const order = await Order.create(orderData);
    console.log(`[${new Date().toISOString()}] [INFO] [Orders:${correlationId}] ✅ Order ${order.orderId} successfully persisted with _id=${order._id}`);

    let barcodePayload = null;
    try {
      barcodePayload = await ensureOrderBarcode(order);
    } catch (bErr) {
      console.warn(`[${new Date().toISOString()}] [WARN] [Orders:${correlationId}] Barcode generation warning:`, bErr.message);
    }

    let qrPayload = null;
    try {
      qrPayload = await createOrRotateOrderVerification({
        orderId: order.orderId,
        studentId: order.studentId,
        canteenId: order.canteenId,
      });
    } catch (qrErr) {
      console.warn(`[${new Date().toISOString()}] [WARN] [Orders:${correlationId}] Verification QR generation warning:`, qrErr.message);
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
      console.warn(`[${new Date().toISOString()}] [WARN] [Orders:${correlationId}] Realtime dispatch warning:`, e.message);
    }

    if (idempotencyKey) {
      await IdempotencyKey.updateOne(
        { key: idempotencyKey },
        { $set: { inProgress: false, statusCode: 201, responseBody: responseOrder } }
      ).catch(() => {});
    }

    return res.status(201).json(responseOrder);
  } catch (err) {
    console.error(`[${new Date().toISOString()}] [ERROR] [Orders:${correlationId}] Order creation failed:`, err);
    if (idempotencyKey) {
      await IdempotencyKey.deleteOne({ key: idempotencyKey }).catch(() => {});
    }
    return res.status(400).json({ error: err.message || 'Failed to place order.' });
  }
});

// POST /api/orders/:id/accept - Owner accepts a pending order
router.post('/:id/accept', async (req, res) => {
  try {
    const targetOrder = await findOrderByIdOrToken(req.params.id);
    if (!targetOrder) {
      return res.status(404).json({ success: false, error: 'Order not found' });
    }

    const currentStatus = (targetOrder.status || '').toUpperCase().trim();
    if (currentStatus === 'ACCEPTED') {
      return res.json({ success: true, message: 'Order is already accepted', order: targetOrder });
    }

    if (['COMPLETED', 'CANCELLED', 'REJECTED'].includes(currentStatus)) {
      return res.status(400).json({
        success: false,
        error: `Cannot accept order: Already in terminal status "${currentStatus}".`,
      });
    }

    const changedBy = req.body.changedBy || req.headers['x-owner-id'] || 'OWNER';
    const changedByRole = req.body.changedByRole || 'OWNER';
    const now = new Date();
    const nowLocal = formatIstDateTime(now);

    const updated = await Order.findOneAndUpdate(
      { _id: targetOrder._id, status: { $in: ['PENDING', 'NEW', 'WAITING'] } },
      {
        $set: {
          status: 'ACCEPTED',
          confirmedAt: now,
          confirmedAtLocal: nowLocal,
          acceptedAt: now,
          acceptedAtLocal: nowLocal,
          acceptedByOwnerId: changedBy,
        },
        $push: {
          statusHistory: {
            status: 'ACCEPTED',
            previousStatus: currentStatus,
            timestamp: now,
            timestampLocal: nowLocal,
            changedBy,
            changedByRole,
          },
        },
      },
      { new: true }
    );

    const resultOrder = updated || (await Order.findById(targetOrder._id));

    try {
      broadcastOrderStatus(resultOrder);
      if (resultOrder.canteenId) {
        broadcastQueueUpdate(resultOrder.canteenId);
      }

      const isGuest = (resultOrder.studentId || '').toLowerCase().includes('guest') || (resultOrder.studentName || '').toLowerCase().includes('guest');
      if (!isGuest && resultOrder.studentId) {
        const canteen = await Canteen.findOne({ id: resultOrder.canteenId }).lean();
        const canteenName = (canteen && canteen.name) || resultOrder.pickupCanteenName || 'Canteen Kitchen';
        const cleanToken = (resultOrder.tokenNumber || resultOrder.orderId.slice(-4)).replace(/^#+/, '');
        const title = `Order #${cleanToken}: Order Accepted!`;
        const body = `Your order has been confirmed by ${canteenName}. Preparation will begin soon.`;

        const Notification = require('../models/Notification');
        await Notification.findOneAndUpdate(
          { notificationId: `NOTIF-${resultOrder.orderId}-ACCEPTED` },
          {
            $set: {
              notificationId: `NOTIF-${resultOrder.orderId}-ACCEPTED`,
              userId: resultOrder.studentId,
              title,
              message: body,
              type: 'ORDER_STATUS',
              orderId: resultOrder.orderId,
              canteenId: resultOrder.canteenId,
              read: false,
              createdAt: now,
            },
          },
          { upsert: true, new: true }
        ).catch(() => {});

        sendPushToUser(resultOrder.studentId, {
          title,
          body,
          data: { orderId: resultOrder.orderId, status: 'ACCEPTED', type: 'ORDER_STATUS' },
        }).catch(() => {});
      }
    } catch (e) {
      console.warn('[Orders:Accept] Realtime notification warning:', e.message);
    }

    return res.json({ success: true, order: resultOrder });
  } catch (err) {
    console.error('[Orders:Accept] Error:', err);
    return res.status(500).json({ success: false, error: err.message || 'Failed to accept order.' });
  }
});

// POST /api/orders/:id/reject - Owner rejects a pending order with reason and initiates refund
router.post('/:id/reject', async (req, res) => {
  try {
    const targetOrder = await findOrderByIdOrToken(req.params.id);
    if (!targetOrder) {
      return res.status(404).json({ success: false, error: 'Order not found' });
    }

    const currentStatus = (targetOrder.status || '').toUpperCase().trim();
    if (currentStatus === 'REJECTED') {
      return res.json({ success: true, message: 'Order is already rejected', order: targetOrder });
    }

    if (['COMPLETED', 'PICKED_UP'].includes(currentStatus)) {
      return res.status(400).json({
        success: false,
        error: `Cannot reject order: Already collected by student.`,
      });
    }

    const changedBy = req.body.changedBy || req.headers['x-owner-id'] || 'OWNER';
    const changedByRole = req.body.changedByRole || 'OWNER';
    const rejectionReason = (req.body.reason || req.body.rejectionReason || 'Canteen kitchen is currently unable to accept new orders.').trim();
    const now = new Date();
    const nowLocal = formatIstDateTime(now);

    // Process refund if online payment was made
    let refundInfo = { status: 'NONE', refundId: '' };
    if (targetOrder.paymentStatus === 'PAID' && targetOrder.refundStatus !== 'PROCESSED') {
      try {
        const refundResult = await processRefund(targetOrder, rejectionReason);
        refundInfo = {
          status: refundResult.status || (refundResult.success ? 'PROCESSED' : 'PENDING'),
          refundId: refundResult.refundId || '',
        };
      } catch (refErr) {
        console.error('[Orders:Reject] Refund trigger error:', refErr);
        refundInfo = { status: 'PENDING', refundId: '' };
      }
    }

    const updated = await Order.findOneAndUpdate(
      { _id: targetOrder._id },
      {
        $set: {
          status: 'REJECTED',
          rejectedAt: now,
          rejectedAtLocal: nowLocal,
          rejectedByOwnerId: changedBy,
          rejectionReason,
          barcodeStatus: 'CANCELLED',
          refundStatus: refundInfo.status,
          refundId: refundInfo.refundId,
          refundAmount: targetOrder.totalAmount,
        },
        $push: {
          statusHistory: {
            status: 'REJECTED',
            previousStatus: currentStatus,
            timestamp: now,
            timestampLocal: nowLocal,
            changedBy,
            changedByRole,
          },
        },
      },
      { new: true }
    );

    const resultOrder = updated || (await Order.findById(targetOrder._id));

    try {
      broadcastOrderStatus(resultOrder);
      if (resultOrder.canteenId) {
        broadcastQueueUpdate(resultOrder.canteenId);
      }

      const isGuest = (resultOrder.studentId || '').toLowerCase().includes('guest') || (resultOrder.studentName || '').toLowerCase().includes('guest');
      if (!isGuest && resultOrder.studentId) {
        const cleanToken = (resultOrder.tokenNumber || resultOrder.orderId.slice(-4)).replace(/^#+/, '');
        const title = `Order #${cleanToken}: Order Rejected`;
        const refundMsg = refundInfo.status === 'PROCESSED'
          ? ` A refund of ₹${resultOrder.totalAmount} has been processed.`
          : refundInfo.status === 'PENDING'
          ? ` A refund of ₹${resultOrder.totalAmount} has been scheduled to your original payment method.`
          : '';
        const body = `Reason: ${rejectionReason}.${refundMsg}`;

        const Notification = require('../models/Notification');
        await Notification.findOneAndUpdate(
          { notificationId: `NOTIF-${resultOrder.orderId}-REJECTED` },
          {
            $set: {
              notificationId: `NOTIF-${resultOrder.orderId}-REJECTED`,
              userId: resultOrder.studentId,
              title,
              message: body,
              type: 'ORDER_STATUS',
              orderId: resultOrder.orderId,
              canteenId: resultOrder.canteenId,
              read: false,
              createdAt: now,
            },
          },
          { upsert: true, new: true }
        ).catch(() => {});

        sendPushToUser(resultOrder.studentId, {
          title,
          body,
          data: { orderId: resultOrder.orderId, status: 'REJECTED', type: 'ORDER_STATUS' },
        }).catch(() => {});
      }
    } catch (e) {
      console.warn('[Orders:Reject] Realtime notification warning:', e.message);
    }

    return res.json({ success: true, order: resultOrder, refundStatus: resultOrder.refundStatus });
  } catch (err) {
    console.error('[Orders:Reject] Error:', err);
    return res.status(500).json({ success: false, error: err.message || 'Failed to reject order.' });
  }
});

// PATCH /api/orders/:id/status - Update order status with server-authoritative timestamps
// Status Transition: Updates order lifecycle status, emits WebSocket events, and sends push notifications
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

    const currentStatus = (targetOrder.status || '').toUpperCase().trim();

    // Idempotent: If already in requested status, return success immediately
    if (currentStatus === normalizedStatus) {
      return res.json(targetOrder);
    }

    // Terminal states cannot be changed
    if (['COMPLETED', 'CANCELLED', 'REJECTED'].includes(currentStatus)) {
      return res.status(400).json({
        error: `Invalid transition: Order ${targetOrder.orderId} is already in terminal state "${currentStatus}".`,
        currentStatus: currentStatus,
      });
    }

    // Workflow validation: Order in PENDING/NEW cannot jump directly to PREPARING or READY without acceptance
    if (['PENDING', 'NEW', 'WAITING'].includes(currentStatus)) {
      if (!['ACCEPTED', 'REJECTED', 'CANCELLED'].includes(normalizedStatus)) {
        return res.status(400).json({
          error: `Order must be accepted by the canteen before it can move to status "${normalizedStatus}".`,
          currentStatus: currentStatus,
          requiredNextStatus: 'ACCEPTED',
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

    if (normalizedStatus === 'ACCEPTED' || normalizedStatus === 'CONFIRMED') {
      updateFields.status = 'ACCEPTED';
      updateFields.confirmedAt = now;
      updateFields.confirmedAtLocal = nowLocal;
      updateFields.acceptedAt = now;
      updateFields.acceptedAtLocal = nowLocal;
      updateFields.acceptedByOwnerId = changedBy;
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
      updateFields.collectedAt = now;
      updateFields.estimatedCompletionTime = 'Completed';
      updateFields.estimatedReadyTime = 'Completed';
      if (targetOrder.barcodeStatus === 'ACTIVE') {
        updateFields.barcodeStatus = 'USED';
        updateFields.usedAt = now;
      }
    }
    if (normalizedStatus === 'CANCELLED') {
      updateFields.cancelledAt = now;
      updateFields.cancelledAtLocal = nowLocal;
      updateFields.barcodeStatus = 'CANCELLED';
      updateFields.estimatedCompletionTime = 'Cancelled';
      updateFields.estimatedReadyTime = 'Cancelled';
    }
    if (normalizedStatus === 'REJECTED') {
      const rejectionReason = (req.body.reason || req.body.rejectionReason || 'Canteen kitchen is unable to accept order').trim();
      updateFields.rejectedAt = now;
      updateFields.rejectedAtLocal = nowLocal;
      updateFields.rejectedByOwnerId = changedBy;
      updateFields.rejectionReason = rejectionReason;
      updateFields.barcodeStatus = 'CANCELLED';

      if (targetOrder.paymentStatus === 'PAID' && targetOrder.refundStatus !== 'PROCESSED') {
        try {
          const refundResult = await processRefund(targetOrder, rejectionReason);
          updateFields.refundStatus = refundResult.status || 'PENDING';
          updateFields.refundId = refundResult.refundId || '';
          updateFields.refundAmount = targetOrder.totalAmount;
        } catch (_) {
          updateFields.refundStatus = 'PENDING';
          updateFields.refundAmount = targetOrder.totalAmount;
        }
      }
    }

    const pushUpdate = normalizedStatus
      ? { $push: { statusHistory: { status: normalizedStatus, previousStatus: currentStatus, timestamp: now, timestampLocal: nowLocal, changedBy, changedByRole } } }
      : {};

    const updated = await Order.findOneAndUpdate(
      { _id: targetOrder._id },
      {
        $set: updateFields,
        ...pushUpdate,
      },
      { new: true }
    );

    try {
      broadcastOrderStatus(updated);
      if (updated.canteenId) {
        broadcastQueueUpdate(updated.canteenId);
      }

      const shouldNotify = ['ACCEPTED', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED', 'REJECTED'].includes(normalizedStatus);
      const isGuest = (updated.studentId || '').toLowerCase().includes('guest') || (updated.studentName || '').toLowerCase().includes('guest');
      if (!isGuest && shouldNotify && updated.studentId) {
        const canteen = await Canteen.findOne({ id: updated.canteenId }).lean();
        const canteenName = (canteen && canteen.name) || updated.pickupCanteenName || 'Canteen Kitchen';
        const rawBlock = (canteen && (canteen.block || canteen.location)) || updated.pickupLocation || '';
        const blockNumber = rawBlock.replace(/block\s*/i, '').trim();
        const blockDisplay = blockNumber ? `Block ${blockNumber}` : (rawBlock || 'Campus');
        const counter = updated.pickupCounter || 'Counter 1';
        const cleanToken = (updated.tokenNumber || updated.orderId.slice(-4)).replace(/^#+/, '');

        const statusTitle = normalizedStatus === 'ACCEPTED'
          ? `Order #${cleanToken}: Order Accepted!`
          : normalizedStatus === 'PREPARING'
          ? `Order #${cleanToken}: Preparing`
          : normalizedStatus === 'READY'
          ? `Order #${cleanToken}: Ready for Pickup`
          : normalizedStatus === 'COMPLETED'
          ? `Order #${cleanToken}: Picked Up`
          : normalizedStatus === 'REJECTED'
          ? `Order #${cleanToken}: Order Rejected`
          : `Order #${cleanToken}: Cancelled`;

        const statusBody = normalizedStatus === 'ACCEPTED'
          ? `Your order has been confirmed by ${canteenName}. Preparation will begin soon.`
          : normalizedStatus === 'PREPARING'
          ? `Your meal is being prepared by ${canteenName} at ${blockDisplay}, ${counter}.`
          : normalizedStatus === 'READY'
          ? `Your order is ready! Please collect your food at ${counter} from ${canteenName} at ${blockDisplay}.`
          : normalizedStatus === 'COMPLETED'
          ? 'Your order has been picked up. Enjoy your food!'
          : normalizedStatus === 'REJECTED'
          ? `Reason: ${updated.rejectionReason || 'Canteen kitchen unable to accept'}. Refund scheduled.`
          : 'Your order has been cancelled by the canteen kitchen.';

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
              createdAt: now,
            },
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
