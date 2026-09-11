package com.example.data.api

import android.util.Log
import com.example.data.firebase.CanteenDocument
import com.example.data.firebase.InventoryItem
import com.example.data.firebase.ItemDocument
import com.example.data.firebase.NotificationDocument
import com.example.data.firebase.OrderDocument
import com.example.data.firebase.OrderItemDocument
import com.example.data.firebase.OwnerDocument
import com.example.data.firebase.UserDocument
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class MongoRepository(
  private val apiService: QuickbiteApiService = ApiClient.apiService,
) {
  private val TAG = "MongoRepository"

  companion object {
    private val defaultInstance by lazy { MongoRepository() }

    val cachedCanteens = java.util.concurrent.atomic.AtomicReference<List<CanteenDocument>?>(null)
    val cachedCanteenDetails = java.util.concurrent.ConcurrentHashMap<String, CanteenDocument>()
    val cachedCanteenItems = java.util.concurrent.ConcurrentHashMap<String, List<ItemDocument>>()
    val cachedCanteenOrders = java.util.concurrent.ConcurrentHashMap<String, List<OrderDocument>>()
    val cachedStudentOrders = java.util.concurrent.ConcurrentHashMap<String, List<OrderDocument>>()
    val cachedOrderDetails = java.util.concurrent.ConcurrentHashMap<String, OrderDocument>()

    fun getCachedOrder(orderId: String): OrderDocument? = cachedOrderDetails[orderId]

    suspend fun getCanteenQueue(canteenId: String): Result<CanteenQueueDto> =
      defaultInstance.getCanteenQueue(canteenId)

    suspend fun getOrderQueuePosition(orderId: String): Result<OrderQueuePositionDto> =
      defaultInstance.getOrderQueuePosition(orderId)
  }

  private fun parseErrorMessage(errorBody: String?, fallback: String): String {
    if (errorBody.isNullOrBlank()) return fallback
    return try {
      val json = org.json.JSONObject(errorBody)
      json.optString("error", json.optString("message", fallback))
    } catch (e: Exception) {
      fallback
    }
  }

  // ── Student / User Auth ───────────────────────────────────────────────────
  suspend fun loginUser(email: String, pass: String): Result<LoginResponseDto> {
    return runCatching {
      val res = apiService.loginUser(LoginRequestDto(email = email.trim(), password = pass))
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        val err = parseErrorMessage(res.errorBody()?.string(), "Login failed (${res.code()})")
        throw Exception(err)
      }
    }.onFailure { Log.e(TAG, "loginUser error: ${it.message}") }
  }

  suspend fun registerUser(request: RegisterRequestDto): Result<LoginResponseDto> {
    return runCatching {
      val res = apiService.registerUser(request)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        val err = parseErrorMessage(res.errorBody()?.string(), "Registration failed (${res.code()})")
        throw Exception(err)
      }
    }.onFailure { Log.e(TAG, "registerUser error: ${it.message}") }
  }

  suspend fun forgotPassword(email: String): Result<String> {
    return runCatching {
      val res = apiService.forgotPassword(ForgotPasswordRequestDto(email = email.trim()))
      if (res.isSuccessful && res.body() != null) {
        res.body()!!.message ?: "Password reset link sent to your email."
      } else {
        val err = parseErrorMessage(res.errorBody()?.string(), "Failed to send reset link (${res.code()})")
        throw Exception(err)
      }
    }.onFailure { Log.e(TAG, "forgotPassword error: ${it.message}") }
  }

  suspend fun resetPassword(token: String, email: String, newPassword: String): Result<String> {
    return runCatching {
      val res = apiService.resetPassword(ResetPasswordRequestDto(token = token.trim(), email = email.trim(), newPassword = newPassword))
      if (res.isSuccessful && res.body() != null) {
        res.body()!!.message ?: "Password reset successfully."
      } else {
        val err = parseErrorMessage(res.errorBody()?.string(), "Password reset failed (${res.code()})")
        throw Exception(err)
      }
    }.onFailure { Log.e(TAG, "resetPassword error: ${it.message}") }
  }

  // ── Owner Auth ────────────────────────────────────────────────────────────
  suspend fun ownerLogin(email: String, pass: String): Result<OwnerLoginResponseDto> {
    return runCatching {
      val res = apiService.ownerLogin(OwnerLoginRequestDto(email = email.trim(), password = pass))
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        val err = parseErrorMessage(res.errorBody()?.string(), "Owner login failed (${res.code()})")
        throw Exception(err)
      }
    }.onFailure { Log.e(TAG, "ownerLogin error: ${it.message}") }
  }

  suspend fun ownerVerifyOtp(email: String, otp: String): Result<OwnerAuthSuccessResponseDto> {
    return runCatching {
      val res = apiService.ownerVerifyOtp(OwnerVerifyOtpRequestDto(email = email.trim(), otp = otp.trim()))
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        val err = parseErrorMessage(res.errorBody()?.string(), "OTP verification failed (${res.code()})")
        throw Exception(err)
      }
    }.onFailure { Log.e(TAG, "ownerVerifyOtp error: ${it.message}") }
  }

  suspend fun ownerResendOtp(email: String): Result<String> {
    return runCatching {
      val res = apiService.ownerResendOtp(OwnerResendOtpRequestDto(email = email.trim()))
      if (res.isSuccessful && res.body() != null) {
        res.body()!!.message ?: "OTP sent successfully."
      } else {
        val err = parseErrorMessage(res.errorBody()?.string(), "Failed to resend OTP (${res.code()})")
        throw Exception(err)
      }
    }.onFailure { Log.e(TAG, "ownerResendOtp error: ${it.message}") }
  }

  // ── Canteens ──────────────────────────────────────────────────────────────
  suspend fun getCanteens(): Result<List<MongoCanteenDto>> {
    return runCatching {
      val res = apiService.getCanteens()
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to fetch canteens: ${res.code()} ${res.message()}")
      }
    }.onFailure { Log.e(TAG, "getCanteens error: ${it.message}") }
  }

  suspend fun getCanteen(id: String): Result<MongoCanteenDto> {
    return runCatching {
      val res = apiService.getCanteen(id)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to fetch canteen $id: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "getCanteen error: ${it.message}") }
  }

  suspend fun saveCanteen(canteen: MongoCanteenDto): Result<MongoCanteenDto> {
    return runCatching {
      val res = apiService.saveCanteen(canteen)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to save canteen: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "saveCanteen error: ${it.message}") }
  }

  suspend fun updateCanteenStatus(canteenId: String, isOpen: Boolean, closeReason: String): Result<Unit> {
    return runCatching {
      val res = apiService.updateCanteenStatus(canteenId, StatusUpdateDto(isOpen = isOpen, closeReason = closeReason))
      if (!res.isSuccessful) throw Exception("Update canteen status failed: ${res.code()}")
    }.onFailure { Log.e(TAG, "updateCanteenStatus error: ${it.message}") }
  }

  suspend fun updateCanteenProfile(
    canteenId: String,
    imageUrl: String? = null,
    timings: String? = null,
    isOpen: Boolean? = null,
    closeReason: String? = null,
  ): Result<Unit> {
    return runCatching {
      val res = apiService.updateCanteenProfile(
        canteenId,
        ProfileUpdateDto(imageUrl = imageUrl, timings = timings, isOpen = isOpen, closeReason = closeReason)
      )
      if (!res.isSuccessful) throw Exception("Update canteen profile failed: ${res.code()}")
    }.onFailure { Log.e(TAG, "updateCanteenProfile error: ${it.message}") }
  }

  // ── Items ─────────────────────────────────────────────────────────────────
  suspend fun getCanteenItems(canteenId: String): Result<List<MongoItemDto>> {
    val effectiveId = canteenId.ifBlank { "canteen_33" }
    return runCatching {
      val res = apiService.getCanteenItems(effectiveId)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to fetch items: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "getCanteenItems error: ${it.message}") }
  }

  suspend fun saveItem(item: MongoItemDto): Result<MongoItemDto> {
    return runCatching {
      val res = apiService.saveItem(item)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to save item: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "saveItem error: ${it.message}") }
  }

  suspend fun updateItemStock(itemId: String, available: Boolean, stock: Int): Result<Unit> {
    return runCatching {
      val res = apiService.updateItemStock(itemId, StockUpdateDto(available = available, stock = stock))
      if (!res.isSuccessful) throw Exception("Update stock failed: ${res.code()}")
    }.onFailure { Log.e(TAG, "updateItemStock error: ${it.message}") }
  }

  suspend fun deleteItem(itemId: String): Result<Unit> {
    return runCatching {
      val res = apiService.deleteItem(itemId)
      if (!res.isSuccessful) throw Exception("Delete item failed: ${res.code()}")
    }.onFailure { Log.e(TAG, "deleteItem error: ${it.message}") }
  }

  suspend fun uploadItemImage(imageBytes: ByteArray, filename: String = "item.jpg"): Result<ImageUploadResponseDto> {
    return runCatching {
      val reqBody = imageBytes.toRequestBody("image/*".toMediaTypeOrNull())
      val part = MultipartBody.Part.createFormData("image", filename, reqBody)
      val res = apiService.uploadItemImage(part)
      if (res.isSuccessful && res.body() != null) {
        val body = res.body()!!
        if (body.success && body.imageUrl.isNotBlank()) {
          body
        } else {
          throw Exception(body.error ?: "Upload failed with empty URL")
        }
      } else {
        val err = parseErrorMessage(res.errorBody()?.string(), "Image upload failed (${res.code()})")
        throw Exception(err)
      }
    }.onFailure { Log.e(TAG, "uploadItemImage error: ${it.message}") }
  }

  // ── Orders ────────────────────────────────────────────────────────────────
  suspend fun createOrder(order: MongoOrderDto): Result<MongoOrderDto> {
    return runCatching {
      val res = apiService.createOrder(order)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to place order: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "createOrder error: ${it.message}") }
  }

  suspend fun getStudentOrders(studentId: String): Result<List<MongoOrderDto>> {
    return runCatching {
      val res = apiService.getStudentOrders(studentId)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to fetch student orders: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "getStudentOrders error: ${it.message}") }
  }

  suspend fun getCanteenOrders(canteenId: String): Result<List<MongoOrderDto>> {
    return runCatching {
      val res = apiService.getCanteenOrders(canteenId)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to fetch canteen orders: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "getCanteenOrders error: ${it.message}") }
  }

  suspend fun getOrder(orderId: String): Result<MongoOrderDto> {
    return runCatching {
      val res = apiService.getOrder(orderId)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to fetch order: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "getOrder error: ${it.message}") }
  }

  suspend fun updateOrderStatus(orderId: String, status: String): Result<Unit> {
    return runCatching {
      val normalized = if (status.equals("PICKED_UP", ignoreCase = true) || status.equals("PICKED UP", ignoreCase = true)) "COMPLETED" else status.uppercase()
      val res = apiService.updateOrderStatus(orderId, StatusUpdateDto(status = normalized))
      if (!res.isSuccessful) {
        val errBody = res.errorBody()?.string()
        Log.e(TAG, "updateOrderStatus failed: code=${res.code()} body=$errBody")
        throw Exception("Update status failed: ${res.code()} $errBody")
      }
      Log.i(TAG, "updateOrderStatus success: $orderId -> $normalized")

      // Update in-memory caches immediately so any screen re-opening has the new status
      cachedOrderDetails[orderId]?.let { old ->
        val updated = old.copy(status = normalized, updatedAt = System.currentTimeMillis())
        cachedOrderDetails[orderId] = updated
      }
      cachedCanteenOrders.forEach { (canteen, list) ->
        cachedCanteenOrders[canteen] = list.map {
          if (it.orderId == orderId) it.copy(status = normalized, updatedAt = System.currentTimeMillis()) else it
        }
      }
    }.onFailure { Log.e(TAG, "updateOrderStatus error: ${it.message}") }
  }

  suspend fun getCanteenQueue(canteenId: String): Result<CanteenQueueDto> {
    return runCatching {
      val res = apiService.getCanteenQueue(canteenId)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to fetch canteen queue: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "getCanteenQueue error: ${it.message}") }
  }

  suspend fun getOrderQueuePosition(orderId: String): Result<OrderQueuePositionDto> {
    return runCatching {
      val res = apiService.getOrderQueuePosition(orderId)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to fetch order queue: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "getOrderQueuePosition error: ${it.message}") }
  }

  // ── Users ─────────────────────────────────────────────────────────────────
  suspend fun getUser(uid: String): Result<MongoUserDto> {
    return runCatching {
      val res = apiService.getUser(uid)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("User not found: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "getUser error: ${it.message}") }
  }

  suspend fun saveUser(user: MongoUserDto): Result<MongoUserDto> {
    return runCatching {
      val res = apiService.saveUser(user)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to save user: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "saveUser error: ${it.message}") }
  }

  suspend fun updateUserFcmToken(uid: String, token: String): Result<Unit> {
    return runCatching {
      val res = apiService.updateUserFcmToken(uid, FcmTokenDto(fcmToken = token))
      if (!res.isSuccessful) throw Exception("Failed to update FCM token: ${res.code()}")
    }.onFailure { Log.e(TAG, "updateUserFcmToken error: ${it.message}") }
  }

  // ── Owners ────────────────────────────────────────────────────────────────
  suspend fun getOwner(uid: String): Result<MongoOwnerDto> {
    return runCatching {
      val res = apiService.getOwner(uid)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Owner not found: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "getOwner error: ${it.message}") }
  }

  suspend fun saveOwner(owner: MongoOwnerDto): Result<MongoOwnerDto> {
    return runCatching {
      val res = apiService.saveOwner(owner)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to save owner: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "saveOwner error: ${it.message}") }
  }

  suspend fun assignCanteen(uid: String, canteenAssigned: Boolean, canteenId: String, block: String): Result<MongoOwnerDto> {
    return runCatching {
      val body = mapOf(
        "canteenAssigned" to canteenAssigned,
        "canteenId" to canteenId,
        "block" to block,
      )
      val res = apiService.assignCanteen(uid, body)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to assign canteen in MongoDB: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "assignCanteen error: ${it.message}") }
  }

  // ── Inventory ─────────────────────────────────────────────────────────────
  suspend fun getCanteenInventory(canteenId: String): Result<List<MongoInventoryDto>> {
    return runCatching {
      val res = apiService.getCanteenInventory(canteenId)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to fetch inventory: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "getCanteenInventory error: ${it.message}") }
  }

  suspend fun updateInventoryQuantity(id: String, quantity: Double): Result<Unit> {
    return runCatching {
      val res = apiService.updateInventoryQuantity(id, InventoryQuantityDto(quantity = quantity))
      if (!res.isSuccessful) throw Exception("Failed to update inventory: ${res.code()}")
    }.onFailure { Log.e(TAG, "updateInventoryQuantity error: ${it.message}") }
  }

  suspend fun addInventoryItem(item: MongoInventoryDto): Result<MongoInventoryDto> {
    return runCatching {
      val res = apiService.addInventoryItem(item)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to add inventory: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "addInventoryItem error: ${it.message}") }
  }

  // ── Notifications ─────────────────────────────────────────────────────────
  suspend fun getUserNotifications(userId: String): Result<List<MongoNotificationDto>> {
    return runCatching {
      val res = apiService.getUserNotifications(userId)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to fetch notifications: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "getUserNotifications error: ${it.message}") }
  }

  suspend fun createNotification(notification: MongoNotificationDto): Result<MongoNotificationDto> {
    return runCatching {
      val res = apiService.createNotification(notification)
      if (res.isSuccessful && res.body() != null) {
        res.body()!!
      } else {
        throw Exception("Failed to create notification: ${res.code()}")
      }
    }.onFailure { Log.e(TAG, "createNotification error: ${it.message}") }
  }

  suspend fun markNotificationRead(notificationId: String): Result<Unit> {
    return runCatching {
      val res = apiService.markNotificationRead(notificationId)
      if (!res.isSuccessful) throw Exception("Failed to mark notification read: ${res.code()}")
    }.onFailure { Log.e(TAG, "markNotificationRead error: ${it.message}") }
  }

  // ── Real-Time Reactive Flows ──────────────────────────────────────────────
  fun observeCanteens(): Flow<List<CanteenDocument>> = flow {
    cachedCanteens.get()?.let { emit(it) }
    var delayMs = 15000L
    while (currentCoroutineContext().isActive) {
      val res = getCanteens()
      if (res.isSuccess) {
        val list = res.getOrThrow().map { it.toDocument() }
        cachedCanteens.set(list)
        emit(list)
        delayMs = 15000L
      } else {
        delayMs = (delayMs * 1.5).toLong().coerceAtMost(45000L)
      }
      delay(delayMs)
    }
  }

  fun observeCanteen(canteenId: String): Flow<CanteenDocument?> = flow {
    val cached = cachedCanteenDetails[canteenId]
    if (cached != null) {
      emit(cached)
    } else {
      emit(CanteenDocument(id = canteenId, name = "Campus Canteen", isOpen = true))
    }
    var delayMs = 15000L
    while (currentCoroutineContext().isActive) {
      val res = getCanteen(canteenId)
      if (res.isSuccess) {
        val doc = res.getOrThrow().toDocument()
        cachedCanteenDetails[canteenId] = doc
        emit(doc)
        delayMs = 15000L
      } else {
        delayMs = (delayMs * 1.5).toLong().coerceAtMost(45000L)
      }
      delay(delayMs)
    }
  }

  fun observeItems(canteenId: String): Flow<List<ItemDocument>> = flow {
    val effectiveId = canteenId.ifBlank { "canteen_33" }
    val cached = cachedCanteenItems[effectiveId]
    if (cached != null) {
      emit(cached)
    }
    var delayMs = 15000L
    while (currentCoroutineContext().isActive) {
      val res = getCanteenItems(effectiveId)
      if (res.isSuccess) {
        val items = res.getOrThrow().map { it.toDocument() }
        cachedCanteenItems[effectiveId] = items
        emit(items)
        delayMs = 15000L
      } else {
        if (cached == null && cachedCanteenItems[effectiveId] == null) {
          emit(emptyList())
        }
        delayMs = (delayMs * 1.5).toLong().coerceAtMost(45000L)
      }
      delay(delayMs)
    }
  }

  fun observeStudentOrders(studentId: String): Flow<List<OrderDocument>> = channelFlow {
    WebSocketManager.connect()
    val currentOrders = mutableListOf<OrderDocument>()

    cachedStudentOrders[studentId]?.let { cached ->
      currentOrders.addAll(cached)
      trySend(cached)
    }

    val wsJob = launch {
      WebSocketManager.orderUpdates.collect { dto ->
        val doc = dto.toDocument()
        cachedOrderDetails[doc.orderId] = doc
        val existingIdx = currentOrders.indexOfFirst { it.orderId == doc.orderId }
        if (existingIdx >= 0) {
          currentOrders[existingIdx] = doc
          val snapshot = currentOrders.toList()
          cachedStudentOrders[studentId] = snapshot
          trySend(snapshot)
        }
      }
    }

    val pollJob = launch {
      var delayMs = 15000L
      while (isActive) {
        if (com.example.util.NetworkMonitor.isCurrentlyOnline()) {
          val res = getStudentOrders(studentId)
          if (res.isSuccess) {
            val fetched = res.getOrThrow().map { it.toDocument() }
            currentOrders.clear()
            currentOrders.addAll(fetched)
            fetched.forEach { cachedOrderDetails[it.orderId] = it }
            val snapshot = currentOrders.toList()
            cachedStudentOrders[studentId] = snapshot
            trySend(snapshot)
            delayMs = 15000L
          } else {
            delayMs = (delayMs * 1.5).toLong().coerceAtMost(45000L)
          }
        }
        delay(delayMs)
      }
    }

    awaitClose {
      wsJob.cancel()
      pollJob.cancel()
    }
  }

  fun observeCanteenOrders(canteenId: String): Flow<List<OrderDocument>> = channelFlow {
    WebSocketManager.connect()
    val currentOrders = mutableListOf<OrderDocument>()

    // Instant 0ms emit from cache if available or initial empty list
    val cached = cachedCanteenOrders[canteenId]
    if (cached != null) {
      currentOrders.addAll(cached)
      trySend(cached)
    } else {
      trySend(emptyList())
    }

    val wsJob = launch {
      WebSocketManager.observeCanteenOrders(canteenId).collect { dto ->
        val doc = dto.toDocument()
        cachedOrderDetails[doc.orderId] = doc
        val existingIdx = currentOrders.indexOfFirst { it.orderId == doc.orderId }
        if (existingIdx >= 0) {
          currentOrders[existingIdx] = doc
        } else {
          currentOrders.add(0, doc)
        }
        val snapshot = currentOrders.toList()
        cachedCanteenOrders[canteenId] = snapshot
        trySend(snapshot)
      }
    }

    val pollJob = launch {
      var delayMs = 15000L
      while (isActive) {
        val res = getCanteenOrders(canteenId)
        if (res.isSuccess) {
          val fetched = res.getOrThrow().map { it.toDocument() }
          currentOrders.clear()
          currentOrders.addAll(fetched)
          fetched.forEach { cachedOrderDetails[it.orderId] = it }
          val snapshot = currentOrders.toList()
          cachedCanteenOrders[canteenId] = snapshot
          trySend(snapshot)
          delayMs = 15000L
        } else {
          delayMs = (delayMs * 1.5).toLong().coerceAtMost(45000L)
        }
        delay(delayMs)
      }
    }

    awaitClose {
      wsJob.cancel()
      pollJob.cancel()
      WebSocketManager.unsubscribe("canteen:$canteenId")
    }
  }

  fun observeOrder(orderId: String): Flow<OrderDocument?> = channelFlow {
    WebSocketManager.connect()

    // Instant 0ms emit from cache if available
    cachedOrderDetails[orderId]?.let { trySend(it) }

    // 1. Instant sub-second updates pushed via WebSocket
    val wsJob = launch {
      WebSocketManager.observeOrder(orderId).collect { dto ->
        val doc = dto.toDocument()
        cachedOrderDetails[orderId] = doc
        trySend(doc)
      }
    }

    // 2. Fetch initial order and periodic safety fallback with backoff
    val pollJob = launch {
      var delayMs = 12000L
      while (isActive) {
        if (com.example.util.NetworkMonitor.isCurrentlyOnline()) {
          val res = getOrder(orderId)
          if (res.isSuccess) {
            val doc = res.getOrThrow().toDocument()
            cachedOrderDetails[orderId] = doc
            trySend(doc)
            delayMs = 12000L
          } else {
            delayMs = (delayMs * 1.5).toLong().coerceAtMost(45000L)
          }
        }
        delay(delayMs)
      }
    }

    awaitClose {
      wsJob.cancel()
      pollJob.cancel()
      WebSocketManager.unsubscribe("order:$orderId")
    }
  }

  fun observeInventory(canteenId: String): Flow<List<InventoryItem>> = flow {
    while (currentCoroutineContext().isActive) {
      val res = getCanteenInventory(canteenId)
      if (res.isSuccess) {
        emit(res.getOrThrow().map { it.toInventoryItem() })
      }
      delay(2000)
    }
  }

  fun observeUserNotifications(userId: String): Flow<List<NotificationDocument>> = flow {
    while (currentCoroutineContext().isActive) {
      val res = getUserNotifications(userId)
      if (res.isSuccess) {
        emit(res.getOrThrow().map { it.toDocument() })
      }
      delay(3000)
    }
  }
}

