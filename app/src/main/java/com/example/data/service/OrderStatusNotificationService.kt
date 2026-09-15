package com.example.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.api.MongoRepository
import com.example.data.api.WebSocketManager
import com.example.data.session.SessionManager
import com.example.util.OrderNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background Service to track active orders and deliver status update notifications
 * to the system notification tray even when the user closes or swipes away the app.
 */
class OrderStatusNotificationService : Service() {

  companion object {
    private const val TAG = "OrderStatusService"
    private const val FOREGROUND_CHANNEL_ID = "quickbite_order_monitor_channel"
    private const val FOREGROUND_CHANNEL_NAME = "Order Live Tracking Service"
    private const val FOREGROUND_NOTIFICATION_ID = 9001

    const val EXTRA_ORDER_ID = "extra_order_id"
    const val EXTRA_TOKEN = "extra_token"
    const val EXTRA_CANTEEN_ID = "extra_canteen_id"
    const val EXTRA_CANTEEN_NAME = "extra_canteen_name"
    const val EXTRA_BLOCK = "extra_block"
    const val EXTRA_STUDENT_ID = "extra_student_id"

    fun start(
      context: Context,
      orderId: String,
      tokenNumber: String,
      canteenId: String = "",
      studentId: String = "",
      canteenName: String = "",
      block: String = "",
    ) {
      try {
        val intent = Intent(context, OrderStatusNotificationService::class.java).apply {
          putExtra(EXTRA_ORDER_ID, orderId)
          putExtra(EXTRA_TOKEN, tokenNumber)
          putExtra(EXTRA_CANTEEN_ID, canteenId)
          putExtra(EXTRA_CANTEEN_NAME, canteenName)
          putExtra(EXTRA_BLOCK, block)
          putExtra(EXTRA_STUDENT_ID, studentId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to start OrderStatusNotificationService: ${e.message}", e)
      }
    }

    fun stop(context: Context) {
      try {
        val intent = Intent(context, OrderStatusNotificationService::class.java)
        context.stopService(intent)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to stop OrderStatusNotificationService: ${e.message}", e)
      }
    }
  }

  private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private var monitorJob: Job? = null
  private var currentOrderId: String = ""
  private var currentToken: String = ""
  private var currentCanteenId: String = ""
  private var currentCanteenName: String = ""
  private var currentBlock: String = ""
  private var currentStudentId: String = ""
  private var lastKnownStatus: String = "NEW"

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannels()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val orderId = intent?.getStringExtra(EXTRA_ORDER_ID)?.ifBlank { null }
      ?: SessionManager.getActiveOrderId()
      ?: ""

    val token = intent?.getStringExtra(EXTRA_TOKEN)?.ifBlank { null }
      ?: SessionManager.getActiveOrderToken()

    val canteenId = intent?.getStringExtra(EXTRA_CANTEEN_ID)?.ifBlank { null }
      ?: SessionManager.getActiveOrderCanteenId()

    val canteenName = intent?.getStringExtra(EXTRA_CANTEEN_NAME)?.ifBlank { null }
      ?: SessionManager.getActiveOrderCanteenName()

    val block = intent?.getStringExtra(EXTRA_BLOCK)?.ifBlank { null }
      ?: SessionManager.getActiveOrderBlock()

    val studentId = intent?.getStringExtra(EXTRA_STUDENT_ID)?.ifBlank { null }
      ?: SessionManager.getStudentSession()?.registrationNumber
      ?: SessionManager.getStudentSession()?.email
      ?: ""

    if (orderId.isBlank()) {
      Log.i(TAG, "No active order to track, stopping service.")
      stopSelf()
      return START_NOT_STICKY
    }

    currentOrderId = orderId
    currentToken = token
    currentCanteenId = canteenId
    currentCanteenName = canteenName
    currentBlock = block
    currentStudentId = studentId
    lastKnownStatus = SessionManager.getActiveOrderLastStatus().ifBlank { "NEW" }

    // Start Foreground Service with sticky notification to prevent OS kill
    startForegroundServiceWithNotification()

    // Launch active order tracking flow
    startTracking()

    return START_STICKY
  }

  private fun startForegroundServiceWithNotification() {
    val notification = buildForegroundNotification(
      title = "QuickBite Service",
      content = "Syncing order updates in background"
    )

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
          FOREGROUND_NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
      } else {
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error starting foreground service: ${e.message}", e)
    }
  }

