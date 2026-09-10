const express = require('express');
const router = express.Router();
const User = require('../models/User');

// GET /api/users/:uid - Get student profile
router.get('/:uid', async (req, res) => {
  try {
    const user = await User.findOne({ uid: req.params.uid }).lean();
    if (!user) return res.status(404).json({ error: 'User not found' });
    res.json(user);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/users - Create or update user profile
router.post('/', async (req, res) => {
  try {
    const userData = req.body;
    const user = await User.findOneAndUpdate(
      { uid: userData.uid },
      userData,
      { upsert: true, new: true }
    );
    res.status(201).json(user);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// PATCH /api/users/:uid/fcm - Update FCM Token
router.patch('/:uid/fcm', async (req, res) => {
  try {
    const { fcmToken } = req.body;
    const updated = await User.findOneAndUpdate(
      { uid: req.params.uid },
      { $set: { fcmToken } },
      { new: true }
    );
    if (!updated) return res.status(404).json({ error: 'User not found' });
    res.json(updated);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

module.exports = router;
