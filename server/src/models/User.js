const mongoose = require('mongoose');

const userSchema = new mongoose.Schema(
  {
    uid: { type: String, required: true, unique: true, index: true },
    name: { type: String, default: '' },
    email: { type: String, required: true, unique: true, index: true, lowercase: true, trim: true },
    password: { type: String, default: '' },
    role: { type: String, default: 'student' },
    registrationNumber: { type: String, default: '' },
    department: { type: String, default: '' },
    avatarId: { type: String, default: 'scholar' },
    fcmToken: { type: String, default: '' },
    phone: { type: String, default: '' },
    course: { type: String, default: '' },
  },
  { timestamps: true }
);

module.exports = mongoose.model('User', userSchema);
