const path = require('path');
const dotenv = require('dotenv');

dotenv.config({ path: path.resolve(__dirname, '../../.env') });
dotenv.config();

const connectDB = require('./config/db');
const Item = require('./models/Item');
const Review = require('./models/Review');

async function seedReviews() {
  try {
    await connectDB();
    console.log('Connected to MongoDB for seeding reviews...');

    const items = await Item.find({}).limit(10);
    if (!items || items.length === 0) {
      console.log('No items found in DB yet.');
      process.exit(0);
    }

    const sampleReviewsData = [
      {
        userName: 'Aarav Patel',
        rating: 5,
        comment: 'Crispy, hot, and bursting with authentic flavor! Definitely my go-to lunch at Block 33.',
      },
      {
        userName: 'Priya Sharma',
        rating: 4.8,
        comment: 'Super fresh ingredients and the preparation time was very quick. Loved the seasoning.',
      },
      {
        userName: 'Rohan Mehra',
        rating: 4.5,
        comment: 'Great portion size for the price. Taste was consistently good and served piping hot.',
      },
      {
        userName: 'Ananya Verma',
        rating: 5,
        comment: 'One of the best dishes on campus! Delicious and freshly made.',
      },
    ];

    for (const item of items) {
      const existingReviews = await Review.find({ itemId: item.id });
      if (existingReviews.length === 0) {
        console.log(`Seeding reviews for item: ${item.name} (${item.id})`);
        const itemReviews = sampleReviewsData.map((rev, idx) => ({
          id: `rev_${item.id}_${idx + 1}`,
          itemId: item.id,
          canteenId: item.canteenId,
          userName: rev.userName,
          rating: rev.rating,
          comment: rev.comment,
          createdAt: new Date(Date.now() - (idx + 1) * 86400000 * 2),
        }));

        await Review.insertMany(itemReviews);

        const avg = Math.round((itemReviews.reduce((sum, r) => sum + r.rating, 0) / itemReviews.length) * 10) / 10;
        item.rating = avg;
        item.reviewCount = itemReviews.length;
        await item.save();
      }
    }

    console.log('Seeding reviews completed successfully!');
    process.exit(0);
  } catch (err) {
    console.error('Error seeding reviews:', err);
    process.exit(1);
  }
}

seedReviews();

