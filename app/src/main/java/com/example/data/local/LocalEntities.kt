package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "local_canteens")
data class LocalCanteenEntity(
  @PrimaryKey val id: String,
  val name: String,
  val block: String,
  val location: String,
  val isOpen: Boolean,
  val closeReason: String,
  val floorInfo: String,
  val specialty: String,
  val avgWaitMinutes: Int,
  val icon: String,
  val imageUrl: String,
  val timings: String,
  val lastUpdated: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "local_items",
  indices = [Index(value = ["canteenId"]), Index(value = ["category"])]
)
data class LocalItemEntity(
  @PrimaryKey val id: String,
  val canteenId: String,
  val name: String,
  val description: String,
  val price: Int,
  val category: String,
  val imageUrl: String,
  val available: Boolean,
  val stock: Int,
  val preparationTime: String,
  val prepMinutes: Int,
  val rating: Double,
  val lastUpdated: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "local_orders",
  indices = [Index(value = ["canteenId"]), Index(value = ["studentId"])]
)
data class LocalOrderEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val studentName: String,
  val canteenId: String,
  val totalAmount: Int,
  val status: String,
  val paymentStatus: String,
  val tokenNumber: String,
  val pickupPreference: String,
  val pickupCanteenName: String,
  val pickupLocation: String,
  val pickupCounter: String,
  val estimatedReadyTime: String,
  val createdAt: Long,
  val itemsJson: String,
)
