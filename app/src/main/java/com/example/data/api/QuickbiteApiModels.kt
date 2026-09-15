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
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun extractEpochFromOrderId(orderId: String?): Long {
  if (orderId.isNullOrBlank()) return 0L
  val regex = Regex("""ORD-(\d{10,14})(?:-\d+)?""")
  val match = regex.find(orderId.trim())
  return match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
}

fun formatUtcToLocalTime(timestampStr: String?): String {
  if (timestampStr.isNullOrBlank()) return ""
  val trimmed = timestampStr.trim()

  // 1. If it is an ISO timestamp (e.g. 2026-09-12T15:00:00.000Z), parse epoch and convert to IST/device local time
  if (trimmed.contains("T") || trimmed.endsWith("Z", ignoreCase = true)) {
    val epochMillis = parseIsoOrMillisToEpoch(trimmed)
    if (epochMillis > 0) {
      val localFormat = SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
      }
      return localFormat.format(Date(epochMillis))
    }
  }

  // 2. Exact 12-hour format e.g. "1:00 AM", "01:00 AM", "8:30 PM"
  if (trimmed.matches(Regex("""^\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)$""", RegexOption.IGNORE_CASE))) {
    return trimmed
  }

  // 3. If string contains date and 12-hour time e.g. "12 Sep 2026, 01:00:14 AM" or "01:00:14 AM"
  val timeWithSecondsMatch = Regex("""(\d{1,2}:\d{2}):\d{2}\s*(AM|PM|am|pm)""", RegexOption.IGNORE_CASE).find(trimmed)
  if (timeWithSecondsMatch != null) {
    val h = timeWithSecondsMatch.groupValues[1]
    val ampm = timeWithSecondsMatch.groupValues[2].uppercase()
    return "$h $ampm"
  }

  // 4. Check if string contains simple "h:mm a"
  val simpleTimeMatch = Regex("""(\d{1,2}:\d{2}\s*(?:AM|PM|am|pm))""", RegexOption.IGNORE_CASE).find(trimmed)
  if (simpleTimeMatch != null) {
    return simpleTimeMatch.groupValues[1]
  }

  return try {
    val epochMillis = parseIsoOrMillisToEpoch(trimmed)
    if (epochMillis > 0) {
      val localFormat = SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
      }
      return localFormat.format(Date(epochMillis))
    }
    ""
  } catch (e: Exception) {
    ""
  }
}

fun parseIsoOrMillisToEpoch(timestampStr: String?): Long {
  if (timestampStr.isNullOrBlank()) return 0L
  val trimmed = timestampStr.trim()

  // 1. Direct Epoch Milliseconds
  val asLong = trimmed.toLongOrNull()
  if (asLong != null && asLong > 0) return asLong

  // 2. Embedded in Order ID (e.g. ORD-1726065360000-1234)
  if (trimmed.startsWith("ORD-", ignoreCase = true)) {
    val fromId = extractEpochFromOrderId(trimmed)
    if (fromId > 0) return fromId
  }

  // 3. 12-Hour Local Time (e.g. "8:05 PM" or "Today, 8:05 PM")
  val timeOnly = trimmed.removePrefix("Today,").removePrefix("today,").trim()
  if (timeOnly.matches(Regex("""\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)""", RegexOption.IGNORE_CASE))) {
    try {
      val parsedTime = SimpleDateFormat("h:mm a", Locale.US).parse(timeOnly)
      if (parsedTime != null) {
        val cal = Calendar.getInstance()
        val timeCal = Calendar.getInstance().apply { time = parsedTime }
        cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
        cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
      }
    } catch (_: Exception) {}
  }

  // 4. Java 8 java.time parsing (handles any ISO-8601 string)
  try {
    return Instant.parse(trimmed).toEpochMilli()
  } catch (_: Exception) {}
  try {
    return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
  } catch (_: Exception) {}

  // 5. Fallback SimpleDateFormat patterns
  val isoPatterns = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    "yyyy-MM-dd'T'HH:mm:ss.SSS",
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
    "yyyy-MM-dd'T'HH:mm:ssXXX",
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd HH:mm:ss"
  )
  for (pattern in isoPatterns) {
    try {
      val sdf = SimpleDateFormat(pattern, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
      }
      val parsed = sdf.parse(trimmed)
      if (parsed != null) return parsed.time
    } catch (_: Exception) {}
  }
  return 0L
}

