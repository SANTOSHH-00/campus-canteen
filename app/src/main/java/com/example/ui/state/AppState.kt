package com.example.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.BottomNavTab
import com.example.data.CampusBlockCanteen
import com.example.data.CartItem
import com.example.data.FoodAddon
import com.example.data.FoodCategory
import com.example.data.FoodItem
import com.example.data.MenuDietaryFilter
import com.example.data.MenuSortOption
import com.example.data.NotificationItem
import com.example.data.OrderRecord
import com.example.data.OrderStatus
import com.example.data.SampleFoodData
import com.example.data.firebase.FirestoreRepository
import com.example.data.firebase.NotificationDocument
import com.example.data.firebase.OrderDocument
import com.example.data.session.SessionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UserProfile(
  val name: String,
  val registrationNumber: String = "",
  val department: String = "",
  val email: String,
  val avatarId: String = "scholar", // scholar, coder, creator, athlete, foodie
  val phone: String = "",
  val course: String = "",
)

data class UsualOrderInfo(
  val itemName: String,
  val count: Int,
  val price: Int,
  val foodItem: FoodItem? = null,
)

class CanteenAppState(val context: android.content.Context? = null) {
  private val firestoreRepo = FirestoreRepository()

  // ── Intermediate Block Canteen State ───────────────────────────────────────
  var allCanteens by mutableStateOf<List<CampusBlockCanteen>>(SampleFoodData.campusCanteens)
  var selectedCanteen by mutableStateOf<CampusBlockCanteen>(SampleFoodData.canteen_33)
  var studentCurrentBlock by mutableStateOf<Int?>(33)

  private var canteenObservationJob: Job? = null
  private var itemsObservationJob: Job? = null
  private var allCanteensObservationJob: Job? = null
  private var studentOrdersJob: Job? = null

  private fun startAllCanteensObservation() {
    allCanteensObservationJob?.cancel()
    allCanteensObservationJob = CoroutineScope(Dispatchers.IO).launch {
      try {
        firestoreRepo.observeCanteens().collect { remoteDocs ->
          if (remoteDocs.isNotEmpty()) {
            val mapped = remoteDocs.map { doc ->
              val item = doc.toCampusBlockCanteen(emptyList())
              val finalImg = if (doc.imageUrl.isNotBlank()) doc.imageUrl else SessionManager.getCanteenImage(doc.id)
              val finalTimings = if (doc.timings.isNotBlank() && doc.timings != "7:00 AM – 10:00 PM") doc.timings else (SessionManager.getCanteenTimings(doc.id) ?: doc.timings)
              item.copy(
                imageUrl = finalImg,
                timings = finalTimings,
              )
            }
            allCanteens = mapped

            // If selected canteen has remote updates, keep it updated; otherwise switch to first remote
            val remoteSelected = mapped.find { it.id == selectedCanteen.id } ?: mapped.firstOrNull()
            if (remoteSelected != null) {
              selectedCanteen = selectedCanteen.copy(
                id = remoteSelected.id,
                name = remoteSelected.name,
                betweenBlocks = remoteSelected.betweenBlocks,
                floorInfo = remoteSelected.floorInfo,
                primaryBlocks = remoteSelected.primaryBlocks,
                specialty = remoteSelected.specialty,
                avgWaitMinutes = remoteSelected.avgWaitMinutes,
                icon = remoteSelected.icon,
                isOpen = remoteSelected.isOpen,
                closeReason = remoteSelected.closeReason,
                imageUrl = remoteSelected.imageUrl,
                timings = remoteSelected.timings,
              )
            }
          }
        }
      } catch (e: Exception) {
        android.util.Log.w("CanteenAppState", "observeCanteens error: ${e.message}")
      }
    }
  }

