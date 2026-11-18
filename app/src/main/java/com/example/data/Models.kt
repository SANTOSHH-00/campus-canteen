// Models: Core utility and helper functions
package com.example.data

enum class FoodCategory(val displayName: String) {
  BREAKFAST("Breakfast"),
  HEALTHY_BREAKFAST("Healthy Breakfast"),
  BEVERAGES("Beverages"),
  LUNCH("Lunch"),
  SNACKS("Snacks"),
  QUICK_ORDER("Quick Order"),
  POPULAR("Popular"),
  READY_UNDER_10("Ready Under 10"),
}

enum class PopularBadge(val displayName: String) {
  BESTSELLER("BESTSELLER"),
  CHEFS_PICK("CHEF'S PICK"),
  POPULAR("POPULAR"),
}

enum class MenuSortOption(val displayName: String, val shortLabel: String = displayName) {
  RECOMMENDED("Recommended", "Recommended"),
  POPULAR("Popular", "Popular"),
  PRICE_LOW_TO_HIGH("Price: Low to High", "Price ↑"),
  PRICE_HIGH_TO_LOW("Price: High to Low", "Price ↓"),
  NEWEST_FIRST("Recently Added", "Recently Added"),
  RATING_HIGH_TO_LOW("Rating: High to Low", "Rating ★"),
  FASTEST_PREP("Fastest Prep Time", "Fast Prep"),
  TOP_RATED("Highest Rated (4.0+ ★)", "Top Rated"),
}

enum class MenuDietaryFilter(val displayName: String) {
  ALL("All Items"),
  VEG_ONLY("Vegetarian"),
  NON_VEG("Non-Vegetarian"),
}


data class FoodAddon(
  val name: String,
  val price: Int,
)

data class FoodReview(
  val id: String,
  val itemId: String,
  val userName: String,
  val rating: Double,
  val comment: String,
  val date: String = "",
)

data class FoodItem(
  val id: String,
  val name: String,
  val price: Int,
  val prepTime: String,
  val prepMinutes: Int,
  val rating: Double? = null,
  val reviewCount: Int = 0,
  val originalPrice: Int? = null,
  val category: FoodCategory,
  val imageLabel: String,
  val imageUrl: String = "",
  val description: String = "",
  val ingredients: List<String> = emptyList(),
  val ordersCount: String = "1.2k orders",
  val customizationTitle: String = "Bread Type",
  val customizationOptions: List<String> = emptyList(),
  val addons: List<FoodAddon> = emptyList(),
  val isFavorite: Boolean = false,
  val isChefPick: Boolean = false,
  val dailyOrdersToday: Int = 0,
  val isAvailable: Boolean = true,
  val isVeg: Boolean = true,
  val createdAt: Long = 0L,
) {
  val popularBadge: PopularBadge
    get() = when {
      isChefPick -> PopularBadge.CHEFS_PICK
      dailyOrdersToday >= 20 || name.contains("Thali", ignoreCase = true) || name.contains("Chole", ignoreCase = true) -> PopularBadge.BESTSELLER
      else -> PopularBadge.POPULAR
    }

  val displayIngredients: List<String>
    get() = ingredients

  val displaySubtitle: String
    get() = when {
      ingredients.isNotEmpty() -> "with " + ingredients.take(2).joinToString(" & ")
      name.contains("with", ignoreCase = true) -> ""
      category == FoodCategory.BREAKFAST -> "with Mint Chutney & Pickle"
      category == FoodCategory.LUNCH -> "with Basmati Rice & Dal"
      category == FoodCategory.BEVERAGES -> "served Chilled & Fresh"
      else -> "with Fresh Seasonal Herbs"
    }

  val displayCustomizationTitle: String
    get() = customizationTitle

  val displayCustomizationOptions: List<String>
    get() = customizationOptions

  val displayAddons: List<FoodAddon>
    get() = addons
}

data class CartItem(
  val foodItem: FoodItem,
  var quantity: Int,
  val selectedOption: String? = null,
  val selectedAddons: List<FoodAddon> = emptyList(),
) {
  val unitPrice: Int
    get() = foodItem.price + selectedAddons.sumOf { it.price }

  val totalPrice: Int
    get() = unitPrice * quantity
}

enum class FulfillmentType(val displayName: String, val description: String) {
  DINE_IN("Dine-in", "Order now and enjoy your food at the canteen."),
  TAKEAWAY("Takeaway", "Get your food packed and ready to collect."),
}

enum class OrderStatus(val displayName: String) {
  PENDING("Waiting for Confirmation"),
  NEW("Pending Confirmation"),
  ACCEPTED("Order Accepted"),
  PREPARING("Preparing at Counter"),
  READY("Ready for Pickup!"),
  COMPLETED("Completed"),
  CANCELLED("Cancelled"),
  REJECTED("Rejected by Canteen"),
}

