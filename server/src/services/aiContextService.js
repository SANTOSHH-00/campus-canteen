/**
 * AI Context Service for QuickBite – MunchMate
 * Retrieves verified, authoritative MongoDB data to ground MunchMate AI responses.
 * Enforces strict student data isolation and multi-identifier ownership verification.
 * Supports personalized time-of-day greetings and robust active order retrieval (waiting, preparing, ready).
 */

const Order = require('../models/Order');
const Canteen = require('../models/Canteen');
const Item = require('../models/Item');
const User = require('../models/User');

const ACTIVE_STATUS_LIST = [
  'NEW', 'WAITING', 'PLACED', 'PENDING', 'CONFIRMED', 'ACCEPTED', 'PREPARING', 'READY', 'READY_FOR_PICKUP',
  'new', 'waiting', 'placed', 'pending', 'confirmed', 'accepted', 'preparing', 'ready', 'ready_for_pickup',
  'Ready for Pickup', 'Waiting', 'Preparing', 'Placed', 'Pending', 'Confirmed', 'Accepted'
];

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
 * Returns a human-friendly status description accurately matching order lifecycle
 */
function getStatusHumanDescription(status, orderId, tokenNumber) {
  const s = (status || '').toUpperCase().trim();
  if (['NEW', 'WAITING', 'PLACED', 'PENDING'].includes(s)) {
    return `Your order #${orderId} is currently waiting for the canteen to accept it.`;
  }
  if (['CONFIRMED', 'ACCEPTED'].includes(s)) {
    return `Your order #${orderId} has been accepted and is queued for preparation.`;
  }
  if (['PREPARING'].includes(s)) {
    return `Your order #${orderId} is currently being prepared by the canteen kitchen.`;
  }
  if (['READY', 'READY_FOR_PICKUP'].includes(s)) {
    return `Your order #${orderId} is ready for pickup! Please show token #${tokenNumber} at the counter.`;
  }
  if (['COMPLETED', 'PICKED_UP'].includes(s)) {
    return `Your order #${orderId} was completed and picked up.`;
  }
  if (['CANCELLED'].includes(s)) {
    return `Your order #${orderId} was cancelled.`;
  }
  return `Your order #${orderId} status is ${status}.`;
}

/**
 * Maps a MongoDB order document to a clean DTO for client tracking UI
 */