@JsonClass(generateAdapter = true)
data class StatusHistoryDto(
  @Json(name = "status") val status: String = "",
  @Json(name = "timestamp") val timestamp: String = "",
  @Json(name = "timestampLocal") val timestampLocal: String? = null,
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
  @Json(name = "gender") val gender: String = "",
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
      gender = gender,
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
  @Json(name = "customerPickupTime") val customerPickupTime: String? = null,
  @Json(name = "estimatedCompletionTime") val estimatedCompletionTime: String? = null,
  @Json(name = "orderDate") val orderDate: String? = null,
  @Json(name = "orderTime") val orderTime: String? = null,
  @Json(name = "orderPlacedAtLocal") val orderPlacedAtLocal: String? = null,
  @Json(name = "confirmedAtLocal") val confirmedAtLocal: String? = null,
  @Json(name = "preparingAtLocal") val preparingAtLocal: String? = null,
  @Json(name = "readyAtLocal") val readyAtLocal: String? = null,
  @Json(name = "completedAtLocal") val completedAtLocal: String? = null,
  @Json(name = "cancelledAtLocal") val cancelledAtLocal: String? = null,
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

    val placedLocalTime = orderPlacedAtLocal?.ifBlank { null }
      ?: orderTime?.ifBlank { null }
      ?: formatUtcToLocalTime(orderPlacedAt ?: createdAt).ifBlank {
        val fallbackEpoch = extractEpochFromOrderId(orderId)
        if (fallbackEpoch > 0) SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(fallbackEpoch)) else ""
      }
    val confirmedLocalTime = confirmedAtLocal?.ifBlank { null }
      ?: formatUtcToLocalTime(confirmedAt).ifBlank { placedLocalTime }
    val preparingLocalTime = preparingAtLocal?.ifBlank { null }
      ?: formatUtcToLocalTime(preparingAt).ifBlank {
        statusHistory.find { it.status.equals("PREPARING", ignoreCase = true) }?.let {
          it.timestampLocal?.ifBlank { null } ?: formatUtcToLocalTime(it.timestamp)
        } ?: ""
      }
    val readyLocalTime = readyAtLocal?.ifBlank { null }
      ?: formatUtcToLocalTime(readyAt).ifBlank {
        statusHistory.find { it.status.equals("READY", ignoreCase = true) }?.let {
          it.timestampLocal?.ifBlank { null } ?: formatUtcToLocalTime(it.timestamp)
        } ?: ""
      }
    val completedLocalTime = completedAtLocal?.ifBlank { null }
      ?: formatUtcToLocalTime(completedAt).ifBlank {
        statusHistory.find { it.status.equals("COMPLETED", ignoreCase = true) }?.let {
          it.timestampLocal?.ifBlank { null } ?: formatUtcToLocalTime(it.timestamp)
        } ?: ""
      }

    val displayOrderTime = if (placedLocalTime.isNotBlank()) "Today, $placedLocalTime" else "Today"

    val effectiveCustomerPickup = customerPickupTime?.ifBlank { null }
      ?: if (pickupPreference.contains("at ", ignoreCase = true)) pickupPreference.substringAfter("at ").trim()
      else if (pickupPreference.contains("ASAP", ignoreCase = true)) "ASAP" else "ASAP"
    val effectiveCompletion = estimatedCompletionTime?.ifBlank { null }
      ?: estimatedReadyTime.ifBlank { "Ready soon" }

    val placedEpoch = parseIsoOrMillisToEpoch(orderPlacedAt ?: createdAt).let {
      if (it > 0) it else extractEpochFromOrderId(orderId)
    }
    val effectiveOrderDate = orderDate?.ifBlank { null }
      ?: if (placedEpoch > 0) SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(placedEpoch)) else ""

    return OrderRecord(
      id = orderId,
      tokenNumber = tokenNumber.ifBlank { "042" },
      items = cartItems,
      totalPrice = totalAmount,
      status = parsedStatus,
      orderTime = displayOrderTime,
      estimatedReadyTime = effectiveCompletion,
      customerPickupTime = effectiveCustomerPickup,
      estimatedCompletionTime = effectiveCompletion,
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
      placedEpoch = placedEpoch,
      orderDate = effectiveOrderDate,
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
        customerPickupTime = record.customerPickupTime.ifBlank {
          if (record.pickupPreference.contains("at ", ignoreCase = true)) record.pickupPreference.substringAfter("at ").trim() else "ASAP"
        },
        estimatedCompletionTime = record.estimatedCompletionTime.ifBlank { record.estimatedReadyTime },
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
    val now = System.currentTimeMillis()
    val arrivalTime = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(now))
    return NotificationItem(
      id = notificationId,
      title = title,
      message = message,
      timeAgo = "Just now",
      isUnread = !read,
      timestamp = now,
      arrivalTimeFormatted = arrivalTime,
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
  @Json(name = "gender") val gender: String = "",
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
data class CartQueueItemRequestDto(
  @Json(name = "itemId") val itemId: String,
  @Json(name = "name") val name: String,
  @Json(name = "quantity") val quantity: Int = 1,
)

@JsonClass(generateAdapter = true)
data class CartQueueRequestDto(
  @Json(name = "canteenId") val canteenId: String,
  @Json(name = "items") val items: List<CartQueueItemRequestDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CartItemQueueInfoDto(
  @Json(name = "itemId") val itemId: String = "",
  @Json(name = "name") val name: String = "",
  @Json(name = "quantity") val quantity: Int = 1,
  @Json(name = "queuePosition") val queuePosition: Int = 1,
  @Json(name = "similarOrdersAhead") val similarOrdersAhead: Int = 0,
  @Json(name = "similarItemsAhead") val similarItemsAhead: Int = 0,
  @Json(name = "estWaitMinutes") val estWaitMinutes: Int = 5,
  @Json(name = "estimatedReadyTime") val estimatedReadyTime: String = "",
)

@JsonClass(generateAdapter = true)
data class CartQueueResponseDto(
  @Json(name = "canteenId") val canteenId: String = "",
  @Json(name = "primaryItemName") val primaryItemName: String = "",
  @Json(name = "queuePosition") val queuePosition: Int = 1,
  @Json(name = "queueNumber") val queueNumber: Int = 1,
  @Json(name = "ordersAhead") val ordersAhead: Int = 0,
  @Json(name = "similarOrdersAhead") val similarOrdersAhead: Int = 0,
  @Json(name = "similarItemsAhead") val similarItemsAhead: Int = 0,
  @Json(name = "distinctCustomersAhead") val distinctCustomersAhead: Int = 0,
  @Json(name = "totalRemainingAheadMinutes") val totalRemainingAheadMinutes: Int = 0,
  @Json(name = "cartPrepMinutes") val cartPrepMinutes: Int = 0,
  @Json(name = "estWaitMinutes") val estWaitMinutes: Int = 5,
  @Json(name = "estimatedCompletionTime") val estimatedCompletionTime: String = "",
  @Json(name = "estimatedCompletionAt") val estimatedCompletionAt: String? = null,
  @Json(name = "currentServerTime") val currentServerTime: String? = null,
  @Json(name = "headline") val headline: String = "",
  @Json(name = "workloadSummary") val workloadSummary: String = "",
  @Json(name = "itemsQueue") val itemsQueue: List<CartItemQueueInfoDto> = emptyList(),
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
  @Json(name = "similarOrdersAhead") val similarOrdersAhead: Int = 0,
  @Json(name = "similarItemsAhead") val similarItemsAhead: Int = 0,
  @Json(name = "distinctCustomersAhead") val distinctCustomersAhead: Int = 0,
  @Json(name = "totalRemainingAheadMinutes") val totalRemainingAheadMinutes: Int = 0,
  @Json(name = "currentOrderPrepMinutes") val currentOrderPrepMinutes: Int = 0,
  @Json(name = "primaryItemName") val primaryItemName: String = "",
  @Json(name = "itemSummary") val itemSummary: String = "",
  @Json(name = "workloadSummary") val workloadSummary: String = "",
  @Json(name = "estWaitMinutes") val estWaitMinutes: Int = 5,
  @Json(name = "estimatedCompletionTime") val estimatedCompletionTime: String = "",
  @Json(name = "estimatedCompletionAt") val estimatedCompletionAt: String? = null,
  @Json(name = "currentServerTime") val currentServerTime: String? = null,
  @Json(name = "customerPickupTime") val customerPickupTime: String? = null,
  @Json(name = "message") val message: String = "",
  @Json(name = "orderDate") val orderDate: String? = null,
  @Json(name = "orderTime") val orderTime: String? = null,
  @Json(name = "orderPlacedAtLocal") val orderPlacedAtLocal: String? = null,
  @Json(name = "confirmedAtLocal") val confirmedAtLocal: String? = null,
  @Json(name = "preparingAtLocal") val preparingAtLocal: String? = null,
  @Json(name = "readyAtLocal") val readyAtLocal: String? = null,
  @Json(name = "completedAtLocal") val completedAtLocal: String? = null,
  @Json(name = "cancelledAtLocal") val cancelledAtLocal: String? = null,
  @Json(name = "orderPlacedAt") val orderPlacedAt: String? = null,
  @Json(name = "confirmedAt") val confirmedAt: String? = null,
  @Json(name = "preparingAt") val preparingAt: String? = null,
  @Json(name = "readyAt") val readyAt: String? = null,
  @Json(name = "completedAt") val completedAt: String? = null,
  @Json(name = "cancelledAt") val cancelledAt: String? = null,
  @Json(name = "statusHistory") val statusHistory: List<StatusHistoryDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class UserFavoritesResponseDto(
  @Json(name = "favorites") val favorites: List<String> = emptyList(),
)



