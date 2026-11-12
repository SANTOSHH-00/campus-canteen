const mongoose = require('mongoose');

const orderItemSchema = new mongoose.Schema(
  {
    itemId: { type: String, required: true },
    name: { type: String, required: true },
    price: { type: Number, required: true },
    quantity: { type: Number, required: true, default: 1 },
    subtotal: { type: Number, default: 0 },
    prepMinutes: { type: Number, default: 7 },
    selectedOption: { type: String, default: null },
    selectedAddons: { type: [String], default: [] },
  },
  { _id: false }
);

const statusHistorySchema = new mongoose.Schema(
  {
    status: { type: String, required: true },
    timestamp: { type: Date, default: Date.now },
    timestampLocal: { type: String, default: '' },
    changedBy: { type: String, default: 'SYSTEM' },
    changedByRole: { type: String, default: 'SYSTEM' },
  },
  { _id: false }
);

const orderSchema = new mongoose.Schema(
  {
    orderId: { type: String, required: true, unique: true, index: true },
    studentId: { type: String, required: true, index: true },
    studentName: { type: String, default: '' },
    studentPhone: { type: String, default: '' },
    studentCourse: { type: String, default: '' },
    canteenId: { type: String, required: true, index: true },
    items: { type: [orderItemSchema], default: [] },
    totalAmount: { type: Number, required: true },
    status: {
      type: String,
      enum: ['NEW', 'WAITING', 'CONFIRMED', 'PREPARING', 'READY', 'COMPLETED', 'PICKED_UP', 'CANCELLED'],
      default: 'PREPARING',
      index: true,
    },
    paymentStatus: { type: String, default: 'PAID' },
    tokenNumber: { type: String, default: '' },
    pickupPreference: { type: String, default: 'Pickup ASAP' },
    customerPickupTime: { type: String, default: '' },
    estimatedCompletionTime: { type: String, default: '' },
    pickupCanteenName: { type: String, default: '' },
    pickupLocation: { type: String, default: '' },
    pickupCounter: { type: String, default: 'Counter 1' },
    estimatedReadyTime: { type: String, default: '' },
    estimatedPrepMinutes: { type: Number, default: 7 },
    // Human-readable local campus time strings (e.g. "12 Sep 2026", "1:00 AM", "12 Sep 2026, 01:00:14 AM")
    orderDate: { type: String, default: '' },
    orderTime: { type: String, default: '' },
    orderPlacedAtLocal: { type: String, default: '' },
    confirmedAtLocal: { type: String, default: '' },
    preparingAtLocal: { type: String, default: '' },
    readyAtLocal: { type: String, default: '' },
    pickedUpAtLocal: { type: String, default: '' },
    completedAtLocal: { type: String, default: '' },
    cancelledAtLocal: { type: String, default: '' },
    // Server-authoritative status event timestamps aligned with local campus time
    orderPlacedAt: { type: Date, default: Date.now, index: true },
    confirmedAt: { type: Date, default: null },
    preparingAt: { type: Date, default: null },
    readyAt: { type: Date, default: null },
    pickedUpAt: { type: Date, default: null },
    completedAt: { type: Date, default: null },
    cancelledAt: { type: Date, default: null },
    verifiedByOwnerId: { type: String, default: null },
    // Barcode Verification & Pickup Fields
    verificationToken: {
      type: String,
      unique: true,
      sparse: true,
      index: true,
    },
    barcodeFormat: {
      type: String,
      default: 'CODE128',
    },
    barcodeStatus: {
      type: String,
      enum: ['ACTIVE', 'USED', 'EXPIRED', 'CANCELLED'],
      default: 'ACTIVE',
      index: true,
    },
    barcodeDataUrl: {
      type: String,
      default: '',
    },
    generatedAt: {
      type: Date,
      default: Date.now,
    },
    expiresAt: {
      type: Date,
      default: null,
    },
    usedAt: {
      type: Date,
      default: null,
    },
    collectedAt: {
      type: Date,
      default: null,
    },
    collectedBy: {
      type: String,
      default: null,
    },
    statusHistory: { type: [statusHistorySchema], default: [] },
  },
  { timestamps: true }
);

// High performance deterministic compound index for canteen queue ordering on server time
orderSchema.index({ canteenId: 1, status: 1, orderPlacedAt: 1, createdAt: 1, orderId: 1 });
orderSchema.index({ canteenId: 1, status: 1, createdAt: -1 });
orderSchema.index({ studentId: 1, createdAt: -1 });
orderSchema.index({ tokenNumber: 1, canteenId: 1 });
orderSchema.index({ verificationToken: 1, barcodeStatus: 1 });
orderSchema.index({ canteenId: 1, verificationToken: 1 });

module.exports = mongoose.model('Order', orderSchema);
