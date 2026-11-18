// FirestoreRepository: Repository managing data synchronization between remote API and local storage
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

  suspend fun saveUser(user: UserDocument): Result<Unit> {
    return mongoRepo.saveUser(user.toMongoDto()).map { }
  }

  suspend fun updateUserFcmToken(userId: String, token: String): Result<Unit> {
    return mongoRepo.updateUserFcmToken(userId, token)
  }

  suspend fun saveOwner(owner: OwnerDocument): Result<Unit> {
    return mongoRepo.saveOwner(owner.toMongoDto()).map { }
  }

  suspend fun getOwner(uid: String): Result<OwnerDocument> {
    return mongoRepo.getOwner(uid).map { it.toDocument() }
  }

  fun observeCanteens(): Flow<List<CanteenDocument>> {
    return mongoRepo.observeCanteens()
  }

  suspend fun fetchFreshCanteens(): Result<List<CanteenDocument>> {
    return mongoRepo.fetchFreshCanteens()
  }

  suspend fun fetchFreshItems(canteenId: String): Result<List<ItemDocument>> {
    return mongoRepo.fetchFreshItems(canteenId)
  }

  suspend fun fetchFreshStudentOrders(studentId: String): Result<List<OrderDocument>> {
    return mongoRepo.fetchFreshStudentOrders(studentId)
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

  open suspend fun updateItemAvailability(itemId: String, canteenId: String, available: Boolean): Result<Unit> {
    return mongoRepo.updateItemAvailability(itemId, canteenId, available)
  }

  open fun observeStudentOrders(studentId: String): Flow<List<OrderDocument>> {
    return mongoRepo.observeStudentOrders(studentId)
  }

  open fun observeCanteenOrders(canteenId: String): Flow<List<OrderDocument>> {
    return mongoRepo.observeCanteenOrders(canteenId)
  }

  open fun observeOrder(orderId: String): Flow<OrderDocument?> {
    return mongoRepo.observeOrder(orderId)
  }

  open suspend fun createPaymentOrder(amount: Double, canteenId: String, orderId: String? = null, fulfillmentType: String? = null) =
    mongoRepo.createPaymentOrder(amount, canteenId, orderId, fulfillmentType)

  open suspend fun verifyPayment(razorpayOrderId: String, razorpayPaymentId: String, razorpaySignature: String, orderId: String? = null) =
    mongoRepo.verifyPayment(razorpayOrderId, razorpayPaymentId, razorpaySignature, orderId)

  open suspend fun getPaymentConfig() = mongoRepo.getPaymentConfig()

  open suspend fun createOrder(order: OrderDocument): Result<String> {
    return mongoRepo.createOrder(order.toMongoDto()).map { it.orderId }
  }

  open suspend fun updateOrderStatus(orderId: String, status: String): Result<Unit> {
    return mongoRepo.updateOrderStatus(orderId, status)
  }

  open suspend fun acceptOrder(orderId: String): Result<com.example.data.api.MongoOrderDto> {
    return mongoRepo.acceptOrder(orderId)
  }

  open suspend fun rejectOrder(orderId: String, reason: String): Result<com.example.data.api.MongoOrderDto> {
    return mongoRepo.rejectOrder(orderId, reason)
  }

  open suspend fun getOrderQr(orderId: String): Result<com.example.data.api.OrderQrResponseDto> {
    return mongoRepo.getOrderQr(orderId)
  }

  open suspend fun verifyQrCode(token: String, ownerToken: String? = null): Result<com.example.data.api.VerifyQrResponseDto> {
    return mongoRepo.verifyQrCode(token, ownerToken)
  }

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
