// Database Configuration: Establishes and manages resilient MongoDB Atlas connection with pooling and auto-reconnect
const mongoose = require('mongoose');
const path = require('path');
const dotenv = require('dotenv');

dotenv.config({ path: path.resolve(__dirname, '../../../.env') });
dotenv.config();

let isInitialConnectionSetup = false;
let retryTimeout = null;

// Registers connection lifecycle listeners to handle disconnects and prevent unhandled EventEmitter errors
function registerConnectionListeners() {
  if (isInitialConnectionSetup) return;
  isInitialConnectionSetup = true;

  mongoose.connection.on('connected', () => {
    console.log(`[${new Date().toISOString()}] [INFO] [DB] MongoDB Atlas connection established successfully.`);
  });

  mongoose.connection.on('error', (err) => {
    console.error(`[${new Date().toISOString()}] [ERROR] [DB] MongoDB connection error:`, err.message);
  });

  mongoose.connection.on('disconnected', () => {
    console.warn(`[${new Date().toISOString()}] [WARN] [DB] MongoDB Atlas connection lost. Mongoose will attempt automatic reconnection.`);
  });

  mongoose.connection.on('reconnected', () => {
    console.log(`[${new Date().toISOString()}] [INFO] [DB] MongoDB Atlas reconnected successfully.`);
  });
}

// Connects to MongoDB Atlas with connection pool configuration and automatic retry
const connectDB = async () => {
  registerConnectionListeners();

  const mongoURI = process.env.MONGODB_URI;
  const dbName = process.env.MONGODB_DB_NAME || 'Quickbite';

  if (!mongoURI) {
    console.error(`[${new Date().toISOString()}] [ERROR] [DB] MONGODB_URI is not defined in environment variables. Database operations will fail.`);
    return;
  }

  try {
    const conn = await mongoose.connect(mongoURI, {
      dbName: dbName,
      maxPoolSize: 20,
      minPoolSize: 2,
      maxIdleTimeMS: 30000,
      serverSelectionTimeoutMS: 5000,
      socketTimeoutMS: 45000,
    });
    console.log(`[${new Date().toISOString()}] [INFO] [DB] ✅ MongoDB Atlas Connected: ${conn.connection.host} [Database: ${dbName}]`);
  } catch (error) {
    console.error(`[${new Date().toISOString()}] [ERROR] [DB] ❌ MongoDB Connection Error: ${error.message}`);
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
