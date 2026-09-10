package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalCanteenDao {
  @Query("SELECT * FROM local_canteens ORDER BY name ASC")
  fun observeCanteens(): Flow<List<LocalCanteenEntity>>

  @Query("SELECT * FROM local_canteens WHERE id = :canteenId LIMIT 1")
  fun observeCanteen(canteenId: String): Flow<LocalCanteenEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCanteens(canteens: List<LocalCanteenEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCanteen(canteen: LocalCanteenEntity)
}

@Dao
interface LocalItemDao {
  @Query("SELECT * FROM local_items WHERE canteenId = :canteenId ORDER BY name ASC")
  fun observeItemsByCanteen(canteenId: String): Flow<List<LocalItemEntity>>

  @Query("SELECT * FROM local_items ORDER BY name ASC")
  fun observeAllItems(): Flow<List<LocalItemEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertItems(items: List<LocalItemEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertItem(item: LocalItemEntity)

  @Query("DELETE FROM local_items WHERE id = :itemId")
  suspend fun deleteItem(itemId: String)

  @Query("UPDATE local_items SET available = :available, stock = :stock WHERE id = :itemId")
  suspend fun updateStock(itemId: String, available: Boolean, stock: Int)
}

@Dao
interface LocalOrderDao {
  @Query("SELECT * FROM local_orders WHERE studentId = :studentId ORDER BY createdAt DESC")
  fun observeStudentOrders(studentId: String): Flow<List<LocalOrderEntity>>

  @Query("SELECT * FROM local_orders WHERE canteenId = :canteenId ORDER BY createdAt DESC")
  fun observeCanteenOrders(canteenId: String): Flow<List<LocalOrderEntity>>

  @Query("SELECT * FROM local_orders WHERE id = :orderId LIMIT 1")
  fun observeOrder(orderId: String): Flow<LocalOrderEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertOrders(orders: List<LocalOrderEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertOrder(order: LocalOrderEntity)

  @Query("UPDATE local_orders SET status = :status WHERE id = :orderId")
  suspend fun updateOrderStatus(orderId: String, status: String)
}
