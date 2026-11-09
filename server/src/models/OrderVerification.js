const mongoose = require('mongoose');

const orderVerificationSchema = new mongoose.Schema(
  {
    orderId: {
      type: String,
      required: true,
      index: true,
    },
    studentId: {
      type: String,
      required: true,
      index: true,
    },
    canteenId: {
      type: String,
      required: true,
      index: true,
    },
    tokenHash: {
      type: String,
      required: true,
      unique: true,
      index: true,
    },
    status: {
      type: String,
      enum: ['ACTIVE', 'REDEEMED', 'EXPIRED', 'CANCELLED'],
      default: 'ACTIVE',
      index: true,
    },
    expiresAt: {
      type: Date,
      required: true,
    },
    usedAt: {
      type: Date,
      default: null,
    },
    verifiedBy: {
      type: String, // Owner UID
      default: null,
    },
  },
  {
    timestamps: true,
    collection: 'order_verifications',
  }
);

// High-performance compound indexes for atomic queries and state lookups
orderVerificationSchema.index({ orderId: 1, status: 1 });
orderVerificationSchema.index({ tokenHash: 1, status: 1 });
orderVerificationSchema.index({ canteenId: 1, status: 1 });

// TTL index to automatically clean up aged verification records after 30 days.
// NOTE: Backend code must always check `expiresAt` directly and never rely solely on TTL.
orderVerificationSchema.index({ expiresAt: 1 }, { expireAfterSeconds: 30 * 24 * 60 * 60 });

module.exports = mongoose.model('OrderVerification', orderVerificationSchema);
