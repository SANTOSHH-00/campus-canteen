// Razorpay Payment Gateway Routes
const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const Razorpay = require('razorpay');
const Order = require('../models/Order');

// Initialize Razorpay client safely
const getRazorpayInstance = () => {
  const key_id = process.env.RAZORPAY_KEY_ID;
  const key_secret = process.env.RAZORPAY_KEY_SECRET;

  if (!key_id || !key_secret) {
    return null;
  }

  return new Razorpay({
    key_id: key_id,
    key_secret: key_secret,
  });
};

/**
 * GET /api/payments/config
 * Returns whether Razorpay is configured and the public Key ID for the client SDK
 */
router.get('/config', (req, res) => {
  const keyId = process.env.RAZORPAY_KEY_ID || '';
  const isEnabled = Boolean(process.env.RAZORPAY_KEY_ID && process.env.RAZORPAY_KEY_SECRET);
  res.json({
    success: true,
    isEnabled,
    keyId: keyId,
    currency: 'INR',
  });
});

/**
 * POST /api/payments/create-order
 * Creates an official Razorpay Order ID for checkout
 * Body: { amount: number (in Rupees), canteenId: string, orderId?: string, fulfillmentType?: string, items?: Array }
 */
router.post('/create-order', async (req, res) => {
  try {
    const { amount, canteenId, orderId, fulfillmentType, items } = req.body;

    let finalPayableAmount = Number(amount);

    // If items and canteenId are provided, compute authoritative amount on the backend
    if (canteenId && Array.isArray(items) && items.length > 0) {
      try {
        const Canteen = require('../models/Canteen');
        const canteen = await Canteen.findOne({ id: canteenId }).lean();
        if (canteen) {
          const Menu = require('../models/Menu');
          const menuList = await Menu.find({ canteenId }).lean();
          const priceMap = new Map();
          for (const m of menuList) {
            if (m.id) priceMap.set(m.id, m.price);
            if (m.name) priceMap.set(m.name.toLowerCase().trim(), m.price);
          }
          let itemsSubtotal = 0;
          for (const it of items) {
            const q = Math.max(1, parseInt(it.quantity, 10) || 1);
            const pr = (it.itemId && priceMap.get(it.itemId)) ||
                       (it.name && priceMap.get(it.name.toLowerCase().trim())) ||
                       (Number(it.price) || 0);
            itemsSubtotal += pr * q;
          }
          const isTakeaway = String(fulfillmentType || '').toUpperCase() !== 'DINE_IN';
          const packaging = isTakeaway ? (typeof canteen.packagingCharge === 'number' ? canteen.packagingCharge : 10) : 0;
          const taxRate = typeof canteen.taxRate === 'number' ? canteen.taxRate : 0.05;
          const taxes = Math.round(itemsSubtotal * taxRate);
          finalPayableAmount = itemsSubtotal + packaging + taxes;
        }
      } catch (err) {
        console.warn('[Payments] Could not recalculate authoritative amount, using client amount:', err.message);
      }
    }

    if (!finalPayableAmount || isNaN(finalPayableAmount) || Number(finalPayableAmount) <= 0) {
      return res.status(400).json({
        success: false,
        message: 'A valid payment amount is required',
      });
    }

    const rzp = getRazorpayInstance();
    const amountInPaise = Math.round(Number(finalPayableAmount) * 100);

    // If Razorpay keys are not yet configured in .env, return a simulated order for smooth testing
    if (!rzp) {
      console.warn('[Payments] RAZORPAY_KEY_ID or RAZORPAY_KEY_SECRET not set in .env. Falling back to test simulation mode.');
      return res.json({
        success: true,
        simulation: true,
        razorpayOrderId: `order_sim_${Date.now()}_${Math.floor(Math.random() * 9000 + 1000)}`,
        amount: amountInPaise,
        currency: 'INR',
        keyId: process.env.RAZORPAY_KEY_ID || 'rzp_test_simulation',
      });
    }

    const receiptId = (orderId ? `rcpt_${orderId.replace(/[^a-zA-Z0-9]/g, '_')}` : `rcpt_${Date.now()}`).substring(0, 40);

    const options = {
      amount: amountInPaise,
      currency: 'INR',
      receipt: receiptId,
      notes: {
        canteenId: canteenId || 'default_canteen',
        appOrderId: orderId || '',
        fulfillmentType: String(fulfillmentType || 'TAKEAWAY').toUpperCase(),
      },
    };

    const order = await rzp.orders.create(options);

    console.log(`[Payments] Razorpay order created: ${order.id} for ₹${amount} (${amountInPaise} paise)`);

    return res.json({
      success: true,
      simulation: false,
      razorpayOrderId: order.id,
      amount: order.amount,
      currency: order.currency,
      keyId: process.env.RAZORPAY_KEY_ID,
    });
  } catch (error) {
    console.error('[Payments] Failed to create Razorpay order:', error);
    return res.status(500).json({
      success: false,
      message: error?.error?.description || error.message || 'Failed to initiate payment order',
    });
  }
});