  private fun startCanteenObservation(canteenId: String) {
    canteenObservationJob?.cancel()
    itemsObservationJob?.cancel()

    canteenObservationJob = CoroutineScope(Dispatchers.IO).launch {
      try {
        firestoreRepo.observeCanteen(canteenId).collect { canteenDoc ->
          if (canteenDoc != null && selectedCanteen.id == canteenId) {
            selectedCanteen = selectedCanteen.copy(
              name = canteenDoc.name.ifBlank { selectedCanteen.name },
              isOpen = canteenDoc.isOpen,
              closeReason = canteenDoc.closeReason.ifBlank { null },
              betweenBlocks = canteenDoc.location.ifBlank { selectedCanteen.betweenBlocks },
              floorInfo = canteenDoc.floorInfo.ifBlank { selectedCanteen.floorInfo },
              specialty = canteenDoc.specialty.ifBlank { selectedCanteen.specialty },
              imageUrl = if (canteenDoc.imageUrl.isNotBlank()) canteenDoc.imageUrl else (SessionManager.getCanteenImage(canteenId) ?: selectedCanteen.imageUrl),
              timings = if (canteenDoc.timings.isNotBlank()) canteenDoc.timings else (SessionManager.getCanteenTimings(canteenId) ?: selectedCanteen.timings),
            )
          }
        }
      } catch (e: Exception) {}
    }

    itemsObservationJob = CoroutineScope(Dispatchers.IO).launch {
      try {
        firestoreRepo.observeItems(canteenId).collect { itemDocs ->
          if (selectedCanteen.id == canteenId) {
            val availableDocs = itemDocs.filter { it.available }
            val foodItems = availableDocs.map { it.toFoodItem() }
            val quick = foodItems.filter { it.category == FoodCategory.QUICK_ORDER }.ifEmpty { foodItems.filter { it.prepMinutes <= 5 } }
            val popular = foodItems.filter { it.category == FoodCategory.POPULAR }.ifEmpty { foodItems.take(8) }
            val readyIn10 = foodItems.filter { it.category == FoodCategory.READY_UNDER_10 }.ifEmpty { foodItems.filter { it.prepMinutes <= 10 } }

            withContext(Dispatchers.Main) {
              selectedCanteen = selectedCanteen.copy(
                rawItems = foodItems,
                quickOrderItems = quick,
                popularItems = popular,
                readyIn10Items = readyIn10,
              )
            }
          }
        }
      } catch (e: Exception) {}
    }
  }

  fun selectCanteen(canteen: CampusBlockCanteen) {
    selectedCanteen = canteen
    startCanteenObservation(canteen.id)
  }

  fun selectCanteenByBlock(blockNumber: Int) {
    studentCurrentBlock = blockNumber
    val closest = allCanteens.find { it.primaryBlocks.contains(blockNumber) }
      ?: when (blockNumber) {
        33 -> allCanteens.find { it.id == "canteen_33" } ?: SampleFoodData.canteen_33
        34 -> allCanteens.find { it.id == "canteen_34" } ?: SampleFoodData.canteen_34
        38 -> allCanteens.find { it.id == "canteen_38" } ?: SampleFoodData.canteen_38
        25 -> allCanteens.find { it.id == "canteen_25" } ?: SampleFoodData.canteen_25
        26, 27 -> allCanteens.find { it.id == "canteen_26" } ?: SampleFoodData.canteen_26
        28 -> allCanteens.find { it.id == "canteen_28" } ?: SampleFoodData.canteen_28
        29 -> allCanteens.find { it.id == "canteen_29" } ?: SampleFoodData.canteen_29
        else -> allCanteens.firstOrNull() ?: SampleFoodData.canteen_33
      }
    selectedCanteen = closest
    startCanteenObservation(closest.id)
  }

  // Navigation
  var currentTab by mutableStateOf(BottomNavTab.HOME)
  var isBottomBarVisible by mutableStateOf(true)

  // Current Student / User State (null indicates Guest Mode)
  var currentUser by mutableStateOf<UserProfile?>(null)

  val isGuest: Boolean
    get() = currentUser == null

  val displayUserName: String
    get() = currentUser?.name ?: "Guest"

  fun loginUser(profile: UserProfile) {
    currentUser = profile
    SessionManager.saveStudentSession(profile)

    // Sync student to Cloud Firestore and observe their live orders
    studentOrdersJob?.cancel()
    studentOrdersJob = CoroutineScope(Dispatchers.IO).launch {
      try {
        val userDoc = com.example.data.firebase.UserDocument(
          uid = profile.registrationNumber.ifBlank { profile.email },
          name = profile.name,
          email = profile.email,
          role = "student",
          registrationNumber = profile.registrationNumber,
          department = profile.department,
          avatarId = profile.avatarId,
          createdAt = System.currentTimeMillis(),
        )
        firestoreRepo.saveUser(userDoc)

        // Observe student orders from Firestore
        firestoreRepo.observeStudentOrders(userDoc.uid).collect { firestoreOrders ->
          val converted = firestoreOrders.map { it.toOrderRecord() }
          withContext(Dispatchers.Main) {
            for (item in converted) {
              val idx = orders.indexOfFirst { it.id == item.id }
              if (idx >= 0) {
                val oldStatus = orders[idx].status
                orders[idx] = item
                if (oldStatus != item.status) {
                  context?.let { ctx ->
                    val matchingDoc = firestoreOrders.find { it.orderId == item.id }
                    com.example.util.OrderNotificationHelper.showOrderStatusNotification(
                      context = ctx,
                      tokenNumber = item.tokenNumber,
                      status = item.status.name,
                      counter = matchingDoc?.pickupCounter ?: "Counter 1",
                    )
                  }
                }
              } else {
                orders.add(0, item)
              }
              if (selectedOrderForTracking?.id == item.id) {
                selectedOrderForTracking = item
              }
            }
          }
        }
      } catch (e: Exception) {
        // Safe fallback for offline mode
      }
    }
  }