  private fun buildForegroundNotification(title: String, content: String): Notification {
    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(title)
      .setContentText(content)
      .setOngoing(true)
      .setSilent(true)
      .setShowWhen(false)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setPriority(NotificationCompat.PRIORITY_MIN)
      .setContentIntent(pendingIntent)
      .build()
  }

  private fun startTracking() {
    monitorJob?.cancel()
    monitorJob = serviceScope.launch {
      // 1. Connect WebSocket and subscribe to order and student channels
      try {
        WebSocketManager.connect()
        WebSocketManager.subscribe("order:$currentOrderId")
        if (currentCanteenId.isNotBlank()) {
          WebSocketManager.subscribe("canteen:$currentCanteenId")
        }
        if (currentStudentId.isNotBlank()) {
          WebSocketManager.subscribe("student:$currentStudentId")
        }
      } catch (e: Exception) {
        Log.w(TAG, "WebSocket subscription error: ${e.message}")
      }

      // 2. Real-time WebSocket listener
      val wsCollector = launch {
        WebSocketManager.orderUpdates.collect { updatedDto ->
          if (updatedDto.orderId == currentOrderId || updatedDto.tokenNumber == currentToken) {
            handleStatusUpdate(updatedDto.status, updatedDto.pickupCounter)
          }
        }
      }

      // 3. Fallback active polling loop (every 4 seconds) to guarantee delivery even if WebSocket reconnects
      val pollingLoop = launch {
        while (isActive) {
          try {
            val queryId = currentOrderId.ifBlank { currentToken }
            MongoRepository.getOrder(queryId).onSuccess { dto ->
              handleStatusUpdate(dto.status, dto.pickupCounter)
            }
          } catch (e: Exception) {
            Log.w(TAG, "Polling check warning: ${e.message}")
          }
          delay(4000)
        }
      }

      wsCollector.join()
      pollingLoop.join()
    }
  }

  private fun handleStatusUpdate(newStatus: String, counter: String) {
    val normalized = newStatus.uppercase().trim()
    if (normalized.isBlank()) return

    val oldNormalized = lastKnownStatus.uppercase().trim()

    // Only notify when status transitions or on initial significant status
    if (normalized != oldNormalized) {
      Log.i(TAG, "Order status changed from $oldNormalized to $normalized")
      lastKnownStatus = normalized
      val effectiveCounter = counter.ifBlank { SessionManager.getActiveOrderCounter() }

      // Update session persistence
      SessionManager.updateActiveOrderStatus(normalized, effectiveCounter)

      // Fire the High Priority System Notification strictly for PREPARING, READY, COMPLETED
      if (normalized in listOf("PREPARING", "READY", "COMPLETED", "PICKED_UP", "DELIVERED", "CANCELLED")) {
        OrderNotificationHelper.showOrderStatusNotification(
          context = this,
          tokenNumber = currentToken,
          status = normalized,
          counter = effectiveCounter,
          canteenName = currentCanteenName,
          block = currentBlock,
        )
      }

      // Check if order lifecycle has completed or cancelled
      val isFinished = normalized == "COMPLETED" ||
          normalized == "PICKED_UP" ||
          normalized == "DELIVERED" ||
          normalized == "CANCELLED"

      if (isFinished) {
        Log.i(TAG, "Order reached final state $normalized. Cleaning up background service.")
        SessionManager.clearActiveOrderTracking()
        serviceScope.launch {
          delay(3000) // Allow final notification to deliver cleanly
          stopForeground(STOP_FOREGROUND_REMOVE)
          stopSelf()
        }
      }
    }
  }

  private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      val channel = NotificationChannel(
        FOREGROUND_CHANNEL_ID,
        FOREGROUND_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_MIN
      ).apply {
        description = "Runs quietly in background while your meal is being prepared by the canteen"
        setShowBadge(false)
        enableLights(false)
        enableVibration(false)
      }
      notificationManager.createNotificationChannel(channel)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    monitorJob?.cancel()
    serviceScope.cancel()
    try {
      WebSocketManager.unsubscribe("order:$currentOrderId")
      if (currentCanteenId.isNotBlank()) {
        WebSocketManager.unsubscribe("canteen:$currentCanteenId")
      }
      if (currentStudentId.isNotBlank()) {
        WebSocketManager.unsubscribe("student:$currentStudentId")
      }
    } catch (_: Exception) {}
    Log.i(TAG, "OrderStatusNotificationService stopped.")
  }
}
