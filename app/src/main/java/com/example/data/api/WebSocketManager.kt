package com.example.data.api

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Real-Time WebSocket Manager for Quick Bite
 * Maintains persistent WebSocket connection with auto-reconnect and channel subscriptions
 * (order status updates, owner kitchen notifications, inventory changes).
 */
object WebSocketManager {
  private const val TAG = "QuickBiteWS"

  private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

  private val orderDtoAdapter = moshi.adapter(MongoOrderDto::class.java)

  private val client = OkHttpClient.Builder()
    .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive for WebSockets
    .pingInterval(25, TimeUnit.SECONDS)
    .build()

  private var webSocket: WebSocket? = null
  private val activeChannels = ConcurrentHashMap.newKeySet<String>()

  @Volatile
  private var isConnecting = false

  @Volatile
  var isConnected: Boolean = false
    private set

  private val scope = CoroutineScope(Dispatchers.IO)

  private val _orderUpdates = MutableSharedFlow<MongoOrderDto>(extraBufferCapacity = 64)
  val orderUpdates: Flow<MongoOrderDto> = _orderUpdates.asSharedFlow()

  private val _newOrders = MutableSharedFlow<MongoOrderDto>(extraBufferCapacity = 64)
  val newOrders: Flow<MongoOrderDto> = _newOrders.asSharedFlow()

  private val _queueUpdates = MutableSharedFlow<String>(extraBufferCapacity = 64)
  val queueUpdates: Flow<String> = _queueUpdates.asSharedFlow()

  fun getWsUrl(): String {
    val base = ApiClient.baseUrl.trimEnd('/')
    val wsPrefix = if (base.startsWith("https://", ignoreCase = true)) "wss://" else "ws://"
    val hostAndPort = base.substringAfter("://").removeSuffix("/api")
    return "$wsPrefix$hostAndPort/ws"
  }

  @Synchronized
  fun connect() {
    if (isConnected || isConnecting) return
    isConnecting = true

    val wsUrl = getWsUrl()
    Log.d(TAG, "Connecting to Quick Bite WebSocket: $wsUrl")

    val request = Request.Builder()
      .url(wsUrl)
      .build()

    webSocket = client.newWebSocket(request, object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        isConnected = true
        isConnecting = false
        Log.i(TAG, "WebSocket connected successfully to $wsUrl")

        // Resubscribe to all active channels
        for (channel in activeChannels) {
          sendSubscription(webSocket, channel)
        }
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        handleIncomingMessage(text)
      }

      override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        isConnected = false
        Log.d(TAG, "WebSocket closing: $code / $reason")
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        isConnected = false
        isConnecting = false
        Log.d(TAG, "WebSocket closed: $code / $reason")
        scheduleReconnect()
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        isConnected = false
        isConnecting = false
        Log.w(TAG, "WebSocket connection failed: ${t.message}. Response: ${response?.code}")
        scheduleReconnect()
      }
    })
  }

  private fun scheduleReconnect() {
    scope.launch {
      delay(4000)
      if (!isConnected) {
        Log.d(TAG, "Attempting WebSocket reconnect...")
        connect()
      }
    }
  }

  fun subscribe(channel: String) {
    activeChannels.add(channel)
    webSocket?.let { ws ->
      if (isConnected) {
        sendSubscription(ws, channel)
      }
    } ?: connect()
  }

  fun unsubscribe(channel: String) {
    activeChannels.remove(channel)
    webSocket?.let { ws ->
      if (isConnected) {
        val payload = "{\"action\":\"unsubscribe\",\"channel\":\"$channel\"}"
        ws.send(payload)
      }
    }
  }

  private fun sendSubscription(ws: WebSocket, channel: String) {
    val payload = "{\"action\":\"subscribe\",\"channel\":\"$channel\"}"
    ws.send(payload)
    Log.d(TAG, "Subscribed to WebSocket channel: $channel")
  }

  private fun handleIncomingMessage(text: String) {
    try {
      if (text.contains("\"type\":\"ORDER_STATUS_UPDATED\"")) {
        extractAndEmitOrder(text, _orderUpdates)
      } else if (text.contains("\"type\":\"ORDER_CREATED\"")) {
        extractAndEmitOrder(text, _newOrders)
      } else if (text.contains("\"type\":\"QUEUE_UPDATED\"")) {
        val canteenIdx = text.indexOf("\"canteenId\":")
        if (canteenIdx != -1) {
          val canteenId = text.substring(canteenIdx + 12).substringBefore(",").substringBefore("}").trim('"', ' ', '\n', '\r')
          _queueUpdates.tryEmit(canteenId)
          Log.i(TAG, "Real-time Queue Update for canteen: $canteenId")
        }
      } else if (text.contains("\"type\":\"CONNECTED\"")) {
        Log.d(TAG, "Received server welcome: $text")
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error handling incoming WS message: ${e.message}")
    }
  }

  private fun extractAndEmitOrder(jsonText: String, targetFlow: MutableSharedFlow<MongoOrderDto>) {
    try {
      // Find the "order": { ... } object inside the wrapper message
      val orderIdx = jsonText.indexOf("\"order\":")
      if (orderIdx != -1) {
        val jsonOrderPart = jsonText.substring(orderIdx + 8).trimEnd('}', ' ', '\n', '\r')
        // Try parsing full text first or substring
        val parsed = try {
          orderDtoAdapter.fromJson(jsonOrderPart)
        } catch (e: Exception) {
          // If outer structure has extra fields, extract JSON object
          val start = jsonText.indexOf('{', orderIdx)
          var depth = 0
          var end = -1
          for (i in start until jsonText.length) {
            if (jsonText[i] == '{') depth++
            else if (jsonText[i] == '}') {
              depth--
              if (depth == 0) {
                end = i + 1
                break
              }
            }
          }
          if (start != -1 && end != -1) {
            orderDtoAdapter.fromJson(jsonText.substring(start, end))
          } else null
        }

        if (parsed != null && parsed.orderId.isNotBlank()) {
          Log.i(TAG, "Real-time Order Event Received: ${parsed.orderId} status: ${parsed.status}")
          targetFlow.tryEmit(parsed)
          if (parsed.canteenId.isNotBlank()) {
            _queueUpdates.tryEmit(parsed.canteenId)
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed parsing real-time order: ${e.message}")
    }
  }

  /**
   * Observe live real-time status updates for a specific order.
   */
  fun observeOrder(orderId: String): Flow<MongoOrderDto> {
    subscribe("order:$orderId")
    return orderUpdates.filter { it.orderId == orderId }
  }

  /**
   * Observe live incoming new orders for a canteen.
   */
  fun observeCanteenOrders(canteenId: String): Flow<MongoOrderDto> {
    subscribe("canteen:$canteenId")
    return newOrders.filter { it.canteenId == canteenId }
  }
}
