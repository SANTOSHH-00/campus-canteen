const mongoose = require('mongoose');

const idempotencyKeySchema = new mongoose.Schema(
  {
    key: { type: String, required: true, unique: true, index: true },
    studentId: { type: String, required: true },
    statusCode: { type: Number },
    responseBody: { type: mongoose.Schema.Types.Mixed },
    inProgress: { type: Boolean, default: true },
    createdAt: { type: Date, default: Date.now, expires: 300 }, // TTL index: auto-deletes after 5 minutes (300 seconds)
  },
  { timestamps: true }
);

module.exports = mongoose.model('IdempotencyKey', idempotencyKeySchema);
