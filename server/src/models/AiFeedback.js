const mongoose = require('mongoose');

const aiFeedbackSchema = new mongoose.Schema(
  {
    feedbackId: { type: String, required: true, unique: true, index: true },
    studentId: { type: String, required: true, index: true },
    studentEmail: { type: String, default: '' },
    messageId: { type: String, required: true, index: true },
    userQuestion: { type: String, default: '' },
    assistantResponse: { type: String, default: '' },
    feedbackType: {
      type: String,
      enum: ['positive', 'negative'],
      required: true,
      index: true,
    },
    reason: { type: String, default: '' },
    modelUsed: { type: String, default: 'gemini-1.5-pro' },
  },
  { timestamps: true }
);

// Prevent duplicate submissions per student and message; enables fast upsert
aiFeedbackSchema.index({ studentId: 1, messageId: 1 }, { unique: true });
aiFeedbackSchema.index({ feedbackType: 1, createdAt: -1 });

module.exports = mongoose.model('AiFeedback', aiFeedbackSchema);

