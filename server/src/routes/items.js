// Menu & Food Item Routes: Item catalogue management, Cloudinary image upload, availability toggles, and customer reviews
const express = require('express');
const router = express.Router();
const Item = require('../models/Item');
const Review = require('../models/Review');
const { cloudinary, upload } = require('../config/cloudinary');
const { generateItemDescription } = require('../services/geminiService');

function invalidateCanteenCache(canteenId) {
}

// GET /api/items - List all items across campus (Always fresh from MongoDB)
router.get('/', async (req, res) => {
  try {
    const filter = {};
    if (req.query.canteenId && req.query.canteenId !== 'all') {
      filter.canteenId = req.query.canteenId;
    }
    const items = await Item.find(filter).sort({ canteenId: 1, category: 1, name: 1 }).lean();
    res.json(items);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// GET /api/items/canteen/:canteenId - List items for a canteen (Always fresh from MongoDB)
router.get('/canteen/:canteenId', async (req, res) => {
  try {
    const { canteenId } = req.params;
    const items = await Item.find({ canteenId }).sort({ category: 1, name: 1 }).lean();
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
    if (item && item.canteenId) invalidateCanteenCache(item.canteenId);
    res.status(201).json(item);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// PATCH /api/items/:id/availability - Fast partial update for menu item availability
router.patch('/:id/availability', async (req, res) => {
  try {
    const { available, canteenId } = req.body;
    if (typeof available !== 'boolean') {
      return res.status(400).json({ error: 'Field "available" (boolean) is required' });
    }

    const filter = { id: req.params.id };
    if (canteenId) {
      filter.canteenId = canteenId;
    }

    const item = await Item.findOne(filter);
    if (!item) {
      return res.status(404).json({ error: 'Item not found or unauthorized canteen access' });
    }

    if (available && item.stock === 0) {
      item.stock = (typeof req.body.stock === 'number' && req.body.stock > 0) ? req.body.stock : 20;
    }

    item.available = available;
    item.updatedAt = new Date();
    await item.save();

    if (item.canteenId) invalidateCanteenCache(item.canteenId);

    return res.status(200).json({
      success: true,
      id: item.id,
      name: item.name,
      canteenId: item.canteenId,
      available: item.available,
      stock: item.stock,
      item,
    });
  } catch (err) {
    console.error('Error toggling availability:', err);
    return res.status(500).json({ error: err.message || 'Failed to update item availability' });
  }
});

// PATCH /api/items/:id/stock - Update stock and availability
router.patch('/:id/stock', async (req, res) => {
  try {
    const { available, stock, canteenId } = req.body;
    const filter = { id: req.params.id };
    if (canteenId) filter.canteenId = canteenId;

    const item = await Item.findOne(filter);
    if (!item) return res.status(404).json({ error: 'Item not found' });

    if (typeof stock === 'number') {
      if (stock < 0) return res.status(400).json({ error: 'Stock cannot be negative' });
      item.stock = stock;
      if (stock === 0) item.available = false;
    }

    if (typeof available === 'boolean') {
      if (available && item.stock === 0) {
        return res.status(400).json({ error: 'Cannot mark available when stock is 0' });
      }
      item.available = available;
    }

    item.updatedAt = new Date();
    await item.save();

    if (item.canteenId) invalidateCanteenCache(item.canteenId);
    res.json(item);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// DELETE /api/items/:id - Delete an item from MongoDB and cleanup Cloudinary
router.delete('/:id', async (req, res) => {
  try {
    const item = await Item.findOne({ id: req.params.id });
    if (!item) return res.status(404).json({ error: 'Item not found' });

    if (item.cloudinaryPublicId) {
      try {
        await cloudinary.uploader.destroy(item.cloudinaryPublicId);
        console.log(`Deleted Cloudinary asset: ${item.cloudinaryPublicId}`);
      } catch (cloudErr) {
        console.error('Failed to delete image from Cloudinary:', cloudErr.message);
      }
    }

    if (item.canteenId) invalidateCanteenCache(item.canteenId);
    await Item.deleteOne({ id: req.params.id });
    res.json({ success: true, message: 'Item and associated image deleted successfully' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/items/generate-description - AI generated description draft for Owner Dashboard
router.post('/generate-description', async (req, res) => {
  try {
    const { name, category, ingredients } = req.body;
    if (!name || name.trim() === '') {
      return res.status(400).json({ error: 'Food item name is required to generate a description' });
    }
    const result = await generateItemDescription({ name, category, ingredients });
    res.json(result);
  } catch (err) {
    console.error('Error generating item description:', err);
    res.status(500).json({ error: err.message || 'Failed to generate description' });
  }
});

// GET /api/items/:id/reviews - Fetch real customer reviews for a food item
router.get('/:id/reviews', async (req, res) => {
  try {
    const { id } = req.params;
    const item = await Item.findOne({ id }).lean();
    if (!item) return res.status(404).json({ error: 'Item not found' });

    const reviews = await Review.find({ itemId: id }).sort({ createdAt: -1 }).lean();
    const totalReviews = reviews.length;
    const averageRating = totalReviews > 0
      ? Math.round((reviews.reduce((sum, r) => sum + r.rating, 0) / totalReviews) * 10) / 10
      : (item.rating || 4.5);

    res.json({
      itemId: id,
      averageRating,
      totalReviews,
      reviews,
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/items/:id/reviews - Submit a review for a food item
router.post('/:id/reviews', async (req, res) => {
  try {
    const { id } = req.params;
    const { rating, comment, userName, userId, canteenId } = req.body;

    const numRating = Number(rating);
    if (!numRating || numRating < 1 || numRating > 5) {
      return res.status(400).json({ error: 'Rating must be between 1 and 5' });
    }

    const item = await Item.findOne({ id });
    if (!item) return res.status(404).json({ error: 'Item not found' });

    const review = await Review.create({
      id: `rev_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      itemId: id,
      canteenId: canteenId || item.canteenId || '',
      userId: userId || '',
      userName: (userName || '').trim() || 'Campus Student',
      rating: numRating,
      comment: (comment || '').trim(),
      createdAt: new Date(),
    });

    const allReviews = await Review.find({ itemId: id });
    const totalReviews = allReviews.length;
    const averageRating = Math.round((allReviews.reduce((sum, r) => sum + r.rating, 0) / totalReviews) * 10) / 10;

    item.rating = averageRating;
    item.reviewCount = totalReviews;
    await item.save();

    if (item.canteenId) invalidateCanteenCache(item.canteenId);

    res.status(201).json({
      success: true,
      review,
      averageRating,
      totalReviews,
    });
  } catch (err) {
    console.error('Error creating review:', err);
    res.status(400).json({ error: err.message });
  }
});

module.exports = router;
