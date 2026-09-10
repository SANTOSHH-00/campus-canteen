const express = require('express');
const router = express.Router();
const Notification = require('../models/Notification');

// GET /api/notifications/user/:userId - List notifications
router.get('/user/:userId', async (req, res) => {
  try {
    const notifs = await Notification.find({ userId: req.params.userId })
      .sort({ createdAt: -1 })
      .lean();
    res.json(notifs);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/notifications - Create notification
router.post('/', async (req, res) => {
  try {
    const notifData = req.body;
    if (!notifData.notificationId) {
      notifData.notificationId = 'NOTIF-' + Date.now();
    }
    const notif = await Notification.create(notifData);
    res.status(201).json(notif);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// PATCH /api/notifications/:id/read - Mark notification as read
router.patch('/:id/read', async (req, res) => {
  try {
    const updated = await Notification.findOneAndUpdate(
      { notificationId: req.params.id },
      { $set: { read: true } },
      { new: true }
    );
    if (!updated) return res.status(404).json({ error: 'Notification not found' });
    res.json(updated);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

module.exports = router;
