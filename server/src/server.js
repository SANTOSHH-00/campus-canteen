// Express Application Server: Configures middleware, security rate limiting, API routes, and WebSocket gateway
const express = require('express');
const cors = require('cors');
const morgan = require('morgan');
const path = require('path');
const dotenv = require('dotenv');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const http = require('http');

dotenv.config({ path: path.resolve(__dirname, '../../.env') });
dotenv.config();

const connectDB = require('./config/db');
connectDB();

const app = express();
app.set('trust proxy', 1);

// Security Headers: Helmet protection against common web vulnerabilities
app.use(helmet({
  contentSecurityPolicy: false,
  crossOriginEmbedderPolicy: false,
}));
app.use(cors());
app.use(express.json());

const isDev = process.env.NODE_ENV !== 'production';
const morganFormat = isDev
  ? 'dev'
  : '[:date[iso]] :method :url :status :response-time ms - :res[content-length]';
app.use(morgan(morganFormat));

const isLocalOrPrivateIp = (ip) => {
  if (!ip) return false;
  return (
    ip === '127.0.0.1' ||
    ip === '::1' ||
    ip.endsWith('127.0.0.1') ||
    ip === '10.0.2.2' ||
    ip.startsWith('192.168.') ||
    ip.startsWith('10.') ||
    /^172\.(1[6-9]|2[0-9]|3[0-1])\./.test(ip)
  );
};

// Rate Limiting: General API limiter keyed per user token or student ID to support campus NAT
const apiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 15000,
  standardHeaders: true,
  legacyHeaders: false,
  validate: { keyGeneratorIpFallback: false },
  keyGenerator: (req) => {
    const authHeader = req.headers['authorization'];
    if (authHeader) return `auth_${authHeader.slice(-24)}`;
    const studentId = req.headers['x-student-id'] || req.body?.studentId;
    if (studentId) return `student_${studentId}`;
    return req.ip || req.connection?.remoteAddress || 'unknown';
  },
  message: { error: 'Too many requests, please try again in a few minutes.' },
  skip: (req) => {
    if (req.path === '/health' || req.path === '/api/health' || req.path === '/') return true;
    const ip = req.ip || req.connection?.remoteAddress || '';
    if (isDev || isLocalOrPrivateIp(ip)) return true;
    return false;
  },
});
app.use('/api/', apiLimiter);

// Rate Limiting: Sensitive authentication and OTP verification limiter
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 100,
  standardHeaders: true,
  legacyHeaders: false,
  validate: { keyGeneratorIpFallback: false },
  keyGenerator: (req) => {
    const email = (req.body?.email || '').trim().toLowerCase();
    if (email) return `auth_email_${email}`;
    return req.ip || req.connection?.remoteAddress || 'unknown';
  },
  message: { error: 'Too many login or OTP attempts. Please wait 10 minutes before trying again.' },
  skip: (req) => {
    const ip = req.ip || req.connection?.remoteAddress || '';
    if (isDev || isLocalOrPrivateIp(ip)) return true;
    return false;
  },
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
app.use('/api/admin/login', authLimiter);
app.use('/api/admin/register', authLimiter);
app.use('/api/admin/resend-otp', authLimiter);
app.use('/api/owners/admin/login', authLimiter);
app.use('/api/owners/admin/register', authLimiter);
app.use('/api/owners/admin/resend-otp', authLimiter);

// Rate Limiting: Order verification and counter barcode scanner limiter
const verifyLimiter = rateLimit({
  windowMs: 10 * 60 * 1000,
  max: 300,
  standardHeaders: true,
  legacyHeaders: false,
  validate: { keyGeneratorIpFallback: false },
  keyGenerator: (req) => {
    const authHeader = req.headers['authorization'];
    if (authHeader) return `verify_${authHeader.slice(-24)}`;
    return req.ip || req.connection?.remoteAddress || 'unknown';
  },
  message: { error: 'Too many verification attempts. Please wait a minute before scanning again.' },
  skip: (req) => {
    const ip = req.ip || req.connection?.remoteAddress || '';
    if (isDev || isLocalOrPrivateIp(ip)) return true;
    return false;
  },
});
app.use('/api/owner/orders/verify-barcode', verifyLimiter);
app.use('/api/owners/orders/verify-barcode', verifyLimiter);
app.use('/api/owner/orders/confirm-pickup', verifyLimiter);
app.use('/api/owners/orders/confirm-pickup', verifyLimiter);
app.use('/api/owner/orders/verify-qr', verifyLimiter);
app.use('/api/owners/orders/verify-qr', verifyLimiter);

