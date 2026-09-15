package com.example

import com.example.data.CartItem
import com.example.data.FoodCategory
import com.example.data.FoodItem
import com.example.data.OrderRecord
import com.example.data.OrderStatus
import com.example.data.OrdersDateFilterOption
import com.example.data.OrdersFilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class OrdersFilterLogicTest {

  private val mockFoodItem = FoodItem(
    id = "item_1",
    name = "Test Item",
    price = 50,
    prepTime = "5 min",
    prepMinutes = 5,
    category = FoodCategory.QUICK_ORDER,
    imageLabel = "Test Item",
  )

  private val mockCartItem = CartItem(
    foodItem = mockFoodItem,
    quantity = 1,
  )

  @Test
  fun testDefaultFilterStateIsNotActive() {
    val state = OrdersFilterState()
    assertFalse(state.isActive)
    assertEquals(0, state.activeFilterCount)
  }

  @Test
  fun testDateFilterStateIsActive() {
    val state = OrdersFilterState(dateOption = OrdersDateFilterOption.TODAY)
    assertTrue(state.isActive)
    assertEquals(1, state.activeFilterCount)
  }

  @Test
  fun testCanteenFilterStateIsActive() {
    val state = OrdersFilterState(selectedCanteenIds = setOf("canteen_33", "canteen_34"))
    assertTrue(state.isActive)
    assertEquals(2, state.activeFilterCount)
  }

  @Test
  fun testCombinedFilterStateCount() {
    val state = OrdersFilterState(
      dateOption = OrdersDateFilterOption.LAST_7_DAYS,
      selectedCanteenIds = setOf("canteen_33"),
    )
    assertTrue(state.isActive)
    assertEquals(2, state.activeFilterCount)
  }

  @Test
  fun testDateFilterEpochMatching() {
    val cal = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val todayStart = cal.timeInMillis
    val todayEnd = todayStart + 24L * 60 * 60 * 1000 - 1L
    val yesterdayEpoch = todayStart - 12L * 60 * 60 * 1000 // middle of yesterday
    val fiveDaysAgoEpoch = todayStart - 4L * 24 * 60 * 60 * 1000

    val orderToday = OrderRecord(
      id = "ORD-TODAY",
      tokenNumber = "101",
      items = listOf(mockCartItem),
      totalPrice = 50,
      status = OrderStatus.COMPLETED,
      orderTime = "Today, 10:00 AM",
      estimatedReadyTime = "10:05 AM",
      canteenId = "canteen_33",
      placedEpoch = todayStart + 1000,
    )

    val orderYesterday = OrderRecord(
      id = "ORD-YESTERDAY",
      tokenNumber = "102",
      items = listOf(mockCartItem),
      totalPrice = 50,
      status = OrderStatus.COMPLETED,
      orderTime = "Yesterday, 2:00 PM",
      estimatedReadyTime = "2:05 PM",
      canteenId = "canteen_34",
      placedEpoch = yesterdayEpoch,
    )

    val order5DaysAgo = OrderRecord(
      id = "ORD-5DAYS",
      tokenNumber = "103",
      items = listOf(mockCartItem),
      totalPrice = 50,
      status = OrderStatus.COMPLETED,
      orderTime = "5 days ago",
      estimatedReadyTime = "",
      canteenId = "canteen_33",
      placedEpoch = fiveDaysAgoEpoch,
    )

    val allOrders = listOf(orderToday, orderYesterday, order5DaysAgo)

    // Test filter TODAY
    val filteredToday = allOrders.filter { order ->
      order.placedEpoch in todayStart..todayEnd
    }
    assertEquals(1, filteredToday.size)
    assertEquals("ORD-TODAY", filteredToday[0].id)

    // Test filter Canteen 33
    val filteredCanteen33 = allOrders.filter { it.canteenId == "canteen_33" }
    assertEquals(2, filteredCanteen33.size)
    assertTrue(filteredCanteen33.any { it.id == "ORD-TODAY" })
    assertTrue(filteredCanteen33.any { it.id == "ORD-5DAYS" })

    // Test filter Canteen 34
    val filteredCanteen34 = allOrders.filter { it.canteenId == "canteen_34" }
    assertEquals(1, filteredCanteen34.size)
    assertEquals("ORD-YESTERDAY", filteredCanteen34[0].id)
  }
}
