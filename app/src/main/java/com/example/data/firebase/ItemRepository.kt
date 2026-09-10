package com.example.data.firebase

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed class ItemValidationException(message: String) : IllegalArgumentException(message) {
  class EmptyName : ItemValidationException("Item name cannot be empty.")
  class InvalidPrice : ItemValidationException("Item price must be greater than zero.")
  class InvalidStock : ItemValidationException("Item stock cannot be negative.")
  class InvalidPrepTime : ItemValidationException("Preparation time must be greater than zero.")
  class UnauthorizedCanteen : ItemValidationException("Security error: You cannot modify items from another canteen.")
}

open class ItemRepository(
  private val firestoreRepo: FirestoreRepository = FirestoreRepository(),
  private val storageRepo: StorageRepository = StorageRepository(),
) {
  private val TAG = "ItemRepository"

  // In-memory fallback items for testing and offline execution
  private val localItems = mutableListOf<ItemDocument>()

  /**
   * Observe all food items belonging to the owner's assigned canteen.
   */
  open fun observeCanteenItems(canteenId: String): Flow<List<ItemDocument>> {
    return firestoreRepo.observeItems(canteenId).map { remoteList ->
      if (remoteList.isNotEmpty() || FirebaseConfig.hasLiveFirebaseConfig) {
        remoteList
      } else {
        localItems.filter { it.canteenId == canteenId }
      }
    }
  }

  /**
   * Validate input fields according to specification:
   * - name required
   * - price > 0
   * - stock >= 0
   * - preparation time > 0
   * - item.canteenId == ownerAssignedCanteenId
   */
  fun validateItem(item: ItemDocument, ownerCanteenId: String) {
    if (item.name.trim().isBlank()) throw ItemValidationException.EmptyName()
    if (item.price <= 0) throw ItemValidationException.InvalidPrice()
    if (item.stock < 0) throw ItemValidationException.InvalidStock()
    if (item.prepMinutes <= 0) throw ItemValidationException.InvalidPrepTime()
    if (ownerCanteenId.isNotBlank() && item.canteenId != ownerCanteenId) {
      throw ItemValidationException.UnauthorizedCanteen()
    }
  }

  /**
   * Add a new item for the owner's canteen.
   * If stock reaches 0, automatically sets available = false.
   */
  open suspend fun addItem(
    ownerCanteenId: String,
    item: ItemDocument,
  ): Result<String> {
    return try {
      val itemToSave = item.copy(
        canteenId = ownerCanteenId,
        available = if (item.stock == 0) false else item.available,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
      )
      validateItem(itemToSave, ownerCanteenId)

      val remoteResult = firestoreRepo.saveItem(itemToSave)
      if (remoteResult.isSuccess) {
        val savedId = remoteResult.getOrThrow()
        localItems.removeAll { it.id == savedId }
        localItems.add(itemToSave.copy(id = savedId))
        Result.success(savedId)
      } else {
        val id = if (itemToSave.id.isNotBlank()) itemToSave.id else "item_${System.currentTimeMillis()}"
        val withId = itemToSave.copy(id = id)
        localItems.add(withId)
        Result.success(id)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to add item: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Update an existing item belonging to the owner's canteen.
   */
  open suspend fun updateItem(
    ownerCanteenId: String,
    item: ItemDocument,
  ): Result<Unit> {
    return try {
      validateItem(item, ownerCanteenId)
      val itemToUpdate = item.copy(
        available = if (item.stock == 0) false else item.available,
        updatedAt = System.currentTimeMillis(),
      )

      val remoteResult = firestoreRepo.saveItem(itemToUpdate)
      if (remoteResult.isSuccess) {
        localItems.removeAll { it.id == itemToUpdate.id }
        localItems.add(itemToUpdate)
        Result.success(Unit)
      } else {
        val index = localItems.indexOfFirst { it.id == itemToUpdate.id }
        if (index >= 0) {
          localItems[index] = itemToUpdate
        } else {
          localItems.add(itemToUpdate)
        }
        Result.success(Unit)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to update item: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Delete an item belonging to the owner's canteen.
   */
  open suspend fun deleteItem(ownerCanteenId: String, item: ItemDocument): Result<Unit> {
    return try {
      if (item.canteenId != ownerCanteenId) {
        throw ItemValidationException.UnauthorizedCanteen()
      }
      val remoteResult = firestoreRepo.deleteItem(item.id)
      localItems.removeAll { it.id == item.id }
      if (remoteResult.isSuccess) {
        Result.success(Unit)
      } else {
        Result.success(Unit)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to delete item: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Toggle availability for an item.
   * Note: Cannot set available = true if stock is 0.
   */
  open suspend fun toggleAvailability(ownerCanteenId: String, item: ItemDocument, available: Boolean): Result<Unit> {
    val newAvailable = if (item.stock == 0) false else available
    return updateItem(ownerCanteenId, item.copy(available = newAvailable))
  }

  /**
   * Update item stock. If stock is set to 0, automatically set available = false.
   */
  open suspend fun updateStock(ownerCanteenId: String, item: ItemDocument, newStock: Int): Result<Unit> {
    if (newStock < 0) return Result.failure(ItemValidationException.InvalidStock())
    val newAvailable = if (newStock == 0) false else item.available
    return updateItem(ownerCanteenId, item.copy(stock = newStock, available = newAvailable))
  }

  /**
   * Upload item image to Cloudinary via backend proxy and return the downloadable URL and public ID.
   */
  open suspend fun uploadImage(canteenId: String, itemId: String, imageUri: Uri): Result<com.example.data.api.ImageUploadResponseDto> {
    return storageRepo.uploadItemImage(canteenId, itemId, imageUri)
  }
}

