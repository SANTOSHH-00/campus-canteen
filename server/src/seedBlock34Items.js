const path = require('path');
const dotenv = require('dotenv');

dotenv.config({ path: path.resolve(__dirname, '../../.env') });
dotenv.config();

const connectDB = require('./config/db');
const Item = require('./models/Item');

const block34Items = [
  // ── NORTH INDIAN / MEALS ───────────────────────────────────────────────────
  {
    name: "Normal Roti Meal",
    description: "Daal / Chana Gravy + 4 Roti",
    price: 75,
    category: "LUNCH",
    prepMinutes: 7,
    ingredients: ["Daal / Chana Gravy", "Roti"]
  },
  {
    name: "Normal Rice Meal",
    description: "Daal / Chana Gravy + Rice",
    price: 75,
    category: "LUNCH",
    prepMinutes: 7,
    ingredients: ["Daal / Chana Gravy", "Rice"]
  },
  {
    name: "Normal Paratha Meal",
    description: "Daal / Chana Gravy + 3 Paratha",
    price: 85,
    category: "LUNCH",
    prepMinutes: 8,
    ingredients: ["Daal / Chana Gravy", "Paratha"]
  },
  {
    name: "Special Roti Meal",
    description: "Any Gravy + 4 Roti",
    price: 85,
    category: "LUNCH",
    prepMinutes: 8,
    ingredients: ["Special Gravy", "Roti"]
  },
  {
    name: "Special Rice Meal",
    description: "Any Gravy + Rice",
    price: 85,
    category: "LUNCH",
    prepMinutes: 8,
    ingredients: ["Special Gravy", "Rice"]
  },
  {
    name: "Special Paratha Meal",
    description: "Any Gravy + 3 Paratha",
    price: 95,
    category: "LUNCH",
    prepMinutes: 9,
    ingredients: ["Special Gravy", "Paratha"]
  },
  {
    name: "Lallantop Thali",
    description: "Any 2 Gravies + Daal / Chhole + 3 Roti + Rice",
    price: 120,
    category: "LUNCH",
    prepMinutes: 10,
    ingredients: ["2 Gravies", "Daal / Chhole", "3 Roti", "Rice"]
  },
  {
    name: "Biryani",
    description: "Hyderabadi dum Biryani",
    price: 70,
    category: "LUNCH",
    prepMinutes: 7,
    ingredients: ["Biryani Rice", "Aromatic Spices"]
  },
  {
    name: "Gravy + Fried Rice",
    description: "Paneer Gravy with Fried Rice",
    price: 90,
    category: "LUNCH",
    prepMinutes: 8,
    ingredients: ["Paneer Gravy", "Fried Rice"]
  },
  {
    name: "Gravy + Pulao Rice",
    description: "Paneer Gravy with Pulao Rice",
    price: 90,
    category: "LUNCH",
    prepMinutes: 8,
    ingredients: ["Paneer Gravy", "Pulao Rice"]
  },
  {
    name: "Aloo Pyaz Paratha",
    description: "Flatbread stuffed with Potato and Onions",
    price: 35,
    category: "BREAKFAST",
    prepMinutes: 7,
    ingredients: ["Potato", "Onion", "Wheat Flour"]
  },

  // ── RICE ───────────────────────────────────────────────────────────────────
  {
    name: "Fried Rice",
    description: "Stir-fried in a wok and mixed with vegetables",
    price: 70,
    category: "LUNCH",
    prepMinutes: 8,
    ingredients: ["Rice", "Vegetables", "Spices"]
  },
  {
    name: "Pulao Rice",
    description: "Fragrant rice with aromatic whole spices",
    price: 70,
    category: "LUNCH",
    prepMinutes: 7,
    ingredients: ["Basmati Rice", "Whole Spices"]
  },
  {
    name: "Lemon Rice",
    description: "Cooked rice with a special seasoning",
    price: 60,
    category: "LUNCH",
    prepMinutes: 7,
    ingredients: ["Rice", "Lemon Seasoning", "Mustard Seeds", "Curry Leaves"]
  },

  // ── SOUTH INDIAN ───────────────────────────────────────────────────────────
  {
    name: "Idli Sambar",
    description: "Rice cake with hot spicy vegetable stew",
    price: 60,
    category: "BREAKFAST",
    prepMinutes: 6,
    ingredients: ["Steamed Idli", "Sambar", "Chutney"]
  },
  {
    name: "Vada Sambar",
    description: "Lentil fritters with hot spicy vegetable stew",
    price: 60,
    category: "BREAKFAST",
    prepMinutes: 6,
    ingredients: ["Crispy Medu Vada", "Sambar", "Chutney"]
  },

  // ── SNACKS ─────────────────────────────────────────────────────────────────
  {
    name: "Pav Bhaji",
    description: "Thick vegetable curry served with soft bread",
    price: 70,
    category: "SNACKS",
    prepMinutes: 8,
    ingredients: ["Mixed Veg Curry", "Butter Pav"]
  },
  {
    name: "Dahi Bhalle",
    description: "Soaking Bhalla in thick dahi",
    price: 60,
    category: "SNACKS",
    prepMinutes: 5,
    ingredients: ["Lentil Dumpling", "Curd", "Sweet & Spicy Chutneys"]
  },
  {
    name: "Samosa",
    description: "Fried pastry with savoury filling",
    price: 25,
    category: "SNACKS",
    prepMinutes: 4,
    ingredients: ["Crispy Pastry", "Spiced Potato Filling"]
  },
  {
    name: "1 Samosa + Chana",
    description: "Single Samosa with Chana gravy",
    price: 30,
    category: "SNACKS",
    prepMinutes: 5,
    ingredients: ["1 Samosa", "Chana Gravy"]
  },
  {
    name: "2 Samosa + Chana",
    description: "Double Samosa with Chana gravy",
    price: 50,
    category: "SNACKS",
    prepMinutes: 5,
    ingredients: ["2 Samosas", "Chana Gravy"]
  },
  {
    name: "Bread Pakoda",
    description: "Bread slices dipped in gram flour batter & fried",
    price: 20,
    category: "SNACKS",
    prepMinutes: 5,
    ingredients: ["Bread", "Besan", "Potato Masala"]
  },

  // ── BEVERAGES ──────────────────────────────────────────────────────────────
  {
    name: "Chai",
    description: "Indian hot beverage",
    price: 15,
    category: "BEVERAGES",
    prepMinutes: 4,
    ingredients: ["Tea leaves", "Milk", "Ginger / Cardamom"]
  },
  {
    name: "Coffee",
    description: "Hot beverage",
    price: 30,
    category: "BEVERAGES",
    prepMinutes: 5,
    ingredients: ["Coffee", "Milk"]
  },
  {
    name: "Lassi",
    description: "Indian cold beverage",
    price: 40,
    category: "BEVERAGES",
    prepMinutes: 4,
    ingredients: ["Yogurt", "Sugar / Cardamom"]
  },
];

