const express = require('express');
const router = express.Router();
const Canteen = require('../models/Canteen');

// GET /api/canteens - List all canteens
router.get('/', async (req, res) => {
  try {
    const canteens = await Canteen.find({}).lean();
    res.json(canteens);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// GET /api/canteens/:id - Get single canteen
router.get('/:id', async (req, res) => {
  try {
    const canteen = await Canteen.findOne({ id: req.params.id }).lean();
    if (!canteen) {
      return res.status(404).json({ error: 'Canteen not found' });
    }
    res.json(canteen);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/canteens - Create or upsert canteen
router.post('/', async (req, res) => {
  try {
    const canteenData = req.body;
    const canteen = await Canteen.findOneAndUpdate(
      { id: canteenData.id },
      canteenData,
      { upsert: true, new: true }
    );
    res.status(201).json(canteen);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// PATCH /api/canteens/:id/status - Toggle open/closed status
router.patch('/:id/status', async (req, res) => {
  try {
    const { isOpen, closeReason } = req.body;
    const update = { isOpen };
    if (closeReason !== undefined) update.closeReason = closeReason;
    const updated = await Canteen.findOneAndUpdate(
      { id: req.params.id },
      { $set: update },
      { new: true }
    );
    if (!updated) return res.status(404).json({ error: 'Canteen not found' });
    res.json(updated);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// PATCH /api/canteens/:id/profile - Update image or timings
router.patch('/:id/profile', async (req, res) => {
  try {
    const { imageUrl, timings, isOpen, closeReason } = req.body;
    const update = {};
    if (imageUrl !== undefined) update.imageUrl = imageUrl;
    if (timings !== undefined) update.timings = timings;
    if (isOpen !== undefined) update.isOpen = isOpen;
    if (closeReason !== undefined) update.closeReason = closeReason;

    const updated = await Canteen.findOneAndUpdate(
      { id: req.params.id },
      { $set: update },
      { new: true }
    );
    if (!updated) return res.status(404).json({ error: 'Canteen not found' });
    res.json(updated);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

module.exports = router;
