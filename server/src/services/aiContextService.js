/**
 * AI Context Service for QuickBite – MunchMate
 * Retrieves verified, authoritative MongoDB data to ground MunchMate AI responses.
 * Enforces strict student data isolation and ownership rules.
 * Optimized with in-memory caching and fast-path intent routing for < 50ms context retrieval.
 */

const Order = require('../models/Order');
const Canteen = require('../models/Canteen');
const Item = require('../models/Item');
const User = require('../models/User');

const ACTIVE_STATUSES = ['NEW', 'WAITING', 'CONFIRMED', 'PREPARING', 'READY'];

// ── In-Memory Caches for High-Performance Latency Optimization ──
let cachedCanteens = null;
let cachedCanteensTimestamp = 0;
const CANTEEN_CACHE_TTL_MS = 3 * 60 * 1000; // 3 minutes

let cachedPopularItems = null;
let cachedPopularItemsTimestamp = 0;
const ITEM_CACHE_TTL_MS = 3 * 60 * 1000; // 3 minutes

async function getCachedCanteens() {
  const now = Date.now();
  if (cachedCanteens && (now - cachedCanteensTimestamp < CANTEEN_CACHE_TTL_MS)) {
    return cachedCanteens;
  }
  try {
    const canteens = await Canteen.find({}).lean();
    cachedCanteens = canteens || [];
    cachedCanteensTimestamp = now;
    return cachedCanteens;
  } catch (err) {
    console.warn('[AiContextService] Failed to refresh canteens cache:', err.message);
    return cachedCanteens || [];
  }
}

async function getCachedPopularItems() {
  const now = Date.now();
  if (cachedPopularItems && (now - cachedPopularItemsTimestamp < ITEM_CACHE_TTL_MS)) {
    return cachedPopularItems;
  }
  try {
    const items = await Item.find({ available: true })
      .sort({ rating: -1, orderCount: -1 })
      .limit(15)
      .lean();
    cachedPopularItems = items || [];
    cachedPopularItemsTimestamp = now;
    return cachedPopularItems;
  } catch (err) {
    console.warn('[AiContextService] Failed to refresh items cache:', err.message);
    return cachedPopularItems || [];
  }
}

/**
 * Maps a MongoDB order document to a clean DTO for client tracking UI
 */
