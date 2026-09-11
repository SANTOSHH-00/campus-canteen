package com.example.data.api

import com.example.data.CampusBlockCanteen
import com.example.data.CartItem
import com.example.data.CrowdStatus
import com.example.data.FoodCategory
import com.example.data.FoodItem
import com.example.data.NotificationItem
import com.example.data.OrderRecord
import com.example.data.OrderStatus
import com.example.data.firebase.InventoryItem
import com.example.ui.state.UserProfile
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun formatUtcToLocalTime(timestampStr: String?): String {
  if (timestampStr.isNullOrBlank()) return ""
  return try {
    val epochMillis = timestampStr.toLongOrNull()
    if (epochMillis != null && epochMillis > 0) {
      val localFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
      return localFormat.format(Date(epochMillis))
    }
    val isoWithMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
    }
    val parsed = isoWithMillis.parse(timestampStr) ?: run {
      val isoStandard = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
      }
      isoStandard.parse(timestampStr)
    }
    if (parsed != null) {
      val localFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
      localFormat.format(parsed)
    } else {
      ""
    }
  } catch (e: Exception) {
    ""
  }
}

@JsonClass(generateAdapter = true)
data class StatusHistoryDto(
  @Json(name = "status") val status: String = "",
  @Json(name = "timestamp") val timestamp: String = "",
)

@JsonClass(generateAdapter = true)
data class MongoUserDto(
  @Json(name = "uid") val uid: String = "",
  @Json(name = "name") val name: String = "",
  @Json(name = "email") val email: String = "",
  @Json(name = "role") val role: String = "student",
  @Json(name = "registrationNumber") val registrationNumber: String = "",
  @Json(name = "department") val department: String = "",
  @Json(name = "avatarId") val avatarId: String = "scholar",
  @Json(name = "fcmToken") val fcmToken: String = "",
  @Json(name = "phone") val phone: String = "",
  @Json(name = "course") val course: String = "",
) {
  fun toUserProfile(): UserProfile {
    return UserProfile(
      name = name.ifBlank { "Campus Student" },
      registrationNumber = registrationNumber,
      department = department,
      email = email,
      avatarId = avatarId,
      phone = phone,
      course = course,
    )
  }

  companion object {
    fun fromUserProfile(uid: String, profile: UserProfile, fcmToken: String = ""): MongoUserDto {
      return MongoUserDto(
        uid = uid,
        name = profile.name,
        email = profile.email,
        role = "student",
        registrationNumber = profile.registrationNumber,
        department = profile.department,
        avatarId = profile.avatarId,
        fcmToken = fcmToken,
        phone = profile.phone,
        course = profile.course,
      )
    }
  }
}

@JsonClass(generateAdapter = true)
data class MongoOwnerDto(
  @Json(name = "uid") val uid: String = "",
  @Json(name = "name") val name: String = "",
  @Json(name = "email") val email: String = "",
  @Json(name = "role") val role: String = "owner",
  @Json(name = "canteenAssigned") val canteenAssigned: Boolean = false,
  @Json(name = "canteenId") val canteenId: String = "",
  @Json(name = "block") val block: String = "",
  @Json(name = "phone") val phone: String = "",
  @Json(name = "fcmToken") val fcmToken: String = "",
)

