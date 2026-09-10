const path = require('path');
const dotenv = require('dotenv');

dotenv.config({ path: path.resolve(__dirname, '../../.env') });
dotenv.config();

const connectDB = require('./config/db');
const Item = require('./models/Item');
const Order = require('./models/Order');

// Complete Block 33 menu requested by user
const block33Items = [
  // ── BREAKFAST ───────────────────────────────────────────────────────────────
  { name: "Chana Bhatura", price: 75, category: "BREAKFAST", prepMinutes: 8 },
  { name: "Chana Puri", price: 75, category: "BREAKFAST", prepMinutes: 8 },
  { name: "Chana Kulcha", price: 75, category: "BREAKFAST", prepMinutes: 6 },
  { name: "Nutri Kulcha", price: 60, category: "BREAKFAST", prepMinutes: 6 },
  { name: "Aloo Prantha", price: 35, category: "BREAKFAST", prepMinutes: 7 },
  { name: "Gobi Prantha", price: 35, category: "BREAKFAST", prepMinutes: 7 },
  { name: "Onion Prantha", price: 35, category: "BREAKFAST", prepMinutes: 7 },
  { name: "Mix Prantha", price: 35, category: "BREAKFAST", prepMinutes: 7 },
  { name: "Paneer Prantha", price: 50, category: "BREAKFAST", prepMinutes: 8 },
  { name: "Poha", price: 40, category: "BREAKFAST", prepMinutes: 5 },
  { name: "Samosa", price: 20, category: "BREAKFAST", prepMinutes: 3 },
  { name: "Chana Samosa", price: 50, category: "BREAKFAST", prepMinutes: 5 },
  { name: "Patty", price: 25, category: "BREAKFAST", prepMinutes: 3 },
  { name: "Bread Pakora", price: 20, category: "BREAKFAST", prepMinutes: 3 },
  { name: "Maggi", price: 30, category: "BREAKFAST", prepMinutes: 5 },
  { name: "Veg. Maggi", price: 50, category: "BREAKFAST", prepMinutes: 7 },
  { name: "Aloo Sandwich", price: 30, category: "BREAKFAST", prepMinutes: 5 },
  { name: "Paneer Sandwich", price: 50, category: "BREAKFAST", prepMinutes: 6 },

  // ── HEALTHY BREAKFAST ───────────────────────────────────────────────────────
  { name: "Masala Oats", price: 40, category: "HEALTHY BREAKFAST", prepMinutes: 5 },
  { name: "Milk Oats", price: 50, category: "HEALTHY BREAKFAST", prepMinutes: 5 },
  { name: "Milk Dalia", price: 50, category: "HEALTHY BREAKFAST", prepMinutes: 6 },
  { name: "Peanut Butter with Banana (2 Pc.)", price: 50, category: "HEALTHY BREAKFAST", prepMinutes: 4 },

  // ── BEVERAGES ───────────────────────────────────────────────────────────────
  { name: "Tea", price: 15, category: "BEVERAGES", prepMinutes: 3 },
  { name: "Coffee", price: 30, category: "BEVERAGES", prepMinutes: 4 },
  { name: "Lassi (Sweet & Salted)", price: 40, category: "BEVERAGES", prepMinutes: 3 },
  { name: "Shakes (Banana, Mango, Chocolate, Strawberry)", price: 60, category: "BEVERAGES", prepMinutes: 5 },

  // ── LUNCH ───────────────────────────────────────────────────────────────────
  { name: "Normal Thali (Dal, Veg, Sabji, Rice, Raita, Salad, 3 Roti)", price: 90, category: "LUNCH", prepMinutes: 7 },
  { name: "Special Thali (Paneer, Dal, Veg, Sabji, Rice, Raita, Salad, 3 Roti)", price: 90, category: "LUNCH", prepMinutes: 8 },
  { name: "Dal Rice", price: 75, category: "LUNCH", prepMinutes: 5 },
  { name: "Chana-Rice", price: 75, category: "LUNCH", prepMinutes: 5 },
  { name: "Kari Rice", price: 75, category: "LUNCH", prepMinutes: 5 },
  { name: "Rajma Rice", price: 75, category: "LUNCH", prepMinutes: 5 },
  { name: "Paneer Rice", price: 90, category: "LUNCH", prepMinutes: 6 },
  { name: "Paneer Bowl", price: 90, category: "LUNCH", prepMinutes: 6 },
  { name: "Dal Rajma Bowl", price: 90, category: "LUNCH", prepMinutes: 5 },
  { name: "Dry Sabji Bowl", price: 50, category: "LUNCH", prepMinutes: 4 },
  { name: "Sabji + Roti", price: 60, category: "LUNCH", prepMinutes: 6 },
  { name: "Paneer + 4 Roti", price: 70, category: "LUNCH", prepMinutes: 7 },
  { name: "Paneer Burji + 2 Plain Prantha", price: 80, category: "LUNCH", prepMinutes: 8 },
  { name: "Paneer Burji + 2 Bread", price: 80, category: "LUNCH", prepMinutes: 6 },

  // ── SNACKS ──────────────────────────────────────────────────────────────────
  { name: "Noodles", price: 70, category: "SNACKS", prepMinutes: 8 },
  { name: "Manchurian", price: 80, category: "SNACKS", prepMinutes: 9 },
  { name: "Fried Rice", price: 80, category: "SNACKS", prepMinutes: 8 },
  { name: "Lemon Rice", price: 80, category: "SNACKS", prepMinutes: 7 },
  { name: "Noodles + Manchurian", price: 100, category: "SNACKS", prepMinutes: 10 },
  { name: "Fried Rice + Manchurian", price: 80, category: "SNACKS", prepMinutes: 9 },
  { name: "Grilled Sandwich", price: 80, category: "SNACKS", prepMinutes: 7 },
  { name: "Nutri + Kulcha", price: 60, category: "SNACKS", prepMinutes: 6 },
  { name: "Pav Bhaji", price: 70, category: "SNACKS", prepMinutes: 8 },
  { name: "White Sauce Pasta", price: 90, category: "SNACKS", prepMinutes: 10 },
  { name: "Red Sauce Pasta", price: 60, category: "SNACKS", prepMinutes: 9 },
  { name: "Dahi Bhalla", price: 50, category: "SNACKS", prepMinutes: 4 },
  { name: "French Fries", price: 50, category: "SNACKS", prepMinutes: 6 },
  { name: "Sweet Corn", price: 50, category: "SNACKS", prepMinutes: 4 },
];