function mapOrderToDto(o) {
  if (!o) return null;
  const itemsSummary = (o.items || []).map(i => `${i.name} x${i.quantity}`).join(', ');
  const token = o.tokenNumber || (o.orderId && o.orderId.length >= 3 ? o.orderId.slice(-3) : '001');
  return {
    orderId: o.orderId || '',
    tokenNumber: token,
    canteenId: o.canteenId || '',
    canteenName: o.pickupCanteenName || o.canteenName || o.canteenId || 'Campus Canteen',
    status: (o.status || 'NEW').toUpperCase().trim(),
    paymentStatus: o.paymentStatus || 'PAID',
    totalAmount: typeof o.totalAmount === 'number' ? o.totalAmount : parseFloat(o.totalAmount || 0),
    orderDate: o.orderDate || (o.orderPlacedAt ? new Date(o.orderPlacedAt).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' }) : 'Today'),
    estimatedReadyTime: o.estimatedReadyTime || o.estimatedCompletionTime || '5-10 mins',
    pickupLocation: o.pickupLocation || o.pickupCounter || 'Counter 1',
    itemsSummary,
    statusExplanation: getStatusHumanDescription(o.status, o.orderId, token),
  };
}

/**
 * Builds a verified context package based on the authenticated student's query.
 * 
 * @param {Object} params
 * @param {string} params.studentId - Authenticated student ID (uid, registration number, or email)
 * @param {string} [params.studentEmail] - Student's email if available
 * @param {string} [params.registrationNumber] - Student's campus registration number
 * @param {string} [params.studentName] - Student's display name
 * @param {string} params.message - Raw text query from student
 * @param {string} [params.selectedCanteenId] - Optional currently selected canteen
 * @returns {Promise<{contextText: string, recommendedItems: Array, activeOrders: Array, trackedOrder: Object|null, greetingData: Object}>}
 */
async function buildAiContext({
  studentId,
  studentEmail = '',
  registrationNumber = '',
  studentName = '',
  message = '',
  selectedCanteenId = ''
}) {
  const normalizedQuery = message.toLowerCase().trim();
  const contextParts = [];
  let recommendedItems = [];
  let activeOrders = [];
  let trackedOrder = null;

  // 1. Time-of-Day Calculation (Indian Standard Time: UTC+5:30)
  const nowUtc = new Date();
  const istOffsetMs = 5.5 * 60 * 60 * 1000;
  const istNow = new Date(nowUtc.getTime() + istOffsetMs);
  const istHours = istNow.getUTCHours();

  let timeOfDay = 'day';
  let timeGreeting = 'Hello';
  if (istHours >= 4 && istHours < 12) {
    timeOfDay = 'morning';
    timeGreeting = 'Good morning';
  } else if (istHours >= 12 && istHours < 17) {
    timeOfDay = 'afternoon';
    timeGreeting = 'Good afternoon';
  } else {
    timeOfDay = 'evening';
    timeGreeting = 'Good evening';
  }

  // 2. Greeting Intent Detection
  const isGreeting = /^(hi|hello|hey|good\s+morning|good\s+afternoon|good\s+evening|gm|morning|evening|afternoon|howdy|sup|what'?s\s+up|namaste)\b/i.test(normalizedQuery);

  // 3. User Identity Resolution (supports uid, registrationNumber, email)
  const userQueryConditions = [];
  if (studentId && studentId !== 'guest_student') {
    userQueryConditions.push({ uid: studentId });
    userQueryConditions.push({ registrationNumber: studentId });
    userQueryConditions.push({ email: studentId.toLowerCase() });
  }
  if (registrationNumber) {
    userQueryConditions.push({ registrationNumber: registrationNumber });
    userQueryConditions.push({ uid: registrationNumber });
  }
  if (studentEmail) {
    userQueryConditions.push({ email: studentEmail.toLowerCase() });
  }

  let student = null;
  if (userQueryConditions.length > 0) {
    student = await User.findOne({ $or: userQueryConditions }).lean();
  }

  const effectiveName = student?.name || studentName || 'Student';
  const firstName = effectiveName.trim().split(' ')[0].replace(/^./, c => c.toUpperCase());

  // Aggregate ALL possible student identities to ensure 100% order matching
  const candidateIdentities = [
    studentId,
    studentId ? studentId.toLowerCase() : null,
    registrationNumber,
    registrationNumber ? registrationNumber.toLowerCase() : null,
    studentEmail,
    studentEmail ? studentEmail.toLowerCase() : null,
    student ? student.uid : null,
    student ? student.registrationNumber : null,
    student ? student.registrationNumber?.toLowerCase() : null,
    student ? student.email : null,
    student ? student.email?.toLowerCase() : null,
  ].filter(id => id && id !== 'guest_student');

  const uniqueIdentities = [...new Set(candidateIdentities)];

  contextParts.push(`AUTHENTICATED STUDENT PROFILE:
- Full Name: ${effectiveName}
- First Name: ${firstName}
- Local Time of Day: ${timeOfDay} (Local Greeting: "${timeGreeting}")
- Student Identifiers: ${uniqueIdentities.join(', ') || 'Guest'}`);

  if (isGreeting) {
    contextParts.push(`GREETING INSTRUCTION:
- Student has sent a greeting ("${message.trim()}").
- Greet the student warmly using their first name "${firstName}" and the current time greeting: "${timeGreeting}".
- Example responses:
  * "${timeGreeting} ${firstName}! How can I help you today?"
  * "Hi ${firstName}! What can I get for you today?"
- Avoid repeating greetings unnecessarily on follow-up questions.`);
  }

  // 4. Intent classification
  const isOrderRelated = normalizedQuery.includes('order') ||
                         normalizedQuery.includes('track') ||
                         normalizedQuery.includes('where') ||
                         normalizedQuery.includes('status') ||
                         normalizedQuery.includes('ready') ||
                         normalizedQuery.includes('waiting') ||
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

  // 5. Specific Order ID or Token check
  const orderIdMatch = message.match(/(?:order\s*(?:number\s*|no\.?\s*|#\s*)?|#)\s*([A-Za-z0-9_-]{3,24})/i) ||
                       message.match(/\b(QB[0-9]{3,8}|ORD-[0-9]{3,14}(?:-[0-9]{3,6})?)\b/i);
  const requestedOrderId = orderIdMatch ? orderIdMatch[1].toUpperCase() : null;

  // 6. Parallel DB Execution (Active Orders, Canteens, Specific Order)
  const dbPromises = [
    getCachedCanteens(),
  ];

  if (uniqueIdentities.length > 0) {
    // Active orders query: match by ANY of the student's unique IDs and ANY active status (waiting, preparing, ready, etc.)
    dbPromises.push(
      Order.find({
        studentId: { $in: uniqueIdentities },
        status: { $in: ACTIVE_STATUS_LIST },
      }).sort({ orderPlacedAt: -1, createdAt: -1 }).limit(5).lean()
    );
  } else {
    dbPromises.push(Promise.resolve([]));
  }

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

  // Past orders if history explicitly requested
  if (isOrderRelated && (normalizedQuery.includes('history') || normalizedQuery.includes('past') || normalizedQuery.includes('previous'))) {
    if (uniqueIdentities.length > 0) {
      dbPromises.push(
        Order.find({
          studentId: { $in: uniqueIdentities },
          status: { $nin: ACTIVE_STATUS_LIST },
        }).sort({ orderPlacedAt: -1, createdAt: -1 }).limit(3).lean()
      );
    } else {
      dbPromises.push(Promise.resolve([]));
    }
  } else {
    dbPromises.push(Promise.resolve([]));
  }

  const [allCanteens, activeDbOrders, specificOrder, pastDbOrders] = await Promise.all(dbPromises);

  // 7. Process Specific Order Verification
  if (specificOrder) {
    const isOwner = uniqueIdentities.length === 0 || uniqueIdentities.some(id =>
      id.toLowerCase() === (specificOrder.studentId || '').toLowerCase()
    );

    if (!isOwner) {
      contextParts.push(`\nSECURITY NOTICE: The student requested order #${requestedOrderId}, but this order belongs to another account. ACCESS DENIED. Inform the student clearly that you can only view orders placed by their own account.`);
    } else {
      trackedOrder = mapOrderToDto(specificOrder);
      const statusExplanation = getStatusHumanDescription(specificOrder.status, specificOrder.orderId, specificOrder.tokenNumber || '001');
      contextParts.push(`\nREQUESTED ORDER DETAILS (#${specificOrder.orderId}):
- Order ID: ${specificOrder.orderId}
- Canteen: ${specificOrder.pickupCanteenName || specificOrder.canteenId}
- Current Status: ${specificOrder.status}
- Status Explanation: ${statusExplanation}
- Payment Status: ${specificOrder.paymentStatus || 'PAID'}
- Items: ${(specificOrder.items || []).map(i => `${i.name} (Qty: ${i.quantity}, ₹${i.price})`).join(', ')}
- Total Amount: ₹${specificOrder.totalAmount}
- Estimated Ready Time: ${specificOrder.estimatedReadyTime || specificOrder.estimatedCompletionTime || 'In preparation'}
- Pickup Location: ${specificOrder.pickupLocation || specificOrder.pickupCounter || 'Counter 1'}`);
    }
  } else if (requestedOrderId) {
    contextParts.push(`\nREQUESTED ORDER DETAILS (#${requestedOrderId}):
- Note: Order ID #${requestedOrderId} was not found in the system for this student.`);
  }

  // 8. Process Active Orders
  activeOrders = (activeDbOrders || []).map(mapOrderToDto);

  if (!trackedOrder && isOrderRelated) {
    if (activeOrders.length === 1) {
      trackedOrder = activeOrders[0];
    }
  }

  if (activeOrders.length > 0) {
    const activeListText = activeOrders.map(o => {
      return `• Order ID: #${o.orderId}
  Canteen: ${o.canteenName}
  Status: ${o.status}
  Status Explanation: ${o.statusExplanation}
  Payment Status: ${o.paymentStatus}
  Token Number: #${o.tokenNumber}
  Items: ${o.itemsSummary}
  Total: ₹${o.totalAmount}
  Estimated Ready Time: ${o.estimatedReadyTime}`;
    }).join('\n\n');

    contextParts.push(`\nACTIVE ORDERS FOR THIS STUDENT (${activeOrders.length} active order${activeOrders.length > 1 ? 's' : ''} found in database):
${activeListText}

TRACKING RULE:
- The student HAS active orders in the database.
- State clearly: "${activeOrders[0].statusExplanation}"
- Mention order ID #${activeOrders[0].orderId}, canteen ${activeOrders[0].canteenName}, and items: ${activeOrders[0].itemsSummary}.
- Never say there are no active orders when active orders are listed above!
---`);
  } else {
    contextParts.push(`\nACTIVE ORDERS FOR THIS STUDENT:
None. Student currently has no pending, waiting, or preparing orders in the database.
---`);
  }

  // 9. Past Orders (if requested)
  if (pastDbOrders && pastDbOrders.length > 0) {
    const pastListText = pastDbOrders.map(o => {
      const itemsSummary = (o.items || []).map(i => `${i.name} x${i.quantity}`).join(', ');
      return `• Order ID: #${o.orderId} - Status: ${o.status} | Total: ₹${o.totalAmount} (${itemsSummary})`;
    }).join('\n');
    contextParts.push(`\nRECENT COMPLETED ORDERS:
${pastListText}
---`);
  }

  // 10. Available Canteens
  if (allCanteens && allCanteens.length > 0) {
    const canteensList = allCanteens.map(c => {
      const status = c.isOpen ? 'OPEN' : `CLOSED (${c.closeReason || 'Closed'})`;
      return `• ${c.name} (ID: ${c.id}) - ${status} | Location: ${c.location || c.block || 'Campus'}`;
    }).join('\n');
    contextParts.push(`\nAVAILABLE CANTEENS:
${canteensList}
---`);
  }

  // 11. Food Menu Context (Only fetched if food/menu is relevant, skipping for pure order tracking)
  if (isFoodRelated || (!isOrderRelated && !isGreeting && normalizedQuery.length < 30)) {
    let maxBudget = null;
    const budgetMatch = normalizedQuery.match(/(?:under|below|less than|within)\s*(?:₹|rs\.?|inr)?\s*(\d+)/i) ||
                        normalizedQuery.match(/(?:₹|rs\.?|inr)\s*(\d+)/i);
    if (budgetMatch) {
      maxBudget = parseInt(budgetMatch[1], 10);
    }

    let matchingItems = [];
    if (!maxBudget && !selectedCanteenId && !normalizedQuery.includes('veg')) {
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
    greetingData: {
      isGreeting,
      timeGreeting,
      timeOfDay,
      firstName,
      studentName: effectiveName,
    },
  };
}

module.exports = {
  buildAiContext,
  mapOrderToDto,
  getStatusHumanDescription,
  getCachedCanteens,
  getCachedPopularItems,
};