// ── Extension Document Converters ───────────────────────────────────────────
fun MongoCanteenDto.toDocument(): CanteenDocument {
  return CanteenDocument(
    id = id,
    name = name,
    block = block,
    location = location,
    isOpen = isOpen,
    closeReason = closeReason,
    floorInfo = floorInfo,
    specialty = specialty,
    avgWaitMinutes = avgWaitMinutes,
    icon = icon,
    imageUrl = imageUrl,
    timings = timings,
  )
}

fun CanteenDocument.toMongoDto(): MongoCanteenDto {
  return MongoCanteenDto(
    id = id,
    name = name,
    block = block,
    location = location,
    isOpen = isOpen,
    closeReason = closeReason,
    floorInfo = floorInfo,
    specialty = specialty,
    avgWaitMinutes = avgWaitMinutes,
    icon = icon,
    imageUrl = imageUrl,
    timings = timings,
  )
}

fun MongoItemDto.toDocument(): ItemDocument {
  return ItemDocument(
    id = id,
    canteenId = canteenId,
    name = name,
    description = description,
    price = price,
    category = category,
    imageUrl = imageUrl,
    cloudinaryPublicId = cloudinaryPublicId,
    isCustom = isCustom,
    available = available,
    stock = stock,
    preparationTime = preparationTime,
    prepMinutes = prepMinutes,
    rating = rating,
    ingredients = ingredients,
    customizationTitle = customizationTitle,
    customizationOptions = customizationOptions,
    addons = addons.map { com.example.data.firebase.AddonDocument(it.name, it.price) },
  )
}

