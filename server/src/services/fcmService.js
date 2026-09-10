const User = require('../models/User');
const Owner = require('../models/Owner');

let admin = null;
let isFcmInitialized = false;

try {
  // Check if firebase-admin is available
  admin = require('firebase-admin');

  let credential = null;
  if (process.env.FIREBASE_SERVICE_ACCOUNT) {
    try {
      const parsed = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
      credential = admin.credential.cert(parsed);
    } catch (e) {
      console.warn('[FCMService] Could not parse FIREBASE_SERVICE_ACCOUNT env JSON:', e.message);
    }
  } else {
    // Check if local serviceAccountKey.json exists
    const fs = require('fs');
    const path = require('path');
    const keyPath = path.resolve(__dirname, '../../serviceAccountKey.json');
    if (fs.existsSync(keyPath)) {
      const serviceAccount = require(keyPath);
      credential = admin.credential.cert(serviceAccount);
    }
  }

  if (credential) {
    admin.initializeApp({ credential });
    isFcmInitialized = true;
    console.log('🔔 Firebase Admin SDK initialized for FCM push notifications.');
  } else {
    console.log('ℹ️ FCM Push Notifications: Service account credentials not provided. (Provide FIREBASE_SERVICE_ACCOUNT or serviceAccountKey.json to send background push alerts to devices when app is closed).');
  }
} catch (e) {
  console.log('ℹ️ Firebase Admin SDK not installed or not configured yet:', e.message);
}

/**
 * Send FCM push notification directly to a registration token.
 * Note: High priority + notification payload ensures display in Android notification tray even when app is killed.
 */
async function sendPushToToken(token, { title, body, data = {} }) {
  if (!token || !token.trim()) return { success: false, reason: 'No token' };
  if (!isFcmInitialized || !admin) {
    console.log(`[FCM Mock] Push message would be delivered to token: ${token.substring(0, 15)}... Title: "${title}" Body: "${body}"`);
    return { success: false, reason: 'FCM not configured on server' };
  }

  try {
    const payload = {
      token: token.trim(),
      notification: {
        title,
        body,
      },
      data: {
        ...data,
        click_action: 'FLUTTER_NOTIFICATION_CLICK',
        channel_id: 'messq_orders_channel',
      },
      android: {
        priority: 'high',
        notification: {
          channelId: 'messq_orders_channel',
          sound: 'default',
          priority: 'high',
        },
      },
    };

    const response = await admin.messaging().send(payload);
    console.log(`[FCMService] Push delivered successfully to token. MessageId: ${response}`);
    return { success: true, messageId: response };
  } catch (err) {
    console.warn(`[FCMService] Failed to send push notification:`, err.message);
    return { success: false, error: err.message };
  }
}

/**
 * Send push notification to a student or owner by their ID.
 */
async function sendPushToUser(userId, { title, body, data = {} }) {
  try {
    // Look up in User model
    let target = await User.findOne({ $or: [{ uid: userId }, { registrationNumber: userId }] }).lean();
    if (!target) {
      target = await Owner.findOne({ $or: [{ ownerId: userId }, { canteenId: userId }] }).lean();
    }

    if (target && target.fcmToken) {
      return await sendPushToToken(target.fcmToken, { title, body, data });
    } else {
      console.log(`[FCMService] No FCM registration token registered for user ${userId}`);
      return { success: false, reason: 'User has no registered FCM token' };
    }
  } catch (err) {
    console.warn(`[FCMService] Error finding user token for ${userId}:`, err.message);
    return { success: false, error: err.message };
  }
}

module.exports = {
  isFcmInitialized: () => isFcmInitialized,
  sendPushToToken,
  sendPushToUser,
};
