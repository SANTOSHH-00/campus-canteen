package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FoodCategory
import com.example.data.FoodItem
import com.example.data.MenuDietaryFilter
import com.example.data.MenuSortOption
import com.example.ui.components.FoodImagePlaceholder
import com.example.ui.state.CanteenAppState
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.CardSurface
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream

@Composable
fun MenuScreen(
  appState: CanteenAppState,
  modifier: Modifier = Modifier,
) {
  var searchQuery by remember { mutableStateOf("") }

  val currentCanteen = appState.selectedCanteen
  val activeCategory = appState.selectedMenuCategory
  val activeDietary = appState.menuFilterDietary
  val activeMaxMinutes = appState.menuFilterMaxMinutes
  val activeMaxPrice = appState.menuFilterMaxPrice
  val activeMinRating = appState.menuFilterMinRating
  val activeSort = appState.menuFilterSort

  // Filter items
  val filteredItems = remember(
    currentCanteen,
    searchQuery,
    activeCategory,
    activeDietary,
    activeMaxMinutes,
    activeMaxPrice,
    activeMinRating,
  ) {
    currentCanteen.allItems.filter { item ->
      val matchesQuery = searchQuery.isBlank() ||
        item.name.contains(searchQuery, ignoreCase = true) ||
        item.description.contains(searchQuery, ignoreCase = true)

      val matchesCategory = activeCategory == null || item.category == activeCategory

      val matchesVeg = when (activeDietary) {
        MenuDietaryFilter.ALL -> true
        MenuDietaryFilter.VEG_ONLY -> {
          !item.name.contains("Chicken", ignoreCase = true) &&
          !item.name.contains("Egg", ignoreCase = true) &&
          !item.name.contains("Fish", ignoreCase = true) &&
          !item.name.contains("Mutton", ignoreCase = true)
        }
        MenuDietaryFilter.NON_VEG -> {
          item.name.contains("Chicken", ignoreCase = true) ||
          item.name.contains("Egg", ignoreCase = true) ||
          item.name.contains("Fish", ignoreCase = true) ||
          item.name.contains("Mutton", ignoreCase = true)
        }
      }

      val matchesMinutes = activeMaxMinutes == null || item.prepMinutes <= activeMaxMinutes

      val matchesPrice = activeMaxPrice == null || item.price <= activeMaxPrice

      val matchesRating = activeMinRating == null || ((item.rating ?: 0.0) >= activeMinRating)

      matchesQuery && matchesCategory && matchesVeg && matchesMinutes && matchesPrice && matchesRating
    }
  }

  // Sort items
  val sortedItems = remember(filteredItems, activeSort) {
    when (activeSort) {
      MenuSortOption.RECOMMENDED -> filteredItems
      MenuSortOption.PRICE_LOW_TO_HIGH -> filteredItems.sortedBy { it.price }
      MenuSortOption.PRICE_HIGH_TO_LOW -> filteredItems.sortedByDescending { it.price }
      MenuSortOption.FASTEST_PREP -> filteredItems.sortedBy { it.prepMinutes }
      MenuSortOption.TOP_RATED -> filteredItems.sortedByDescending { it.rating ?: 0.0 }
    }
  }

  val listState = rememberLazyListState()

  LaunchedEffect(Unit) {
    appState.isBottomBarVisible = true
  }

  // Hardware/System back button closes full-screen filter panel
  BackHandler(enabled = appState.isMenuFilterOpen) {
    appState.isMenuFilterOpen = false
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream)
  ) {
    // ── Main Menu Layout: Pinned Header + Scrolling Menu Items ─────────────
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding(),
    ) {
      // ── Pinned Header: Title, Search Bar + Filter Button, and Active Chips ──
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(WarmCream)
          .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
      ) {
        Text(
          text = "Menu: ${currentCanteen.name}",
          fontSize = 22.sp,
          fontWeight = FontWeight.ExtraBold,
          color = BlackPrimary,
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ── Search Bar + Circular Dark Filter Button ────────────────────────
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          // Pill-shaped search bar
          Box(
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .clip(RoundedCornerShape(24.dp))
              .background(PureWhite)
              .border(1.dp, BorderGray.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
              .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Color(0xFFAAAAAA),
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.5.sp, color = TextDark, fontWeight = FontWeight.Medium),
                decorationBox = { innerTextField ->
                  if (searchQuery.isEmpty()) {
                    Text(
                      text = "Search for dishes, cuisines...",
                      fontSize = 13.sp,
                      color = Color(0xFFAAAAAA),
                    )
                  }
                  innerTextField()
                },
                modifier = Modifier.weight(1f),
              )
              if (searchQuery.isNotEmpty()) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Clear",
                  tint = TextMuted,
                  modifier = Modifier
                    .size(18.dp)
                    .clickable { searchQuery = "" },
                )
              }
            }
          }

          // Circular Dark Button with Sliders Icon (Opens Right-Side Full-Screen Panel)
          Box(
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(Color(0xFF1E1E1E))
              .clickable { appState.isMenuFilterOpen = true },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Tune,
              contentDescription = "Filter",
              tint = PureWhite,
              modifier = Modifier.size(20.dp),
            )
            // Numbered badge if filters are currently active
            if (appState.activeMenuFilterCount > 0) {
              Box(
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .padding(top = 2.dp, end = 2.dp)
                  .size(18.dp)
                  .clip(CircleShape)
                  .background(DrawerAmber)
                  .border(1.5.dp, Color(0xFF1E1E1E), CircleShape),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "${appState.activeMenuFilterCount}",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = PureWhite,
                )
              }
            }
          }
        }

        // ── Active Filters Chips Row ──
        if (appState.isAnyMenuFilterActive) {
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (activeCategory != null) {
              val catLabel = when (activeCategory) {
                FoodCategory.BREAKFAST -> "Breakfast"
                FoodCategory.HEALTHY_BREAKFAST -> "Healthy Breakfast"
                FoodCategory.BEVERAGES -> "Beverages"
                FoodCategory.LUNCH -> "Lunch"
                FoodCategory.SNACKS -> "Snacks"
                FoodCategory.QUICK_ORDER -> "Quick Order"
                FoodCategory.POPULAR -> "Popular"
                FoodCategory.READY_UNDER_10 -> "Ready < 10m"
              }
              ActiveFilterChip(label = catLabel, onRemove = { appState.selectedMenuCategory = null })
            }

            if (activeDietary != MenuDietaryFilter.ALL) {
              ActiveFilterChip(label = activeDietary.displayName, onRemove = { appState.menuFilterDietary = MenuDietaryFilter.ALL })
            }

            if (activeSort != MenuSortOption.RECOMMENDED) {
              ActiveFilterChip(label = activeSort.displayName, onRemove = { appState.menuFilterSort = MenuSortOption.RECOMMENDED })
            }

            if (activeMaxMinutes != null) {
              ActiveFilterChip(label = "< $activeMaxMinutes min", onRemove = { appState.menuFilterMaxMinutes = null })
            }

            if (activeMaxPrice != null) {
              ActiveFilterChip(label = "Under ₹$activeMaxPrice", onRemove = { appState.menuFilterMaxPrice = null })
            }

            if (activeMinRating != null) {
              ActiveFilterChip(label = "$activeMinRating+ ★", onRemove = { appState.menuFilterMinRating = null })
            }

            Text(
              text = "Clear all",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = DrawerAmber,
              modifier = Modifier
                .clickable { appState.resetAllMenuFilters() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            )
          }
        }
      }

      // ── Scrolling Menu Items LazyColumn ────────────────────────────────────
      LazyColumn(
        state = listState,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {

      if (currentCanteen.allItems.isEmpty()) {
        item(key = "empty_menu") {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f)),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = "No menu items available yet.",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "No items have been added to ${currentCanteen.name} yet.\nItems added in the Owner Dashboard will appear here.",
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp,
              )
            }
          }
        }
      } else if (sortedItems.isEmpty()) {
        item(key = "no_filter_matches") {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "No items found matching your filters.",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark,
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "Reset all filters",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = DeliveryOrange,
                modifier = Modifier.clickable {
                  searchQuery = ""
                  appState.resetAllMenuFilters()
                },
              )
            }
          }
        }
      } else {
        // ── 2 Items in a Row (Chunked pairs with stable keys) ─────────────────
        val chunkedItems = sortedItems.chunked(2)
        items(
          items = chunkedItems,
          key = { pair -> pair.joinToString("_") { it.id } },
        ) { pair ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            for (foodItem in pair) {
              Box(modifier = Modifier.weight(1f)) {
                MenuItemGridCard(
                  foodItem = foodItem,
                  onAddToCart = { appState.addToCart(foodItem) },
                  onCardClick = { appState.openFoodDetail(foodItem) },
                )
              }
            }
            if (pair.size == 1) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }
    }
  }

  // ── Full-Screen Right Side Filter Panel (Appears from Right & Occupies Whole Screen) ──
    AnimatedVisibility(
      visible = appState.isMenuFilterOpen,
      enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + fadeIn(),
      exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + fadeOut(),
      modifier = Modifier.fillMaxSize(),
    ) {
      FilterFullScreenPanel(
        appState = appState,
        matchingItemsCount = sortedItems.size,
        onClose = { appState.isMenuFilterOpen = false },
      )
    }
  }
}

