const mongoose = require('mongoose');

const ownerSchema = new mongoose.Schema(
  {
    uid: { type: String, required: true, unique: true, index: true },
    name: { type: String, default: '' },
    email: { type: String, default: '', index: true },
    password: { type: String, default: '' },
    role: { type: String, default: 'owner' },
    canteenAssigned: { type: Boolean, default: false },
    canteenId: { type: String, default: '', index: true },
    block: { type: String, default: '' },
    phone: { type: String, default: '' },
    fcmToken: { type: String, default: '' },
  },
  { timestamps: true }
);

module.exports = mongoose.model('Owner', ownerSchema);
