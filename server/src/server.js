const express = require('express');
const cors = require('cors');
const morgan = require('morgan');
const path = require('path');
const dotenv = require('dotenv');

// Load environment variables
dotenv.config({ path: path.resolve(__dirname, '../../.env') });
dotenv.config();

const connectDB = require('./config/db');

// Connect to MongoDB Atlas
connectDB();

const app = express();

// Middlewares
app.use(cors());
app.use(express.json());
app.use(morgan('dev'));

// Static Admin Portal
app.use('/admin', express.static(path.join(__dirname, '../public/admin')));

// Root homepage endpoint
app.get('/', (req, res) => {
  res.json({
    status: 'online',
    message: 'Welcome to Quickbite Express API connected to MongoDB Atlas (Database: Quickbite)',
    endpoints: {
      health: '/api/health',
      canteens: '/api/canteens',
      sampleCanteenItems: '/api/items/canteen/canteen_33',
      sampleOrders: '/api/orders/canteen/canteen_33',
      sampleInventory: '/api/inventory/canteen/canteen_33'
    }
  });
});

// Health check endpoint
app.get('/api/health', (req, res) => {
  const mongoose = require('mongoose');
  const isConnected = mongoose.connection.readyState === 1;
  res.json({
    success: true,
    message: 'Quick Bite backend is running',
    database: isConnected ? 'connected' : 'disconnected',
    databaseName: process.env.MONGODB_DB_NAME || 'Quickbite',
    timestamp: new Date().toISOString(),
  });
});

// Mount Routes
app.use('/api/auth', require('./routes/auth'));
app.use('/api/owner', require('./routes/owners'));
app.use('/api/owners', require('./routes/owners'));
app.use('/api/canteens', require('./routes/canteens'));
app.use('/api/items', require('./routes/items'));
app.use('/api/orders', require('./routes/orders'));
app.use('/api/users', require('./routes/users'));
app.use('/api/inventory', require('./routes/inventory'));
app.use('/api/notifications', require('./routes/notifications'));

// Global error handler
app.use((err, req, res, next) => {
  console.error('Unhandled Error:', err.stack);
  res.status(500).json({ error: err.message || 'Internal Server Error' });
});

const PORT = process.env.PORT || 5000;
app.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 Quickbite Node.js/Express Server listening on port ${PORT}`);
});