@JsonClass(generateAdapter = true)
data class MongoCanteenDto(
  @Json(name = "id") val id: String = "",
  @Json(name = "name") val name: String = "",
  @Json(name = "block") val block: String = "",
  @Json(name = "location") val location: String = "",
  @Json(name = "isOpen") val isOpen: Boolean = true,
  @Json(name = "closeReason") val closeReason: String = "",
  @Json(name = "floorInfo") val floorInfo: String = "",
  @Json(name = "specialty") val specialty: String = "",
  @Json(name = "avgWaitMinutes") val avgWaitMinutes: Int = 5,
  @Json(name = "icon") val icon: String = "🏢",
  @Json(name = "imageUrl") val imageUrl: String = "",
  @Json(name = "timings") val timings: String = "7:00 AM – 10:00 PM",
) {
  fun toCampusBlockCanteen(items: List<FoodItem> = emptyList()): CampusBlockCanteen {
    val quick = items.filter { it.category == FoodCategory.QUICK_ORDER }.ifEmpty { items.filter { it.prepMinutes <= 5 } }
    val popular = items.filter { it.category == FoodCategory.POPULAR }.ifEmpty { items.take(8) }
    val readyIn10 = items.filter { it.category == FoodCategory.READY_UNDER_10 }.ifEmpty { items.filter { it.prepMinutes <= 10 } }

    val primaryBlocks = block.split(",", "&", "-", " ")
      .mapNotNull { it.trim().filter { char -> char.isDigit() }.toIntOrNull() }
      .ifEmpty { listOf(26, 27) }

    val effectiveIcon = if (icon.isNotBlank() && !icon.contains("?")) icon else if (name.contains("Library", ignoreCase = true) || block.contains("36")) "📚" else "🏢"

    return CampusBlockCanteen(
      id = id,
      name = name,
      betweenBlocks = location.ifBlank { "Block $block" },
      floorInfo = floorInfo.ifBlank { "5th Floor" },
      primaryBlocks = primaryBlocks,
      specialty = specialty.ifBlank { "" },
      crowdStatus = when {
        avgWaitMinutes <= 5 -> CrowdStatus.LOW
        avgWaitMinutes <= 10 -> CrowdStatus.MODERATE
        else -> CrowdStatus.BUSY
      },
      avgWaitMinutes = avgWaitMinutes,
      icon = effectiveIcon,
      rawItems = items,
      quickOrderItems = quick,
      popularItems = popular,
      readyIn10Items = readyIn10,
      isOpen = isOpen,
      closeReason = closeReason.ifBlank { null },
      imageUrl = imageUrl.ifBlank { null },
      timings = timings.ifBlank { "7:00 AM – 10:00 PM" },
    )
  }
}

@JsonClass(generateAdapter = true)
data class MongoAddonDto(
  @Json(name = "name") val name: String = "",
  @Json(name = "price") val price: Int = 0,
)

@JsonClass(generateAdapter = true)
data class MongoItemDto(
  @Json(name = "id") val id: String = "",
  @Json(name = "canteenId") val canteenId: String = "",
  @Json(name = "name") val name: String = "",
  @Json(name = "description") val description: String = "",
  @Json(name = "price") val price: Int = 0,
  @Json(name = "category") val category: String = "QUICK_ORDER",
  @Json(name = "imageUrl") val imageUrl: String = "",
  @Json(name = "cloudinaryPublicId") val cloudinaryPublicId: String? = null,
  @Json(name = "isCustom") val isCustom: Boolean = false,
  @Json(name = "available") val available: Boolean = true,
  @Json(name = "stock") val stock: Int = 100,
  @Json(name = "preparationTime") val preparationTime: String = "5-7 min",
  @Json(name = "prepMinutes") val prepMinutes: Int = 7,
  @Json(name = "rating") val rating: Double = 4.5,
  @Json(name = "ingredients") val ingredients: List<String> = emptyList(),
  @Json(name = "customizationTitle") val customizationTitle: String = "",
  @Json(name = "customizationOptions") val customizationOptions: List<String> = emptyList(),
  @Json(name = "addons") val addons: List<MongoAddonDto> = emptyList(),
) {
  fun toFoodItem(): FoodItem {
    val normalized = category.trim().uppercase().replace(" ", "_")
    val cat = try {
      FoodCategory.valueOf(normalized)
    } catch (e: Exception) {
      FoodCategory.QUICK_ORDER
    }
    return FoodItem(
      id = id,
      name = name,
      price = price,
      prepTime = preparationTime,
      prepMinutes = prepMinutes,
      rating = rating,
      category = cat,
      imageLabel = name,
      imageUrl = imageUrl,
      description = description,
      ingredients = ingredients,
      customizationTitle = customizationTitle.ifBlank { "Bread Type" },
      customizationOptions = customizationOptions,
      addons = addons.map { com.example.data.FoodAddon(it.name, it.price) },
    )
  }
}