/**
 * Clean active filter chip with quick dismiss cross
 */
@Composable
private fun ActiveFilterChip(
  label: String,
  onRemove: () -> Unit,
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(14.dp))
      .background(PureWhite)
      .border(1.dp, BorderGray, RoundedCornerShape(14.dp))
      .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = label,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextDark,
      )
      Spacer(modifier = Modifier.width(4.dp))
      Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "Remove",
        tint = TextMuted,
        modifier = Modifier
          .size(14.dp)
          .clickable { onRemove() },
      )
    }
  }
}

/**
 * Full-Screen Filter Panel sliding from right edge with clean layout and clear sections.
 */
@Composable
private fun FilterFullScreenPanel(
  appState: CanteenAppState,
  matchingItemsCount: Int,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Local working copy of filter states so user can make changes cleanly
  var draftCategory by remember(appState.selectedMenuCategory) { mutableStateOf(appState.selectedMenuCategory) }
  var draftDietary by remember(appState.menuFilterDietary) { mutableStateOf(appState.menuFilterDietary) }
  var draftSort by remember(appState.menuFilterSort) { mutableStateOf(appState.menuFilterSort) }
  var draftMaxMinutes by remember(appState.menuFilterMaxMinutes) { mutableStateOf(appState.menuFilterMaxMinutes) }
  var draftMaxPrice by remember(appState.menuFilterMaxPrice) { mutableStateOf(appState.menuFilterMaxPrice) }
  var draftMinRating by remember(appState.menuFilterMinRating) { mutableStateOf(appState.menuFilterMinRating) }

  val draftActiveCount = (if (draftCategory != null) 1 else 0) +
    (if (draftDietary != MenuDietaryFilter.ALL) 1 else 0) +
    (if (draftSort != MenuSortOption.RECOMMENDED) 1 else 0) +
    (if (draftMaxMinutes != null) 1 else 0) +
    (if (draftMaxPrice != null) 1 else 0) +
    (if (draftMinRating != null) 1 else 0)

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(PureWhite)
      .statusBarsPadding()
      .navigationBarsPadding(),
  ) {
    // ── Header Bar ──────────────────────────────────────────────────────────
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Circular Back / Close Button
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(SoftGray)
          .clickable { onClose() },
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back",
          tint = BlackPrimary,
          modifier = Modifier.size(20.dp),
        )
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "Filters & Sort",
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = BlackPrimary,
          )
          if (draftActiveCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(DrawerAmber.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
              Text(
                text = "$draftActiveCount active",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = DrawerAmber,
              )
            }
          }
        }
        Text(
          text = "Refine dishes by dietary, category, time & price",
          fontSize = 11.5.sp,
          color = TextMuted,
        )
      }

      // Reset All Button
      Text(
        text = "Reset All",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = if (draftActiveCount > 0) DrawerAmber else TextMuted.copy(alpha = 0.5f),
        modifier = Modifier
          .clickable(enabled = draftActiveCount > 0) {
            draftCategory = null
            draftDietary = MenuDietaryFilter.ALL
            draftSort = MenuSortOption.RECOMMENDED
            draftMaxMinutes = null
            draftMaxPrice = null
            draftMinRating = null
          }
          .padding(8.dp),
      )
    }

    HorizontalDivider(color = BorderGray.copy(alpha = 0.6f), thickness = 1.dp)

    // ── Scrollable Structured Filter Sections (Logical Natural Order) ───────
    Column(
      modifier = Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
      // ── Section 1: Dietary Preference (Top Priority for Students) ─────────
      FilterSection(
        title = "Dietary Preference",
        icon = Icons.Default.Fastfood,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          DietaryOptionCard(
            label = "All Items",
            isSelected = draftDietary == MenuDietaryFilter.ALL,
            onClick = { draftDietary = MenuDietaryFilter.ALL },
            modifier = Modifier.weight(1f),
          )
          DietaryOptionCard(
            label = "Pure Veg",
            isVeg = true,
            isSelected = draftDietary == MenuDietaryFilter.VEG_ONLY,
            onClick = { draftDietary = MenuDietaryFilter.VEG_ONLY },
            modifier = Modifier.weight(1f),
          )
          DietaryOptionCard(
            label = "Non-Veg",
            isVeg = false,
            isSelected = draftDietary == MenuDietaryFilter.NON_VEG,
            onClick = { draftDietary = MenuDietaryFilter.NON_VEG },
            modifier = Modifier.weight(1f),
          )
        }
      }

      // ── Section 2: Food Category ──────────────────────────────────────────
      FilterSection(
        title = "Category",
        icon = Icons.Default.Category,
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterOptionPill(
            label = "All Categories",
            isSelected = draftCategory == null,
            onClick = { draftCategory = null },
          )
          FilterOptionPill(
            label = "Breakfast",
            isSelected = draftCategory == FoodCategory.BREAKFAST,
            onClick = { draftCategory = FoodCategory.BREAKFAST },
          )
          FilterOptionPill(
            label = "Healthy Breakfast",
            isSelected = draftCategory == FoodCategory.HEALTHY_BREAKFAST,
            onClick = { draftCategory = FoodCategory.HEALTHY_BREAKFAST },
          )
          FilterOptionPill(
            label = "Lunch",
            isSelected = draftCategory == FoodCategory.LUNCH,
            onClick = { draftCategory = FoodCategory.LUNCH },
          )
          FilterOptionPill(
            label = "Beverages",
            isSelected = draftCategory == FoodCategory.BEVERAGES,
            onClick = { draftCategory = FoodCategory.BEVERAGES },
          )
          FilterOptionPill(
            label = "Snacks",
            isSelected = draftCategory == FoodCategory.SNACKS,
            onClick = { draftCategory = FoodCategory.SNACKS },
          )
        }
      }

      // ── Section 3: Sort By ────────────────────────────────────────────────
      FilterSection(
        title = "Sort By",
        icon = Icons.Default.Sort,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterOptionPill(
              label = "Recommended",
              isSelected = draftSort == MenuSortOption.RECOMMENDED,
              onClick = { draftSort = MenuSortOption.RECOMMENDED },
            )
            FilterOptionPill(
              label = "Fastest Prep Time",
              isSelected = draftSort == MenuSortOption.FASTEST_PREP,
              onClick = { draftSort = MenuSortOption.FASTEST_PREP },
            )
            FilterOptionPill(
              label = "Price: Low to High",
              isSelected = draftSort == MenuSortOption.PRICE_LOW_TO_HIGH,
              onClick = { draftSort = MenuSortOption.PRICE_LOW_TO_HIGH },
            )
            FilterOptionPill(
              label = "Price: High to Low",
              isSelected = draftSort == MenuSortOption.PRICE_HIGH_TO_LOW,
              onClick = { draftSort = MenuSortOption.PRICE_HIGH_TO_LOW },
            )
            FilterOptionPill(
              label = "Highest Rated",
              isSelected = draftSort == MenuSortOption.TOP_RATED,
              onClick = { draftSort = MenuSortOption.TOP_RATED },
            )
          }
        }
      }

      // ── Section 4: Maximum Preparation Time ───────────────────────────────
      FilterSection(
        title = "Preparation Time",
        icon = Icons.Default.AccessTime,
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterOptionPill(
            label = "Any Time",
            isSelected = draftMaxMinutes == null,
            onClick = { draftMaxMinutes = null },
          )
          FilterOptionPill(
            label = "< 5 mins",
            isSelected = draftMaxMinutes == 5,
            onClick = { draftMaxMinutes = 5 },
          )
          FilterOptionPill(
            label = "< 10 mins",
            isSelected = draftMaxMinutes == 10,
            onClick = { draftMaxMinutes = 10 },
          )
          FilterOptionPill(
            label = "< 15 mins",
            isSelected = draftMaxMinutes == 15,
            onClick = { draftMaxMinutes = 15 },
          )
        }
      }

      // ── Section 5: Price Range ────────────────────────────────────────────
      FilterSection(
        title = "Price Range",
        icon = Icons.Default.CurrencyRupee,
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterOptionPill(
            label = "Any Price",
            isSelected = draftMaxPrice == null,
            onClick = { draftMaxPrice = null },
          )
          FilterOptionPill(
            label = "Under ₹50",
            isSelected = draftMaxPrice == 50,
            onClick = { draftMaxPrice = 50 },
          )
          FilterOptionPill(
            label = "Under ₹100",
            isSelected = draftMaxPrice == 100,
            onClick = { draftMaxPrice = 100 },
          )
          FilterOptionPill(
            label = "Under ₹150",
            isSelected = draftMaxPrice == 150,
            onClick = { draftMaxPrice = 150 },
          )
          FilterOptionPill(
            label = "Under ₹200",
            isSelected = draftMaxPrice == 200,
            onClick = { draftMaxPrice = 200 },
          )
        }
      }

      // ── Section 6: Customer Ratings ───────────────────────────────────────
      FilterSection(
        title = "Customer Rating",
        icon = Icons.Default.Star,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterOptionPill(
            label = "Any Rating",
            isSelected = draftMinRating == null,
            onClick = { draftMinRating = null },
            modifier = Modifier.weight(1f),
          )
          FilterOptionPill(
            label = "4.0+ Stars ★",
            isSelected = draftMinRating == 4.0,
            onClick = { draftMinRating = 4.0 },
            modifier = Modifier.weight(1f),
          )
          FilterOptionPill(
            label = "4.5+ Stars ★",
            isSelected = draftMinRating == 4.5,
            onClick = { draftMinRating = 4.5 },
            modifier = Modifier.weight(1f),
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
    }

    // ── Sticky Bottom Action Bar ─────────────────────────────────────────────
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .shadow(8.dp)
        .background(PureWhite)
        .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Outline Clear Button
        Box(
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF5F2EC))
            .border(1.dp, BorderGray, RoundedCornerShape(14.dp))
            .clickable {
              draftCategory = null
              draftDietary = MenuDietaryFilter.ALL
              draftSort = MenuSortOption.RECOMMENDED
              draftMaxMinutes = null
              draftMaxPrice = null
              draftMinRating = null
            },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "Clear All",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )
        }

        // Apply Filters Button (Decent Sleek Black Primary)
        Box(
          modifier = Modifier
            .weight(1.8f)
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BlackPrimary)
            .clickable {
              appState.selectedMenuCategory = draftCategory
              appState.menuFilterDietary = draftDietary
              appState.menuFilterSort = draftSort
              appState.menuFilterMaxMinutes = draftMaxMinutes
              appState.menuFilterMaxPrice = draftMaxPrice
              appState.menuFilterMinRating = draftMinRating
              onClose()
            },
          contentAlignment = Alignment.Center,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = null,
              tint = PureWhite,
              modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (draftActiveCount > 0) "Apply Filters ($draftActiveCount)" else "Apply Filters",
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Bold,
              color = PureWhite,
            )
          }
        }
      }
    }
  }
}

