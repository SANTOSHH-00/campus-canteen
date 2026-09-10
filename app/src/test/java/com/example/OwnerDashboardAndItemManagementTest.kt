package com.example

import com.example.data.CampusBlockCanteen
import com.example.data.FoodCategory
import com.example.data.FoodItem
import com.example.data.OrderStatus
import com.example.data.firebase.CanteenDocument
import com.example.data.firebase.FirestoreRepository
import com.example.data.firebase.ItemDocument
import com.example.data.firebase.ItemRepository
import com.example.data.firebase.ItemValidationException
import com.example.data.firebase.OrderDocument
import com.example.data.firebase.OwnerDocument
import com.example.data.firebase.OwnerRepository
import com.example.ui.state.CanteenAppState
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.ItemViewModel
import com.example.ui.viewmodel.OwnerDashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeFirestoreRepository : FirestoreRepository(firestore = null) {
  val canteenFlow = MutableStateFlow<CanteenDocument?>(null)
  val ordersFlow = MutableStateFlow<List<OrderDocument>>(emptyList())
  val itemsFlow = MutableStateFlow<List<ItemDocument>>(emptyList())

  var updatedStatusOrderId: String? = null
  var updatedOrderStatusValue: String? = null

  var updatedCanteenId: String? = null
  var updatedCanteenIsOpen: Boolean? = null
  var updatedCanteenCloseReason: String? = null

  override fun observeCanteen(canteenId: String): Flow<CanteenDocument?> = canteenFlow
  override fun observeCanteenOrders(canteenId: String): Flow<List<OrderDocument>> = ordersFlow
  override fun observeItems(canteenId: String): Flow<List<ItemDocument>> = itemsFlow

  override suspend fun updateOrderStatus(orderId: String, status: String): Result<Unit> {
    updatedStatusOrderId = orderId
    updatedOrderStatusValue = status
    val current = ordersFlow.value.toMutableList()
    val idx = current.indexOfFirst { it.orderId == orderId }
    if (idx >= 0) {
      current[idx] = current[idx].copy(status = status)
      ordersFlow.value = current
    }
    return Result.success(Unit)
  }

  override suspend fun updateCanteenStatus(canteenId: String, isOpen: Boolean, closeReason: String): Result<Unit> {
    updatedCanteenId = canteenId
    updatedCanteenIsOpen = isOpen
    updatedCanteenCloseReason = closeReason
    canteenFlow.value = canteenFlow.value?.copy(isOpen = isOpen, closeReason = closeReason)
    return Result.success(Unit)
  }
}

class FakeItemRepository(private val fakeFirestore: FakeFirestoreRepository) : ItemRepository(fakeFirestore) {
  val storedItems = mutableListOf<ItemDocument>()

  override fun observeCanteenItems(canteenId: String): Flow<List<ItemDocument>> {
    return fakeFirestore.itemsFlow
  }

  override suspend fun addItem(ownerCanteenId: String, item: ItemDocument): Result<String> {
    validateItem(item, ownerCanteenId)
    val toSave = if (item.stock == 0) item.copy(available = false) else item
    storedItems.add(toSave)
    fakeFirestore.itemsFlow.value = storedItems.toList()
    return Result.success("item_${System.currentTimeMillis()}")
  }

  override suspend fun updateItem(ownerCanteenId: String, item: ItemDocument): Result<Unit> {
    validateItem(item, ownerCanteenId)
    val toSave = if (item.stock == 0) item.copy(available = false) else item
    val idx = storedItems.indexOfFirst { it.id == toSave.id }
    if (idx >= 0) {
      storedItems[idx] = toSave
    } else {
      storedItems.add(toSave)
    }
    fakeFirestore.itemsFlow.value = storedItems.toList()
    return Result.success(Unit)
  }