enum class CrowdStatus(val label: String, val colorHex: Long) {
  LOW("LOW CROWD", 0xFF2E7D32),
  MODERATE("MODERATE CROWD", 0xFFE65100),
  BUSY("BUSY CROWD", 0xFFC62828),
}

data class CampusBlockCanteen(
  val id: String,
  val name: String,
  val betweenBlocks: String,
  val floorInfo: String,
  val primaryBlocks: List<Int>,
  val specialty: String,
  val crowdStatus: CrowdStatus,
  val avgWaitMinutes: Int,
  val icon: String = "🏢",
  val rawItems: List<FoodItem> = emptyList(),
  val quickOrderItems: List<FoodItem> = emptyList(),
  val popularItems: List<FoodItem> = emptyList(),
  val readyIn10Items: List<FoodItem> = emptyList(),
  val isOpen: Boolean = true,
  val closeReason: String? = null,
  val imageUrl: String? = null,
  val timings: String = "7:00 AM – 10:00 PM",
  val ownerName: String = "",
  val packagingCharge: Int = 10,
  val taxRatePercent: Int = 5,
) {
  val allItems: List<FoodItem>
    get() = if (rawItems.isNotEmpty()) rawItems else (quickOrderItems + popularItems + readyIn10Items).distinctBy { it.id }

  fun getOwnerDisplayName(): String {
    if (ownerName.isNotBlank()) return ownerName
    return when (id) {
      "canteen_33" -> "Govinda Das"
      "canteen_34" -> "Ramesh Sharma"
      "canteen_38" -> "Vikram Malhotra"
      "canteen_25" -> "Rajesh Gupta"
      "canteen_26" -> "Govinda Das"
      "canteen_28" -> "Vishal Singh"
      "canteen_29" -> "Prashant Roy"
      else -> "Canteen Incharge"
    }
  }
}

enum class OrdersDateFilterOption(val displayName: String) {
  ALL("All Dates"),
  TODAY("Today"),
  YESTERDAY("Yesterday"),
  LAST_7_DAYS("Last 7 Days"),
  LAST_30_DAYS("Last 30 Days"),
  CUSTOM("Custom Date Range"),
}

data class OrdersFilterState(
  val dateOption: OrdersDateFilterOption = OrdersDateFilterOption.ALL,
  val customStartDateEpoch: Long? = null,
  val customEndDateEpoch: Long? = null,
  val selectedCanteenIds: Set<String> = emptySet(),
) {
  val isActive: Boolean
    get() = dateOption != OrdersDateFilterOption.ALL || selectedCanteenIds.isNotEmpty()

  val activeFilterCount: Int
    get() {
      var count = 0
      if (dateOption != OrdersDateFilterOption.ALL) count++
      if (selectedCanteenIds.isNotEmpty()) count += selectedCanteenIds.size
      return count
    }
}

data class OrderRecord(
  val id: String,
  val tokenNumber: String,
  val items: List<CartItem>,
  val totalPrice: Int,
  val status: OrderStatus,
  val orderTime: String,
  val estimatedReadyTime: String,
  val customerPickupTime: String = "",
  val estimatedCompletionTime: String = "",
  val pickupCanteenName: String = "Govinda's Kitchen",
  val pickupLocation: String = "Block 33 · 6th Floor",
  val pickupPreference: String = "Pickup ASAP",
  val pickupCounter: String = "Counter 1",
  val canteenId: String = "canteen_33",
  val orderPlacedAt: String = "",
  val confirmedAt: String = "",
  val preparingAt: String = "",
  val readyAt: String = "",
  val completedAt: String = "",
  val acceptedAt: String = "",
  val rejectedAt: String = "",
  val rejectionReason: String = "",
  val refundStatus: String = "",
  val fulfillmentType: FulfillmentType = FulfillmentType.TAKEAWAY,
  val packagingCharge: Int = 0,
  val taxAmount: Int = 0,
  val itemsSubtotal: Int = 0,
  val isScheduled: Boolean = false,
  val scheduledPickupTime: String = "",
  val placedEpoch: Long = 0L,
  val orderDate: String = "",
)

data class NotificationItem(
  val id: String,
  val title: String,
  val message: String,
  val timeAgo: String,
  val isUnread: Boolean = true,
  val timestamp: Long = System.currentTimeMillis(),
  val arrivalTimeFormatted: String = "",
)

enum class BottomNavTab(val label: String) {
  HOME("Home"),
  MENU("Menu"),
  ORDERS("Orders"),
  AI_ASSISTANT("MunchMate"),
  CANTEENS("Menu"),
  PROFILE("Profile"),
  CART("Cart"),
}

object SampleFoodData {
  val canteen_33 = CampusBlockCanteen(
    id = "canteen_33",
    name = "Govinda's Kitchen",
    betweenBlocks = "Block 33",
    floorInfo = "6th Floor",
    primaryBlocks = listOf(33),
    specialty = "Pure Veg Meals, Thali & Sweets",
    crowdStatus = CrowdStatus.LOW,
    avgWaitMinutes = 6,
    icon = "🍲",
    isOpen = true,
    timings = "7:00 AM – 10:00 PM",
    ownerName = "Govinda Das",
    quickOrderItems = emptyList(),
    popularItems = emptyList(),
    readyIn10Items = emptyList(),
  )

