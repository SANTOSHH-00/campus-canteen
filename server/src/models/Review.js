const mongoose = require('mongoose');

const reviewSchema = new mongoose.Schema(
  {
    id: { type: String, required: true, unique: true, index: true },
    itemId: { type: String, required: true, index: true },
    canteenId: { type: String, required: true, index: true },
    userId: { type: String, default: '' },
    userName: { type: String, default: 'Campus Foodie' },
    rating: { type: Number, required: true, min: 1, max: 5 },
    comment: { type: String, default: '' },
    createdAt: { type: Date, default: Date.now },
  },
  { timestamps: true }
);

reviewSchema.index({ itemId: 1, createdAt: -1 });

module.exports = mongoose.model('Review', reviewSchema);