fun ItemDocument.toMongoDto(): MongoItemDto {
  return MongoItemDto(
    id = id,
    canteenId = canteenId,
    name = name,
    description = description,
    price = price,
    category = category,
    imageUrl = imageUrl,
    cloudinaryPublicId = cloudinaryPublicId,
    isCustom = isCustom,
    available = available,
    stock = stock,
    preparationTime = preparationTime,
    prepMinutes = prepMinutes,
    rating = rating,
    ingredients = ingredients,
    customizationTitle = customizationTitle,
    customizationOptions = customizationOptions,
    addons = addons.map { MongoAddonDto(it.name, it.price) },
  )
}

fun MongoOrderDto.toDocument(): OrderDocument {
  return OrderDocument(
    orderId = orderId,
    studentId = studentId,
    studentName = studentName,
    studentPhone = studentPhone,
    studentCourse = studentCourse,
    canteenId = canteenId,
    items = items.map {
      OrderItemDocument(
        itemId = it.itemId,
        name = it.name,
        price = it.price,
        quantity = it.quantity,
        selectedOption = it.selectedOption,
        selectedAddons = it.selectedAddons,
      )
    },
    totalAmount = totalAmount,
    status = status,
    paymentStatus = paymentStatus,
    tokenNumber = tokenNumber,
    pickupPreference = pickupPreference,
    pickupCanteenName = pickupCanteenName,
    pickupLocation = pickupLocation,
    pickupCounter = pickupCounter,
    estimatedReadyTime = estimatedReadyTime,
  )
}

