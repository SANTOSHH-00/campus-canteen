package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.CanteenDocument
import com.example.data.firebase.FirestoreRepository
import com.example.data.firebase.ItemDocument
import com.example.data.firebase.ItemRepository
import com.example.data.firebase.NotificationDocument
import com.example.data.firebase.OrderDocument
import com.example.data.firebase.OwnerDocument
import com.example.data.firebase.OwnerRepository
import com.example.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OwnerDashboardMetrics(
  val newOrdersCount: Int = 0,
  val preparingOrdersCount: Int = 0,
  val readyOrdersCount: Int = 0,
  val todayOrdersCount: Int = 0,
  val todayRevenue: Int = 0,
)

sealed interface DashboardUiState {
  object Loading : DashboardUiState
  data class Ready(
    val owner: OwnerDocument,
    val canteen: CanteenDocument,
    val metrics: OwnerDashboardMetrics,
    val recentOrders: List<OrderDocument>,
    val lowStockItems: List<ItemDocument>,
  ) : DashboardUiState
  data class Error(val message: String) : DashboardUiState
}

open class OwnerDashboardViewModel(
  private val firestoreRepo: FirestoreRepository = FirestoreRepository(),
  private val itemRepo: ItemRepository = ItemRepository(),
  private val ownerRepo: OwnerRepository = OwnerRepository(),
  initialOwner: OwnerDocument? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
  val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

  private val _currentOwner = MutableStateFlow<OwnerDocument?>(initialOwner)
  val currentOwner: StateFlow<OwnerDocument?> = _currentOwner.asStateFlow()

  private val _canteen = MutableStateFlow<CanteenDocument?>(null)
  val canteen: StateFlow<CanteenDocument?> = _canteen.asStateFlow()

  private val _orders = MutableStateFlow<List<OrderDocument>>(emptyList())
  val orders: StateFlow<List<OrderDocument>> = _orders.asStateFlow()

  private val _items = MutableStateFlow<List<ItemDocument>>(emptyList())
  val items: StateFlow<List<ItemDocument>> = _items.asStateFlow()

  init {
    if (initialOwner != null && initialOwner.canteenAssigned && initialOwner.canteenId.isNotBlank()) {
      setupDashboard(initialOwner)
    } else {
      resolveOwnerAndCanteen()
    }
  }

  fun resolveOwnerAndCanteen() {
    viewModelScope.launch {
      _uiState.value = DashboardUiState.Loading

      val current = _currentOwner.value
        ?: OwnerRepository.activeSessionOwner
        ?: SessionManager.getOwnerSession()

      // If no valid session or not assigned to a canteen -> strictly deny access
      if (current == null || !current.canteenAssigned || current.canteenId.isBlank()) {
        _uiState.value = DashboardUiState.Error(
          "Access Denied: You must be an authorized canteen owner with an assigned canteen to access this dashboard."
        )
        return@launch
      }

      val profileResult = ownerRepo.getOwnerProfile(current.uid)
      if (profileResult.isSuccess) {
        val ownerDoc = profileResult.getOrThrow()
        if (ownerDoc.canteenAssigned && ownerDoc.canteenId.isNotBlank()) {
          _currentOwner.value = ownerDoc
          setupDashboard(ownerDoc)
        } else {
          _uiState.value = DashboardUiState.Error(
            "Access Denied: No canteen is assigned to your account. Please contact the administrator."
          )
        }
      } else {
        if (current.canteenAssigned && current.canteenId.isNotBlank()) {
          setupDashboard(current)
        } else {
          _uiState.value = DashboardUiState.Error(
            "Access Denied: Could not verify owner credentials. Please log in again."
          )
        }
      }
    }
  }

  fun setOwner(owner: OwnerDocument) {
    _currentOwner.value = owner
    setupDashboard(owner)
  }

  fun isGuestOrder(order: OrderDocument): Boolean {
    return order.studentId.contains("guest", ignoreCase = true) ||
        order.studentName.contains("guest", ignoreCase = true)
  }

  private fun setupDashboard(owner: OwnerDocument) {
    val canteenId = owner.canteenId
    viewModelScope.launch {
      // 1. Observe Canteen Document
      firestoreRepo.observeCanteen(canteenId).collect { remoteCanteen ->
        val effectiveCanteen = remoteCanteen ?: CanteenDocument(
          id = canteenId,
          name = if (owner.block.isNotBlank()) "Campus Canteen ${owner.block}" else "Campus Canteen",
          block = owner.block,
          location = "Block ${owner.block}",
          isOpen = true,
        )
        _canteen.value = effectiveCanteen
        recomputeUiState()
      }
    }

    viewModelScope.launch {
      // 2. Observe Canteen Orders in Real-Time (Filter out guest orders)
      firestoreRepo.observeCanteenOrders(canteenId).catch {}.collect { ordersList ->
        _orders.value = ordersList.filter { !isGuestOrder(it) }
        recomputeUiState()
      }
    }

    viewModelScope.launch {
      // 3. Observe Canteen Food Items in Real-Time
      itemRepo.observeCanteenItems(canteenId).catch {}.collect { itemsList ->
        _items.value = itemsList
        recomputeUiState()
      }
    }
  }

  private fun recomputeUiState() {
    val owner = _currentOwner.value ?: return
    val canteenDoc = _canteen.value ?: return
    val ordersList = _orders.value.filter { !isGuestOrder(it) }
    val itemsList = _items.value

    // Calculate metrics strictly from non-guest orders
    val newCount = ordersList.count { it.status.equals("NEW", ignoreCase = true) }
    val preparingCount = ordersList.count { it.status.equals("PREPARING", ignoreCase = true) }
    val readyCount = ordersList.count { it.status.equals("READY", ignoreCase = true) }

    // Today's orders & revenue calculation
    val todayDateString = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    val todayOrders = ordersList.filter { order ->
      val orderDateString = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(order.createdAt))
      orderDateString == todayDateString
    }
    val todayRevenue = todayOrders.sumOf { it.totalAmount }

    val metrics = OwnerDashboardMetrics(
      newOrdersCount = newCount,
      preparingOrdersCount = preparingCount,
      readyOrdersCount = readyCount,
      todayOrdersCount = todayOrders.size,
      todayRevenue = todayRevenue,
    )

    // Low stock items (stock <= 5 or out of stock)
    val lowStock = itemsList.filter { it.stock <= 5 || !it.available }

    // Recent orders: strictly show previous delivered/completed orders
    val recent = ordersList
      .filter { it.status.equals("COMPLETED", ignoreCase = true) || it.status.equals("DELIVERED", ignoreCase = true) }
      .sortedByDescending { it.updatedAt.coerceAtLeast(it.createdAt) }
      .take(10)

    _uiState.value = DashboardUiState.Ready(
      owner = owner,
      canteen = canteenDoc,
      metrics = metrics,
      recentOrders = recent,
      lowStockItems = lowStock,
    )
  }

  /**
   * Canteen Open/Close Control:
   * Saves isOpen and closeReason to canteens/{canteenId}.
   */
  fun setCanteenOpenStatus(isOpen: Boolean, closeReason: String = "") {
    val currentCanteen = _canteen.value ?: return
    viewModelScope.launch {
      val result = firestoreRepo.updateCanteenStatus(
        canteenId = currentCanteen.id,
        isOpen = isOpen,
        closeReason = if (isOpen) "" else closeReason,
      )
      if (result.isSuccess) {
        _canteen.value = currentCanteen.copy(
          isOpen = isOpen,
          closeReason = if (isOpen) "" else closeReason,
        )
        recomputeUiState()
      }
    }
  }

  fun updateCanteenProfileImage(imageUrl: String) {
    val currentCanteen = _canteen.value ?: return
    _canteen.value = currentCanteen.copy(imageUrl = imageUrl)
    recomputeUiState()
  }

  fun updateCanteenTimings(timings: String) {
    val currentCanteen = _canteen.value ?: return
    _canteen.value = currentCanteen.copy(timings = timings)
    recomputeUiState()
  }

  /**
   * Update Order Status:
   * NEW -> PREPARING -> READY -> COMPLETED (or CANCELLED)
   */
  fun observeOrder(orderId: String): Flow<OrderDocument?> {
    return firestoreRepo.observeOrder(orderId)
  }

  fun updateOrderStatus(orderId: String, newStatus: String) {
    viewModelScope.launch {
      firestoreRepo.updateOrderStatus(orderId, newStatus)
      // Optimistic local update
      _orders.value = _orders.value.map {
        if (it.orderId == orderId) it.copy(status = newStatus, updatedAt = System.currentTimeMillis()) else it
      }
      recomputeUiState()

      // Send status notification to student with decent, professional wording
      try {
        val targetOrder = _orders.value.find { it.orderId == orderId }
        if (targetOrder != null && targetOrder.studentId.isNotBlank()) {
          val tokenFormatted = if (targetOrder.tokenNumber.startsWith("#")) targetOrder.tokenNumber else "#${targetOrder.tokenNumber}"
          val counter = targetOrder.pickupCounter.ifBlank { "Counter 1" }
          val (notifTitle, notifMsg) = when (newStatus.uppercase()) {
            "PREPARING" -> Pair(
              "Order $tokenFormatted: Preparing",
              "Your order is being prepared by the canteen kitchen."
            )
            "READY" -> Pair(
              "Order $tokenFormatted: Ready for Pickup",
              "Your order is ready. Please collect your food at $counter."
            )
            "COMPLETED", "PICKED_UP" -> Pair(
              "Order $tokenFormatted: Completed",
              "Your order has been completed. Thank you for dining with QuickBite."
            )
            "CANCELLED" -> Pair(
              "Order $tokenFormatted: Cancelled",
              "Your order has been cancelled by the canteen kitchen."
            )
            else -> Pair(
              "Order $tokenFormatted Update",
              "Your order status has been updated to $newStatus."
            )
          }

          firestoreRepo.sendNotification(
            NotificationDocument(
              userId = targetOrder.studentId,
              title = notifTitle,
              message = notifMsg,
              type = "ORDER_STATUS",
              orderId = orderId,
              canteenId = targetOrder.canteenId,
              createdAt = System.currentTimeMillis(),
              read = false,
            )
          )
        }
      } catch (e: Exception) {
        android.util.Log.e("OwnerDashboardVM", "Error sending status notification: ${e.message}", e)
      }
    }
  }

  /**
   * Quick Restock action from the low stock section.
   */
  fun restockItem(item: ItemDocument, addedStock: Int = 20) {
    val owner = _currentOwner.value ?: return
    viewModelScope.launch {
      itemRepo.updateStock(owner.canteenId, item, item.stock + addedStock)
    }
  }

  fun refresh() {
    resolveOwnerAndCanteen()
  }
}