/**
 * Filter Section with Title and Icon
 */
@Composable
private fun FilterSection(
  title: String,
  icon: ImageVector,
  content: @Composable () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(bottom = 10.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = DrawerAmber,
        modifier = Modifier.size(17.dp),
      )
      Spacer(modifier = Modifier.width(7.dp))
      Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = BlackPrimary,
        letterSpacing = 0.2.sp,
      )
    }
    content()
  }
}

/**
 * Modern Filter Option Pill with Sleek Black & Warm Cream Theme
 */
@Composable
private fun FilterOptionPill(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(20.dp))
      .background(if (isSelected) BlackPrimary else Color(0xFFF5F2EC))
      .border(
        1.dp,
        if (isSelected) BlackPrimary else BorderGray.copy(alpha = 0.6f),
        RoundedCornerShape(20.dp),
      )
      .clickable { onClick() }
      .padding(horizontal = 14.dp, vertical = 9.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      fontSize = 12.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = if (isSelected) PureWhite else TextDark,
    )
  }
}

/**
 * Dietary Option Card (Veg / Non-Veg / All) with Official Symbol and Theme Palette
 */
@Composable
private fun DietaryOptionCard(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isVeg: Boolean? = null,
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(if (isSelected) BlackPrimary else Color(0xFFF5F2EC))
      .border(
        1.5.dp,
        if (isSelected) BlackPrimary else BorderGray.copy(alpha = 0.6f),
        RoundedCornerShape(14.dp),
      )
      .clickable { onClick() }
      .padding(horizontal = 8.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (isVeg != null) {
        val symbolColor = if (isVeg) Color(0xFF2E7D32) else Color(0xFFC62828)
        Box(
          modifier = Modifier
            .size(13.dp)
            .border(1.2.dp, symbolColor, RoundedCornerShape(2.dp)),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier = Modifier
              .size(6.dp)
              .clip(CircleShape)
              .background(symbolColor)
          )
        }
        Spacer(modifier = Modifier.width(5.dp))
      }
      Text(
        text = label,
        fontSize = 11.5.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        color = if (isSelected) PureWhite else TextDark,
        maxLines = 1,
      )
    }
  }
}

