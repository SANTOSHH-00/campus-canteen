const mongoose = require('mongoose');
const path = require('path');
const dotenv = require('dotenv');

// Load environment variables from project root .env
dotenv.config({ path: path.resolve(__dirname, '../../../.env') });
dotenv.config(); // fallback to local .env if present

const connectDB = async () => {
  const mongoURI = process.env.MONGODB_URI;
  const dbName = process.env.MONGODB_DB_NAME || 'Quickbite';

  if (!mongoURI) {
    console.error('❌ MONGODB_URI is not defined in environment variables.');
    process.exit(1);
  }

  try {
    const conn = await mongoose.connect(mongoURI, {
      dbName: dbName,
      maxPoolSize: 50,                // Maintain up to 50 socket connections per instance (100-150 across 2-3 instances)
      minPoolSize: 10,                // Keep 10 warm connections ready for sudden bursts
      maxIdleTimeMS: 30000,           // Close idle connections after 30s
      serverSelectionTimeoutMS: 5000, // Fail fast (5s) instead of hanging requests indefinitely
      socketTimeoutMS: 45000,         // Close sockets inactive for 45s
    });
    console.log(`✅ MongoDB Atlas Connected: ${conn.connection.host} [Database: ${dbName}]`);
  } catch (error) {
    console.error(`❌ MongoDB Connection Error: ${error.message}`);
    // Don't crash immediately in dev mode to allow diagnosing connection
  }
};

module.exports = connectDB;