// Rate Limiting: AI assistant chat queries to protect API quotas
const aiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 120,
  standardHeaders: true,
  legacyHeaders: false,
  validate: { keyGeneratorIpFallback: false },
  keyGenerator: (req) => {
    const authHeader = req.headers['authorization'];
    if (authHeader) return `ai_${authHeader.slice(-24)}`;
    return req.ip || req.connection?.remoteAddress || 'unknown';
  },
  message: { error: 'Too many AI Assistant requests. Please wait a moment before asking again.' },
  skip: (req) => {
    const ip = req.ip || req.connection?.remoteAddress || '';
    if (isDev || isLocalOrPrivateIp(ip)) return true;
    return false;
  },
});
app.use('/api/ai', aiLimiter);

app.get('/privacy-policy', (req, res) => {
  res.sendFile(path.join(__dirname, '../public/privacy-policy.html'));
});

app.get('/delete-account', (req, res) => {
  res.sendFile(path.join(__dirname, '../public/delete-account.html'));
});

app.get(['/admin', '/admin/'], (req, res) => {
  res.sendFile(path.join(__dirname, '../public/admin/index.html'));
});
app.use('/admin', express.static(path.join(__dirname, '../public/admin')));

app.get('/', (req, res) => {
  res.json({
    status: 'online',
    message: 'Welcome to Quickbite Express API connected to MongoDB Atlas (Database: Quickbite)',
    adminPortal: '/admin',
    endpoints: {
      admin: '/admin',
      health: '/health',
      apiHealth: '/api/health',
      canteens: '/api/canteens',
      sampleCanteenItems: '/api/items/canteen/canteen_33',
      sampleOrders: '/api/orders/canteen/canteen_33',
      sampleInventory: '/api/inventory/canteen/canteen_33'
    }
  });
});

// Health Probe: Provides uptime, database connectivity state, and memory usage for monitoring
const healthCheckHandler = (req, res) => {
  const mongoose = require('mongoose');
  const dbStatusMap = {
    0: 'disconnected',
    1: 'connected',
    2: 'connecting',
    3: 'disconnecting',
  };
  const dbState = dbStatusMap[mongoose.connection.readyState] || 'unknown';
  const isDbReady = mongoose.connection.readyState === 1;

  const healthData = {
    status: isDbReady ? 'ok' : 'degraded',
    message: 'Quick Bite backend is running',
    timestamp: new Date().toISOString(),
    uptime: Math.floor(process.uptime()),
    database: {
      status: dbState,
      name: process.env.MONGODB_DB_NAME || 'Quickbite',
    },
    memory: {
      rssMb: Math.round(process.memoryUsage().rss / (1024 * 1024)),
      heapUsedMb: Math.round(process.memoryUsage().heapUsed / (1024 * 1024)),
    },
    version: '1.0.0',
    environment: process.env.NODE_ENV || 'development',
  };

  return res.status(200).json(healthData);
};

app.get('/health', healthCheckHandler);
app.head('/health', healthCheckHandler);
app.get('/api/health', healthCheckHandler);
app.head('/api/health', healthCheckHandler);

app.use('/api', (req, res, next) => {
  res.set('Cache-Control', 'no-store, no-cache, must-revalidate, proxy-revalidate');
  res.set('Pragma', 'no-cache');
  res.set('Expires', '0');
  res.set('Surrogate-Control', 'no-store');
  next();
});

