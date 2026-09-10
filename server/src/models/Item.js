const mongoose = require('mongoose');

const addonSchema = new mongoose.Schema(
  {
    name: { type: String, required: true },
    price: { type: Number, required: true, min: 0 },
  },
  { _id: false }
);

const itemSchema = new mongoose.Schema(
  {
    id: { type: String, required: true, unique: true, index: true },
    canteenId: { type: String, required: true, index: true },
    name: { type: String, required: true },
    description: { type: String, default: '' },
    price: { type: Number, required: true, min: 0 },
    category: { type: String, default: 'QUICK_ORDER' },
    imageUrl: { type: String, default: '' },
    cloudinaryPublicId: { type: String, default: null },
    isCustom: { type: Boolean, default: false },
    available: { type: Boolean, default: true },
    stock: { type: Number, default: 100, min: 0 },
    preparationTime: { type: String, default: '5-7 min' },
    prepMinutes: { type: Number, default: 7 },
    rating: { type: Number, default: 4.5 },
    ingredients: { type: [String], default: [] },
    customizationTitle: { type: String, default: '' },
    customizationOptions: { type: [String], default: [] },
    addons: { type: [addonSchema], default: [] },
  },
  { timestamps: true }
);

module.exports = mongoose.model('Item', itemSchema);
