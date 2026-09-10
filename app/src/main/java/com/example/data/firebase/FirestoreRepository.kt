package com.example.data.firebase

import android.util.Log
import com.example.data.api.MongoRepository
import com.example.data.api.toDocument
import com.example.data.api.toMongoDto
import kotlinx.coroutines.flow.Flow

/**
 * Repository powered strictly by MongoDB Atlas Node.js REST backend.
 * Preserves existing document models to ensure seamless integration with ViewModels and UI,
 * while streaming real-time reactive updates from MongoDB Atlas.
 */
open class FirestoreRepository(
  protected val mongoRepo: MongoRepository = MongoRepository(),
  firestore: Any? = null,
) {
  private val TAG = "FirestoreRepository"

  // ── 1. Users ────────────────────────────────────────────────────────────────
  suspend fun saveUser(user: UserDocument): Result<Unit> {
    return mongoRepo.saveUser(user.toMongoDto()).map { }
  }

  suspend fun updateUserFcmToken(userId: String, token: String): Result<Unit> {
    return mongoRepo.updateUserFcmToken(userId, token)
  }

  // ── 2. Owners ───────────────────────────────────────────────────────────────
  suspend fun saveOwner(owner: OwnerDocument): Result<Unit> {
    return mongoRepo.saveOwner(owner.toMongoDto()).map { }
  }

  suspend fun getOwner(uid: String): Result<OwnerDocument> {
    return mongoRepo.getOwner(uid).map { it.toDocument() }
  }

  // ── 3. Canteens ─────────────────────────────────────────────────────────────
  fun observeCanteens(): Flow<List<CanteenDocument>> {
    return mongoRepo.observeCanteens()
  }

  suspend fun getCanteen(canteenId: String): Result<CanteenDocument> {
    return mongoRepo.getCanteen(canteenId).map { it.toDocument() }
  }

  suspend fun saveCanteen(canteen: CanteenDocument): Result<String> {
    return mongoRepo.saveCanteen(canteen.toMongoDto()).map { it.id }
  }

  open fun observeCanteen(canteenId: String): Flow<CanteenDocument?> {
    return mongoRepo.observeCanteen(canteenId)
  }

  open suspend fun updateCanteenStatus(canteenId: String, isOpen: Boolean, closeReason: String): Result<Unit> {
    return mongoRepo.updateCanteenStatus(canteenId, isOpen, closeReason)
  }

  open suspend fun updateCanteenProfile(
    canteenId: String,
    imageUrl: String? = null,
    timings: String? = null,
    isOpen: Boolean? = null,
    closeReason: String? = null,
  ): Result<Unit> {
    return mongoRepo.updateCanteenProfile(canteenId, imageUrl, timings, isOpen, closeReason)
  }

  // ── 4. Items ────────────────────────────────────────────────────────────────
  open fun observeItems(canteenId: String): Flow<List<ItemDocument>> {
    return mongoRepo.observeItems(canteenId)
  }

  open suspend fun saveItem(item: ItemDocument): Result<String> {
    return mongoRepo.saveItem(item.toMongoDto()).map { it.id }
  }

  open suspend fun deleteItem(itemId: String): Result<Unit> {
    return mongoRepo.deleteItem(itemId)
  }

  open suspend fun updateItemStock(itemId: String, available: Boolean, stock: Int): Result<Unit> {
    return mongoRepo.updateItemStock(itemId, available, stock)
  }

  // ── 5. Orders ───────────────────────────────────────────────────────────────
  open fun observeStudentOrders(studentId: String): Flow<List<OrderDocument>> {
    return mongoRepo.observeStudentOrders(studentId)
  }

  open fun observeCanteenOrders(canteenId: String): Flow<List<OrderDocument>> {
    return mongoRepo.observeCanteenOrders(canteenId)
  }

  open fun observeOrder(orderId: String): Flow<OrderDocument?> {
    return mongoRepo.observeOrder(orderId)
  }

  open suspend fun createOrder(order: OrderDocument): Result<String> {
    return mongoRepo.createOrder(order.toMongoDto()).map { it.orderId }
  }

  open suspend fun updateOrderStatus(orderId: String, status: String): Result<Unit> {
    return mongoRepo.updateOrderStatus(orderId, status)
  }

  // ── 6. Notifications ────────────────────────────────────────────────────────
  fun observeUserNotifications(userId: String): Flow<List<NotificationDocument>> {
    return mongoRepo.observeUserNotifications(userId)
  }

  suspend fun sendNotification(notification: NotificationDocument): Result<String> {
    return mongoRepo.createNotification(notification.toMongoDto()).map { it.notificationId }
  }

  suspend fun markNotificationAsRead(notificationId: String): Result<Unit> {
    return mongoRepo.markNotificationRead(notificationId)
  }

  suspend fun seedDefaultDataIfEmpty(): Result<Boolean> {
    return Result.success(false)
  }
}