  override suspend fun deleteItem(ownerCanteenId: String, item: ItemDocument): Result<Unit> {
    storedItems.removeAll { it.id == item.id }
    fakeFirestore.itemsFlow.value = storedItems.toList()
    return Result.success(Unit)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class OwnerDashboardAndItemManagementTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var fakeFirestore: FakeFirestoreRepository
  private lateinit var fakeItemRepository: FakeItemRepository
  private lateinit var fakeOwnerRepo: FakeOwnerRepository

  private val sampleOwner = OwnerDocument(
    uid = "owner_123",
    name = "Rajesh Sharma",
    email = "rajesh.canteen@messq.com",
    role = "owner",
    canteenAssigned = true,
    canteenId = "canteen_block_26",
    block = "26-27",
  )

  private val sampleCanteen = CanteenDocument(
    id = "canteen_block_26",
    name = "Block 26 Central Canteen",
    block = "26-27",
    location = "Between Block 26 & 27",
    floorInfo = "Ground Floor",
    isOpen = true,
    closeReason = "",
    specialty = "South Indian & Chai",
  )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    fakeFirestore = FakeFirestoreRepository()
    fakeItemRepository = FakeItemRepository(fakeFirestore)
    fakeOwnerRepo = FakeOwnerRepository()
    fakeFirestore.canteenFlow.value = sampleCanteen
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun testDashboardMetricsComputationFromRealOrders() = runTest(testDispatcher) {
    val now = System.currentTimeMillis()
    val orders = listOf(
      OrderDocument(
        orderId = "ORD-001",
        canteenId = sampleOwner.canteenId,
        studentName = "Alex",
        totalAmount = 150,
        status = "NEW",
        createdAt = now,
      ),
      OrderDocument(
        orderId = "ORD-002",
        canteenId = sampleOwner.canteenId,
        studentName = "Priya",
        totalAmount = 220,
        status = "PREPARING",
        createdAt = now,
      ),
      OrderDocument(
        orderId = "ORD-003",
        canteenId = sampleOwner.canteenId,
        studentName = "Rohan",
        totalAmount = 90,
        status = "READY",
        createdAt = now,
      ),
      OrderDocument(
        orderId = "ORD-004",
        canteenId = sampleOwner.canteenId,
        studentName = "Neha",
        totalAmount = 300,
        status = "COMPLETED",
        createdAt = now,
      ),
    )
    fakeFirestore.ordersFlow.value = orders

    val dashboardViewModel = OwnerDashboardViewModel(
      ownerRepo = fakeOwnerRepo,
      firestoreRepo = fakeFirestore,
      itemRepo = fakeItemRepository,
      initialOwner = sampleOwner,
    )
    advanceUntilIdle()

    val readyState = dashboardViewModel.uiState.value as DashboardUiState.Ready
    assertEquals(1, readyState.metrics.newOrdersCount)
    assertEquals(1, readyState.metrics.preparingOrdersCount)
    assertEquals(1, readyState.metrics.readyOrdersCount)
    assertEquals(4, readyState.metrics.todayOrdersCount)
    assertEquals(760, readyState.metrics.todayRevenue)
  }

  @Test
  fun testLowStockItemsDetection() = runTest(testDispatcher) {
    val items = listOf(
      ItemDocument(id = "1", canteenId = sampleOwner.canteenId, name = "Veg Sandwich", stock = 15, available = true),
      ItemDocument(id = "2", canteenId = sampleOwner.canteenId, name = "Cold Coffee", stock = 4, available = true),
      ItemDocument(id = "3", canteenId = sampleOwner.canteenId, name = "Masala Dosa", stock = 0, available = false),
    )
    fakeFirestore.itemsFlow.value = items

    val dashboardViewModel = OwnerDashboardViewModel(
      ownerRepo = fakeOwnerRepo,
      firestoreRepo = fakeFirestore,
      itemRepo = fakeItemRepository,
      initialOwner = sampleOwner,
    )
    advanceUntilIdle()

    val readyState = dashboardViewModel.uiState.value as DashboardUiState.Ready
    val lowStock = readyState.lowStockItems
    assertEquals(2, lowStock.size)
    assertTrue(lowStock.any { it.name == "Cold Coffee" })
    assertTrue(lowStock.any { it.name == "Masala Dosa" })
  }

  @Test
  fun testCanteenStatusToggleWithCloseReason() = runTest(testDispatcher) {
    val dashboardViewModel = OwnerDashboardViewModel(
      ownerRepo = fakeOwnerRepo,
      firestoreRepo = fakeFirestore,
      itemRepo = fakeItemRepository,
      initialOwner = sampleOwner,
    )
    advanceUntilIdle()

    // Close canteen with reason
    dashboardViewModel.setCanteenOpenStatus(isOpen = false, closeReason = "Kitchen Deep Cleaning")
    advanceUntilIdle()

    assertEquals("canteen_block_26", fakeFirestore.updatedCanteenId)
    assertEquals(false, fakeFirestore.updatedCanteenIsOpen)
    assertEquals("Kitchen Deep Cleaning", fakeFirestore.updatedCanteenCloseReason)

    val readyState = dashboardViewModel.uiState.value as DashboardUiState.Ready
    assertFalse(readyState.canteen.isOpen)
    assertEquals("Kitchen Deep Cleaning", readyState.canteen.closeReason)

    // Reopen canteen
    dashboardViewModel.setCanteenOpenStatus(isOpen = true, closeReason = "")
    advanceUntilIdle()

    assertEquals(true, fakeFirestore.updatedCanteenIsOpen)
    val openReadyState = dashboardViewModel.uiState.value as DashboardUiState.Ready
    assertTrue(openReadyState.canteen.isOpen)
  }

  @Test
  fun testOrderStatusProgressionFromNewToCompleted() = runTest(testDispatcher) {
    val order = OrderDocument(
      orderId = "ORD-101",
      canteenId = sampleOwner.canteenId,
      studentName = "Alex",
      totalAmount = 120,
      status = "NEW",
    )
    fakeFirestore.ordersFlow.value = listOf(order)

    val dashboardViewModel = OwnerDashboardViewModel(
      ownerRepo = fakeOwnerRepo,
      firestoreRepo = fakeFirestore,
      itemRepo = fakeItemRepository,
      initialOwner = sampleOwner,
    )
    advanceUntilIdle()

    dashboardViewModel.updateOrderStatus("ORD-101", "PREPARING")
    advanceUntilIdle()
    assertEquals("PREPARING", fakeFirestore.updatedOrderStatusValue)

    dashboardViewModel.updateOrderStatus("ORD-101", "READY")
    advanceUntilIdle()
    assertEquals("READY", fakeFirestore.updatedOrderStatusValue)

    dashboardViewModel.updateOrderStatus("ORD-101", "COMPLETED")
    advanceUntilIdle()
    assertEquals("COMPLETED", fakeFirestore.updatedOrderStatusValue)
  }

  @Test
  fun testItemValidationConstraints() {
    // Empty name
    assertThrows(ItemValidationException.EmptyName::class.java) {
      fakeItemRepository.validateItem(
        ItemDocument(name = "", price = 50, stock = 10, prepMinutes = 5, canteenId = sampleOwner.canteenId),
        sampleOwner.canteenId,
      )
    }

    // Invalid price <= 0
    assertThrows(ItemValidationException.InvalidPrice::class.java) {
      fakeItemRepository.validateItem(
        ItemDocument(name = "Samosa", price = 0, stock = 10, prepMinutes = 5, canteenId = sampleOwner.canteenId),
        sampleOwner.canteenId,
      )
    }

    // Negative stock < 0
    assertThrows(ItemValidationException.InvalidStock::class.java) {
      fakeItemRepository.validateItem(
        ItemDocument(name = "Samosa", price = 20, stock = -1, prepMinutes = 5, canteenId = sampleOwner.canteenId),
        sampleOwner.canteenId,
      )
    }

    // Invalid prepMinutes <= 0
    assertThrows(ItemValidationException.InvalidPrepTime::class.java) {
      fakeItemRepository.validateItem(
        ItemDocument(name = "Samosa", price = 20, stock = 10, prepMinutes = 0, canteenId = sampleOwner.canteenId),
        sampleOwner.canteenId,
      )
    }

    // Modifying other canteen's item
    assertThrows(ItemValidationException.UnauthorizedCanteen::class.java) {
      fakeItemRepository.validateItem(
        ItemDocument(name = "Samosa", price = 20, stock = 10, prepMinutes = 5, canteenId = "other_canteen_99"),
        sampleOwner.canteenId,
      )
    }
  }

  @Test
  fun testZeroStockAutoDisablesAvailability() = runTest(testDispatcher) {
    val itemWithZeroStock = ItemDocument(
      id = "item_zero",
      canteenId = sampleOwner.canteenId,
      name = "Special Thali",
      price = 100,
      stock = 0,
      prepMinutes = 10,
      available = true, // Attempted true, should auto-disable
    )

    fakeItemRepository.addItem(sampleOwner.canteenId, itemWithZeroStock)
    advanceUntilIdle()

    val added = fakeItemRepository.storedItems.first()
    assertEquals("Special Thali", added.name)
    assertEquals(0, added.stock)
    assertFalse(added.available)
  }

  @Test
  fun testItemViewModelSearchAndCategoryFilter() = runTest(testDispatcher) {
    val items = listOf(
      ItemDocument(id = "1", canteenId = sampleOwner.canteenId, name = "Veg Cheese Sandwich", category = "QUICK_ORDER", price = 60),
      ItemDocument(id = "2", canteenId = sampleOwner.canteenId, name = "Cold Coffee Frappe", category = "BEVERAGES", price = 50),
      ItemDocument(id = "3", canteenId = sampleOwner.canteenId, name = "Paneer Tikka Roll", category = "POPULAR", price = 90),
    )
    fakeFirestore.itemsFlow.value = items

    val itemViewModel = ItemViewModel(fakeItemRepository, sampleOwner.canteenId)
    advanceUntilIdle()

    // All items initially
    assertEquals(3, itemViewModel.filteredItems.value.size)

    // Filter by category
    itemViewModel.onCategorySelected("BEVERAGES")
    advanceUntilIdle()
    assertEquals(1, itemViewModel.filteredItems.value.size)
    assertEquals("Cold Coffee Frappe", itemViewModel.filteredItems.value[0].name)

    // Filter by search query
    itemViewModel.onCategorySelected("ALL")
    itemViewModel.onSearchQueryChanged("Sandwich")
    advanceUntilIdle()
    assertEquals(1, itemViewModel.filteredItems.value.size)
    assertEquals("Veg Cheese Sandwich", itemViewModel.filteredItems.value[0].name)
  }

  @Test
  fun testClosedCanteenBlocksStudentOrder() {
    val appState = CanteenAppState()
    val testFoodItem = FoodItem(
      id = "food_1",
      name = "Veg Burger",
      price = 50,
      prepTime = "5 min",
      prepMinutes = 5,
      category = FoodCategory.QUICK_ORDER,
      imageLabel = "Veg Burger",
    )
    appState.addToCart(testFoodItem)
    assertEquals(1, appState.cartItems.size)

    // Close canteen
    appState.selectedCanteen = appState.selectedCanteen.copy(isOpen = false, closeReason = "Maintenance")

    val placed = appState.placeOrder()
    assertNull("Placing order while canteen is closed must return null", placed)
    assertEquals("Cart items must remain unconsumed when canteen is closed", 1, appState.cartItems.size)

    // Reopen canteen
    appState.selectedCanteen = appState.selectedCanteen.copy(isOpen = true, closeReason = null)
    val placedSuccess = appState.placeOrder()
    assertNotNull("Placing order when canteen is open must succeed", placedSuccess)
    assertEquals(OrderStatus.NEW, placedSuccess!!.status)
    assertEquals(0, appState.cartItems.size)
  }
}
