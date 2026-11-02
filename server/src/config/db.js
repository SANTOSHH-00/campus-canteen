const mongoose = require('mongoose');
const path = require('path');
const dotenv = require('dotenv');

// Load environment variables from project root .env
dotenv.config({ path: path.resolve(__dirname, '../../../.env') });
dotenv.config(); // fallback to local .env if present

let isInitialConnectionSetup = false;
let retryTimeout = null;

/**
 * Register connection listeners once to prevent duplicate listeners
 * and to prevent unhandled 'error' events on Mongoose EventEmitter from crashing Node.js
 */
function registerConnectionListeners() {
  if (isInitialConnectionSetup) return;
  isInitialConnectionSetup = true;

  mongoose.connection.on('connected', () => {
    console.log(`[${new Date().toISOString()}] [INFO] [DB] MongoDB Atlas connection established successfully.`);
  });

  mongoose.connection.on('error', (err) => {
    // CRITICAL: Unhandled 'error' events on EventEmitter crash the Node.js process.
    // Handling it here prevents unexpected server crashes during network hiccups or Atlas failovers.
    console.error(`[${new Date().toISOString()}] [ERROR] [DB] MongoDB connection error:`, err.message);
  });

  mongoose.connection.on('disconnected', () => {
    console.warn(`[${new Date().toISOString()}] [WARN] [DB] MongoDB Atlas connection lost. Mongoose will attempt automatic reconnection.`);
  });

  mongoose.connection.on('reconnected', () => {
    console.log(`[${new Date().toISOString()}] [INFO] [DB] MongoDB Atlas reconnected successfully.`);
  });
}

const connectDB = async () => {
  registerConnectionListeners();

  const mongoURI = process.env.MONGODB_URI;
  const dbName = process.env.MONGODB_DB_NAME || 'Quickbite';

  if (!mongoURI) {
    console.error(`[${new Date().toISOString()}] [ERROR] [DB] MONGODB_URI is not defined in environment variables. Database operations will fail.`);
    // Do not call process.exit(1) - allowing Express to start ensures /health can respond,
    // logs can be inspected on Render, and services won't enter a silent CrashLoopBackOff.
    return;
  }

  try {
    const conn = await mongoose.connect(mongoURI, {
      dbName: dbName,
      maxPoolSize: 20,                // Sized appropriately for Render free/starter instances & Atlas M0 connection limit
      minPoolSize: 2,                 // Keep a modest warm pool without overwhelming idle socket limits
      maxIdleTimeMS: 30000,           // Close idle connections after 30s
      serverSelectionTimeoutMS: 5000, // Fail fast (5s) instead of hanging requests indefinitely
      socketTimeoutMS: 45000,         // Close sockets inactive for 45s
    });
    console.log(`[${new Date().toISOString()}] [INFO] [DB] ✅ MongoDB Atlas Connected: ${conn.connection.host} [Database: ${dbName}]`);
  } catch (error) {
    console.error(`[${new Date().toISOString()}] [ERROR] [DB] ❌ MongoDB Connection Error: ${error.message}`);
    // Auto-retry initial connection after 5 seconds if initially unreachable (e.g. Atlas cold start or DNS lag)
    if (!retryTimeout) {
      console.log(`[${new Date().toISOString()}] [INFO] [DB] Scheduling MongoDB reconnect attempt in 5 seconds...`);
      retryTimeout = setTimeout(() => {
        retryTimeout = null;
        connectDB();
      }, 5000);
    }
  }
};

module.exports = connectDB;
