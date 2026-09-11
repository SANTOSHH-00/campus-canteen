package com.example.data.firebase

import com.example.data.CampusBlockCanteen
import com.example.data.CartItem
import com.example.data.CrowdStatus
import com.example.data.FoodAddon
import com.example.data.FoodCategory
import com.example.data.FoodItem
import com.example.data.NotificationItem
import com.example.data.OrderRecord
import com.example.data.OrderStatus
import com.example.ui.state.UserProfile
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── 1. Users Collection: users/{uid} ──────────────────────────────────────────
@IgnoreExtraProperties
data class UserDocument(
  @DocumentId val uid: String = "",
  val name: String = "",
  val email: String = "",
  val role: String = "student",
  val registrationNumber: String = "",
  val department: String = "",
  val avatarId: String = "scholar",
  val fcmToken: String = "",
  val phone: String = "",
  val course: String = "",
  val createdAt: Long = System.currentTimeMillis(),
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
    fun fromUserProfile(uid: String, profile: UserProfile, fcmToken: String = ""): UserDocument {
      return UserDocument(
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

// ── 2. Owners Collection: owners/{uid} ────────────────────────────────────────
@IgnoreExtraProperties
data class OwnerDocument(
  @DocumentId val uid: String = "",
  val name: String = "",
  val email: String = "",
  val role: String = "owner",
  @get:PropertyName("canteenAssigned") @set:PropertyName("canteenAssigned") var canteenAssigned: Boolean = false,
  val canteenId: String = "",
  val block: String = "",
  val phone: String = "",
  val fcmToken: String = "",
  val createdAt: Long = System.currentTimeMillis(),
)

// ── 3. Canteens Collection: canteens/{canteenId} ─────────────────────────────
@IgnoreExtraProperties
data class CanteenDocument(
  @DocumentId val id: String = "",
  val name: String = "",
  val block: String = "",
  val location: String = "",
  @get:PropertyName("isOpen") @set:PropertyName("isOpen") var isOpen: Boolean = true,
  val closeReason: String = "",
  val floorInfo: String = "",
  val specialty: String = "",
  val avgWaitMinutes: Int = 5,
  val icon: String = "🏢",
  val imageUrl: String = "",
  val timings: String = "7:00 AM – 10:00 PM",
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

  companion object {
    fun fromCampusBlockCanteen(canteen: CampusBlockCanteen): CanteenDocument {
      return CanteenDocument(
        id = canteen.id,
        name = canteen.name,
        block = canteen.primaryBlocks.joinToString("-"),
        location = canteen.betweenBlocks,
        isOpen = canteen.isOpen,
        closeReason = canteen.closeReason ?: "",
        floorInfo = canteen.floorInfo,
        specialty = canteen.specialty,
        avgWaitMinutes = canteen.avgWaitMinutes,
        icon = canteen.icon,
        imageUrl = canteen.imageUrl ?: "",
        timings = canteen.timings,
      )
    }
  }
}

// ── 4. Items Collection: items/{itemId} ───────────────────────────────────────
@IgnoreExtraProperties
data class AddonDocument(
  val name: String = "",
  val price: Int = 0,
)

@IgnoreExtraProperties
data class ItemDocument(
  @DocumentId val id: String = "",
  val canteenId: String = "",
  val name: String = "",
  val description: String = "",
  val price: Int = 0,
  val category: String = "QUICK_ORDER", // QUICK_ORDER, POPULAR, READY_UNDER_10, BEVERAGES, SNACKS
  val imageUrl: String = "",
  val cloudinaryPublicId: String? = null,
  val isCustom: Boolean = false,
  @get:PropertyName("available") @set:PropertyName("available") var available: Boolean = true,
  val stock: Int = 100,
  val preparationTime: String = "5-7 min",
  val prepMinutes: Int = 7,
  val rating: Double = 4.5,
  val ingredients: List<String> = emptyList(),
  val customizationTitle: String = "",
  val customizationOptions: List<String> = emptyList(),
  val addons: List<AddonDocument> = emptyList(),
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
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
      addons = addons.map { FoodAddon(it.name, it.price) },
    )
  }

  companion object {
    fun fromFoodItem(foodItem: FoodItem, canteenId: String): ItemDocument {
      return ItemDocument(
        id = foodItem.id,
        canteenId = canteenId,
        name = foodItem.name,
        description = foodItem.description,
        price = foodItem.price,
        category = foodItem.category.name,
        imageUrl = foodItem.imageUrl,
        available = true,
        stock = 100,
        preparationTime = foodItem.prepTime,
        prepMinutes = foodItem.prepMinutes,
        rating = foodItem.rating ?: 4.5,
        ingredients = foodItem.ingredients,
        customizationTitle = foodItem.customizationTitle,
        customizationOptions = foodItem.customizationOptions,
        addons = foodItem.addons.map { AddonDocument(it.name, it.price) },
      )
    }
  }
}

// ── 5. Orders Collection: orders/{orderId} ────────────────────────────────────
@IgnoreExtraProperties
data class OrderItemDocument(
  val itemId: String = "",
  val name: String = "",
  val price: Int = 0,
  val quantity: Int = 1,
  val selectedOption: String? = null,
  val selectedAddons: List<String> = emptyList(),
)

@IgnoreExtraProperties
data class OrderDocument(
  @DocumentId val orderId: String = "",
  val studentId: String = "",
  val studentName: String = "",
  val studentPhone: String = "",
  val studentCourse: String = "",
  val canteenId: String = "",
  val items: List<OrderItemDocument> = emptyList(),
  val totalAmount: Int = 0,
  val status: String = "PREPARING", // PREPARING, READY, COMPLETED, CANCELLED
  val paymentStatus: String = "PAID",
  val tokenNumber: String = "",
  val pickupPreference: String = "Pickup ASAP",
  val pickupCanteenName: String = "",
  val pickupLocation: String = "",
  val pickupCounter: String = "Counter 1", // Counter 1, Counter 2
  val estimatedReadyTime: String = "",
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
) {
  fun toOrderRecord(): OrderRecord {
    val parsedStatus = when (status.uppercase()) {
      "NEW" -> OrderStatus.NEW
      "READY" -> OrderStatus.READY
      "COMPLETED" -> OrderStatus.COMPLETED
      else -> OrderStatus.PREPARING
    }

    val cartItems = items.map { itemDoc ->
      CartItem(
        foodItem = FoodItem(
          id = itemDoc.itemId,
          name = itemDoc.name,
          price = itemDoc.price,
          prepTime = "5-10 min",
          prepMinutes = 7,
          category = FoodCategory.QUICK_ORDER,
          imageLabel = itemDoc.name,
        ),
        quantity = itemDoc.quantity,
        selectedOption = itemDoc.selectedOption,
      )
    }

    val timeFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(createdAt))

    return OrderRecord(
      id = orderId,
      tokenNumber = tokenNumber.ifBlank { "042" },
      items = cartItems,
      totalPrice = totalAmount,
      status = parsedStatus,
      orderTime = "Today, $timeFormatted",
      estimatedReadyTime = estimatedReadyTime.ifBlank { "Ready soon" },
      pickupCanteenName = pickupCanteenName.ifBlank { "Campus Canteen" },
      pickupLocation = pickupLocation.ifBlank { "Counter 1" },
      pickupPreference = pickupPreference,
      pickupCounter = pickupCounter.ifBlank { "Counter 1" },
      canteenId = canteenId.ifBlank { "canteen_33" },
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
    ): OrderDocument {
      val itemDocs = record.items.map { cart ->
        OrderItemDocument(
          itemId = cart.foodItem.id,
          name = cart.foodItem.name,
          price = cart.unitPrice,
          quantity = cart.quantity,
          selectedOption = cart.selectedOption,
          selectedAddons = cart.selectedAddons.map { it.name },
        )
      }

      return OrderDocument(
        orderId = record.id,
        studentId = studentId,
        studentName = studentName,
        studentPhone = studentPhone,
        studentCourse = studentCourse,
        canteenId = canteenId,
        items = itemDocs,
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

// ── 6. Notifications Collection: notifications/{notificationId} ───────────────
@IgnoreExtraProperties
data class NotificationDocument(
  @DocumentId val notificationId: String = "",
  val userId: String = "",
  val title: String = "",
  val message: String = "",
  val type: String = "ORDER_UPDATE", // ORDER_UPDATE, ALERT, PROMO
  val orderId: String = "",
  val canteenId: String = "",
  val createdAt: Long = System.currentTimeMillis(),
  @get:PropertyName("read") @set:PropertyName("read") var read: Boolean = false,
) {
  fun toNotificationItem(): NotificationItem {
    val diffMinutes = ((System.currentTimeMillis() - createdAt) / 60000).coerceAtLeast(1)
    val timeAgo = when {
      diffMinutes < 60 -> "${diffMinutes}m ago"
      diffMinutes < 1440 -> "${diffMinutes / 60}h ago"
      else -> "${diffMinutes / 1440}d ago"
    }
    return NotificationItem(
      id = notificationId,
      title = title,
      message = message,
      timeAgo = timeAgo,
      isUnread = !read,
    )
  }
}
