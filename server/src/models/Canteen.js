const mongoose = require('mongoose');

const canteenSchema = new mongoose.Schema(
  {
    id: { type: String, required: true, unique: true, index: true },
    name: { type: String, required: true },
    block: { type: String, default: '' },
    location: { type: String, default: '' },
    isOpen: { type: Boolean, default: true },
    closeReason: { type: String, default: '' },
    floorInfo: { type: String, default: '5th Floor' },
    specialty: { type: String, default: '' },
    avgWaitMinutes: { type: Number, default: 5 },
    icon: { type: String, default: '🏢' },
    imageUrl: { type: String, default: '' },
    timings: { type: String, default: '7:00 AM – 10:00 PM' },
  },
  { timestamps: true }
);

module.exports = mongoose.model('Canteen', canteenSchema);
