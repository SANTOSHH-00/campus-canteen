package com.example.data.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MessQFirebaseMessagingService : FirebaseMessagingService() {

  private val TAG = "MessQFCM"
  private val CHANNEL_ID = "messq_orders_channel"
  private val CHANNEL_NAME = "MessQ Order & Rush Updates"

  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun onNewToken(token: String) {
    super.onNewToken(token)
    Log.d(TAG, "New FCM Registration Token: $token")
    val authRepo = AuthRepository()
    val firestoreRepo = FirestoreRepository()
    val currentUid = authRepo.currentUser?.uid
    if (currentUid != null) {
      CoroutineScope(Dispatchers.IO).launch {
        firestoreRepo.updateUserFcmToken(currentUid, token)
      }
    }
  }

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    super.onMessageReceived(remoteMessage)
    Log.d(TAG, "From: ${remoteMessage.from}")

    val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "MessQ Campus Canteen"
    val body = remoteMessage.notification?.body ?: remoteMessage.data["message"] ?: "New notification for your order."

    com.example.data.session.SessionManager.saveIncomingNotification(title, body)

    showNotification(title, body)
  }

  private fun showNotification(title: String, body: String) {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Instant notifications when your order is preparing or ready for pickup."
        enableVibration(true)
      }
      notificationManager.createNotificationChannel(channel)
    }

    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(title)
      .setContentText(body)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setContentIntent(pendingIntent)
      .build()

    notificationManager.notify(System.currentTimeMillis().toInt(), notification)
  }
}
