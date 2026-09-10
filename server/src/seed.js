const path = require('path');
const dotenv = require('dotenv');

dotenv.config({ path: path.resolve(__dirname, '../../.env') });
dotenv.config();

const connectDB = require('./config/db');
const Canteen = require('./models/Canteen');

// Preserved Canteen Names per requirements (No dummy items, owners, or inventory)
const canteensData = [
  {
    id: "canteen_33",
    name: "Govinda's Kitchen",
    block: "33",
    location: "Block 33",
    floorInfo: "6th Floor",
    specialty: "Pure Veg Meals, Thali & Sweets",
    avgWaitMinutes: 5,
    icon: "🍲",
    isOpen: true,
    closeReason: "",
    imageUrl: "",
    timings: "7:00 AM – 10:00 PM"
  },
  {
    id: "canteen_34",
    name: "Talk of the town",
    block: "34",
    location: "Block 34",
    floorInfo: "6th Floor",
    specialty: "North Indian, Rolls & Fast Food",
    avgWaitMinutes: 5,
    icon: "🌯",
    isOpen: true,
    closeReason: "",
    imageUrl: "",
    timings: "7:30 AM – 10:00 PM"
  },
  {
    id: "canteen_38",
    name: "Cafe",
    block: "38",
    location: "Block 38 (Central Link)",
    floorInfo: "5th Floor",
    specialty: "Espresso, Beverages & Continental Bites",
    avgWaitMinutes: 5,
    icon: "☕",
    isOpen: true,
    closeReason: "",
    imageUrl: "",
    timings: "8:00 AM – 9:00 PM"
  },
  {
    id: "canteen_25",
    name: "Gupta Canteen",
    block: "25",
    location: "Block 25",
    floorInfo: "6th Floor",
    specialty: "Chaat, Samosa & North Indian Snacks",
    avgWaitMinutes: 5,
    icon: "🥟",
    isOpen: true,
    closeReason: "",
    imageUrl: "",
    timings: "7:30 AM – 9:30 PM"
  },
  {
    id: "canteen_26",
    name: "Govinda's Kitchen",
    block: "26",
    location: "Block 26",
    floorInfo: "6th Floor",
    specialty: "South Indian Breakfast, Dosa & Filter Kaapi",
    avgWaitMinutes: 5,
    icon: "🍛",
    isOpen: true,
    closeReason: "",
    imageUrl: "",
    timings: "7:00 AM – 10:00 PM"
  },
  {
    id: "canteen_28",
    name: "Vishal Dhaba & Cafe",
    block: "28",
    location: "Block 28",
    floorInfo: "5th Floor",
    specialty: "Dhaba Style Parathas & Meals",
    avgWaitMinutes: 5,
    icon: "🥘",
    isOpen: true,
    closeReason: "",
    imageUrl: "",
    timings: "8:00 AM – 10:00 PM"
  },
  {
    id: "canteen_29",
    name: "P.R Terrace Cafe",
    block: "29",
    location: "Block 29",
    floorInfo: "5th Floor",
    specialty: "Terrace Cafe, Burgers, Pasta & Shakes",
    avgWaitMinutes: 5,
    icon: "🍕",
    isOpen: true,
    closeReason: "",
    imageUrl: "",
    timings: "8:00 AM – 9:30 PM"
  }
];

const seedDatabase = async () => {
  try {
    await connectDB();
    console.log('🚀 Initializing preserved canteen names in MongoDB Atlas...');

    for (const c of canteensData) {
      await Canteen.findOneAndUpdate(
        { id: c.id },
        { $set: c },
        { upsert: true, new: true }
      );
    }
    console.log(`✅ Upserted ${canteensData.length} canteen names.`);
    console.log('📌 Predefined items, owners, users, orders, and inventory are omitted as requested.');
    process.exit(0);
  } catch (error) {
    console.error('❌ Seeding failed:', error);
    process.exit(1);
  }
};

seedDatabase();
