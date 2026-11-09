const crypto = require('crypto');
const bwipjs = require('bwip-js');
const Order = require('../models/Order');
const OrderVerification = require('../models/OrderVerification');
const { hashVerificationToken } = require('./qrVerificationService');

/**
 * Generate a cryptographically secure, unpredictable verification token.
 * Format: "QB-" + 16 random uppercase hexadecimal characters (64 bits of entropy).
 * Suitable for high-density Code 128 1D barcode scanning on any mobile/desktop camera.
 */
function generateVerificationToken() {
  const randomHex = crypto.randomBytes(8).toString('hex').toUpperCase();
  return `QB-${randomHex}`;
}

/**
 * Dynamically generate a crisp, high-contrast black-and-white Code 128 barcode image (PNG Data URL).
 * Pure white background (#FFFFFF) and pure black bars (#000000), matching the reference design.
 *
 * @param {string} token - The alphanumeric verification token to encode
 * @returns {Promise<string>} Base64 Data URL (data:image/png;base64,...)
 */
async function generateCode128BarcodeDataUrl(token) {
  if (!token || typeof token !== 'string') {
    throw new Error('Token must be a non-empty string');
  }

  const cleanToken = token.trim();
  const pngBuffer = await bwipjs.toBuffer({
    bcid: 'code128',
    text: cleanToken,
    scale: 3,                 // Sharp resolution for mobile screens
    height: 12,               // Professional aspect ratio
    includetext: false,       // Text rendered with custom typography in UI
    textxalign: 'center',
    backgroundcolor: 'ffffff', // Pure white background
    barcolor: '000000',        // Pure black vertical bars
    paddingwidth: 10,
    paddingheight: 6,
  });

  return `data:image/png;base64,${pngBuffer.toString('base64')}`;
}

/**
 * Ensures an Order has an active, cryptographically unique Code 128 barcode.
 * If the order already has one and it's active, returns the existing barcode (idempotent across app refreshes).
 * Handles duplicate-key collisions gracefully by retrying with a new cryptographically random token.
 *
 * @param {Object} order - Mongoose Order document or plain object
 * @param {Object} [options]
 * @param {boolean} [options.forceRotate=false]
 * @returns {Promise<{ token: string, barcodeDataUrl: string, barcodeStatus: string, format: string }>}
 */
async function ensureOrderBarcode(order, options = {}) {
  const { forceRotate = false } = options;

  // 1. Fast check if in-memory order object already has an active barcode
  if (
    !forceRotate &&
    order.verificationToken &&
    order.barcodeDataUrl &&
    order.barcodeStatus === 'ACTIVE'
  ) {
    return {
      token: order.verificationToken,
      barcodeDataUrl: order.barcodeDataUrl,
      barcodeStatus: order.barcodeStatus,
      format: order.barcodeFormat || 'CODE128',
      expiresAt: order.expiresAt,
    };
  }

  // 2. Check MongoDB to see if this order already has a persistent active barcode
  const dbOrder = await Order.findOne({ orderId: order.orderId });
  if (
    !forceRotate &&
    dbOrder &&
    dbOrder.verificationToken &&
    dbOrder.barcodeDataUrl &&
    dbOrder.barcodeStatus === 'ACTIVE'
  ) {
    // Populate in-memory order reference
    order.verificationToken = dbOrder.verificationToken;
    order.barcodeDataUrl = dbOrder.barcodeDataUrl;
    order.barcodeStatus = dbOrder.barcodeStatus;
    order.barcodeFormat = dbOrder.barcodeFormat || 'CODE128';
    order.expiresAt = dbOrder.expiresAt;

    return {
      token: dbOrder.verificationToken,
      barcodeDataUrl: dbOrder.barcodeDataUrl,
      barcodeStatus: dbOrder.barcodeStatus,
      format: dbOrder.barcodeFormat || 'CODE128',
      expiresAt: dbOrder.expiresAt,
    };
  }

  const maxRetries = 5;
  let attempt = 0;
  let savedOrder = null;
  let rawToken = '';
  let barcodeDataUrl = '';

  while (attempt < maxRetries) {
    attempt++;
    rawToken = generateVerificationToken();
    try {
      barcodeDataUrl = await generateCode128BarcodeDataUrl(rawToken);

      const now = new Date();
      // Token valid for 24 hours (covers daily canteen operational hours)
      const expiresAt = new Date(now.getTime() + 24 * 60 * 60 * 1000);

      savedOrder = await Order.findOneAndUpdate(
        { orderId: order.orderId },
        {
          $set: {
            verificationToken: rawToken,
            barcodeFormat: 'CODE128',
            barcodeStatus: 'ACTIVE',
            barcodeDataUrl: barcodeDataUrl,
            generatedAt: now,
            expiresAt: expiresAt,
          },
        },
        { new: true }
      );

      if (savedOrder) {
        order.verificationToken = savedOrder.verificationToken;
        order.barcodeDataUrl = savedOrder.barcodeDataUrl;
        order.barcodeStatus = savedOrder.barcodeStatus;
        order.barcodeFormat = savedOrder.barcodeFormat;
        order.expiresAt = savedOrder.expiresAt;
      }

      // Also sync OrderVerification collection for unified verification history
      try {
        const tokenHash = hashVerificationToken(rawToken);
        await OrderVerification.updateMany(
          { orderId: order.orderId, status: 'ACTIVE' },
          { $set: { status: 'EXPIRED' } }
        );
        await OrderVerification.create({
          orderId: order.orderId,
          studentId: order.studentId || '',
          canteenId: order.canteenId,
          tokenHash: tokenHash,
          status: 'ACTIVE',
          expiresAt: expiresAt,
        });
      } catch (syncErr) {
        // Non-blocking sync error
        console.warn('[BarcodeService] OrderVerification sync notice:', syncErr.message);
      }

      break;
    } catch (err) {
      // If duplicate token key error (E11000), loop and generate a new random token
      if (err.code === 11000 || (err.message && err.message.includes('duplicate key'))) {
        console.warn(`[BarcodeService] Duplicate token collision on attempt ${attempt}, regenerating...`);
        continue;
      }
      throw err;
    }
  }

  if (!savedOrder) {
    throw new Error('Failed to generate unique verification token after multiple attempts.');
  }

  return {
    token: savedOrder.verificationToken,
    barcodeDataUrl: savedOrder.barcodeDataUrl,
    barcodeStatus: savedOrder.barcodeStatus,
    format: savedOrder.barcodeFormat,
    expiresAt: savedOrder.expiresAt,
  };
}

module.exports = {
  generateVerificationToken,
  generateCode128BarcodeDataUrl,
  ensureOrderBarcode,
};
