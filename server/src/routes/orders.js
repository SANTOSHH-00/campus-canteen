const express = require('express');
const router = express.Router();
const Order = require('../models/Order');
const { broadcastOrderStatus, broadcastNewOrder } = require('../services/socketService');
const { sendPushToUser } = require('../services/fcmService');

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
    const order = await Order.findOne({ orderId: req.params.id }).lean();
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
    const order = await Order.create(orderData);

    // Real-time WebSocket Broadcast & Push Trigger
    try {
      broadcastNewOrder(order);
      if (order.canteenId) {
        sendPushToUser(order.canteenId, {
          title: `New Order #${order.tokenNumber || order.orderId}`,
          body: `Received ${order.items?.length || 1} items totaling ₹${order.totalPrice}.`,
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
    const { status } = req.body;
    const updated = await Order.findOneAndUpdate(
      { orderId: req.params.id },
      { $set: { status } },
      { new: true }
    );
    if (!updated) return res.status(404).json({ error: 'Order not found' });

    // Real-time WebSocket Broadcast & Push Trigger
    try {
      broadcastOrderStatus(updated);

      const statusTitle = status === 'PREPARING'
        ? `Order #${updated.tokenNumber || updated.orderId} is being Prepared 🍳`
        : status === 'READY'
        ? `Order #${updated.tokenNumber || updated.orderId} is Ready for Pickup! 🔔`
        : status === 'COMPLETED'
        ? `Order #${updated.tokenNumber || updated.orderId} Completed 🎉`
        : `Order #${updated.tokenNumber || updated.orderId} Status: ${status}`;

      const statusBody = status === 'READY'
        ? `Your meal is hot and ready at Counter ${updated.pickupCounter || '1'}. Please collect your order!`
        : `Current status updated to ${status}.`;

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