fun OrderDocument.toMongoDto(): MongoOrderDto {
  return MongoOrderDto(
    orderId = orderId,
    studentId = studentId,
    studentName = studentName,
    studentPhone = studentPhone,
    studentCourse = studentCourse,
    canteenId = canteenId,
    items = items.map {
      MongoOrderItemDto(
        itemId = it.itemId,
        name = it.name,
        price = it.price,
        quantity = it.quantity,
        prepMinutes = if (it.prepMinutes > 0) it.prepMinutes else 7,
        selectedOption = it.selectedOption,
        selectedAddons = it.selectedAddons,
      )
    },
    totalAmount = totalAmount,
    status = status,
    paymentStatus = paymentStatus,
    tokenNumber = tokenNumber,
    pickupPreference = pickupPreference,
    pickupCanteenName = pickupCanteenName,
    pickupLocation = pickupLocation,
    pickupCounter = pickupCounter,
    estimatedReadyTime = estimatedReadyTime,
  )
}

fun MongoNotificationDto.toDocument(): NotificationDocument {
  return NotificationDocument(
    notificationId = notificationId,
    userId = userId,
    title = title,
    message = message,
    type = type,
    orderId = orderId,
    canteenId = canteenId,
    read = read,
  )
}

fun NotificationDocument.toMongoDto(): MongoNotificationDto {
  return MongoNotificationDto(
    notificationId = notificationId,
    userId = userId,
    title = title,
    message = message,
    type = type,
    orderId = orderId,
    canteenId = canteenId,
    read = read,
  )
}