  val canteen_34 = CampusBlockCanteen(
    id = "canteen_34",
    name = "Talk of the town",
    betweenBlocks = "Block 34",
    floorInfo = "6th Floor",
    primaryBlocks = listOf(34),
    specialty = "North Indian, Rolls & Fast Food",
    crowdStatus = CrowdStatus.MODERATE,
    avgWaitMinutes = 7,
    icon = "🌯",
    isOpen = true,
    timings = "7:30 AM – 10:00 PM",
    ownerName = "Ramesh Sharma",
    quickOrderItems = emptyList(),
    popularItems = emptyList(),
    readyIn10Items = emptyList(),
  )

  val canteen_38 = CampusBlockCanteen(
    id = "canteen_38",
    name = "Cafe",
    betweenBlocks = "Block 38 (Central Link)",
    floorInfo = "5th Floor",
    primaryBlocks = listOf(38),
    specialty = "Espresso, Beverages & Continental Bites",
    crowdStatus = CrowdStatus.LOW,
    avgWaitMinutes = 5,
    icon = "☕",
    isOpen = true,
    timings = "8:00 AM – 9:00 PM",
    ownerName = "Vikram Malhotra",
    quickOrderItems = emptyList(),
    popularItems = emptyList(),
    readyIn10Items = emptyList(),
  )

  val canteen_25 = CampusBlockCanteen(
    id = "canteen_25",
    name = "Gupta Canteen",
    betweenBlocks = "Block 25",
    floorInfo = "6th Floor",
    primaryBlocks = listOf(25),
    specialty = "Chaat, Samosa & North Indian Snacks",
    crowdStatus = CrowdStatus.MODERATE,
    avgWaitMinutes = 5,
    icon = "🥟",
    isOpen = true,
    timings = "7:30 AM – 9:30 PM",
    ownerName = "Rajesh Gupta",
    quickOrderItems = emptyList(),
    popularItems = emptyList(),
    readyIn10Items = emptyList(),
  )

  val canteen_26 = CampusBlockCanteen(
    id = "canteen_26",
    name = "Govinda's Kitchen",
    betweenBlocks = "Block 26",
    floorInfo = "6th Floor",
    primaryBlocks = listOf(26),
    specialty = "South Indian Breakfast, Dosa & Filter Kaapi",
    crowdStatus = CrowdStatus.LOW,
    avgWaitMinutes = 4,
    icon = "🍛",
    isOpen = true,
    timings = "7:00 AM – 10:00 PM",
    ownerName = "Govinda Das",
    quickOrderItems = emptyList(),
    popularItems = emptyList(),
    readyIn10Items = emptyList(),
  )

  val canteen_28 = CampusBlockCanteen(
    id = "canteen_28",
    name = "Vishal Dhaba & Cafe",
    betweenBlocks = "Block 28",
    floorInfo = "5th Floor",
    primaryBlocks = listOf(28),
    specialty = "Dhaba Style Parathas & Meals",
    crowdStatus = CrowdStatus.LOW,
    avgWaitMinutes = 6,
    icon = "🥘",
    isOpen = true,
    timings = "8:00 AM – 10:00 PM",
    ownerName = "Vishal Singh",
    quickOrderItems = emptyList(),
    popularItems = emptyList(),
    readyIn10Items = emptyList(),
  )

  val canteen_29 = CampusBlockCanteen(
    id = "canteen_29",
    name = "P.R Terrace Cafe",
    betweenBlocks = "Block 29",
    floorInfo = "5th Floor",
    primaryBlocks = listOf(29),
    specialty = "Terrace Cafe, Burgers, Pasta & Shakes",
    crowdStatus = CrowdStatus.LOW,
    avgWaitMinutes = 5,
    icon = "🍕",
    isOpen = true,
    timings = "8:00 AM – 9:30 PM",
    ownerName = "Prashant Roy",
    quickOrderItems = emptyList(),
    popularItems = emptyList(),
    readyIn10Items = emptyList(),
  )

  // Active campus canteens list — ONLY the 7 specified locations
  val campusCanteens: List<CampusBlockCanteen> = listOf(
    canteen_33,
    canteen_34,
    canteen_38,
    canteen_25,
    canteen_26,
    canteen_28,
    canteen_29,
  )

  // Aliases for backward compatibility
  val canteen26_27 get() = canteen_26
  val canteen_central get() = canteen_38
  val canteen_hostel get() = canteen_25
  val canteen_library get() = canteen_29
  val canteen_sports get() = canteen_28

  // Default fallback compatibility lists
  val quickOrderItems: List<FoodItem> = emptyList()
  val popularItems: List<FoodItem> = emptyList()
  val readyIn10Items: List<FoodItem> = emptyList()
  val allItems: List<FoodItem> = emptyList()
}
