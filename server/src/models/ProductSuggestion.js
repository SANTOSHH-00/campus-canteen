const mongoose = require('mongoose');

const productSuggestionSchema = new mongoose.Schema(
  {
    suggestionId: { type: String, required: true, unique: true, index: true },
    canteenId: { type: String, required: true, index: true },
    canteenName: { type: String, default: '' },
    studentId: { type: String, required: true, index: true },
    studentName: { type: String, default: '' },
    studentEmail: { type: String, default: '', index: true },
    studentRegistration: { type: String, default: '' },
    ownerId: { type: String, default: null, index: true },
    suggestionText: { type: String, required: true, trim: true, maxlength: 1000 },
    status: {
      type: String,
      enum: ['New', 'Under Review', 'Accepted', 'Rejected', 'Added to Menu'],
      default: 'New',
      index: true,
    },
    ownerNote: { type: String, default: '', maxlength: 500 },
  },
  { timestamps: true }
);

// Compound index to quickly find recent suggestions by student per canteen for 3-day cooldown
productSuggestionSchema.index({ canteenId: 1, studentId: 1, createdAt: -1 });
productSuggestionSchema.index({ canteenId: 1, studentEmail: 1, createdAt: -1 });

module.exports = mongoose.model('ProductSuggestion', productSuggestionSchema);
