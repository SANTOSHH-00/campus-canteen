/**
 * AI Tools Service for QuickBite – MunchMate
 * Implements Gemini Function Calling / Tool Execution.
 * 
 * Strict Security Architecture:
 * 1. Only allowlisted tools can be executed.
 * 2. All student-specific tools strictly use authenticated student context from JWT.
 * 3. Never trust student IDs, roles, or prices supplied by Gemini or client messages.
 * 4. Execute direct Mongoose queries against the existing MongoDB database.
 * 5. Gemini NEVER connects directly to MongoDB.
 */

const Order = require('../models/Order');
const Canteen = require('../models/Canteen');
const Item = require('../models/Item');
const User = require('../models/User');
const Review = require('../models/Review');
const Cart = require('../models/Cart');

// Terminal order statuses (Orders in these statuses are completed/finalized and no longer active)
const TERMINAL_STATUSES = [
  'COMPLETED', 'PICKED_UP', 'COLLECTED', 'CANCELLED', 'REJECTED',
  'completed', 'picked_up', 'collected', 'cancelled', 'rejected',
  'Completed', 'Picked Up', 'Collected', 'Cancelled', 'Rejected'
];

/**
 * Gemini v1beta function declarations
 */
const TOOL_DECLARATIONS = [
  {
    name: 'get_order_status_summary',
    description: 'Get a structured, compact summary of all active orders for the authenticated student. Helps determine if the student has 0 active orders, 1 active order, or multiple active orders, providing concise details for each.',
    parameters: {
      type: 'OBJECT',
      properties: {},
    },
  },
  {
    name: 'get_my_orders',
    description: "Retrieve the authenticated student's orders from MongoDB. Returns active orders currently being prepared/waiting/ready or past order history.",
    parameters: {
      type: 'OBJECT',
      properties: {
        activeOnly: {
          type: 'BOOLEAN',
          description: 'If true, returns only active in-progress orders (waiting, preparing, ready). If false, returns recent order history. Default is false.',
        },
        limit: {
          type: 'NUMBER',
          description: 'Maximum number of orders to return (1-10, default is 5).',
        },
      },
    },
  },
  {
    name: 'get_order_tracking',
    description: 'Retrieve detailed live tracking information, verified lifecycle timeline, and token for a specific order belonging to the student.',
    parameters: {
      type: 'OBJECT',
      properties: {
        orderId: {
          type: 'STRING',
          description: 'The specific QuickBite order ID to track (e.g. "ORD-123456").',
        },
      },
      required: ['orderId'],
    },
  },
  {
    name: 'get_menu_items',
    description: 'Retrieve actual food items from the selected canteen. Use to answer questions about available food, prices, dish descriptions, categories, preparation time, and ratings.',
    parameters: {
      type: 'OBJECT',
      properties: {
        canteenId: {
          type: 'STRING',
          description: 'Canteen identifier to fetch menu for (e.g. "canteen_33", "canteen_34", "canteen_38").',
        },
        query: {
          type: 'STRING',
          description: 'Optional search term to filter dishes by name or ingredients (e.g. "paneer", "dosa", "tea").',
        },
        category: {
          type: 'STRING',
          description: 'Optional category filter (e.g. "QUICK_ORDER", "MEALS", "BEVERAGES", "SNACKS").',
        },
        availableOnly: {
          type: 'BOOLEAN',
          description: 'Whether to return only currently available items. Default is true.',
        },
      },
      required: ['canteenId'],
    },
  },
  {
    name: 'get_canteen_details',
    description: 'Retrieve authoritative information about a canteen including name, block location, open/closed status, operating hours, and floor.',
    parameters: {
      type: 'OBJECT',
      properties: {
        canteenId: {
          type: 'STRING',
          description: 'Canteen identifier (e.g. "canteen_33", "canteen_34", "canteen_38").',
        },
      },
      required: ['canteenId'],
    },
  },
  {
    name: 'get_cart_details',
    description: "Retrieve the authenticated student's current cart items, verified item prices from database, subtotal, and total amount.",
    parameters: {
      type: 'OBJECT',
      properties: {},
    },
  },
  {
    name: 'get_food_reviews',
    description: 'Retrieve genuine customer reviews, comments, and ratings for a food item or canteen.',
    parameters: {
      type: 'OBJECT',
      properties: {
        itemId: {
          type: 'STRING',
          description: 'The food item ID to retrieve reviews for.',
        },
        canteenId: {
          type: 'STRING',
          description: 'Optional canteen ID to retrieve canteen-level reviews.',
        },
        limit: {
          type: 'NUMBER',
          description: 'Maximum reviews to return (default 5).',
        },
      },
    },
  },
  {
    name: 'search_food_item',
    description: 'Search for a specific food item or dish by name across campus canteens to verify its real-time availability, exact price, preparation time, and which canteen serves it. Crucial for answering queries like "Are pancakes available?", "Do you have dosa?", "What is the price of burger?".',
    parameters: {
      type: 'OBJECT',
      properties: {
        dishName: {
          type: 'STRING',
          description: 'The specific name of the food item or dish to look up (e.g. "pancakes", "masala dosa", "chole bhature", "cold coffee").',
        },
        canteenId: {
          type: 'STRING',
          description: 'Optional canteen ID to prioritize searching in (e.g. "canteen_33"). If omitted or not found in this canteen, the search automatically checks all other campus canteens.',
        },
      },
      required: ['dishName'],
    },
  },
  {
    name: 'get_favourite_items',
    description: "Retrieve the authenticated student's saved favourite food items from MongoDB, along with their live prices and availability status.",
    parameters: {
      type: 'OBJECT',
      properties: {
        limit: {
          type: 'NUMBER',
          description: 'Maximum favourite items to return (default 10).',
        },
      },
    },
  },
  {
    name: 'get_recent_orders',
    description: "Retrieve the authenticated student's recent past food orders and order history from MongoDB.",
    parameters: {
      type: 'OBJECT',
      properties: {
        limit: {
          type: 'NUMBER',
          description: 'Maximum past orders to return (1-10, default 5).',
        },
      },
    },
  },
  {
    name: 'manage_cart',
    description: "Safely perform actions on the authenticated student's cart (add dish, remove dish, clear cart). Uses authoritative MongoDB prices to prevent price hallucination.",
    parameters: {
      type: 'OBJECT',
      properties: {
        action: {
          type: 'STRING',
          description: 'The cart operation: "add", "remove", or "clear".',
        },
        dishNameOrId: {
          type: 'STRING',
          description: 'Name or ID of the food item to add or remove (e.g. "Masala Dosa", "item_123").',
        },
        quantity: {
          type: 'NUMBER',
          description: 'Quantity to add or remove (default 1).',
        },
      },
      required: ['action'],
    },
  },
];

