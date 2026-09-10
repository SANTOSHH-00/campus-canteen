const express = require('express');
const router = express.Router();
const Item = require('../models/Item');
const { cloudinary, upload } = require('../config/cloudinary');

// GET /api/items/canteen/:canteenId - List items for a canteen
router.get('/canteen/:canteenId', async (req, res) => {
  try {
    const items = await Item.find({ canteenId: req.params.canteenId }).lean();
    res.json(items);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// GET /api/items/:id - Get single item
router.get('/:id', async (req, res) => {
  try {
    const item = await Item.findOne({ id: req.params.id }).lean();
    if (!item) return res.status(404).json({ error: 'Item not found' });
    res.json(item);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/items/upload-image - Upload an item image to Cloudinary
router.post('/upload-image', upload.single('image'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: 'No image file provided' });
    }

    // req.file is populated by multer-storage-cloudinary
    const imageUrl = req.file.path || req.file.secure_url;
    const cloudinaryPublicId = req.file.filename;

    return res.status(200).json({
      success: true,
      imageUrl,
      cloudinaryPublicId,
    });
  } catch (err) {
    console.error('Cloudinary image upload error:', err);
    return res.status(500).json({ error: err.message || 'Image upload failed' });
  }
});

// POST /api/items - Create or upsert an item in MongoDB
router.post('/', async (req, res) => {
  try {
    const itemData = req.body;
    const item = await Item.findOneAndUpdate(
      { id: itemData.id },
      itemData,
      { upsert: true, new: true }
    );
    res.status(201).json(item);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// PATCH /api/items/:id/stock - Update stock and availability
router.patch('/:id/stock', async (req, res) => {
  try {
    const { available, stock } = req.body;
    const updated = await Item.findOneAndUpdate(
      { id: req.params.id },
      { $set: { available, stock } },
      { new: true }
    );
    if (!updated) return res.status(404).json({ error: 'Item not found' });
    res.json(updated);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// DELETE /api/items/:id - Delete an item from MongoDB and cleanup Cloudinary
router.delete('/:id', async (req, res) => {
  try {
    const item = await Item.findOne({ id: req.params.id });
    if (!item) return res.status(404).json({ error: 'Item not found' });

    // If item has a custom Cloudinary image, delete it from Cloudinary
    if (item.cloudinaryPublicId) {
      try {
        await cloudinary.uploader.destroy(item.cloudinaryPublicId);
        console.log(`Deleted Cloudinary asset: ${item.cloudinaryPublicId}`);
      } catch (cloudErr) {
        console.error('Failed to delete image from Cloudinary:', cloudErr.message);
      }
    }

    await Item.deleteOne({ id: req.params.id });
    res.json({ success: true, message: 'Item and associated image deleted successfully' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
