const mongoose = require('mongoose');

const orderItemSchema = new mongoose.Schema(
  {
    itemId: { type: String, required: true },
    name: { type: String, required: true },
    price: { type: Number, required: true },
    quantity: { type: Number, required: true, default: 1 },
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
    pickupCanteenName: { type: String, default: '' },
    pickupLocation: { type: String, default: '' },
    pickupCounter: { type: String, default: 'Counter 1' },
    estimatedReadyTime: { type: String, default: '' },
    estimatedPrepMinutes: { type: Number, default: 7 },
    // Server-authoritative UTC status event timestamps
    orderPlacedAt: { type: Date, default: Date.now, index: true },
    confirmedAt: { type: Date, default: null },
    preparingAt: { type: Date, default: null },
    readyAt: { type: Date, default: null },
    completedAt: { type: Date, default: null },
    cancelledAt: { type: Date, default: null },
    statusHistory: { type: [statusHistorySchema], default: [] },
  },
  { timestamps: true }
);

// High performance deterministic compound index for canteen queue ordering on server time
orderSchema.index({ canteenId: 1, status: 1, orderPlacedAt: 1, createdAt: 1, orderId: 1 });

module.exports = mongoose.model('Order', orderSchema);
