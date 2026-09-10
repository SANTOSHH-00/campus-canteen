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
    });
    console.log(`✅ MongoDB Atlas Connected: ${conn.connection.host} [Database: ${dbName}]`);
  } catch (error) {
    console.error(`❌ MongoDB Connection Error: ${error.message}`);
    // Don't crash immediately in dev mode to allow diagnosing connection
  }
};

module.exports = connectDB;