/**
 * 2-Item Grid Card for MenuScreen
 * Sized to evenly occupy screen space with rich visuals and generous touch targets.
 */
@Composable
private fun MenuItemGridCard(
  foodItem: FoodItem,
  onAddToCart: () -> Unit,
  onCardClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .clickable { onCardClick() },
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = CardSurface),
    border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.5f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(10.dp),
    ) {
      FoodImagePlaceholder(
        itemName = foodItem.name,
        imageUrl = foodItem.imageUrl,
        rating = foodItem.rating,
        compact = false,
        modifier = Modifier
          .fillMaxWidth()
          .height(138.dp),
      )

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = foodItem.name,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = TextDark,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      Spacer(modifier = Modifier.height(4.dp))

      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.AccessTime,
          contentDescription = null,
          tint = TextMuted,
          modifier = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = foodItem.prepTime,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.Medium,
          color = TextMuted,
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "₹${foodItem.price}",
          fontSize = 15.5.sp,
          fontWeight = FontWeight.ExtraBold,
          color = BlackPrimary,
        )

        // Circular Amber '+' Button (Exact Match to Dashboard Screen)
        Box(
          modifier = Modifier
            .size(36.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(DrawerAmber)
            .clickable { onAddToCart() },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add to Cart",
            tint = PureWhite,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}