/**
 * POST /api/payments/verify-payment
 * Verifies Razorpay HMAC-SHA256 signature and updates order payment status
 * Body: { razorpay_order_id, razorpay_payment_id, razorpay_signature, orderId? }
 */
router.post('/verify-payment', async (req, res) => {
  try {
    const { razorpay_order_id, razorpay_payment_id, razorpay_signature, orderId } = req.body;

    if (!razorpay_order_id || !razorpay_payment_id) {
      return res.status(400).json({
        success: false,
        message: 'Missing required payment parameters',
      });
    }

    const key_secret = process.env.RAZORPAY_KEY_SECRET;

    // In simulation mode (when no keys provided or mock order)
    if (!key_secret || razorpay_order_id.startsWith('order_sim_')) {
      console.log(`[Payments] Simulation payment verified for order: ${orderId || razorpay_order_id}`);
      if (orderId) {
        await Order.findOneAndUpdate(
          { orderId },
          {
            paymentStatus: 'PAID',
            paymentMethod: 'RAZORPAY',
            razorpayOrderId: razorpay_order_id,
            razorpayPaymentId: razorpay_payment_id,
            razorpaySignature: razorpay_signature || 'simulation',
          }
        );
      }
      return res.json({
        success: true,
        simulation: true,
        message: 'Simulation payment verified',
        paymentId: razorpay_payment_id,
      });
    }

    // Verify cryptographic signature
    const hmac = crypto.createHmac('sha256', key_secret);
    hmac.update(`${razorpay_order_id}|${razorpay_payment_id}`);
    const generatedSignature = hmac.digest('hex');

    const isSignatureValid = generatedSignature === razorpay_signature;

    if (!isSignatureValid) {
      console.error(`[Payments] Invalid signature for payment ${razorpay_payment_id}. Expected ${generatedSignature}, got ${razorpay_signature}`);
      return res.status(400).json({
        success: false,
        message: 'Payment verification failed: Invalid signature',
      });
    }

    console.log(`[Payments] Payment verified successfully: ${razorpay_payment_id} for order ${razorpay_order_id}`);

    // If order already exists in database, update payment details
    if (orderId) {
      await Order.findOneAndUpdate(
        { orderId },
        {
          paymentStatus: 'PAID',
          paymentMethod: 'RAZORPAY',
          razorpayOrderId: razorpay_order_id,
          razorpayPaymentId: razorpay_payment_id,
          razorpaySignature: razorpay_signature,
        }
      );
    }

    return res.json({
      success: true,
      simulation: false,
      message: 'Payment verified successfully',
      paymentId: razorpay_payment_id,
    });
  } catch (error) {
    console.error('[Payments] Payment verification error:', error);
    return res.status(500).json({
      success: false,
      message: error.message || 'Payment verification failed',
    });
  }
});

/**
 * Process a refund through Razorpay or recorded simulation mode
 * @param {Object} order - The Order document
 * @param {string} [reason] - Rejection or cancellation reason
 * @returns {Promise<{ success: boolean, refundId?: string, status: string, error?: string }>}
 */
async function processRefund(order, reason = 'Order rejected by canteen') {
  try {
    if (!order) return { success: false, status: 'NONE', error: 'Order not found' };

    const amount = order.totalAmount || order.totalPrice || 0;
    if (amount <= 0) {
      return { success: true, refundId: 'zero_amount', status: 'PROCESSED' };
    }

    const rzp = getRazorpayInstance();
    const paymentId = order.razorpayPaymentId || '';

    // If simulated or test payment or no keys configured
    if (!rzp || !paymentId || paymentId.startsWith('pay_sim_')) {
      const simRefundId = `rfnd_sim_${Date.now()}_${Math.floor(Math.random() * 9000 + 1000)}`;
      console.log(`[Payments:Refund] Simulated refund ${simRefundId} processed for order ${order.orderId} (₹${amount}). Reason: ${reason}`);
      return {
        success: true,
        refundId: simRefundId,
        status: 'PROCESSED',
      };
    }

    // Official Razorpay API refund
    const amountInPaise = Math.round(Number(amount) * 100);
    const refund = await rzp.payments.refund(paymentId, {
      amount: amountInPaise,
      notes: {
        reason: (reason || 'Order rejected by canteen').substring(0, 255),
        orderId: order.orderId,
      },
    });

    console.log(`[Payments:Refund] Razorpay refund successful for order ${order.orderId}: Refund ID ${refund.id}`);
    return {
      success: true,
      refundId: refund.id,
      status: 'PROCESSED',
    };
  } catch (err) {
    console.error(`[Payments:Refund] Razorpay refund execution failed for order ${order.orderId}:`, err.message);
    return {
      success: false,
      error: err.message,
      status: 'FAILED',
    };
  }
}

router.processRefund = processRefund;
router.getRazorpayInstance = getRazorpayInstance;

module.exports = router;