function mapOrderToDto(o) {
  if (!o) return null;
  const itemsSummary = (o.items || []).map(i => `${i.name} x${i.quantity}`).join(', ');
  return {
    orderId: o.orderId || '',
    tokenNumber: o.tokenNumber || (o.orderId && o.orderId.length >= 3 ? o.orderId.slice(-3) : '001'),
    canteenId: o.canteenId || '',
    canteenName: o.pickupCanteenName || o.canteenName || o.canteenId || 'Campus Canteen',
    status: o.status || 'NEW',
    paymentStatus: o.paymentStatus || 'PAID',
    totalAmount: typeof o.totalAmount === 'number' ? o.totalAmount : parseFloat(o.totalAmount || 0),
    orderDate: o.orderDate || (o.orderPlacedAt ? new Date(o.orderPlacedAt).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' }) : 'Today'),
    estimatedReadyTime: o.estimatedReadyTime || o.estimatedCompletionTime || '5-10 mins',
    pickupLocation: o.pickupLocation || o.pickupCounter || 'Counter 1',
    itemsSummary,
  };
}

/**
 * Builds a verified context package based on the authenticated student's query.
 * 
 * @param {Object} params
 * @param {string} params.studentId - Authenticated student ID from JWT token
 * @param {string} params.message - Raw text query from student
 * @param {string} [params.selectedCanteenId] - Optional currently selected canteen
 * @returns {Promise<{contextText: string, recommendedItems: Array, activeOrders: Array, trackedOrder: Object|null}>}
 */
async function buildAiContext({ studentId, message = '', selectedCanteenId = '' }) {
  const normalizedQuery = message.toLowerCase().trim();
  const contextParts = [];
  let recommendedItems = [];
  let activeOrders = [];
  let trackedOrder = null;

  // Intent classification
  const isOrderRelated = normalizedQuery.includes('order') ||
                         normalizedQuery.includes('track') ||
                         normalizedQuery.includes('where') ||
                         normalizedQuery.includes('status') ||
                         normalizedQuery.includes('ready') ||
                         normalizedQuery.includes('history') ||
                         normalizedQuery.includes('bought') ||
                         normalizedQuery.includes('pickup');

  const isFoodRelated = normalizedQuery.includes('food') ||
                        normalizedQuery.includes('eat') ||
                        normalizedQuery.includes('menu') ||
                        normalizedQuery.includes('recommend') ||
                        normalizedQuery.includes('popular') ||
                        normalizedQuery.includes('veg') ||
                        normalizedQuery.includes('price') ||
                        normalizedQuery.includes('under') ||
                        normalizedQuery.includes('snack') ||
                        normalizedQuery.includes('tasty') ||
                        normalizedQuery.includes('suggest') ||
                        normalizedQuery.includes('breakfast') ||
                        normalizedQuery.includes('lunch') ||
                        normalizedQuery.includes('dinner');

  // Specific Order ID or token check
  const orderIdMatch = message.match(/(?:order\s*(?:number\s*|no\.?\s*|#\s*)?|#)\s*([A-Za-z0-9_-]{3,24})/i) ||
                       message.match(/\b(QB[0-9]{3,8}|ORD-[0-9]{3,8})\b/i);
  const requestedOrderId = orderIdMatch ? orderIdMatch[1].toUpperCase() : null;

  // Parallel database execution for ultra-low latency
  const dbPromises = [
    User.findOne({ uid: studentId }).lean(),
    Order.find({
      studentId,
      status: { $in: ACTIVE_STATUSES },
    }).sort({ orderPlacedAt: -1 }).limit(5).lean(),
    getCachedCanteens(),
  ];

  if (requestedOrderId) {
    dbPromises.push(
      Order.findOne({
        $or: [
          { orderId: requestedOrderId },
          { orderId: new RegExp(`^${requestedOrderId}$`, 'i') },
          { tokenNumber: requestedOrderId },
        ]
      }).lean()
    );
  } else {
    dbPromises.push(Promise.resolve(null));
  }

  // If student is asking for past order history (when active count might be 0)
  if (isOrderRelated && (normalizedQuery.includes('history') || normalizedQuery.includes('past') || normalizedQuery.includes('previous'))) {
    dbPromises.push(
      Order.find({
        studentId,
        status: { $nin: ACTIVE_STATUSES },
      }).sort({ orderPlacedAt: -1 }).limit(3).lean()
    );
  } else {
    dbPromises.push(Promise.resolve([]));
  }

  const [student, activeDbOrders, allCanteens, specificOrder, pastDbOrders] = await Promise.all(dbPromises);

  // 1. Authenticated Student Profile Info
  const studentName = student ? (student.name || student.displayName || 'Student') : 'Student';
  contextParts.push(`AUTHENTICATED STUDENT:
- Name: ${studentName}
- Student ID: ${studentId}`);

  // 2. Specific Order Verification
  if (specificOrder) {
    if (specificOrder.studentId !== studentId) {
      contextParts.push(`\nSECURITY NOTICE: The student requested order #${requestedOrderId}, but this order belongs to another account. ACCESS DENIED. Inform the student clearly that you can only view orders placed by their own account.`);
    } else {
      trackedOrder = mapOrderToDto(specificOrder);
      contextParts.push(`\nREQUESTED ORDER DETAILS (#${specificOrder.orderId}):
- Order ID: ${specificOrder.orderId}
- Canteen: ${specificOrder.pickupCanteenName || specificOrder.canteenId}
- Current Status: ${specificOrder.status}
- Payment Status: ${specificOrder.paymentStatus || 'PAID'}
- Items: ${(specificOrder.items || []).map(i => `${i.name} (Qty: ${i.quantity}, ₹${i.price})`).join(', ')}
- Total Amount: ₹${specificOrder.totalAmount}
- Estimated Ready Time: ${specificOrder.estimatedReadyTime || specificOrder.estimatedCompletionTime || 'In preparation'}
- Pickup Location: ${specificOrder.pickupLocation || specificOrder.pickupCounter || 'Counter 1'}
- Pickup Status: ${specificOrder.status === 'READY' ? 'READY FOR PICKUP - Student should show their pickup token/barcode at the counter.' : 'In preparation'}`);
    }
  } else if (requestedOrderId) {
    contextParts.push(`\nREQUESTED ORDER DETAILS (#${requestedOrderId}):
- Note: Order ID #${requestedOrderId} was not found in the system for this student.`);
  }

  // 3. Active Orders Context
  activeOrders = (activeDbOrders || []).map(mapOrderToDto);

  if (!trackedOrder && isOrderRelated) {
    if (activeOrders.length === 1) {
      trackedOrder = activeOrders[0];
    }
  }

  if (activeOrders.length > 0) {
    const activeListText = activeDbOrders.map(o => {
      const itemsSummary = (o.items || []).map(i => `${i.name} x${i.quantity}`).join(', ');
      return `• Order ID: #${o.orderId}
  Canteen: ${o.pickupCanteenName || o.canteenId}
  Status: ${o.status}
  Payment Status: ${o.paymentStatus || 'PAID'}
  Items: ${itemsSummary}
  Total: ₹${o.totalAmount}
  Estimated Ready Time: ${o.estimatedReadyTime || o.estimatedCompletionTime || 'In preparation'}${o.status === 'READY' ? ' [READY FOR PICKUP]' : ''}`;
    }).join('\n\n');

    contextParts.push(`\nACTIVE ORDERS FOR THIS STUDENT (${activeOrders.length} active):
${activeListText}
---`);
  } else {
    contextParts.push(`\nACTIVE ORDERS FOR THIS STUDENT:
None. Student currently has no pending or active orders.
---`);
  }

  // 4. Past Orders (if requested)
  if (pastDbOrders && pastDbOrders.length > 0) {
    const pastListText = pastDbOrders.map(o => {
      const itemsSummary = (o.items || []).map(i => `${i.name} x${i.quantity}`).join(', ');
      return `• Order ID: #${o.orderId} - Status: ${o.status} | Total: ₹${o.totalAmount} (${itemsSummary})`;
    }).join('\n');
    contextParts.push(`\nRECENT COMPLETED ORDERS:
${pastListText}
---`);
  }

  // 5. Available Canteens
  if (allCanteens && allCanteens.length > 0) {
    const canteensList = allCanteens.map(c => {
      const status = c.isOpen ? 'OPEN' : `CLOSED (${c.closeReason || 'Closed'})`;
      return `• ${c.name} (ID: ${c.id}) - ${status} | Location: ${c.location || c.block || 'Campus'}`;
    }).join('\n');
    contextParts.push(`\nAVAILABLE CANTEENS:
${canteensList}
---`);
  }

  // 6. Food Menu Context (Only fetched if food/menu is relevant, skipping for pure order tracking to save latency)
  if (isFoodRelated || (!isOrderRelated && normalizedQuery.length < 30)) {
    let maxBudget = null;
    const budgetMatch = normalizedQuery.match(/(?:under|below|less than|within)\s*(?:₹|rs\.?|inr)?\s*(\d+)/i) ||
                        normalizedQuery.match(/(?:₹|rs\.?|inr)\s*(\d+)/i);
    if (budgetMatch) {
      maxBudget = parseInt(budgetMatch[1], 10);
    }

    let matchingItems = [];
    if (!maxBudget && !selectedCanteenId && !normalizedQuery.includes('veg')) {
      // Use in-memory cached popular items for ultra fast response
      matchingItems = await getCachedPopularItems();
    } else {
      const itemFilter = { available: true };
      if (selectedCanteenId) itemFilter.canteenId = selectedCanteenId;
      if (maxBudget && !isNaN(maxBudget)) itemFilter.price = { $lte: maxBudget };
      if (normalizedQuery.includes('veg') && !normalizedQuery.includes('non-veg')) {
        itemFilter.$or = [
          { category: { $regex: /veg/i } },
          { name: { $regex: /veg|paneer|dal|roti|chole|paratha|sandwich/i } },
        ];
      }
      matchingItems = await Item.find(itemFilter)
        .sort({ rating: -1, price: 1 })
        .limit(8)
        .lean();
    }

    if (matchingItems.length > 0) {
      const canteenNameMap = {};
      allCanteens.forEach(c => { canteenNameMap[c.id] = c.name; });

      recommendedItems = matchingItems.slice(0, 4).map(item => ({
        id: item.id,
        canteenId: item.canteenId,
        canteenName: canteenNameMap[item.canteenId] || item.canteenId,
        name: item.name,
        price: item.price,
        category: item.category,
        imageUrl: item.imageUrl || '',
        prepTime: item.preparationTime || `${item.prepMinutes || 7} mins`,
        rating: item.rating || 4.5,
        available: item.available,
      }));

      const itemsSummary = recommendedItems.map(i => {
        return `• ${i.name} - ₹${i.price} at ${i.canteenName} (Category: ${i.category}, Rating: ${i.rating}★, Prep: ${i.prepTime})`;
      }).join('\n');

      contextParts.push(`\nMATCHING MENU ITEMS FROM DATABASE:
${itemsSummary}
---`);
    }
  }

  return {
    contextText: contextParts.join('\n'),
    recommendedItems,
    activeOrders,
    trackedOrder,
  };
}

module.exports = {
  buildAiContext,
  mapOrderToDto,
  getCachedCanteens,
  getCachedPopularItems,
};
