package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

object OrderNotificationHelper {

  private const val CHANNEL_ID = "quickbite_order_updates"
  private const val CHANNEL_NAME = "QuickBite Order Status"

  fun showOrderStatusNotification(
    context: Context,
    tokenNumber: String,
    status: String,
    counter: String = "Counter 1",
  ) {
    try {
      val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

      // Register notification channel with HIGH importance for heads-up pop-up alert
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          CHANNEL_NAME,
          NotificationManager.IMPORTANCE_HIGH
        ).apply {
          description = "Live notifications when your QuickBite order status is updated by the canteen"
          enableVibration(true)
          enableLights(true)
          setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)
      }

      val formattedToken = if (tokenNumber.startsWith("#")) tokenNumber else "#Q$tokenNumber"

      val (title, message) = when (status.uppercase()) {
        "PREPARING" -> Pair(
          "Order $formattedToken: Preparing",
          "Your order is being prepared by the canteen kitchen."
        )
        "READY" -> Pair(
          "Order $formattedToken: Ready for Pickup",
          "Your order is ready. Please collect your food at $counter."
        )
        "PICKED_UP", "COMPLETED", "DELIVERED" -> Pair(
          "Order $formattedToken: Completed",
          "Your order has been completed. Thank you for dining with QuickBite."
        )
        "CANCELLED" -> Pair(
          "Order $formattedToken: Cancelled",
          "Your order has been cancelled by the canteen kitchen."
        )
        else -> Pair(
          "Order $formattedToken Update",
          "Your order status has been updated to $status."
        )
      }

      val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
      val pendingIntent = PendingIntent.getActivity(
        context,
        System.currentTimeMillis().toInt(),
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )

      val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

      val notificationId = (tokenNumber.filter { it.isDigit() }.toIntOrNull() ?: 1042) + 2000
      notificationManager.notify(notificationId, notification)
    } catch (e: Exception) {
      android.util.Log.e("OrderNotificationHelper", "Error showing notification: ${e.message}", e)
    }
  }
}