// Route Mounts: REST API endpoints
app.use('/api/auth', require('./routes/auth'));
app.use('/api/owner', require('./routes/owners'));
app.use('/api/owners', require('./routes/owners'));
app.use('/api/admin', require('./routes/owners'));
app.use('/api/canteens', require('./routes/canteens'));
app.use('/api/items', require('./routes/items'));
app.use('/api/orders', require('./routes/orders'));
app.use('/api/users', require('./routes/users'));
app.use('/api/inventory', require('./routes/inventory'));
app.use('/api/notifications', require('./routes/notifications'));
app.use('/api/ai', require('./routes/aiRoutes'));
app.use('/api/suggestions', require('./routes/suggestions'));
app.use('/api/payments', require('./routes/payments'));

app.use((req, res) => {
  res.status(404).json({
    error: 'Not Found',
    message: `Cannot ${req.method} ${req.originalUrl}`,
    timestamp: new Date().toISOString(),
  });
});

// Centralized error handler
app.use((err, req, res, next) => {
  if (res.headersSent) {
    return next(err);
  }
  const statusCode = err.status || err.statusCode || 500;
  console.error(`[${new Date().toISOString()}] [ERROR] [Unhandled] ${req.method} ${req.originalUrl} - ${err.message}`, err.stack || '');
  res.status(statusCode).json({
    error: statusCode === 500 && process.env.NODE_ENV === 'production'
      ? 'Internal Server Error'
      : (err.message || 'Internal Server Error'),
    status: statusCode,
    timestamp: new Date().toISOString(),
  });
});

// WebSocket Server & Process Lifecycle: Initializes socket gateway and handles graceful termination
const { initWebSocket } = require('./services/socketService');
const server = http.createServer(app);
initWebSocket(server).catch((err) => {
  console.error(`[${new Date().toISOString()}] [ERROR] WebSocket Init Error:`, err.message);
});

process.on('uncaughtException', (err) => {
  console.error(`[${new Date().toISOString()}] [FATAL] Uncaught Exception:`, err.message, err.stack);
});

process.on('unhandledRejection', (reason, promise) => {
  console.error(`[${new Date().toISOString()}] [WARN] Unhandled Promise Rejection at:`, promise, 'reason:', reason);
});

const handleShutdown = (signal) => {
  console.log(`[${new Date().toISOString()}] [INFO] Received ${signal}. Initiating graceful shutdown...`);
  server.close(async () => {
    console.log(`[${new Date().toISOString()}] [INFO] HTTP/WebSocket server closed to new connections.`);
    try {
      const mongoose = require('mongoose');
      if (mongoose.connection.readyState !== 0) {
        await mongoose.connection.close(false);
        console.log(`[${new Date().toISOString()}] [INFO] MongoDB Atlas connection closed cleanly.`);
      }
    } catch (dbErr) {
      console.error(`[${new Date().toISOString()}] [ERROR] Error closing database connection:`, dbErr.message);
    }
    process.exit(0);
  });

  setTimeout(() => {
    console.error(`[${new Date().toISOString()}] [ERROR] Graceful shutdown timed out. Forcing process exit.`);
    process.exit(1);
  }, 10000).unref();
};

process.on('SIGTERM', () => handleShutdown('SIGTERM'));
process.on('SIGINT', () => handleShutdown('SIGINT'));

const PORT = parseInt(process.env.PORT, 10) || 5000;
const HOST = '0.0.0.0';

server.listen(PORT, HOST, () => {
  console.log(`[${new Date().toISOString()}] [INFO] 🚀 Quickbite Node.js/Express Server & WebSocket Gateway listening on http://${HOST}:${PORT}`);
  console.log(`[${new Date().toISOString()}] [INFO] Node environment: ${process.env.NODE_ENV || 'development'}`);
  console.log(`[${new Date().toISOString()}] [INFO] Health endpoints active: /health and /api/health`);
});

server.on('error', (err) => {
  console.error(`[${new Date().toISOString()}] [FATAL] Server listener error:`, err.message);
});
