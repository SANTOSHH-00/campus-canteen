const mongoose = require('mongoose');

const inventorySchema = new mongoose.Schema(
  {
    canteenId: { type: String, required: true, index: true },
    name: { type: String, required: true },
    icon: { type: String, default: '📦' },
    quantity: { type: Number, required: true, default: 0 },
    unit: { type: String, default: 'pcs' },
    lowStockThreshold: { type: Number, default: 5 },
  },
  { timestamps: true }
);

module.exports = mongoose.model('Inventory', inventorySchema);
