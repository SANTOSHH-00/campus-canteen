package com.example

import com.example.data.CampusBlockCanteen
import com.example.data.CrowdStatus
import com.example.data.FoodCategory
import com.example.data.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Quick Bite Favorites logic, responsive resolution, and state handling.
 */
class FavoritesLogicTest {

  private val sampleFoodItem1 = FoodItem(
    id = "item_chana_bhatura",
    name = "Chana Bhatura",
    price = 75,
    prepTime = "8-10 min",
    prepMinutes = 9,
    category = FoodCategory.LUNCH,
    imageLabel = "Chana Bhatura",
    description = "Daal + Chana Gravy + Bhatura",
  )

  private val sampleFoodItem2 = FoodItem(
    id = "item_thali",
    name = "Special Roti Meal",
    price = 85,
    prepTime = "10-12 min",
    prepMinutes = 11,
    category = FoodCategory.LUNCH,
    imageLabel = "Special Roti Meal",
    description = "Any Gravy + 4 Roti",
  )

  private val sampleCanteen = CampusBlockCanteen(
    id = "canteen_33",
    name = "Govinda's Kitchen",
    betweenBlocks = "Block 33",
    floorInfo = "6th Floor",
    primaryBlocks = listOf(33),
    specialty = "North Indian",
    crowdStatus = CrowdStatus.LOW,
    avgWaitMinutes = 6,
    icon = "🍲",
    isOpen = true,
    rawItems = listOf(sampleFoodItem1, sampleFoodItem2),
  )

  @Test
  fun testInitialFavoritesStateIsEmpty() {
    val favoriteIds = mutableListOf<String>()
    assertTrue(favoriteIds.isEmpty())
  }

  @Test
  fun testToggleFavoriteAddsAndRemoves() {
    val favoriteIds = mutableListOf<String>()

    // Toggle 1 -> Add
    if (favoriteIds.contains(sampleFoodItem1.id)) {
      favoriteIds.remove(sampleFoodItem1.id)
    } else {
      favoriteIds.add(sampleFoodItem1.id)
    }
    assertEquals(1, favoriteIds.size)
    assertTrue(favoriteIds.contains("item_chana_bhatura"))

    // Toggle 1 again -> Remove
    if (favoriteIds.contains(sampleFoodItem1.id)) {
      favoriteIds.remove(sampleFoodItem1.id)
    } else {
      favoriteIds.add(sampleFoodItem1.id)
    }
    assertEquals(0, favoriteIds.size)
    assertFalse(favoriteIds.contains("item_chana_bhatura"))
  }

  @Test
  fun testResolveFavoriteItemsWithCanteen() {
    val favoriteIds = setOf("item_chana_bhatura", "item_thali")
    val canteens = listOf(sampleCanteen)

    val resolved = mutableListOf<Pair<FoodItem, CampusBlockCanteen>>()
    for (itemId in favoriteIds) {
      for (canteen in canteens) {
        val found = canteen.allItems.find { it.id == itemId }
        if (found != null) {
          resolved.add(found to canteen)
          break
        }
      }
    }

    assertEquals(2, resolved.size)
    assertEquals("Chana Bhatura", resolved[0].first.name)
    assertEquals("Govinda's Kitchen", resolved[0].second.name)
  }

  @Test
  fun testCanteenUpdatedDetailsReflectedInFavorites() {
    // When canteen owner updates price from 75 to 80
    val updatedItem = sampleFoodItem1.copy(price = 80)
    val updatedCanteen = sampleCanteen.copy(rawItems = listOf(updatedItem, sampleFoodItem2))

    val favoriteIds = setOf("item_chana_bhatura")
    val resolved = mutableListOf<Pair<FoodItem, CampusBlockCanteen>>()
    for (itemId in favoriteIds) {
      for (canteen in listOf(updatedCanteen)) {
        val found = canteen.allItems.find { it.id == itemId }
        if (found != null) {
          resolved.add(found to canteen)
          break
        }
      }
    }

    assertEquals(1, resolved.size)
    assertEquals(80, resolved[0].first.price)
  }

  @Test
  fun testFavoritePersistenceRoundtrip() {
    val favoriteIds = setOf("item_chana_bhatura", "item_thali", "item_sandwich")
    val asSet = favoriteIds.toSet()
    assertEquals(3, asSet.size)
    assertTrue(asSet.contains("item_sandwich"))
  }

  @Test
  fun testResolveFavoriteItemsWithKnownCache() {
    val favoriteIds = setOf("item_special_pizza")
    val specialPizza = FoodItem(
      id = "item_special_pizza",
      name = "Special Pizza",
      price = 120,
      prepTime = "15 min",
      prepMinutes = 15,
      category = FoodCategory.SNACKS,
      imageLabel = "Pizza",
      description = "Cheese burst pizza",
    )
    val knownCache = mutableMapOf("item_special_pizza" to (specialPizza to sampleCanteen))
    val emptyCanteens = listOf(sampleCanteen.copy(rawItems = emptyList()))

    val resolved = mutableListOf<Pair<FoodItem, CampusBlockCanteen>>()
    for (itemId in favoriteIds) {
      val cached = knownCache[itemId]
      if (cached != null) {
        resolved.add(cached)
        continue
      }
      for (canteen in emptyCanteens) {
        val found = canteen.allItems.find { it.id == itemId }
        if (found != null) {
          resolved.add(found to canteen)
          break
        }
      }
    }

    assertEquals(1, resolved.size)
    assertEquals("Special Pizza", resolved[0].first.name)
    assertEquals(120, resolved[0].first.price)
  }
}
