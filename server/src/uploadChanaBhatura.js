const path = require('path');
const dotenv = require('dotenv');

dotenv.config({ path: path.resolve(__dirname, '../../.env') });
dotenv.config();

const { cloudinary } = require('./config/cloudinary');
const connectDB = require('./config/db');
const Item = require('./models/Item');

async function uploadAndLinkChanaBhatura() {
  try {
    console.log('Connecting to MongoDB Atlas...');
    await connectDB();

    const localImagePath = path.join(__dirname, 'chana_bhatura.jpg');
    console.log(`Uploading ${localImagePath} to Cloudinary...`);

    const uploadRes = await cloudinary.uploader.upload(localImagePath, {
      folder: 'campus-canteen/items/canteen_33',
      public_id: 'c33_chana_bhatura',
      overwrite: true,
      transformation: [
        { width: 800, height: 800, crop: 'limit' },
        { quality: 'auto', fetch_format: 'auto' }
      ]
    });

    console.log('✅ Cloudinary Upload Success!');
    console.log('Secure URL:', uploadRes.secure_url);
    console.log('Public ID:', uploadRes.public_id);

    // Update Chana Bhatura in MongoDB for canteen_33
    const updated = await Item.findOneAndUpdate(
      {
        canteenId: 'canteen_33',
        name: { $regex: /chana bhatura/i }
      },
      {
        $set: {
          imageUrl: uploadRes.secure_url,
          cloudinaryPublicId: uploadRes.public_id,
          isCustom: false
        }
      },
      { new: true }
    );

    if (updated) {
      console.log('✅ Successfully updated Chana Bhatura in MongoDB:');
      console.log({
        id: updated.id,
        name: updated.name,
        price: updated.price,
        imageUrl: updated.imageUrl,
        cloudinaryPublicId: updated.cloudinaryPublicId
      });
    } else {
      console.warn('⚠️ Item "Chana Bhatura" was not found under canteen_33');
    }

    process.exit(0);
  } catch (err) {
    console.error('❌ Upload or update failed:', err);
    process.exit(1);
  }
}

uploadAndLinkChanaBhatura();