/**
 * Escapes regex special characters to prevent ReDoS
 */
function escapeRegex(str) {
  return String(str || '').replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Human-readable order lifecycle description
 */
function getStatusExplanation(status, orderId, tokenNumber) {
  const s = String(status || '').toUpperCase().trim();
  if (['NEW', 'WAITING', 'PLACED', 'PENDING'].includes(s)) {
    return `Order #${orderId} is placed and waiting for the canteen to accept it.`;
  }
  if (['CONFIRMED', 'ACCEPTED'].includes(s)) {
    return `Order #${orderId} has been accepted and is queued for preparation.`;
  }
  if (['PREPARING'].includes(s)) {
    return `Order #${orderId} is currently being prepared in the kitchen.`;
  }
  if (['READY', 'READY_FOR_PICKUP'].includes(s)) {
    return `Order #${orderId} is ready for pickup! Show token #${tokenNumber || '---'} at the counter.`;
  }
  if (['COMPLETED', 'PICKED_UP', 'COLLECTED'].includes(s)) {
    return `Order #${orderId} was completed and picked up.`;
  }
  if (['CANCELLED', 'REJECTED'].includes(s)) {
    return `Order #${orderId} was cancelled.`;
  }
  return `Order #${orderId} status is ${status}.`;
}

/**
 * Builds an authoritative, evidence-based status timeline.
 * Does NOT invent timestamps or preparation times.
 */
function buildOrderTimeline(order) {
  if (!order) return null;
  const currentStatus = String(order.status || '').toUpperCase().trim();

  // Cancelled or rejected orders
  if (['CANCELLED', 'REJECTED'].includes(currentStatus)) {
    return {
      isCancelled: true,
      currentStep: 'Cancelled',
      status: currentStatus,
      cancelledAt: order.cancelledAtLocal || (order.cancelledAt ? new Date(order.cancelledAt).toLocaleTimeString('en-IN') : null),
      steps: [
        {
          name: 'Order Placed',
          stepKey: 'PLACED',
          state: 'COMPLETED',
          timestamp: order.orderPlacedAtLocal || order.orderTime || (order.createdAt ? new Date(order.createdAt).toLocaleTimeString('en-IN') : null),
        },
        {
          name: 'Order Cancelled',
          stepKey: 'CANCELLED',
          state: 'CURRENT',
          timestamp: order.cancelledAtLocal || null,
        },
      ],
    };
  }

  const historyMap = new Map();
  if (Array.isArray(order.statusHistory)) {
    for (const entry of order.statusHistory) {
      if (entry && entry.status) {
        const k = String(entry.status).toUpperCase().trim();
        const ts = entry.timestampLocal || (entry.timestamp ? new Date(entry.timestamp).toLocaleTimeString('en-IN') : null);
        if (ts) historyMap.set(k, ts);
      }
    }
  }

  // 1: PLACED / NEW / WAITING
  // 2: ACCEPTED / CONFIRMED
  // 3: PREPARING
  // 4: READY
  // 5: COMPLETED / PICKED_UP
  const getLevel = (st) => {
    switch (st) {
      case 'NEW':
      case 'WAITING':
      case 'PLACED':
      case 'PENDING':
        return 1;
      case 'CONFIRMED':
      case 'ACCEPTED':
        return 2;
      case 'PREPARING':
        return 3;
      case 'READY':
      case 'READY_FOR_PICKUP':
        return 4;
      case 'COMPLETED':
      case 'PICKED_UP':
      case 'COLLECTED':
        return 5;
      default:
        return 1;
    }
  };

  const currentLevel = getLevel(currentStatus);

  const stepsConfig = [
    {
      key: 'PLACED',
      name: 'Order Placed',
      level: 1,
      explicitTs: order.orderPlacedAtLocal || order.orderTime || (order.createdAt ? new Date(order.createdAt).toLocaleTimeString('en-IN') : null),
    },
    {
      key: 'ACCEPTED',
      name: 'Order Accepted',
      level: 2,
      explicitTs: order.confirmedAtLocal || historyMap.get('ACCEPTED') || historyMap.get('CONFIRMED') || null,
    },
    {
      key: 'PREPARING',
      name: 'Preparing in Kitchen',
      level: 3,
      explicitTs: order.preparingAtLocal || historyMap.get('PREPARING') || null,
    },
    {
      key: 'READY',
      name: 'Ready for Pickup',
      level: 4,
      explicitTs: order.readyAtLocal || historyMap.get('READY') || historyMap.get('READY_FOR_PICKUP') || null,
    },
    {
      key: 'COMPLETED',
      name: 'Picked Up / Completed',
      level: 5,
      explicitTs: order.completedAtLocal || order.pickedUpAtLocal || historyMap.get('COMPLETED') || historyMap.get('PICKED_UP') || null,
    },
  ];

  const steps = stepsConfig.map(s => {
    let state = 'PENDING';
    if (s.level < currentLevel) {
      state = 'COMPLETED';
    } else if (s.level === currentLevel) {
      state = 'CURRENT';
    } else {
      state = 'PENDING';
    }
    return {
      stepKey: s.key,
      name: s.name,
      state,
      timestamp: (state === 'COMPLETED' || state === 'CURRENT') ? s.explicitTs : null,
    };
  });

  return {
    isCancelled: false,
    currentStep: stepsConfig.find(s => s.level === currentLevel)?.name || currentStatus,
    currentStatus,
    steps,
  };
}

/**
 * Resolves all identifier aliases for a student to ensure complete query coverage
 */
async function resolveStudentIdentities(authenticatedStudent) {
  const identities = new Set();
  if (!authenticatedStudent) return [];

  if (authenticatedStudent.studentId) identities.add(String(authenticatedStudent.studentId));
  if (authenticatedStudent.studentEmail) {
    identities.add(String(authenticatedStudent.studentEmail).toLowerCase().trim());
    identities.add(String(authenticatedStudent.studentEmail).trim());
  }
  if (authenticatedStudent.registrationNumber) identities.add(String(authenticatedStudent.registrationNumber).trim());

  try {
    const userDoc = await User.findOne({
      $or: [
        { uid: authenticatedStudent.studentId },
        { email: (authenticatedStudent.studentEmail || '').toLowerCase().trim() },
      ].filter(Boolean),
    }).lean();

    if (userDoc) {
      if (userDoc.uid) identities.add(String(userDoc.uid));
      if (userDoc.email) {
        identities.add(String(userDoc.email).toLowerCase().trim());
        identities.add(String(userDoc.email).trim());
      }
      if (userDoc.registrationNumber) identities.add(String(userDoc.registrationNumber).trim());
      if (userDoc._id) identities.add(String(userDoc._id));
    }
  } catch (err) {
  }

  return Array.from(identities).filter(Boolean);
}

// TOOL IMPLEMENTATIONS

/**
 * Tool: get_order_status_summary
 * Distinguishes cleanly between:
 *  - NO_ACTIVE_ORDERS
 *  - SINGLE_ACTIVE_ORDER
 *  - MULTIPLE_ACTIVE_ORDERS
 */
async function executeGetOrderStatusSummary(args = {}, authenticatedStudent = {}) {
  if (!authenticatedStudent || !authenticatedStudent.isAuthenticated || !authenticatedStudent.studentId || authenticatedStudent.studentId === 'guest_student') {
    return {
      success: false,
      authenticated: false,
      scenario: 'UNAUTHENTICATED',
      error: 'Please log in to your student account to check your active orders.',
    };
  }

  const identities = await resolveStudentIdentities(authenticatedStudent);
  if (identities.length === 0) {
    return {
      success: true,
      authenticated: true,
      scenario: 'NO_ACTIVE_ORDERS',
      count: 0,
      orders: [],
      message: 'You currently have no active orders.',
    };
  }

  const activeOrders = await Order.find({
    studentId: { $in: identities },
    status: { $nin: TERMINAL_STATUSES },
  })
    .sort({ createdAt: -1, orderPlacedAt: -1 })
    .limit(10)
    .lean();

  if (activeOrders.length === 0) {
    return {
      success: true,
      authenticated: true,
      scenario: 'NO_ACTIVE_ORDERS',
      count: 0,
      orders: [],
      message: "You currently have no active orders. Feel free to browse the canteen menu to place an order!",
    };
  }

  if (activeOrders.length === 1) {
    const o = activeOrders[0];
    const token = o.tokenNumber || (o.orderId ? o.orderId.slice(-3) : '---');
    return {
      success: true,
      authenticated: true,
      scenario: 'SINGLE_ACTIVE_ORDER',
      count: 1,
      order: {
        orderId: o.orderId,
        tokenNumber: token,
        canteenName: o.pickupCanteenName || o.canteenId,
        canteenId: o.canteenId,
        status: o.status,
        statusExplanation: getStatusExplanation(o.status, o.orderId, token),
        totalAmount: o.totalAmount,
        totalFormatted: `₹${o.totalAmount}`,
        paymentStatus: o.paymentStatus || 'PAID',
        items: (o.items || []).map(it => `${it.name} x${it.quantity || 1}`),
        itemCount: (o.items || []).reduce((acc, it) => acc + (it.quantity || 1), 0),
        customerPickupTime: o.customerPickupTime || 'ASAP',
        estimatedReadyTime: o.estimatedReadyTime || 'In preparation',
        pickupLocation: o.pickupLocation || o.pickupCounter || 'Counter 1',
        timeline: buildOrderTimeline(o),
      },
    };
  }

  return {
    success: true,
    authenticated: true,
    scenario: 'MULTIPLE_ACTIVE_ORDERS',
    count: activeOrders.length,
    message: `You have ${activeOrders.length} active orders currently in progress. Present a brief summary of each order and ask the student which one they would like to track in detail.`,
    orders: activeOrders.map((o, idx) => {
      const token = o.tokenNumber || (o.orderId ? o.orderId.slice(-3) : '---');
      return {
        orderIndex: idx + 1,
        orderId: o.orderId,
        tokenNumber: token,
        canteenName: o.pickupCanteenName || o.canteenId,
        canteenId: o.canteenId,
        status: o.status,
        statusExplanation: getStatusExplanation(o.status, o.orderId, token),
        totalAmount: o.totalAmount,
        totalFormatted: `₹${o.totalAmount}`,
        items: (o.items || []).map(it => `${it.name} x${it.quantity || 1}`),
        customerPickupTime: o.customerPickupTime || 'ASAP',
        estimatedReadyTime: o.estimatedReadyTime || 'In preparation',
      };
    }),
  };
}

/**
 * Tool: get_my_orders
 */
async function executeGetMyOrders(args = {}, authenticatedStudent = {}) {
  if (!authenticatedStudent || !authenticatedStudent.isAuthenticated || !authenticatedStudent.studentId || authenticatedStudent.studentId === 'guest_student') {
    return {
      success: false,
      authenticated: false,
      error: 'You need to be logged in to view your orders. Please log in to your QuickBite student account.',
    };
  }

  const identities = await resolveStudentIdentities(authenticatedStudent);
  if (identities.length === 0) {
    return { success: true, count: 0, orders: [], message: 'No orders found.' };
  }

  const query = { studentId: { $in: identities } };

  if (args.activeOnly === true) {
    query.status = { $nin: TERMINAL_STATUSES };
  }

  const limit = Math.min(Math.max(parseInt(args.limit, 10) || 5, 1), 10);

  const orders = await Order.find(query)
    .sort({ createdAt: -1, orderPlacedAt: -1 })
    .limit(limit)
    .lean();

  return {
    success: true,
    authenticated: true,
    studentName: authenticatedStudent.studentName || 'Student',
    count: orders.length,
    activeOnly: Boolean(args.activeOnly),
    orders: orders.map(order => ({
      orderId: order.orderId,
      tokenNumber: order.tokenNumber || (order.orderId ? order.orderId.slice(-3) : ''),
      canteenId: order.canteenId,
      canteenName: order.pickupCanteenName || order.canteenId,
      status: order.status,
      statusExplanation: getStatusExplanation(order.status, order.orderId, order.tokenNumber),
      totalAmount: order.totalAmount,
      totalFormatted: `₹${order.totalAmount}`,
      paymentStatus: order.paymentStatus || 'PAID',
      itemCount: (order.items || []).reduce((acc, it) => acc + (it.quantity || 1), 0),
      items: (order.items || []).map(it => ({
        name: it.name,
        quantity: it.quantity || 1,
        price: it.price,
        subtotal: it.subtotal || (it.price * (it.quantity || 1)),
      })),
      placedAt: order.orderPlacedAtLocal || order.orderTime || (order.createdAt ? new Date(order.createdAt).toLocaleTimeString('en-IN') : ''),
      pickupTime: order.customerPickupTime || 'ASAP',
      estimatedReadyTime: order.estimatedReadyTime || '',
      timeline: buildOrderTimeline(order),
    })),
  };
}

/**
 * Tool: get_order_tracking
 */
async function executeGetOrderTracking(args = {}, authenticatedStudent = {}) {
  const orderId = String(args.orderId || '').trim();
  if (!orderId) {
    return {
      success: false,
      error: 'orderId is required to track an order (e.g. "ORD-123456").',
    };
  }

  if (!authenticatedStudent || !authenticatedStudent.isAuthenticated || !authenticatedStudent.studentId || authenticatedStudent.studentId === 'guest_student') {
    return {
      success: false,
      authenticated: false,
      error: 'You need to be logged in to track orders.',
    };
  }

  const order = await Order.findOne({
    $or: [
      { orderId: orderId },
      { orderId: orderId.toUpperCase() },
      { orderId: new RegExp('^' + escapeRegex(orderId) + '$', 'i') },
      { tokenNumber: orderId },
    ],
  }).lean();

  if (!order) {
    return {
      success: false,
      found: false,
      error: `Order #${orderId} was not found. Please double-check your order ID.`,
    };
  }

  const identities = await resolveStudentIdentities(authenticatedStudent);
  const isOwner = identities.some(id => String(id).toLowerCase() === String(order.studentId).toLowerCase());

  if (!isOwner) {
    return {
      success: false,
      found: true,
      forbidden: true,
      error: 'For privacy and security, I can only provide details and tracking for your own orders.',
    };
  }

  const tokenNumber = order.tokenNumber || (order.orderId ? order.orderId.slice(-3) : '');
  const itemsSummary = (order.items || []).map(i => `${i.name} x${i.quantity || 1}`).join(', ');
  const timeline = buildOrderTimeline(order);

  return {
    success: true,
    found: true,
    orderId: order.orderId,
    tokenNumber,
    canteenId: order.canteenId,
    canteenName: order.pickupCanteenName || order.canteenId,
    status: order.status,
    statusExplanation: getStatusExplanation(order.status, order.orderId, tokenNumber),
    paymentStatus: order.paymentStatus || 'PAID',
    totalAmount: order.totalAmount,
    totalFormatted: `₹${order.totalAmount}`,
    itemsSummary,
    items: (order.items || []).map(i => ({
      name: i.name,
      quantity: i.quantity || 1,
      price: i.price,
      subtotal: i.subtotal || (i.price * (i.quantity || 1)),
    })),
    customerPickupTime: order.customerPickupTime || 'ASAP',
    estimatedReadyTime: order.estimatedReadyTime || 'In preparation',
    pickupLocation: order.pickupLocation || order.pickupCounter || 'Counter 1',
    prepMinutes: order.estimatedPrepMinutes || 7,
    timeline,
    statusHistory: (order.statusHistory || []).map(sh => ({
      status: sh.status,
      timestamp: sh.timestampLocal || (sh.timestamp ? sh.timestamp.toISOString() : ''),
    })),
  };
}

/**
 * Tool: get_menu_items
 */
async function executeGetMenuItems(args = {}, requestContext = {}) {
  const canteenId = (args.canteenId || requestContext.selectedCanteenId || '').trim();
  if (!canteenId) {
    return {
      success: false,
      error: 'canteenId is required. Please specify a canteen (e.g. canteen_33, canteen_34, canteen_38).',
    };
  }

  const canteen = await Canteen.findOne({ id: canteenId }).lean();
  const canteenName = canteen ? canteen.name : canteenId;

  const queryFilter = { canteenId };

  if (args.availableOnly !== false) {
    queryFilter.available = true;
  }

  if (args.category && typeof args.category === 'string' && args.category.trim()) {
    queryFilter.category = new RegExp('^' + escapeRegex(args.category.trim()), 'i');
  }

  if (args.query && typeof args.query === 'string' && args.query.trim()) {
    const escaped = escapeRegex(args.query.trim());
    queryFilter.$or = [
      { name: new RegExp(escaped, 'i') },
      { description: new RegExp(escaped, 'i') },
      { ingredients: new RegExp(escaped, 'i') },
    ];
  }

  const items = await Item.find(queryFilter)
    .select('id name description price category available stock preparationTime prepMinutes rating reviewCount ingredients isCustom')
    .sort({ rating: -1, orderCount: -1 })
    .limit(25)
    .lean();

  return {
    success: true,
    canteenId,
    canteenName,
    isCanteenOpen: canteen ? canteen.isOpen : true,
    totalFound: items.length,
    items: items.map(item => ({
      itemId: item.id,
      name: item.name,
      description: item.description || '',
      price: item.price,
      priceFormatted: `₹${item.price}`,
      category: item.category,
      available: item.available,
      stock: item.stock,
      prepTime: item.preparationTime || `${item.prepMinutes || 7} min`,
      rating: item.rating || 4.5,
      reviewCount: item.reviewCount || 0,
      ingredients: item.ingredients || [],
    })),
  };
}

/**
 * Tool: get_canteen_details
 */
async function executeGetCanteenDetails(args = {}, requestContext = {}) {
  const canteenId = (args.canteenId || requestContext.selectedCanteenId || '').trim();
  if (!canteenId) {
    return {
      success: false,
      error: 'canteenId is required (e.g. canteen_33, canteen_34, canteen_38).',
    };
  }

  const canteen = await Canteen.findOne({
    $or: [{ id: canteenId }, { block: canteenId }, { name: new RegExp(escapeRegex(canteenId), 'i') }],
  }).lean();

  if (!canteen) {
    return {
      success: false,
      error: `Canteen "${canteenId}" was not found in QuickBite records. Available canteens are Block 33 (Govinda's Kitchen), Block 34 (Talk of the Town), and Block 38 (Cafe).`,
    };
  }

  return {
    success: true,
    canteenId: canteen.id,
    name: canteen.name,
    block: canteen.block,
    location: canteen.location || `Block ${canteen.block}`,
    floor: canteen.floorInfo || 'Ground Floor',
    isOpen: Boolean(canteen.isOpen),
    closeReason: canteen.closeReason || '',
    operatingHours: canteen.timings || '7:00 AM – 10:00 PM',
    specialty: canteen.specialty || '',
    avgWaitMinutes: canteen.avgWaitMinutes || 5,
  };
}

/**
 * Tool: get_cart_details
 */
async function executeGetCartDetails(args = {}, authenticatedStudent = {}, requestContext = {}) {
  if (!authenticatedStudent || !authenticatedStudent.isAuthenticated || !authenticatedStudent.studentId || authenticatedStudent.studentId === 'guest_student') {
    return {
      success: false,
      authenticated: false,
      error: 'Please log in to view your cart details.',
    };
  }

  const identities = await resolveStudentIdentities(authenticatedStudent);

  let cartDoc = await Cart.findOne({ studentId: { $in: identities } }).lean();

  let rawItems = cartDoc?.items;
  let canteenId = cartDoc?.canteenId || requestContext.selectedCanteenId || '';

  if ((!rawItems || rawItems.length === 0) && requestContext.cartItems && Array.isArray(requestContext.cartItems)) {
    rawItems = requestContext.cartItems;
    if (!canteenId && requestContext.cartCanteenId) {
      canteenId = requestContext.cartCanteenId;
    }
  }

  if (!rawItems || rawItems.length === 0) {
    return {
      success: true,
      isEmpty: true,
      itemCount: 0,
      totalAmount: 0,
      totalFormatted: '₹0',
      message: 'Your cart is currently empty. Explore the canteen menu to add delicious items!',
    };
  }

  const itemIds = rawItems.map(it => it.itemId || it.id).filter(Boolean);
  const dbItems = await Item.find({ id: { $in: itemIds } }).lean();
  const dbItemMap = new Map(dbItems.map(d => [d.id, d]));

  let verifiedSubtotal = 0;
  const verifiedItems = [];

  for (const item of rawItems) {
    const itemId = item.itemId || item.id;
    const dbItem = dbItemMap.get(itemId);
    if (!dbItem) continue;

    const quantity = Math.max(parseInt(item.quantity, 10) || 1, 1);
    const verifiedPrice = dbItem.price;
    const subtotal = verifiedPrice * quantity;
    verifiedSubtotal += subtotal;

    verifiedItems.push({
      itemId: dbItem.id,
      name: dbItem.name,
      price: verifiedPrice,
      priceFormatted: `₹${verifiedPrice}`,
      quantity,
      subtotal,
      subtotalFormatted: `₹${subtotal}`,
      available: dbItem.available,
      prepTime: dbItem.preparationTime || `${dbItem.prepMinutes || 7} min`,
    });
  }

  let canteenName = canteenId;
  let isCanteenOpen = true;
  if (canteenId) {
    const canteen = await Canteen.findOne({ id: canteenId }).lean();
    if (canteen) {
      canteenName = canteen.name;
      isCanteenOpen = canteen.isOpen;
    }
  }

  return {
    success: true,
    isEmpty: verifiedItems.length === 0,
    canteenId,
    canteenName,
    isCanteenOpen,
    itemCount: verifiedItems.reduce((acc, it) => acc + it.quantity, 0),
    items: verifiedItems,
    subtotal: verifiedSubtotal,
    totalAmount: verifiedSubtotal,
    totalFormatted: `₹${verifiedSubtotal}`,
  };
}

/**
 * Tool: get_food_reviews
 */
async function executeGetFoodReviews(args = {}) {
  const itemId = String(args.itemId || '').trim();
  const canteenId = String(args.canteenId || '').trim();

  if (!itemId && !canteenId) {
    return {
      success: false,
      error: 'Either itemId or canteenId is required to retrieve reviews.',
    };
  }

  const query = {};
  let itemName = '';
  let itemRating = null;

  if (itemId) {
    query.itemId = itemId;
    const item = await Item.findOne({ id: itemId }).select('name rating reviewCount').lean();
    if (item) {
      itemName = item.name;
      itemRating = item.rating;
    }
  } else if (canteenId) {
    query.canteenId = canteenId;
  }

  const limit = Math.min(Math.max(parseInt(args.limit, 10) || 5, 1), 10);
  const reviews = await Review.find(query)
    .sort({ createdAt: -1 })
    .limit(limit)
    .lean();

  if (reviews.length === 0) {
    return {
      success: true,
      count: 0,
      itemName: itemName || undefined,
      averageRating: itemRating || undefined,
      reviews: [],
      message: itemId ? `No reviews have been written for ${itemName || 'this dish'} yet.` : 'No reviews found for this canteen yet.',
    };
  }

  const avgRating = reviews.reduce((acc, r) => acc + (r.rating || 5), 0) / reviews.length;

  return {
    success: true,
    count: reviews.length,
    itemId: itemId || undefined,
    itemName: itemName || undefined,
    averageRating: itemRating || parseFloat(avgRating.toFixed(1)),
    reviews: reviews.map(r => ({
      rating: r.rating,
      comment: r.comment || '',
      userName: r.userName || 'Campus Foodie',
      date: r.createdAt ? new Date(r.createdAt).toLocaleDateString('en-IN') : '',
    })),
  };
}

/**
 * Tool: search_food_item
 * Searches for a specific dish by name across campus canteens.
 * First checks target canteen (or selected canteen); if not found, checks all canteens.
 * Returns availability, price, canteen name, prep time, or realistic alternatives if not found anywhere.
 */
async function executeSearchFoodItem(args = {}, requestContext = {}) {
  const rawQuery = String(args.dishName || '').trim();
  if (!rawQuery) {
    return {
      success: false,
      error: 'dishName is required to search for a food item.',
    };
  }

  const sanitizedQuery = rawQuery
    .replace(/^(are\s+there\s+any|are\s+there|is\s+there|do\s+you\s+have|can\s+i\s+get|what\s+is\s+the\s+price\s+of|what\s+is\s+the\s+price|how\s+much\s+is|show\s+me|find)\s+/i, '')
    .replace(/\s+(available|available\s+today|on\s+the\s+menu|in\s+canteen|here|please)\??$/i, '')
    .trim();

  const searchKeyword = sanitizedQuery || rawQuery;
  const escaped = escapeRegex(searchKeyword);
  const targetCanteenId = (args.canteenId || requestContext.selectedCanteenId || '').trim();

  const allCanteens = await Canteen.find({}).lean();
  const canteenMap = new Map((allCanteens || []).map(c => [c.id, c]));

  let targetItems = [];
  if (targetCanteenId) {
    targetItems = await Item.find({
      canteenId: targetCanteenId,
      name: new RegExp(escaped, 'i'),
    }).lean();
  }

  const allMatchingItems = await Item.find({
    name: new RegExp(escaped, 'i'),
  }).lean();

  if (allMatchingItems.length > 0) {
    const formatted = allMatchingItems.map(item => {
      const c = canteenMap.get(item.canteenId);
      const isAvailable = item.available !== false;
      return {
        id: item.id,
        name: item.name,
        price: item.price,
        priceFormatted: `₹${item.price}`,
        canteenId: item.canteenId,
        canteenName: c ? c.name : item.canteenId,
        available: isAvailable,
        isAvailable,
        rating: item.rating || 4.5,
        prepTime: item.preparationTime || `${item.prepMinutes || 7} min`,
        category: item.category,
      };
    });

    const availableItems = formatted.filter(i => i.isAvailable);
    const hasAvailable = availableItems.length > 0;

    let responseMessage = '';
    if (hasAvailable) {
      const top = availableItems[0];
      responseMessage = `Yes! ${top.name} is available at ${top.canteenName} for ${top.priceFormatted} (Rating: ${top.rating}⭐, Prep time: ${top.prepTime}).`;
      if (availableItems.length > 1) {
        responseMessage += ` It is also available at ${availableItems.slice(1).map(i => `${i.canteenName} (${i.priceFormatted})`).join(', ')}.`;
      }
    } else {
      const top = formatted[0];
      responseMessage = `${top.name} is on the menu at ${top.canteenName} (${top.priceFormatted}), but it is currently SOLD OUT / unavailable.`;
    }

    return {
      success: true,
      found: true,
      dishSearched: searchKeyword,
      count: formatted.length,
      isAvailable: hasAvailable,
      items: formatted,
      message: responseMessage,
    };
  }

  let alternativeItems = [];
  if (targetCanteenId) {
    alternativeItems = await Item.find({
      canteenId: targetCanteenId,
      available: { $ne: false },
    }).sort({ rating: -1, orderCount: -1 }).limit(3).lean();
  }
  if (alternativeItems.length === 0) {
    alternativeItems = await Item.find({
      available: { $ne: false },
    }).sort({ rating: -1, orderCount: -1 }).limit(3).lean();
  }

  const alternatives = alternativeItems.map(item => ({
    id: item.id,
    name: item.name,
    price: item.price,
    priceFormatted: `₹${item.price}`,
    canteenId: item.canteenId,
    canteenName: canteenMap.get(item.canteenId)?.name || item.canteenId,
    rating: item.rating || 4.5,
  }));

  const altList = alternatives.map(a => `${a.name} (${a.priceFormatted} at ${a.canteenName})`).join(', ');

  return {
    success: true,
    found: false,
    isAvailable: false,
    dishSearched: searchKeyword,
    items: [],
    alternatives,
    message: `"${searchKeyword}" is not currently offered on any campus canteen menu. However, you might enjoy these available options: ${altList}.`,
  };
}

/**
 * Tool: get_favourite_items
 * Retrieves student's saved favourite food items with live pricing and availability.
 */
async function executeGetFavouriteItems(args = {}, authenticatedStudent = {}) {
  const identities = await resolveStudentIdentities(authenticatedStudent);
  if (!identities || identities.length === 0) {
    return {
      success: false,
      authenticated: false,
      error: 'You need to be logged in to view your favourite items.',
    };
  }

  const student = await User.findOne({
    $or: identities.map(id => ({ uid: id }))
      .concat(identities.map(id => ({ registrationNumber: id })))
      .concat(identities.map(id => ({ email: id.toLowerCase() }))),
  }).lean();

  const favoriteIds = student?.favorites || [];
  if (favoriteIds.length === 0) {
    return {
      success: true,
      count: 0,
      items: [],
      message: "You haven't added any items to your favourites yet. Tap the heart icon on any dish to save your favourites!",
    };
  }

  const allCanteens = await Canteen.find({}).lean();
  const canteenMap = new Map((allCanteens || []).map(c => [c.id, c]));

  const limit = Math.min(Math.max(parseInt(args.limit, 10) || 10, 1), 20);
  const items = await Item.find({
    id: { $in: favoriteIds },
  }).limit(limit).lean();

  const mapped = items.map(item => ({
    id: item.id,
    name: item.name,
    price: item.price,
    priceFormatted: `₹${item.price}`,
    canteenId: item.canteenId,
    canteenName: canteenMap.get(item.canteenId)?.name || item.canteenId,
    available: item.available !== false,
    rating: item.rating || 4.5,
    category: item.category,
    prepTime: item.preparationTime || `${item.prepMinutes || 7} min`,
  }));

  const listText = mapped.map(i => `• ${i.name} - ${i.priceFormatted} (${i.canteenName}) [${i.available ? 'Available' : 'Sold Out'}]`).join('\n');

  return {
    success: true,
    count: mapped.length,
    items: mapped,
    message: `Here are your saved favourite dishes:\n\n${listText}`,
  };
}

/**
 * Tool: get_recent_orders
 * Retrieves student's past order history from MongoDB.
 */
async function executeGetRecentOrders(args = {}, authenticatedStudent = {}) {
  const identities = await resolveStudentIdentities(authenticatedStudent);
  if (!identities || identities.length === 0) {
    return {
      success: false,
      authenticated: false,
      error: 'You need to be logged in to view your recent orders.',
    };
  }

  const limit = Math.min(Math.max(parseInt(args.limit, 10) || 5, 1), 10);
  const orders = await Order.find({
    studentId: { $in: identities },
  }).sort({ createdAt: -1, orderPlacedAt: -1 }).limit(limit).lean();

  if (!orders || orders.length === 0) {
    return {
      success: true,
      count: 0,
      orders: [],
      message: "You haven't placed any orders yet. Explore the menu to place your first order!",
    };
  }

  const mapped = orders.map(o => {
    const itemsSummary = (o.items || []).map(i => `${i.name} x${i.quantity}`).join(', ');
    const dateFormatted = o.orderPlacedAtLocal || o.orderTime || (o.createdAt ? new Date(o.createdAt).toLocaleDateString('en-IN') : 'Recent');
    return {
      orderId: o.orderId,
      canteenName: o.pickupCanteenName || o.canteenName || o.canteenId,
      status: o.status,
      items: itemsSummary,
      totalAmount: o.totalAmount,
      totalFormatted: `₹${o.totalAmount}`,
      orderDate: dateFormatted,
    };
  });

  const listText = mapped.map((o, idx) =>
    `${idx + 1}. Order #${o.orderId} at ${o.canteenName}: ${o.items} — ${o.totalFormatted} (${o.status})`
  ).join('\n');

  return {
    success: true,
    count: mapped.length,
    orders: mapped,
    message: `Here are your recent orders:\n\n${listText}`,
  };
}

/**
 * Tool: manage_cart
 * Adds, removes, or clears items in the student's cart with authoritative DB pricing.
 */
async function executeManageCart(args = {}, authenticatedStudent = {}, requestContext = {}) {
  const studentId = authenticatedStudent?.studentId;
  if (!studentId || studentId === 'guest_student') {
    return {
      success: false,
      authenticated: false,
      error: 'Please log in to update your cart.',
    };
  }

  const action = String(args.action || '').toLowerCase().trim();

  if (action === 'clear') {
    let cart = await Cart.findOne({ studentId });
    if (cart) {
      cart.items = [];
      cart.subtotal = 0;
      cart.totalAmount = 0;
      await cart.save();
    }
    return {
      success: true,
      action: 'clear',
      isEmpty: true,
      itemCount: 0,
      subtotal: 0,
      totalAmount: 0,
      totalFormatted: '₹0',
      message: 'Your cart has been cleared.',
    };
  }

  const dishQuery = String(args.dishNameOrId || '').trim();
  if (!dishQuery) {
    return {
      success: false,
      error: 'dishNameOrId is required to add or remove items.',
    };
  }

  const quantity = Math.max(parseInt(args.quantity, 10) || 1, 1);

  if (action === 'add') {
    const escaped = escapeRegex(dishQuery);
    const targetCanteenId = requestContext.selectedCanteenId;

    let item = null;
    if (targetCanteenId) {
      item = await Item.findOne({
        canteenId: targetCanteenId,
        $or: [{ id: dishQuery }, { name: new RegExp('^' + escaped + '$', 'i') }, { name: new RegExp(escaped, 'i') }],
      }).lean();
    }
    if (!item) {
      item = await Item.findOne({
        $or: [{ id: dishQuery }, { name: new RegExp('^' + escaped + '$', 'i') }, { name: new RegExp(escaped, 'i') }],
      }).lean();
    }

    if (!item) {
      return {
        success: false,
        error: `Could not find "${dishQuery}" on any canteen menu.`,
      };
    }

    if (item.available === false) {
      return {
        success: false,
        error: `Sorry, "${item.name}" is currently sold out and cannot be added to your cart.`,
      };
    }

    const verifiedPrice = item.price;

    let cart = await Cart.findOne({ studentId });
    if (!cart) {
      cart = new Cart({
        studentId,
        canteenId: item.canteenId,
        items: [],
        subtotal: 0,
        totalAmount: 0,
      });
    }

    if (cart.items.length > 0 && cart.canteenId && cart.canteenId !== item.canteenId) {
      cart.items = [];
      cart.canteenId = item.canteenId;
    } else if (cart.items.length === 0) {
      cart.canteenId = item.canteenId;
    }

    const existingIndex = cart.items.findIndex(i => i.itemId === item.id);
    if (existingIndex >= 0) {
      cart.items[existingIndex].quantity += quantity;
      cart.items[existingIndex].price = verifiedPrice;
    } else {
      cart.items.push({
        itemId: item.id,
        name: item.name,
        price: verifiedPrice,
        quantity,
      });
    }

    cart.subtotal = cart.items.reduce((sum, it) => sum + (it.price * it.quantity), 0);
    cart.totalAmount = cart.subtotal;
    cart.updatedAt = new Date();
    await cart.save();

    return {
      success: true,
      action: 'add',
      itemAdded: item.name,
      quantity,
      price: verifiedPrice,
      canteenId: cart.canteenId,
      itemCount: cart.items.length,
      subtotal: cart.subtotal,
      totalAmount: cart.totalAmount,
      totalFormatted: `₹${cart.totalAmount}`,
      message: `Added ${quantity}x ${item.name} (₹${verifiedPrice} each) to your cart. Cart total is now ₹${cart.totalAmount}.`,
    };
  }

  if (action === 'remove') {
    let cart = await Cart.findOne({ studentId });
    if (!cart || cart.items.length === 0) {
      return {
        success: false,
        error: 'Your cart is already empty.',
      };
    }

    const escaped = escapeRegex(dishQuery);
    const existingIndex = cart.items.findIndex(i =>
      i.itemId === dishQuery || new RegExp(escaped, 'i').test(i.name)
    );

    if (existingIndex < 0) {
      return {
        success: false,
        error: `"${dishQuery}" is not in your cart.`,
      };
    }

    const removedItemName = cart.items[existingIndex].name;
    if (cart.items[existingIndex].quantity <= quantity) {
      cart.items.splice(existingIndex, 1);
    } else {
      cart.items[existingIndex].quantity -= quantity;
    }

    cart.subtotal = cart.items.reduce((sum, it) => sum + (it.price * it.quantity), 0);
    cart.totalAmount = cart.subtotal;
    cart.updatedAt = new Date();
    await cart.save();

    return {
      success: true,
      action: 'remove',
      itemRemoved: removedItemName,
      itemCount: cart.items.length,
      subtotal: cart.subtotal,
      totalAmount: cart.totalAmount,
      totalFormatted: `₹${cart.totalAmount}`,
      message: `Removed ${removedItemName} from your cart. Cart total is now ₹${cart.totalAmount}.`,
    };
  }

  return {
    success: false,
    error: `Unknown cart action: "${action}". Supported actions are "add", "remove", and "clear".`,
  };
}

// SECURE TOOL DISPATCHER

/**
 * Executes a requested tool securely after validating registration and user context.
 */
async function executeTool({ toolName, args = {}, authenticatedStudent = {}, requestContext = {} }) {
  const sanitizedName = String(toolName || '').trim();

  switch (sanitizedName) {
    case 'get_order_status_summary':
      return await executeGetOrderStatusSummary(args, authenticatedStudent);

    case 'get_my_orders':
      return await executeGetMyOrders(args, authenticatedStudent);

    case 'get_order_tracking':
      return await executeGetOrderTracking(args, authenticatedStudent);

    case 'get_menu_items':
      return await executeGetMenuItems(args, requestContext);

    case 'get_canteen_details':
      return await executeGetCanteenDetails(args, requestContext);

    case 'get_cart_details':
      return await executeGetCartDetails(args, authenticatedStudent, requestContext);

    case 'get_food_reviews':
      return await executeGetFoodReviews(args);

    case 'search_food_item':
      return await executeSearchFoodItem(args, requestContext);

    case 'get_favourite_items':
      return await executeGetFavouriteItems(args, authenticatedStudent);

    case 'get_recent_orders':
      return await executeGetRecentOrders(args, authenticatedStudent);

    case 'manage_cart':
      return await executeManageCart(args, authenticatedStudent, requestContext);

    default:
      console.warn(`[AiToolsService] Unauthorized tool execution attempt: ${sanitizedName}`);
      return {
        success: false,
        error: `Tool "${sanitizedName}" is not registered or permitted in MunchMate backend.`,
      };
  }
}

module.exports = {
  TOOL_DECLARATIONS,
  executeTool,
  resolveStudentIdentities,
  getStatusExplanation,
  buildOrderTimeline,
  TERMINAL_STATUSES,
  executeSearchFoodItem,
  executeGetFavouriteItems,
  executeGetRecentOrders,
  executeManageCart,
};