async function runSeed() {
  try {
    await connectDB();
    console.log('Connected to MongoDB Atlas...');

    // 1. Clear any old/pre-defined orders in MongoDB
    const deletedOrders = await Order.deleteMany({});
    console.log(`🗑️ Cleared ${deletedOrders.deletedCount} orders from MongoDB database.`);

    // Fetch existing Chana Bhatura to preserve its uploaded Cloudinary image
    const existingChanaBhatura = await Item.findOne({
      canteenId: 'canteen_33',
      name: { $regex: /chana bhatura/i },
    });

    // 2. Remove previous items for canteen_33 to ensure clean slate
    const deletedItems = await Item.deleteMany({ canteenId: 'canteen_33' });
    console.log(`Cleaned up ${deletedItems.deletedCount} previous items for canteen_33.`);

    // 3. Insert Block 33 menu items
    const docs = block33Items.map((item, index) => {
      const slug = item.name.toLowerCase().replace(/[^a-z0-9]/g, '_').substring(0, 25);
      const isChanaBhatura = item.name.toLowerCase().includes('chana bhatura');
      return {
        id: `c33_item_${String(index + 1).padStart(3, '0')}_${slug}`,
        canteenId: 'canteen_33',
        name: item.name,
        description: `${item.name} freshly prepared at Block 33 Canteen`,
        price: item.price,
        category: item.category,
        imageUrl: isChanaBhatura && existingChanaBhatura?.imageUrl ? existingChanaBhatura.imageUrl : '',
        cloudinaryPublicId: isChanaBhatura && existingChanaBhatura?.cloudinaryPublicId ? existingChanaBhatura.cloudinaryPublicId : null,
        isCustom: false,
        available: true,
        stock: 100,
        preparationTime: `${item.prepMinutes - 2 > 0 ? item.prepMinutes - 2 : 2}-${item.prepMinutes} min`,
        prepMinutes: item.prepMinutes,
        rating: 4.5,
      };
    });

    const inserted = await Item.insertMany(docs);
    console.log(`✅ Successfully inserted ${inserted.length} items for Block 33 Canteen!`);

    process.exit(0);
  } catch (err) {
    console.error('Error inserting Block 33 items:', err);
    process.exit(1);
  }
}

runSeed();
