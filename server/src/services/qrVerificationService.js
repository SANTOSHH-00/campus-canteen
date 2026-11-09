const crypto = require('crypto');
const QRCode = require('qrcode');
const OrderVerification = require('../models/OrderVerification');
const Order = require('../models/Order');

const DEFAULT_VALIDITY_MINUTES = 15;

/**
 * Generate a cryptographically secure random token (256 bits of entropy)
 */
function generateVerificationToken() {
  return crypto.randomBytes(32).toString('hex');
}

/**
 * Hash a verification token using SHA-256
 */
function hashVerificationToken(token) {
  if (!token || typeof token !== 'string') return '';
  return crypto.createHash('sha256').update(token.trim()).digest('hex');
}

/**
 * Generate a high-contrast QR Code data URL (PNG) from the verification token
 */
async function generateQrDataUrl(token) {
  return QRCode.toDataURL(token.trim(), {
    errorCorrectionLevel: 'M',
    margin: 2,
    width: 256,
    color: {
      dark: '#111827', // Crisp dark slate
      light: '#FFFFFF', // Clean white background
    },
  });
}

/**
 * Atomically create or rotate a verification record for an order.
 * Strictly stores only the SHA-256 tokenHash in MongoDB.
 *
 * @param {Object} params
 * @param {string} params.orderId
 * @param {string} params.studentId
 * @param {string} params.canteenId
 * @param {number} [params.validityMinutes=15]
 * @returns {Promise<{ rawToken: string, qrDataUrl: string, expiresAt: Date, status: string }>}
 */
async function createOrRotateOrderVerification({ orderId, studentId, canteenId, validityMinutes = DEFAULT_VALIDITY_MINUTES }) {
  const rawToken = generateVerificationToken();
  const tokenHash = hashVerificationToken(rawToken);
  const now = new Date();
  const expiresAt = new Date(now.getTime() + validityMinutes * 60 * 1000);

  // Invalidate any existing active tokens for this order by marking them EXPIRED
  await OrderVerification.updateMany(
    { orderId, status: 'ACTIVE' },
    { $set: { status: 'EXPIRED' } }
  );

  // Insert new active verification record
  const verification = await OrderVerification.create({
    orderId,
    studentId,
    canteenId,
    tokenHash,
    status: 'ACTIVE',
    expiresAt,
  });

  // Generate QR code data URL from the raw plaintext token
  const qrDataUrl = await generateQrDataUrl(rawToken);

  return {
    verificationId: verification._id,
    rawToken,
    qrDataUrl,
    expiresAt,
    status: 'ACTIVE',
    validityMinutes,
  };
}

module.exports = {
  DEFAULT_VALIDITY_MINUTES,
  generateVerificationToken,
  hashVerificationToken,
  generateQrDataUrl,
  createOrRotateOrderVerification,
};

