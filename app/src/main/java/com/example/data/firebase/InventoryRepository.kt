package com.example.data.firebase

import com.example.data.api.MongoRepository
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.coroutines.flow.Flow

@IgnoreExtraProperties
data class InventoryItem(
  @DocumentId val id: String = "",
  val canteenId: String = "",
  val name: String = "",
  val icon: String = "📦",
  val quantity: Double = 0.0,
  val unit: String = "pcs", // pcs, kg, g, l
  val lowStockThreshold: Double = 5.0,
  val updatedAt: Long = System.currentTimeMillis(),
) {
  val formattedQuantity: String
    get() {
      return if (quantity == quantity.toLong().toDouble()) {
        "${quantity.toLong()} $unit"
      } else {
        String.format(java.util.Locale.US, "%.1f %s", quantity, unit)
      }
    }

  val status: String
    get() {
      return when {
        quantity <= 0.0 -> "Out of Stock"
        quantity <= lowStockThreshold -> "Low Stock"
        else -> "In Stock"
      }
    }
}

class InventoryRepository(
  private val mongoRepo: MongoRepository = MongoRepository(),
) {
  val defaultInventory: List<InventoryItem> = emptyList()

  fun observeInventory(canteenId: String): Flow<List<InventoryItem>> {
    return mongoRepo.observeInventory(canteenId)
  }

  suspend fun updateInventoryQuantity(id: String, quantity: Double): Result<Unit> {
    return mongoRepo.updateInventoryQuantity(id, quantity)
  }

  suspend fun updateInventoryItem(canteenId: String, item: InventoryItem): Result<Unit> {
    return if (item.id.isNotBlank()) {
      mongoRepo.updateInventoryQuantity(item.id, item.quantity)
    } else {
      mongoRepo.addInventoryItem(
        com.example.data.api.MongoInventoryDto(
          canteenId = canteenId,
          name = item.name,
          icon = item.icon,
          quantity = item.quantity,
          unit = item.unit,
          lowStockThreshold = item.lowStockThreshold,
        )
      ).map { }
    }
  }

  suspend fun addInventoryItem(canteenId: String, item: InventoryItem): Result<String> {
    return mongoRepo.addInventoryItem(
      com.example.data.api.MongoInventoryDto(
        canteenId = canteenId,
        name = item.name,
        icon = item.icon,
        quantity = item.quantity,
        unit = item.unit,
        lowStockThreshold = item.lowStockThreshold,
      )
    ).map { it.id ?: "" }
  }

  suspend fun deleteInventoryItem(canteenId: String, itemId: String): Result<Unit> {
    return Result.success(Unit)
  }
}
