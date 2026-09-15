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

  // In-memory cache to prevent duplicate notifications for the same order and status within a time window
  private val lastNotifiedMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

  fun showOrderStatusNotification(
    context: Context,
    tokenNumber: String,
    status: String,
    counter: String = "Counter 1",
    canteenName: String = "Canteen Kitchen",
    block: String = "Campus",
  ) {
    try {
      val rawStatus = status.uppercase().trim()

      // STRICT REQUIREMENT: Only notify about preparing, ready and picked up/completed.
      val normalizedStatus = when (rawStatus) {
        "PREPARING" -> "PREPARING"
        "READY" -> "READY"
        "PICKED_UP", "PICKED UP", "COMPLETED", "DELIVERED" -> "COMPLETED"
        "CANCELLED" -> "CANCELLED"
        else -> return
      }

      val cleanToken = tokenNumber.filter { it.isDigit() }.takeLast(4).ifBlank { tokenNumber.replace("#", "").trim() }
      val formattedToken = "#$cleanToken"
      val dedupKey = "$cleanToken:$normalizedStatus"
      val now = System.currentTimeMillis()

      // Deduplicate: If this token was already notified for this exact status within the last 60s, do NOT notify again
      val lastTime = lastNotifiedMap[dedupKey]
      if (lastTime != null && (now - lastTime) < 60_000L) {
        return
      }
      lastNotifiedMap[dedupKey] = now

      val effectiveCounter = if (counter.isNotBlank()) counter else "Counter 1"
      val blockNumber = block.replace(Regex("(?i)block\\s*"), "").trim()
      val blockDisplay = if (blockNumber.isNotBlank()) "Block $blockNumber" else block.ifBlank { "Campus" }
      val effectiveCanteen = canteenName.ifBlank { "Canteen Kitchen" }

      val (title, message) = when (normalizedStatus) {
        "PREPARING" -> Pair(
          "Order $formattedToken: Preparing",
          "Your meal is being prepared by $effectiveCanteen at $blockDisplay, $effectiveCounter."
        )
        "READY" -> Pair(
          "Order $formattedToken: Ready for Pickup",
          "Your order is ready! Please collect your food at $effectiveCounter from $effectiveCanteen at $blockDisplay."
        )
        "COMPLETED" -> Pair(
          "Order $formattedToken: Picked Up",
          "Your order has been picked up. Enjoy your food!"
        )
        "CANCELLED" -> Pair(
          "Order $formattedToken: Cancelled",
          "Your order has been cancelled by the canteen kitchen."
        )
        else -> return
      }

      postSystemNotification(context, cleanToken, title, message)
    } catch (e: Exception) {
      android.util.Log.e("OrderNotificationHelper", "Error showing notification: ${e.message}", e)
    }
  }

  fun showOrderStatusNotification(
    context: Context,
    tokenNumber: String,
    title: String,
    body: String,
  ) {
    try {
      val cleanToken = tokenNumber.filter { it.isDigit() }.takeLast(4).ifBlank { tokenNumber.replace("#", "").trim() }
      val dedupKey = "$cleanToken:${title.trim().lowercase()}"
      val now = System.currentTimeMillis()

      val lastTime = lastNotifiedMap[dedupKey]
      if (lastTime != null && (now - lastTime) < 60_000L) {
        return
      }
      lastNotifiedMap[dedupKey] = now

      postSystemNotification(context, cleanToken, title, body)
    } catch (e: Exception) {
      android.util.Log.e("OrderNotificationHelper", "Error showing push notification: ${e.message}", e)
    }
  }

  private fun postSystemNotification(
    context: Context,
    cleanToken: String,
    title: String,
    message: String,
  ) {
    val notificationManager =
      context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

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

    // Persist to SessionManager notifications tab with deduplication
    com.example.data.session.SessionManager.saveIncomingNotification(title, message)

    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      context,
      (cleanToken.toIntOrNull() ?: 100) + 500,
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

    val notificationId = (cleanToken.toIntOrNull() ?: 1042) % 10000 + 2000
    notificationManager.notify(notificationId, notification)
  }
}
