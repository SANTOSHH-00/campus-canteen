const path = require('path');
const dotenv = require('dotenv');

dotenv.config({ path: path.resolve(__dirname, '../../.env') });
dotenv.config();

const connectDB = require('./config/db');
const Canteen = require('./models/Canteen');
const Item = require('./models/Item');
const Owner = require('./models/Owner');
const User = require('./models/User');
const Order = require('./models/Order');
const Inventory = require('./models/Inventory');
const Notification = require('./models/Notification');
const OtpVerification = require('./models/OtpVerification');
const PasswordResetToken = require('./models/PasswordResetToken');

// Preserved Canteen Names per project requirements
const canteensToPreserve = [
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
    imageUrl: "", // Supabase storage removed
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

const cleanDatabase = async () => {
  try {
    await connectDB();
    console.log('🧹 Cleaning MongoDB database while preserving canteen names...');

    // 1. Remove all food items
    const deletedItems = await Item.deleteMany({});
    console.log(`🗑️ Deleted ${deletedItems.deletedCount} food items.`);

    // 2. Remove all inventory records
    const deletedInventory = await Inventory.deleteMany({});
    console.log(`🗑️ Deleted ${deletedInventory.deletedCount} inventory records.`);

    // 3. Remove all canteen owners
    const deletedOwners = await Owner.deleteMany({});
    console.log(`🗑️ Deleted ${deletedOwners.deletedCount} owner accounts.`);

    // 4. Remove all sample users
    const deletedUsers = await User.deleteMany({});
    console.log(`🗑️ Deleted ${deletedUsers.deletedCount} student/user records.`);

    // 5. Remove all sample orders
    const deletedOrders = await Order.deleteMany({});
    console.log(`🗑️ Deleted ${deletedOrders.deletedCount} orders.`);

    // 6. Remove all notifications
    const deletedNotifications = await Notification.deleteMany({});
    console.log(`🗑️ Deleted ${deletedNotifications.deletedCount} notifications.`);

    // 7. Remove OTP / reset tokens
    await OtpVerification.deleteMany({});
    await PasswordResetToken.deleteMany({});
    console.log(`🗑️ Cleared OTP and password reset tokens.`);

    // 8. Preserve canteen names only (strip out Supabase storage URLs)
    for (const c of canteensToPreserve) {
      await Canteen.findOneAndUpdate(
        { id: c.id },
        {
          $set: {
            id: c.id,
            name: c.name,
            block: c.block,
            location: c.location,
            floorInfo: c.floorInfo,
            specialty: c.specialty,
            avgWaitMinutes: c.avgWaitMinutes,
            icon: c.icon,
            isOpen: c.isOpen,
            closeReason: c.closeReason,
            imageUrl: "", // Cleaned of Supabase Storage URL
            timings: c.timings
          }
        },
        { upsert: true, new: true }
      );
    }
    console.log(`✅ Preserved all ${canteensToPreserve.length} canteen names without any fake or predefined data.`);

    console.log('✨ DATABASE CLEAN COMPLETE. Ready for real production data.');
    process.exit(0);
  } catch (err) {
    console.error('❌ Database cleaning failed:', err);
    process.exit(1);
  }
};

cleanDatabase();
