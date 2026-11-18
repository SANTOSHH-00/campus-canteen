const mongoose = require('mongoose');

const cartItemSchema = new mongoose.Schema(
  {
    itemId: { type: String, required: true },
    name: { type: String, required: true },
    price: { type: Number, required: true, min: 0 },
    quantity: { type: Number, required: true, default: 1, min: 1 },
    selectedOption: { type: String, default: null },
    selectedAddons: { type: [String], default: [] },
  },
  { _id: false }
);

const cartSchema = new mongoose.Schema(
  {
    studentId: { type: String, required: true, unique: true, index: true },
    canteenId: { type: String, default: '' },
    items: { type: [cartItemSchema], default: [] },
    subtotal: { type: Number, default: 0 },
    totalAmount: { type: Number, default: 0 },
    updatedAt: { type: Date, default: Date.now },
  },
  { timestamps: true }
);

module.exports = mongoose.model('Cart', cartSchema);