@JsonClass(generateAdapter = true)
data class MongoOrderItemDto(
  @Json(name = "itemId") val itemId: String = "",
  @Json(name = "name") val name: String = "",
  @Json(name = "price") val price: Int = 0,
  @Json(name = "quantity") val quantity: Int = 1,
  @Json(name = "prepMinutes") val prepMinutes: Int = 7,
  @Json(name = "selectedOption") val selectedOption: String? = null,
  @Json(name = "selectedAddons") val selectedAddons: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MongoOrderDto(
  @Json(name = "orderId") val orderId: String = "",
  @Json(name = "studentId") val studentId: String = "",
  @Json(name = "studentName") val studentName: String = "",
  @Json(name = "studentPhone") val studentPhone: String = "",
  @Json(name = "studentCourse") val studentCourse: String = "",
  @Json(name = "canteenId") val canteenId: String = "",
  @Json(name = "items") val items: List<MongoOrderItemDto> = emptyList(),
  @Json(name = "totalAmount") val totalAmount: Int = 0,
  @Json(name = "status") val status: String = "PREPARING",
  @Json(name = "paymentStatus") val paymentStatus: String = "PAID",
  @Json(name = "tokenNumber") val tokenNumber: String = "",
  @Json(name = "pickupPreference") val pickupPreference: String = "Pickup ASAP",
  @Json(name = "pickupCanteenName") val pickupCanteenName: String = "",
  @Json(name = "pickupLocation") val pickupLocation: String = "",
  @Json(name = "pickupCounter") val pickupCounter: String = "Counter 1",
  @Json(name = "estimatedReadyTime") val estimatedReadyTime: String = "",
  @Json(name = "orderPlacedAt") val orderPlacedAt: String? = null,
  @Json(name = "confirmedAt") val confirmedAt: String? = null,
  @Json(name = "preparingAt") val preparingAt: String? = null,
  @Json(name = "readyAt") val readyAt: String? = null,
  @Json(name = "completedAt") val completedAt: String? = null,
  @Json(name = "cancelledAt") val cancelledAt: String? = null,
  @Json(name = "createdAt") val createdAt: String? = null,
  @Json(name = "statusHistory") val statusHistory: List<StatusHistoryDto> = emptyList(),
) {
  fun toOrderRecord(): OrderRecord {
    val parsedStatus = when (status.uppercase()) {
      "NEW" -> OrderStatus.NEW
      "READY" -> OrderStatus.READY
      "COMPLETED" -> OrderStatus.COMPLETED
      else -> OrderStatus.PREPARING
    }

    val cartItems = items.map { itemDto ->
      CartItem(
        foodItem = FoodItem(
          id = itemDto.itemId,
          name = itemDto.name,
          price = itemDto.price,
          prepTime = "${itemDto.prepMinutes} min",
          prepMinutes = if (itemDto.prepMinutes > 0) itemDto.prepMinutes else 7,
          category = FoodCategory.QUICK_ORDER,
          imageLabel = itemDto.name,
        ),
        quantity = itemDto.quantity,
        selectedOption = itemDto.selectedOption,
      )
    }

    val placedLocalTime = formatUtcToLocalTime(orderPlacedAt ?: createdAt)
    val confirmedLocalTime = formatUtcToLocalTime(confirmedAt)
    val preparingLocalTime = formatUtcToLocalTime(preparingAt)
    val readyLocalTime = formatUtcToLocalTime(readyAt)
    val completedLocalTime = formatUtcToLocalTime(completedAt)

    val displayOrderTime = if (placedLocalTime.isNotBlank()) "Today, $placedLocalTime" else "Today, 10:42 AM"

    return OrderRecord(
      id = orderId,
      tokenNumber = tokenNumber.ifBlank { "042" },
      items = cartItems,
      totalPrice = totalAmount,
      status = parsedStatus,
      orderTime = displayOrderTime,
      estimatedReadyTime = estimatedReadyTime.ifBlank { "Ready soon" },
      pickupCanteenName = pickupCanteenName.ifBlank { "Campus Canteen" },
      pickupLocation = pickupLocation.ifBlank { "Counter 1" },
      pickupPreference = pickupPreference,
      pickupCounter = pickupCounter.ifBlank { "Counter 1" },
      canteenId = canteenId.ifBlank { "canteen_33" },
      orderPlacedAt = placedLocalTime,
      confirmedAt = confirmedLocalTime,
      preparingAt = preparingLocalTime,
      readyAt = readyLocalTime,
      completedAt = completedLocalTime,
    )
  }

  companion object {
    fun fromOrderRecord(
      record: OrderRecord,
      studentId: String,
      studentName: String,
      canteenId: String,
      studentPhone: String = "",
      studentCourse: String = "",
      pickupCounter: String = "Counter 1",
    ): MongoOrderDto {
      val itemDtos = record.items.map { cart ->
        MongoOrderItemDto(
          itemId = cart.foodItem.id,
          name = cart.foodItem.name,
          price = cart.unitPrice,
          quantity = cart.quantity,
          prepMinutes = if (cart.foodItem.prepMinutes > 0) cart.foodItem.prepMinutes else 7,
          selectedOption = cart.selectedOption,
          selectedAddons = cart.selectedAddons.map { it.name },
        )
      }

      return MongoOrderDto(
        orderId = record.id,
        studentId = studentId,
        studentName = studentName,
        studentPhone = studentPhone,
        studentCourse = studentCourse,
        canteenId = canteenId,
        items = itemDtos,
        totalAmount = record.totalPrice,
        status = record.status.name,
        paymentStatus = "PAID",
        tokenNumber = record.tokenNumber,
        pickupPreference = record.pickupPreference,
        pickupCanteenName = record.pickupCanteenName,
        pickupLocation = record.pickupLocation,
        pickupCounter = pickupCounter,
        estimatedReadyTime = record.estimatedReadyTime,
      )
    }
  }
}

@JsonClass(generateAdapter = true)
data class MongoInventoryDto(
  @Json(name = "_id") val id: String? = null,
  @Json(name = "canteenId") val canteenId: String = "",
  @Json(name = "name") val name: String = "",
  @Json(name = "icon") val icon: String = "📦",
  @Json(name = "quantity") val quantity: Double = 0.0,
  @Json(name = "unit") val unit: String = "pcs",
  @Json(name = "lowStockThreshold") val lowStockThreshold: Double = 5.0,
) {
  fun toInventoryItem(): InventoryItem {
    return InventoryItem(
      id = id ?: "",
      canteenId = canteenId,
      name = name,
      icon = icon,
      quantity = quantity,
      unit = unit,
      lowStockThreshold = lowStockThreshold,
    )
  }
}

@JsonClass(generateAdapter = true)
data class MongoNotificationDto(
  @Json(name = "notificationId") val notificationId: String = "",
  @Json(name = "userId") val userId: String = "",
  @Json(name = "title") val title: String = "",
  @Json(name = "message") val message: String = "",
  @Json(name = "type") val type: String = "ORDER_UPDATE",
  @Json(name = "orderId") val orderId: String = "",
  @Json(name = "canteenId") val canteenId: String = "",
  @Json(name = "read") val read: Boolean = false,
) {
  fun toNotificationItem(): NotificationItem {
    return NotificationItem(
      id = notificationId,
      title = title,
      message = message,
      timeAgo = "Just now",
      isUnread = !read,
    )
  }
}

@JsonClass(generateAdapter = true)
data class StatusUpdateDto(
  @Json(name = "status") val status: String? = null,
  @Json(name = "isOpen") val isOpen: Boolean? = null,
  @Json(name = "closeReason") val closeReason: String? = null,
)

@JsonClass(generateAdapter = true)
data class ProfileUpdateDto(
  @Json(name = "imageUrl") val imageUrl: String? = null,
  @Json(name = "timings") val timings: String? = null,
  @Json(name = "isOpen") val isOpen: Boolean? = null,
  @Json(name = "closeReason") val closeReason: String? = null,
)

@JsonClass(generateAdapter = true)
data class StockUpdateDto(
  @Json(name = "available") val available: Boolean,
  @Json(name = "stock") val stock: Int,
)

@JsonClass(generateAdapter = true)
data class InventoryQuantityDto(
  @Json(name = "quantity") val quantity: Double,
)

@JsonClass(generateAdapter = true)
data class FcmTokenDto(
  @Json(name = "fcmToken") val fcmToken: String,
)

// ── Auth DTOs ─────────────────────────────────────────────────────────────
@JsonClass(generateAdapter = true)
data class LoginRequestDto(
  @Json(name = "email") val email: String,
  @Json(name = "password") val password: String,
)

@JsonClass(generateAdapter = true)
data class RegisterRequestDto(
  @Json(name = "name") val name: String,
  @Json(name = "email") val email: String,
  @Json(name = "password") val password: String,
  @Json(name = "registrationNumber") val registrationNumber: String = "",
  @Json(name = "department") val department: String = "",
  @Json(name = "avatarId") val avatarId: String = "scholar",
  @Json(name = "phone") val phone: String = "",
  @Json(name = "course") val course: String = "",
)

@JsonClass(generateAdapter = true)
data class LoginResponseDto(
  @Json(name = "success") val success: Boolean = true,
  @Json(name = "token") val token: String = "",
  @Json(name = "user") val user: MongoUserDto? = null,
  @Json(name = "error") val error: String? = null,
)

@JsonClass(generateAdapter = true)
data class ForgotPasswordRequestDto(
  @Json(name = "email") val email: String,
)

@JsonClass(generateAdapter = true)
data class ResetPasswordRequestDto(
  @Json(name = "token") val token: String,
  @Json(name = "email") val email: String,
  @Json(name = "newPassword") val newPassword: String,
)

@JsonClass(generateAdapter = true)
data class SimpleMessageResponseDto(
  @Json(name = "success") val success: Boolean = true,
  @Json(name = "message") val message: String? = null,
  @Json(name = "token") val token: String? = null,
  @Json(name = "resetUrl") val resetUrl: String? = null,
  @Json(name = "error") val error: String? = null,
)

// ── Owner Auth DTOs ───────────────────────────────────────────────────────
@JsonClass(generateAdapter = true)
data class OwnerLoginRequestDto(
  @Json(name = "email") val email: String,
  @Json(name = "password") val password: String,
)

@JsonClass(generateAdapter = true)
data class OwnerLoginResponseDto(
  @Json(name = "success") val success: Boolean = true,
  @Json(name = "message") val message: String? = null,
  @Json(name = "email") val email: String? = null,
  @Json(name = "owner") val owner: MongoOwnerDto? = null,
  @Json(name = "otp") val otp: String? = null,
  @Json(name = "error") val error: String? = null,
)

@JsonClass(generateAdapter = true)
data class OwnerVerifyOtpRequestDto(
  @Json(name = "email") val email: String,
  @Json(name = "otp") val otp: String,
)

@JsonClass(generateAdapter = true)
data class OwnerAuthSuccessResponseDto(
  @Json(name = "success") val success: Boolean = true,
  @Json(name = "message") val message: String? = null,
  @Json(name = "token") val token: String = "",
  @Json(name = "owner") val owner: MongoOwnerDto? = null,
  @Json(name = "error") val error: String? = null,
)

@JsonClass(generateAdapter = true)
data class OwnerResendOtpRequestDto(
  @Json(name = "email") val email: String,
)

@JsonClass(generateAdapter = true)
data class ImageUploadResponseDto(
  @Json(name = "success") val success: Boolean = true,
  @Json(name = "imageUrl") val imageUrl: String = "",
  @Json(name = "cloudinaryPublicId") val cloudinaryPublicId: String? = null,
  @Json(name = "error") val error: String? = null,
)

@JsonClass(generateAdapter = true)
data class CanteenQueueDto(
  @Json(name = "canteenId") val canteenId: String = "",
  @Json(name = "queueCount") val queueCount: Int = 0,
  @Json(name = "avgWaitMinutes") val avgWaitMinutes: Int = 5,
  @Json(name = "activeOrdersCount") val activeOrdersCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class OrderQueuePositionDto(
  @Json(name = "orderId") val orderId: String = "",
  @Json(name = "tokenNumber") val tokenNumber: String = "",
  @Json(name = "status") val status: String = "",
  @Json(name = "canteenId") val canteenId: String = "",
  @Json(name = "queuePosition") val queuePosition: Int = 1,
  @Json(name = "queueNumber") val queueNumber: Int = 1,
  @Json(name = "ordersAhead") val ordersAhead: Int = 0,
  @Json(name = "estWaitMinutes") val estWaitMinutes: Int = 5,
  @Json(name = "message") val message: String = "",
  @Json(name = "orderPlacedAt") val orderPlacedAt: String? = null,
  @Json(name = "confirmedAt") val confirmedAt: String? = null,
  @Json(name = "preparingAt") val preparingAt: String? = null,
  @Json(name = "readyAt") val readyAt: String? = null,
  @Json(name = "completedAt") val completedAt: String? = null,
  @Json(name = "cancelledAt") val cancelledAt: String? = null,
  @Json(name = "statusHistory") val statusHistory: List<StatusHistoryDto> = emptyList(),
)


