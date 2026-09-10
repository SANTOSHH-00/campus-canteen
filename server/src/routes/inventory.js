const express = require('express');
const router = express.Router();
const Inventory = require('../models/Inventory');

// GET /api/inventory/canteen/:canteenId - List raw inventory for canteen
router.get('/canteen/:canteenId', async (req, res) => {
  try {
    let items = await Inventory.find({ canteenId: req.params.canteenId }).lean();
    // Seed default inventory items if collection is currently empty
    if (items.length === 0) {
      const defaultInventory = [
        { canteenId: req.params.canteenId, name: 'Bread', icon: '🍞', quantity: 120.0, unit: 'pcs', lowStockThreshold: 20.0 },
        { canteenId: req.params.canteenId, name: 'Paneer', icon: '🧈', quantity: 2.5, unit: 'kg', lowStockThreshold: 1.0 },
        { canteenId: req.params.canteenId, name: 'Tomato', icon: '🍅', quantity: 3.2, unit: 'kg', lowStockThreshold: 1.5 },
        { canteenId: req.params.canteenId, name: 'Potato', icon: '🥔', quantity: 5.0, unit: 'kg', lowStockThreshold: 6.0 },
        { canteenId: req.params.canteenId, name: 'Onion', icon: '🧅', quantity: 2.0, unit: 'kg', lowStockThreshold: 3.0 },
        { canteenId: req.params.canteenId, name: 'Coffee Powder', icon: '🫘', quantity: 1.2, unit: 'kg', lowStockThreshold: 0.5 },
        { canteenId: req.params.canteenId, name: 'Tea Leaves', icon: '🍃', quantity: 800.0, unit: 'g', lowStockThreshold: 200.0 },
      ];
      items = await Inventory.insertMany(defaultInventory);
    }
    res.json(items);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// PATCH /api/inventory/:id - Update stock quantity or details
router.patch('/:id', async (req, res) => {
  try {
    const { quantity } = req.body;
    const updated = await Inventory.findByIdAndUpdate(
      req.params.id,
      { $set: { quantity } },
      { new: true }
    );
    if (!updated) return res.status(404).json({ error: 'Inventory item not found' });
    res.json(updated);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// POST /api/inventory - Add new inventory entry
router.post('/', async (req, res) => {
  try {
    const item = await Inventory.create(req.body);
    res.status(201).json(item);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

module.exports = router;