async function runSeed() {
  try {
    await connectDB();
    console.log('Connected to MongoDB Atlas...');

    // 1. Fetch existing items to preserve any custom images already set
    const existingItems = await Item.find({ canteenId: 'canteen_34' });
    const imageMap = new Map();
    for (const item of existingItems) {
      if (item.imageUrl) {
        imageMap.set(item.name.toLowerCase().trim(), {
          imageUrl: item.imageUrl,
          cloudinaryPublicId: item.cloudinaryPublicId,
        });
      }
    }

    // 2. Clear old items for Block 34
    const deleted = await Item.deleteMany({ canteenId: 'canteen_34' });
    console.log(`🗑️ Cleaned up ${deleted.deletedCount} previous items for Block 34 (canteen_34).`);

    // 3. Prepare items with appropriate IDs and attributes
    const docs = block34Items.map((item, index) => {
      const slug = item.name.toLowerCase().replace(/[^a-z0-9]/g, '_').substring(0, 25);
      const preservedImage = imageMap.get(item.name.toLowerCase().trim());

      return {
        id: `c34_item_${String(index + 1).padStart(3, '0')}_${slug}`,
        canteenId: 'canteen_34',
        name: item.name,
        description: item.description,
        price: item.price,
        category: item.category,
        imageUrl: preservedImage?.imageUrl || '',
        cloudinaryPublicId: preservedImage?.cloudinaryPublicId || null,
        isCustom: false,
        available: true,
        stock: 100,
        preparationTime: `${item.prepMinutes - 2 > 0 ? item.prepMinutes - 2 : 2}-${item.prepMinutes} min`,
        prepMinutes: item.prepMinutes,
        rating: 4.5,
        ingredients: item.ingredients || [],
        customizationTitle: '',
        customizationOptions: [],
        addons: [],
      };
    });

    const inserted = await Item.insertMany(docs);
    console.log(`✅ Successfully inserted ${inserted.length} items for Block 34 Canteen!`);
    process.exit(0);
  } catch (err) {
    console.error('❌ Error inserting Block 34 items:', err);
    process.exit(1);
  }
}

runSeed();