  fun logoutUser() {
    studentOrdersJob?.cancel()
    studentOrdersJob = null
    currentUser = null
    orders.clear()
    SessionManager.clearStudentSession()
  }

  fun updateAvatar(newAvatarId: String) {
    currentUser = currentUser?.copy(avatarId = newAvatarId)
    val user = currentUser
    if (user != null) {
      SessionManager.saveStudentSession(user)
      CoroutineScope(Dispatchers.IO).launch {
        try {
          val userDoc = com.example.data.firebase.UserDocument(
            uid = user.registrationNumber.ifBlank { user.email },
            name = user.name,
            email = user.email,
            role = "student",
            registrationNumber = user.registrationNumber,
            department = user.department,
            avatarId = newAvatarId,
            phone = user.phone,
            course = user.course,
          )
          firestoreRepo.saveUser(userDoc)
        } catch (e: Exception) {}
      }
    }
  }

  fun updateStudentProfile(name: String, course: String, department: String, rollNo: String, phone: String) {
    val updated = (currentUser ?: UserProfile(name = name, email = "student@lpu.in")).copy(
      name = name,
      course = course,
      department = department,
      registrationNumber = rollNo,
      phone = phone,
    )
    currentUser = updated
    SessionManager.saveStudentSession(updated)
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val userDoc = com.example.data.firebase.UserDocument(
          uid = updated.registrationNumber.ifBlank { updated.email },
          name = updated.name,
          email = updated.email,
          role = "student",
          registrationNumber = updated.registrationNumber,
          department = updated.department,
          avatarId = updated.avatarId,
          phone = updated.phone,
          course = updated.course,
        )
        firestoreRepo.saveUser(userDoc)
      } catch (e: Exception) {}
    }
  }



  // Quick Order filter in minutes (5, 10, 15, 20). 10 is highlighted by default per Image 2.
  var selectedQuickFilterMinutes by mutableIntStateOf(10)

  // Break Timer State (per Image 1: 12:30 PM – 1:00 PM, 28 min left)
  var breakStartTime by mutableStateOf("12:30 PM")
  var breakEndTime by mutableStateOf("1:00 PM")
  var breakMinutesLeft by mutableIntStateOf(28)

  // Product Detail Selection State
  var selectedFoodForDetail by mutableStateOf<FoodItem?>(null)

  fun openFoodDetail(item: FoodItem) {
    selectedFoodForDetail = item
  }

  fun closeFoodDetail() {
    selectedFoodForDetail = null
  }

  // Favorites
  val favoriteItemIds = mutableStateListOf<String>()

  fun toggleFavorite(itemId: String) {
    if (favoriteItemIds.contains(itemId)) {
      favoriteItemIds.remove(itemId)
    } else {
      favoriteItemIds.add(itemId)
    }
  }

  fun isItemFavorite(itemId: String): Boolean {
    return favoriteItemIds.contains(itemId)
  }

  // Cart State
  val cartItems = mutableStateListOf<CartItem>()
  var isPlacingOrder by mutableStateOf(false)

  val totalCartCount: Int
    get() = cartItems.sumOf { it.quantity }

  val subtotal: Int
    get() = cartItems.sumOf { it.totalPrice }

  val taxAmount: Int
    get() = if (subtotal > 0) (subtotal * 0.05).toInt().coerceAtLeast(5) else 0

  val grandTotal: Int
    get() = if (subtotal > 0) subtotal + taxAmount else 0

  private fun persistCartToSession() {
    try {
      val jsonArray = org.json.JSONArray()
      for (item in cartItems) {
        val obj = org.json.JSONObject()
        obj.put("foodId", item.foodItem.id)
        obj.put("foodName", item.foodItem.name)
        obj.put("foodPrice", item.foodItem.price)
        obj.put("foodCategory", item.foodItem.category.name)
        obj.put("foodDescription", item.foodItem.description)
        obj.put("foodImageUrl", item.foodItem.imageUrl)
        obj.put("foodPrepMinutes", item.foodItem.prepMinutes)
        obj.put("foodPrepTime", item.foodItem.prepTime)
        obj.put("foodImageLabel", item.foodItem.imageLabel)
        obj.put("quantity", item.quantity)
        obj.put("selectedOption", item.selectedOption ?: "")

        val addonsArray = org.json.JSONArray()
        for (addon in item.selectedAddons) {
          val addonObj = org.json.JSONObject()
          addonObj.put("name", addon.name)
          addonObj.put("price", addon.price)
          addonsArray.put(addonObj)
        }
        obj.put("selectedAddons", addonsArray)
        jsonArray.put(obj)
      }
      SessionManager.saveCartJson(jsonArray.toString())
    } catch (e: Exception) {
      android.util.Log.w("CanteenAppState", "Failed persisting cart: ${e.message}")
    }
  }

  fun loadCartFromSession() {
    try {
      val rawJson = SessionManager.getCartJson() ?: return
      val jsonArray = org.json.JSONArray(rawJson)
      val loaded = mutableListOf<CartItem>()
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        val foodId = obj.optString("foodId")
        val foodName = obj.optString("foodName")
        val foodPrice = obj.optInt("foodPrice", 0)
        val catStr = obj.optString("foodCategory", "SNACKS")
        val category = try { FoodCategory.valueOf(catStr) } catch (_: Exception) { FoodCategory.SNACKS }
        val desc = obj.optString("foodDescription")
        val imgUrl = obj.optString("foodImageUrl")
        val prep = obj.optInt("foodPrepMinutes", 10)
        val prepTime = obj.optString("foodPrepTime", "${prep}m")
        val imgLabel = obj.optString("foodImageLabel", foodName)
        val qty = obj.optInt("quantity", 1)
        val opt = obj.optString("selectedOption").ifBlank { null }

        val addonsList = mutableListOf<FoodAddon>()
        val addonsArray = obj.optJSONArray("selectedAddons")
        if (addonsArray != null) {
          for (j in 0 until addonsArray.length()) {
            val addonObj = addonsArray.getJSONObject(j)
            addonsList.add(FoodAddon(name = addonObj.optString("name"), price = addonObj.optInt("price", 0)))
          }
        }

        val food = FoodItem(
          id = foodId,
          name = foodName,
          price = foodPrice,
          prepTime = prepTime,
          prepMinutes = prep,
          category = category,
          imageLabel = imgLabel,
          imageUrl = imgUrl,
          description = desc,
        )
        loaded.add(CartItem(foodItem = food, quantity = qty, selectedOption = opt, selectedAddons = addonsList))
      }
      if (loaded.isNotEmpty()) {
        cartItems.clear()
        cartItems.addAll(loaded)
      }
    } catch (e: Exception) {
      android.util.Log.w("CanteenAppState", "Failed restoring cart: ${e.message}")
    }
  }

  // Orders State
  val orders = mutableStateListOf<OrderRecord>()

  // Order Tracking Screen State (Images 2 & 3)
  var selectedOrderForTracking by mutableStateOf<OrderRecord?>(null)

  fun openOrderTracking(order: OrderRecord) {
    selectedOrderForTracking = order
  }

  fun closeOrderTracking() {
    selectedOrderForTracking = null
  }

  // Notifications
  val notifications = mutableStateListOf<NotificationItem>()

  // Modern Floating Cart Notification Banner State
  var cartNotificationMessage by mutableStateOf<String?>(null)
  var cartNotificationItemName by mutableStateOf<String?>(null)

  fun notifyCartAdded(itemName: String) {
    cartNotificationItemName = itemName
    cartNotificationMessage = "Added $itemName"
  }

  fun dismissCartNotification() {
    cartNotificationMessage = null
  }

  fun addToCart(item: FoodItem) {
    val existing = cartItems.indexOfFirst { it.foodItem.id == item.id && it.selectedOption == null && it.selectedAddons.isEmpty() }
    if (existing >= 0) {
      val current = cartItems[existing]
      cartItems[existing] = current.copy(quantity = current.quantity + 1)
    } else {
      cartItems.add(CartItem(foodItem = item, quantity = 1))
    }
    notifyCartAdded(item.name)
    persistCartToSession()
  }

  fun addCustomizedToCart(
    item: FoodItem,
    quantity: Int,
    selectedOption: String?,
    selectedAddons: List<FoodAddon>,
  ) {
    val existing = cartItems.indexOfFirst {
      it.foodItem.id == item.id &&
        it.selectedOption == selectedOption &&
        it.selectedAddons == selectedAddons
    }
    if (existing >= 0) {
      val current = cartItems[existing]
      cartItems[existing] = current.copy(quantity = current.quantity + quantity)
    } else {
      cartItems.add(
        CartItem(
          foodItem = item,
          quantity = quantity,
          selectedOption = selectedOption,
          selectedAddons = selectedAddons,
        )
      )
    }
    notifyCartAdded(item.name)
    persistCartToSession()
  }

  fun decreaseQuantity(item: FoodItem) {
    val index = cartItems.indexOfFirst { it.foodItem.id == item.id }
    if (index >= 0) {
      val current = cartItems[index]
      if (current.quantity > 1) {
        cartItems[index] = current.copy(quantity = current.quantity - 1)
      } else {
        cartItems.removeAt(index)
      }
      persistCartToSession()
    }
  }

  fun decreaseCartItem(cartItem: CartItem) {
    val index = cartItems.indexOf(cartItem)
    if (index >= 0) {
      if (cartItem.quantity > 1) {
        cartItems[index] = cartItem.copy(quantity = cartItem.quantity - 1)
      } else {
        cartItems.removeAt(index)
      }
      persistCartToSession()
    }
  }

  fun increaseCartItem(cartItem: CartItem) {
    val index = cartItems.indexOf(cartItem)
    if (index >= 0) {
      cartItems[index] = cartItem.copy(quantity = cartItem.quantity + 1)
      persistCartToSession()
    }
  }

  fun removeCartItem(cartItem: CartItem) {
    cartItems.remove(cartItem)
    persistCartToSession()
  }

  fun removeFromCart(item: FoodItem) {
    cartItems.removeAll { it.foodItem.id == item.id }
    persistCartToSession()
  }

  fun clearCart() {
    cartItems.clear()
    SessionManager.clearCartJson()
  }

  // Category filter state for MenuScreen (e.g. set by Quick Order 'See All')
  var selectedMenuCategory by mutableStateOf<FoodCategory?>(null)

  // Full-screen right-sliding filter drawer state
  var isMenuFilterOpen by mutableStateOf(false)
  var menuFilterSort by mutableStateOf(MenuSortOption.RECOMMENDED)
  var menuFilterDietary by mutableStateOf(MenuDietaryFilter.ALL)
  var menuFilterMaxMinutes by mutableStateOf<Int?>(null)
  var menuFilterMaxPrice by mutableStateOf<Int?>(null)
  var menuFilterMinRating by mutableStateOf<Double?>(null)

  val isAnyMenuFilterActive: Boolean
    get() = selectedMenuCategory != null ||
            menuFilterDietary != MenuDietaryFilter.ALL ||
            menuFilterSort != MenuSortOption.RECOMMENDED ||
            menuFilterMaxMinutes != null ||
            menuFilterMaxPrice != null ||
            menuFilterMinRating != null

  val activeMenuFilterCount: Int
    get() {
      var count = 0
      if (selectedMenuCategory != null) count++
      if (menuFilterDietary != MenuDietaryFilter.ALL) count++
      if (menuFilterSort != MenuSortOption.RECOMMENDED) count++
      if (menuFilterMaxMinutes != null) count++
      if (menuFilterMaxPrice != null) count++
      if (menuFilterMinRating != null) count++
      return count
    }

  fun resetAllMenuFilters() {
    selectedMenuCategory = null
    menuFilterDietary = MenuDietaryFilter.ALL
    menuFilterSort = MenuSortOption.RECOMMENDED
    menuFilterMaxMinutes = null
    menuFilterMaxPrice = null
    menuFilterMinRating = null
  }

  // YOUR USUAL Banner state & computation
  var isUsualBannerDismissed by mutableStateOf(false)

  fun dismissUsualBanner() {
    isUsualBannerDismissed = true
  }

  val usualOrderInfo: UsualOrderInfo?
    get() {
      if (isUsualBannerDismissed) return null
      val itemCounts = mutableMapOf<String, Int>()
      orders.forEach { order ->
        order.items.forEach { cartItem ->
          val name = cartItem.foodItem.name.trim()
          if (name.isNotBlank()) {
            itemCounts[name] = (itemCounts[name] ?: 0) + cartItem.quantity
          }
        }
      }
      val topEntry = itemCounts.maxByOrNull { it.value }
      if (topEntry != null && topEntry.value >= 3) {
        val matchedItem = selectedCanteen.allItems.firstOrNull { it.name.equals(topEntry.key, ignoreCase = true) }
          ?: selectedCanteen.quickOrderItems.firstOrNull { it.name.equals(topEntry.key, ignoreCase = true) }
          ?: selectedCanteen.popularItems.firstOrNull { it.name.equals(topEntry.key, ignoreCase = true) }

        if (matchedItem != null) {
          return UsualOrderInfo(
            itemName = matchedItem.name,
            count = topEntry.value,
            price = matchedItem.price,
            foodItem = matchedItem,
          )
        }
      }

      // Default featured usual item from the current canteen (e.g. Veg Sandwich, Dosa, or top item)
      val defaultItem = selectedCanteen.allItems.firstOrNull {
        it.name.contains("Sandwich", ignoreCase = true) ||
        it.name.contains("Burger", ignoreCase = true) ||
        it.name.contains("Dosa", ignoreCase = true) ||
        it.name.contains("Coffee", ignoreCase = true)
      } ?: selectedCanteen.popularItems.firstOrNull()
        ?: selectedCanteen.quickOrderItems.firstOrNull()
        ?: selectedCanteen.allItems.firstOrNull()

      return defaultItem?.let {
        UsualOrderInfo(
          itemName = it.name,
          count = 4,
          price = it.price,
          foodItem = it,
        )
      }
    }

  // "YOUR USUAL?" Reorder action - Guarantees only the exact displayed item is added
  fun reorderUsual(usualInfo: UsualOrderInfo? = null) {
    try {
      val resolvedItem: FoodItem? = usualInfo?.foodItem
        ?: if (usualInfo != null) {
          selectedCanteen.allItems.firstOrNull { it.name.equals(usualInfo.itemName, ignoreCase = true) }
            ?: selectedCanteen.quickOrderItems.firstOrNull { it.name.equals(usualInfo.itemName, ignoreCase = true) }
            ?: selectedCanteen.popularItems.firstOrNull { it.name.equals(usualInfo.itemName, ignoreCase = true) }
        } else {
          usualOrderInfo?.foodItem
        }

      if (resolvedItem == null) return

      addToCart(resolvedItem)
    } catch (e: Exception) {
      android.util.Log.e("AppState", "Safe reorderUsual handled error: ${e.message}", e)
    }
  }

  fun loadNotificationsFromSession() {
    try {
      val raw = SessionManager.getSavedNotificationsJson()
      val loaded = mutableListOf<NotificationItem>()
      if (raw != null) {
        val array = org.json.JSONArray(raw)
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          val id = obj.optString("id", java.util.UUID.randomUUID().toString())
          val title = obj.optString("title", "QuickBite Update")
          val message = obj.optString("message", "")
          val ts = obj.optLong("timestamp", System.currentTimeMillis())
          val isUnread = obj.optBoolean("isUnread", true)

          val diffMin = ((System.currentTimeMillis() - ts) / (1000 * 60)).coerceAtLeast(0)
          val timeAgo = when {
            diffMin < 1 -> "Just now"
            diffMin < 60 -> "${diffMin}m ago"
            diffMin < 1440 -> "${diffMin / 60}h ago"
            else -> "${diffMin / 1440}d ago"
          }
          loaded.add(NotificationItem(id = id, title = title, message = message, timeAgo = timeAgo, isUnread = isUnread))
        }
      }
      if (loaded.isEmpty()) {
        loaded.add(
          NotificationItem(
            id = "welcome_system_1",
            title = "Welcome to QuickBite! 👋",
            message = "Skip the line! Order your favorite meals from campus canteens and pick them up quickly.",
            timeAgo = "Today",
            isUnread = false,
          )
        )
      }
      notifications.clear()
      notifications.addAll(loaded)
    } catch (e: Exception) {
      android.util.Log.e("AppState", "Failed to load notifications: ${e.message}")
    }
  }

  fun syncNotificationsToSession() {
    try {
      val array = org.json.JSONArray()
      for (notif in notifications) {
        val obj = org.json.JSONObject().apply {
          put("id", notif.id)
          put("title", notif.title)
          put("message", notif.message)
          put("isUnread", notif.isUnread)
          put("timestamp", System.currentTimeMillis())
        }
        array.put(obj)
      }
      SessionManager.saveAllNotificationsJson(array.toString())
    } catch (e: Exception) {
      android.util.Log.e("AppState", "Failed to sync notifications: ${e.message}")
    }
  }

  fun addNotification(title: String, message: String, isUnread: Boolean = true) {
    val notif = NotificationItem(
      id = java.util.UUID.randomUUID().toString(),
      title = title,
      message = message,
      timeAgo = "Just now",
      isUnread = isUnread,
    )
    notifications.add(0, notif)
    syncNotificationsToSession()
  }

  // Pickup Preference State
  var pickupPreferenceType by mutableStateOf("Pickup") // Pickup vs Dine-in
  var isPickupAsap by mutableStateOf(true)
  var customPickupTime by mutableStateOf<String?>(null)

  val estimatedReadyTimeFormatted: String
    get() {
      if (!isPickupAsap && customPickupTime != null) {
        return customPickupTime!!
      }
      val maxPrepMinutes = cartItems.maxOfOrNull { it.foodItem.prepMinutes } ?: 7
      val cal = java.util.Calendar.getInstance()
      cal.add(java.util.Calendar.MINUTE, maxPrepMinutes)
      return SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
    }

  // Place Order action
  fun placeOrder(): OrderRecord? {
    if (cartItems.isEmpty()) return null
    if (!selectedCanteen.isOpen) return null

    // Generate unique 3-digit token & unique collision-safe order ID
    val token = String.format(Locale.getDefault(), "%03d", (100..999).random())
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val now = Date()
    val orderTime = timeFormat.format(now)

    val estimatedReady = estimatedReadyTimeFormatted
    val uniqueOrderId = "ORD-${System.currentTimeMillis()}-${(1000..9999).random()}"

    val newOrder = OrderRecord(
      id = uniqueOrderId,
      tokenNumber = token,
      items = cartItems.map { it.copy() },
      totalPrice = grandTotal,
      status = OrderStatus.NEW,
      orderTime = "Today, $orderTime",
      estimatedReadyTime = estimatedReady,
      pickupCanteenName = selectedCanteen.name,
      pickupLocation = "${selectedCanteen.betweenBlocks} (${selectedCanteen.floorInfo})",
      pickupPreference = if (isPickupAsap) "Pickup ASAP" else "Pickup at $estimatedReady",
      canteenId = selectedCanteen.id,
      orderPlacedAt = orderTime,
      confirmedAt = orderTime,
    )
    orders.add(0, newOrder)
    cartItems.clear()

    // Sync order to backend/MongoDB in background
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val studentUid = currentUser?.registrationNumber?.ifBlank { currentUser?.email }?.ifBlank { null }
          ?: "student_guest"
        val assignedCounter = if ((orders.size % 2) == 0) "Counter 1" else "Counter 2"
        val orderDoc = OrderDocument.fromOrderRecord(
          record = newOrder,
          studentId = studentUid,
          studentName = displayUserName,
          canteenId = selectedCanteen.id,
          studentPhone = currentUser?.phone ?: "",
          studentCourse = currentUser?.course ?: "",
          pickupCounter = assignedCounter,
        )
        firestoreRepo.createOrder(orderDoc)

        // Post notification to Firestore notifications collection
        val notifDoc = NotificationDocument(
          userId = studentUid,
          title = "Order Token #$token Placed",
          message = "Your order at ${selectedCanteen.name} has been placed successfully and is pending confirmation.",
          type = "ORDER_UPDATE",
          orderId = newOrder.id,
          canteenId = selectedCanteen.id,
          createdAt = System.currentTimeMillis(),
          read = false,
        )
        firestoreRepo.sendNotification(notifDoc)

        // Observe live status updates from owner for this specific order
        firestoreRepo.observeOrder(newOrder.id).collect { updatedDoc ->
          if (updatedDoc != null) {
            val updatedRecord = updatedDoc.toOrderRecord()
            withContext(Dispatchers.Main) {
              val idx = orders.indexOfFirst { it.id == updatedRecord.id }
              if (idx >= 0) {
                val oldStatus = orders[idx].status
                orders[idx] = updatedRecord
                if (oldStatus != updatedRecord.status) {
                  val tokenFormatted = if (updatedRecord.tokenNumber.startsWith("#")) updatedRecord.tokenNumber else "#${updatedRecord.tokenNumber}"
                  val counter = updatedDoc.pickupCounter.ifBlank { "Counter 1" }
                  val (notifTitle, notifMsg) = when (updatedRecord.status.name.uppercase()) {
                    "PREPARING" -> Pair(
                      "Order $tokenFormatted Update",
                      "Your order is being prepared by the canteen kitchen."
                    )
                    "READY" -> Pair(
                      "Order $tokenFormatted is Ready",
                      "Your order is ready for pickup at $counter. Please collect your food."
                    )
                    "PICKED_UP", "COMPLETED", "DELIVERED" -> Pair(
                      "Order $tokenFormatted Completed",
                      "Your order has been completed. Thank you for dining with QuickBite."
                    )
                    "CANCELLED" -> Pair(
                      "Order $tokenFormatted Cancelled",
                      "Your order has been cancelled by the canteen kitchen."
                    )
                    else -> Pair(
                      "Order $tokenFormatted Update",
                      "Your order status has been updated to ${updatedRecord.status.name}."
                    )
                  }

                  context?.let { ctx ->
                    com.example.util.OrderNotificationHelper.showOrderStatusNotification(
                      context = ctx,
                      tokenNumber = updatedRecord.tokenNumber,
                      status = updatedRecord.status.name,
                      counter = counter,
                    )
                  }

                  addNotification(notifTitle, notifMsg)
                }
              }
              if (selectedOrderForTracking?.id == updatedRecord.id) {
                selectedOrderForTracking = updatedRecord
              }
            }
          }
        }
      } catch (e: Exception) {}
    }

    return newOrder
  }

  fun updateBreakSchedule(start: String, end: String, minutesRemaining: Int) {
    breakStartTime = start
    breakEndTime = end
    breakMinutesLeft = minutesRemaining
  }

  fun markNotificationsRead() {
    for (i in notifications.indices) {
      notifications[i] = notifications[i].copy(isUnread = false)
    }
    syncNotificationsToSession()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        for (notif in notifications) {
          firestoreRepo.markNotificationAsRead(notif.id)
        }
      } catch (e: Exception) {}
    }
  }

  fun updateCanteenDetails(
    canteenId: String,
    isOpen: Boolean,
    timings: String? = null,
    imageUrl: String? = null,
    closeReason: String? = null,
  ) {
    SessionManager.saveCanteenProfile(
      canteenId = canteenId,
      imageUrl = imageUrl,
      timings = timings,
      isOpen = isOpen,
      closeReason = closeReason,
    )

    allCanteens = allCanteens.map { canteen ->
      if (canteen.id == canteenId) {
        canteen.copy(
          isOpen = isOpen,
          timings = timings ?: canteen.timings,
          imageUrl = imageUrl ?: canteen.imageUrl,
          closeReason = closeReason ?: canteen.closeReason,
        )
      } else canteen
    }

    if (selectedCanteen.id == canteenId) {
      selectedCanteen = selectedCanteen.copy(
        isOpen = isOpen,
        timings = timings ?: selectedCanteen.timings,
        imageUrl = imageUrl ?: selectedCanteen.imageUrl,
        closeReason = closeReason ?: selectedCanteen.closeReason,
      )
    }

    CoroutineScope(Dispatchers.IO).launch {
      try {
        firestoreRepo.updateCanteenProfile(
          canteenId = canteenId,
          imageUrl = imageUrl,
          timings = timings,
          isOpen = isOpen,
          closeReason = closeReason ?: "",
        )
      } catch (e: Exception) {}
    }
  }

  init {
    // 1. Restore persisted student login session if available
    try {
      val savedStudent = SessionManager.getStudentSession()
      if (savedStudent != null) {
        currentUser = savedStudent
        loginUser(savedStudent)
      }
    } catch (e: Exception) {
      android.util.Log.e("CanteenAppState", "Failed to restore student session: ${e.message}", e)
    }

    // 2. Restore any persisted canteen overrides (timings, profile image, open/closed)
    try {
      allCanteens = allCanteens.map { canteen ->
        val savedImg = SessionManager.getCanteenImage(canteen.id)
        val savedTimings = SessionManager.getCanteenTimings(canteen.id)
        val savedOpen = SessionManager.isCanteenOpen(canteen.id, canteen.isOpen)
        val savedReason = SessionManager.getCanteenCloseReason(canteen.id)
        canteen.copy(
          imageUrl = savedImg ?: canteen.imageUrl,
          timings = savedTimings ?: canteen.timings,
          isOpen = savedOpen,
          closeReason = savedReason ?: canteen.closeReason,
        )
      }
      val sel = allCanteens.find { it.id == selectedCanteen.id }
      if (sel != null) selectedCanteen = sel
    } catch (e: Exception) {
      android.util.Log.e("CanteenAppState", "Failed to restore canteen overrides: ${e.message}", e)
    }

    // 3. Start canteen observations
    try {
      startAllCanteensObservation()
      startCanteenObservation(selectedCanteen.id)
    } catch (e: Exception) {
      android.util.Log.e("CanteenAppState", "Failed to start canteen observation: ${e.message}", e)
    }

    // 4. Restore persisted cart items
    try {
      loadCartFromSession()
    } catch (e: Exception) {
      android.util.Log.e("CanteenAppState", "Failed to restore cart: ${e.message}", e)
    }

    // 5. Restore persisted notifications
    try {
      loadNotificationsFromSession()
    } catch (e: Exception) {
      android.util.Log.e("CanteenAppState", "Failed to restore notifications: ${e.message}", e)
    }

    // 6. Listen to real-time order status updates for notifications
    CoroutineScope(Dispatchers.IO).launch {
      try {
        com.example.data.api.WebSocketManager.orderUpdates.collect { orderDto ->
          val status = orderDto.status.uppercase()
          val token = orderDto.tokenNumber.ifBlank { orderDto.orderId.takeLast(4) }
          val title = when (status) {
            "PREPARING" -> "Order #$token in Kitchen 👨‍🍳"
            "READY" -> "Order #$token Ready for Pickup! 🔔"
            "COMPLETED", "PICKED_UP" -> "Order #$token Picked Up ✓"
            else -> "Order #$token Status Update"
          }
          val msg = when (status) {
            "PREPARING" -> "Your meal is being prepared at ${orderDto.pickupCounter.ifBlank { "the counter" }}."
            "READY" -> "Your order is ready! Please collect it from ${orderDto.pickupCounter.ifBlank { "the counter" }}."
            "COMPLETED", "PICKED_UP" -> "Order picked up. Enjoy your food!"
            else -> "Order status: $status"
          }
          kotlinx.coroutines.withContext(Dispatchers.Main) {
            addNotification(title, msg, isUnread = true)
          }
        }
      } catch (e: Exception) {
        android.util.Log.w("CanteenAppState", "Order updates notification listener error: ${e.message}")
      }
    }
  }
}

@Composable
fun rememberCanteenAppState(): CanteenAppState {
  val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
  return remember { CanteenAppState(context = context) }
}
