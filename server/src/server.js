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

const helmet = require('helmet');
const rateLimit = require('express-rate-limit');

const app = express();

// Security Middlewares (Option 3B: Helmet HTTP Security Headers)
app.use(helmet({
  contentSecurityPolicy: false,
  crossOriginEmbedderPolicy: false,
}));
app.use(cors());
app.use(express.json());
app.use(morgan('dev'));

// Option 3A: Rate Limiting
// 1. General API Rate Limiter (600 requests / 15 mins per IP)
const apiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 600,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many requests from this IP, please try again in 15 minutes.' },
});
app.use('/api/', apiLimiter);

// 2. Sensitive Authentication & OTP Rate Limiter (15 attempts / 10 mins per IP)
const authLimiter = rateLimit({
  windowMs: 10 * 60 * 1000,
  max: 15,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many login or OTP attempts. Please wait 10 minutes before trying again.' },
});
app.use('/api/auth/login', authLimiter);
app.use('/api/auth/register', authLimiter);
app.use('/api/auth/send-otp', authLimiter);
app.use('/api/auth/forgot-password', authLimiter);
app.use('/api/auth/reset-password', authLimiter);
app.use('/api/owner/login', authLimiter);
app.use('/api/owners/login', authLimiter);
app.use('/api/owner/resend-otp', authLimiter);
app.use('/api/owners/resend-otp', authLimiter);

// Google Play Mandatory Compliance Pages
app.get('/privacy-policy', (req, res) => {
  res.sendFile(path.join(__dirname, '../public/privacy-policy.html'));
});

app.get('/delete-account', (req, res) => {
  res.sendFile(path.join(__dirname, '../public/delete-account.html'));
});

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

const http = require('http');
const { initWebSocket } = require('./services/socketService');

const server = http.createServer(app);
initWebSocket(server);

const PORT = process.env.PORT || 5000;
server.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 Quickbite Node.js/Express Server & WebSocket Gateway listening on port ${PORT}`);
});