fun MongoOwnerDto.toDocument(): OwnerDocument {
  return OwnerDocument(
    uid = uid,
    name = name,
    email = email,
    role = role,
    canteenAssigned = canteenAssigned,
    canteenId = canteenId,
    block = block,
    phone = phone,
    fcmToken = fcmToken,
  )
}

fun OwnerDocument.toMongoDto(): MongoOwnerDto {
  return MongoOwnerDto(
    uid = uid,
    name = name,
    email = email,
    role = role,
    canteenAssigned = canteenAssigned,
    canteenId = canteenId,
    block = block,
    phone = phone,
    fcmToken = fcmToken,
  )
}

fun MongoUserDto.toDocument(): UserDocument {
  return UserDocument(
    uid = uid,
    name = name,
    email = email,
    role = role,
    registrationNumber = registrationNumber,
    department = department,
    avatarId = avatarId,
    fcmToken = fcmToken,
    phone = phone,
    course = course,
  )
}

fun UserDocument.toMongoDto(): MongoUserDto {
  return MongoUserDto(
    uid = uid,
    name = name,
    email = email,
    role = role,
    registrationNumber = registrationNumber,
    department = department,
    avatarId = avatarId,
    fcmToken = fcmToken,
    phone = phone,
    course = course,
  )
}
