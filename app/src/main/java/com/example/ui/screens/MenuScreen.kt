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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.example.ui.theme.DrawerAmberDark
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RatingStarYellow
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream

private data class CategoryTabItem(
  val category: FoodCategory?,
  val label: String,
  val iconEmoji: String,
)

private val CategoryTabs = listOf(
  CategoryTabItem(null, "All Dishes", "🍽️"),
  CategoryTabItem(FoodCategory.BREAKFAST, "Breakfast", "☕"),
  CategoryTabItem(FoodCategory.LUNCH, "Lunch", "🍛"),
  CategoryTabItem(FoodCategory.SNACKS, "Snacks", "🥪"),
  CategoryTabItem(FoodCategory.BEVERAGES, "Beverages", "🥤"),
  CategoryTabItem(FoodCategory.HEALTHY_BREAKFAST, "Healthy", "🥗"),
  CategoryTabItem(FoodCategory.QUICK_ORDER, "Quick Order", "⚡"),
  CategoryTabItem(FoodCategory.POPULAR, "Popular", "🔥"),
  CategoryTabItem(FoodCategory.READY_UNDER_10, "Ready < 10m", "⏱️"),
)

@Composable
fun DietaryBadge(
  isVeg: Boolean,
  modifier: Modifier = Modifier,
) {
  val color = if (isVeg) Color(0xFF16A34A) else Color(0xFFDC2626)
  Box(
    modifier = modifier
      .size(13.dp)
      .border(1.2.dp, color, RoundedCornerShape(3.dp))
      .padding(2.5.dp),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(CircleShape)
        .background(color)
    )
  }
}

private fun isItemVeg(item: FoodItem): Boolean {
  val nonVegKeywords = listOf("chicken", "egg", "fish", "mutton", "meat", "pork", "beef", "prawn", "seafood", "wings", "nugget")
  return !nonVegKeywords.any {
    item.name.contains(it, ignoreCase = true) || item.description.contains(it, ignoreCase = true)
  }
}

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
  val activeMinPrice = appState.menuFilterMinPrice
  val activeMaxPrice = appState.menuFilterMaxPrice
  val activeMinRating = appState.menuFilterMinRating
  val activeAvailableOnly = appState.menuFilterAvailableOnly
  val activeSort = appState.menuFilterSort

  // Filter items based on search query and all active filter criteria
  val filteredItems = remember(
    currentCanteen,
    searchQuery,
    activeCategory,
    activeDietary,
    activeMaxMinutes,
    activeMinPrice,
    activeMaxPrice,
    activeMinRating,
    activeAvailableOnly,
  ) {
    currentCanteen.allItems.filter { item ->
      val matchesQuery = searchQuery.isBlank() || run {
        val q = searchQuery.trim()
        item.name.contains(q, ignoreCase = true) ||
        item.description.contains(q, ignoreCase = true) ||
        item.category.name.replace("_", " ").contains(q, ignoreCase = true) ||
        item.ingredients.any { it.contains(q, ignoreCase = true) }
      }

      val matchesCategory = activeCategory == null || item.category == activeCategory

      val matchesVeg = when (activeDietary) {
        MenuDietaryFilter.ALL -> true
        MenuDietaryFilter.VEG_ONLY -> item.isVeg
        MenuDietaryFilter.NON_VEG -> !item.isVeg
      }

      val matchesAvailability = !activeAvailableOnly || item.isAvailable

      val matchesMinutes = activeMaxMinutes == null || item.prepMinutes <= activeMaxMinutes

      val matchesMinPrice = activeMinPrice == null || item.price >= activeMinPrice
      val matchesMaxPrice = activeMaxPrice == null || item.price <= activeMaxPrice

      val matchesRating = activeMinRating == null || (item.rating != null && item.rating >= activeMinRating)

      matchesQuery && matchesCategory && matchesVeg && matchesAvailability && matchesMinutes && matchesMinPrice && matchesMaxPrice && matchesRating
    }
  }

  // Sort items based on selected sort option
  val sortedItems = remember(filteredItems, activeSort) {
    when (activeSort) {
      MenuSortOption.RECOMMENDED -> filteredItems
      MenuSortOption.PRICE_LOW_TO_HIGH -> filteredItems.sortedBy { it.price }
      MenuSortOption.PRICE_HIGH_TO_LOW -> filteredItems.sortedByDescending { it.price }
      MenuSortOption.RATING_HIGH_TO_LOW, MenuSortOption.TOP_RATED -> filteredItems.sortedByDescending { it.rating ?: 0.0 }
      MenuSortOption.NEWEST_FIRST -> filteredItems.sortedByDescending { it.createdAt }
      MenuSortOption.POPULAR -> filteredItems.sortedByDescending { it.dailyOrdersToday }
      MenuSortOption.FASTEST_PREP -> filteredItems.sortedBy { it.prepMinutes }
    }
  }

  // Pre-calculate chunked pairs and cart quantities outside LazyColumn to prevent subcomposition lag
  val chunkedMenuItems = remember(sortedItems) { sortedItems.chunked(2) }
  val cartCountsMap = remember(appState.cartItems) {
    appState.cartItems.associate { it.foodItem.id to it.quantity }
  }

  val listState = rememberLazyListState()

  DisposableEffect(Unit) {
    appState.isBottomBarVisible = true
    onDispose {
      appState.isBottomBarVisible = true
    }
  }

  // Hide bottom bar when scrolling down on Menu, reveal when scrolling up or at top
  LaunchedEffect(listState) {
    var lastIndex = listState.firstVisibleItemIndex
    var lastOffset = listState.firstVisibleItemScrollOffset
    var accumulatedDown = 0
    var accumulatedUp = 0

    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
      .collect { (currentIndex, currentOffset) ->
        val delta = if (currentIndex == lastIndex) {
          currentOffset - lastOffset
        } else {
          (currentIndex - lastIndex) * 200 + (currentOffset - lastOffset)
        }

        if (delta > 0) {
          // Scrolling down
          accumulatedDown += delta
          accumulatedUp = 0
          if (accumulatedDown >= 35 && (currentIndex > 0 || currentOffset > 50)) {
            appState.isBottomBarVisible = false
          }
        } else if (delta < 0) {
          // Scrolling up
          accumulatedUp += (-delta)
          accumulatedDown = 0
          if (accumulatedUp >= 25 || (currentIndex == 0 && currentOffset < 30)) {
            appState.isBottomBarVisible = true
          }
        }

        if (currentIndex == 0 && currentOffset < 15) {
          appState.isBottomBarVisible = true
          accumulatedDown = 0
          accumulatedUp = 0
        }

        lastIndex = currentIndex
        lastOffset = currentOffset
      }
  }

  // Animate bottom padding so when the bottom bar slides down, items fill the screen with zero dead space
  val listBottomPadding by animateDpAsState(
    targetValue = if (appState.isBottomBarVisible) 90.dp else 24.dp,
    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
    label = "menuListBottomPadding"
  )

  // Hardware/System back button closes full-screen filter panel or sort sheet
  BackHandler(enabled = appState.isMenuFilterOpen || appState.isMenuSortOpen) {
    if (appState.isMenuFilterOpen) appState.isMenuFilterOpen = false
    if (appState.isMenuSortOpen) appState.isMenuSortOpen = false
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
      // ── Pinned Header: Title, Search Bar + Filter Button, and Categories ──
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(WarmCream)
          .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = "Menu: ${currentCanteen.name}",
              fontSize = 20.sp,
              fontWeight = FontWeight.ExtraBold,
              color = BlackPrimary,
            )
            Text(
              text = if (sortedItems.isNotEmpty()) "${sortedItems.size} dishes available" else "Explore dishes",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Medium,
              color = TextMuted,
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Single Compact Row: Modern Search Bar + Filter Button (Minimal Screen Space) ──
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // Compact Search Input with Theme Integrated Palette
          Box(
            modifier = Modifier
              .weight(1f)
              .height(42.dp)
              .clip(RoundedCornerShape(21.dp))
              .background(Color(0xFFEFE2CD))
              .border(1.dp, Color(0xFFDFCFB7), RoundedCornerShape(21.dp))
              .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = DrawerAmber,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = TextDark, fontWeight = FontWeight.Medium),
                decorationBox = { innerTextField ->
                  if (searchQuery.isEmpty()) {
                    Text(
                      text = "Search dishes, drinks...",
                      fontSize = 12.5.sp,
                      color = TextMuted.copy(alpha = 0.8f),
                    )
                  }
                  innerTextField()
                },
                modifier = Modifier.weight(1f),
              )
              if (searchQuery.isNotEmpty()) {
                Box(
                  modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDFCFB7))
                    .clickable { searchQuery = "" },
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = TextDark,
                    modifier = Modifier.size(12.dp),
                  )
                }
              }
            }
          }

          // Compact Filter Pill Button
          val hasActiveFilters = appState.activeMenuFilterCount > 0
          Box(
            modifier = Modifier
              .height(42.dp)
              .clip(RoundedCornerShape(21.dp))
              .background(if (hasActiveFilters) BlackPrimary else Color(0xFFEFE2CD))
              .border(
                1.dp,
                if (hasActiveFilters) BlackPrimary else Color(0xFFDFCFB7),
                RoundedCornerShape(21.dp),
              )
              .clickable { appState.isMenuFilterOpen = true }
              .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Filter",
                tint = if (hasActiveFilters) PureWhite else BlackPrimary,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Filters",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasActiveFilters) PureWhite else BlackPrimary,
              )
              if (hasActiveFilters) {
                Box(
                  modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(DrawerAmber),
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
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Minimal Quick Filter Pills Row (Logical Natural Order, Minimal Screen Space) ──
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // 1. Sort Pill (Dropdown indicator)
          val isSortActive = activeSort != MenuSortOption.RECOMMENDED
          QuickFilterPill(
            label = if (isSortActive) activeSort.shortLabel else "Sort",
            leadingIcon = Icons.AutoMirrored.Filled.Sort,
            trailingIcon = Icons.Default.ArrowDropDown,
            isSelected = isSortActive,
            onClick = { appState.isMenuSortOpen = true },
          )

          // 2. Vegetarian Pill
          val isVegActive = activeDietary == MenuDietaryFilter.VEG_ONLY
          QuickFilterPill(
            label = "Veg",
            isSelected = isVegActive,
            onClick = {
              appState.menuFilterDietary = if (isVegActive) MenuDietaryFilter.ALL else MenuDietaryFilter.VEG_ONLY
            },
          )

          // 3. Non-Vegetarian Pill
          val isNonVegActive = activeDietary == MenuDietaryFilter.NON_VEG
          QuickFilterPill(
            label = "Non-Veg",
            isSelected = isNonVegActive,
            onClick = {
              appState.menuFilterDietary = if (isNonVegActive) MenuDietaryFilter.ALL else MenuDietaryFilter.NON_VEG
            },
          )

          // 4. Rating 4.0+ Pill
          val isRatingActive = activeMinRating != null && activeMinRating >= 4.0
          QuickFilterPill(
            label = "4.0+ ★",
            isSelected = isRatingActive,
            onClick = {
              appState.menuFilterMinRating = if (isRatingActive) null else 4.0
            },
          )

          // 5. Under ₹100 Pill
          val isPriceActive = activeMaxPrice == 100
          QuickFilterPill(
            label = "Under ₹100",
            isSelected = isPriceActive,
            onClick = {
              appState.menuFilterMaxPrice = if (isPriceActive) null else 100
            },
          )

          // 6. In Stock Pill
          val isStockActive = activeAvailableOnly
          QuickFilterPill(
            label = "In Stock",
            isSelected = isStockActive,
            onClick = {
              appState.menuFilterAvailableOnly = !isStockActive
            },
          )

          // 7. Reset / Clear All Pill (visible when any filter or query is active)
          if (appState.isAnyMenuFilterActive || searchQuery.isNotEmpty()) {
            QuickFilterPill(
              label = "Clear",
              leadingIcon = Icons.Default.Close,
              isSelected = false,
              isDestructive = true,
              onClick = {
                searchQuery = ""
                appState.resetAllMenuFilters()
              },
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = listBottomPadding),
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
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f)),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Box(
                modifier = Modifier
                  .size(56.dp)
                  .clip(CircleShape)
                  .background(DrawerAmber.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Search,
                  contentDescription = null,
                  tint = DrawerAmber,
                  modifier = Modifier.size(28.dp),
                )
              }
              Spacer(modifier = Modifier.height(14.dp))
              Text(
                text = "No dishes found",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = BlackPrimary,
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Try changing your search or adjusting your filters.",
                fontSize = 12.5.sp,
                color = TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
              )
              Spacer(modifier = Modifier.height(18.dp))
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(20.dp))
                  .background(BlackPrimary)
                  .clickable {
                    searchQuery = ""
                    appState.resetAllMenuFilters()
                  }
                  .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "Clear All Filters",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = PureWhite,
                )
              }
            }
          }
        }
      } else {
        // ── 2 Items in a Row (Chunked pairs with stable keys & optimized rendering) ──
        items(
          items = chunkedMenuItems,
          key = { pair -> pair.joinToString("_") { it.id } },
        ) { pair ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            for (foodItem in pair) {
              val inCartCount = cartCountsMap[foodItem.id] ?: 0
              Box(modifier = Modifier.weight(1f)) {
                MenuItemGridCard(
                  foodItem = foodItem,
                  isFavorite = appState.isItemFavorite(foodItem.id),
                  inCartCount = inCartCount,
                  onToggleFavorite = { appState.toggleFavorite(foodItem.id, foodItem) },
                  onAddToCart = { appState.addToCart(foodItem) },
                  onDecreaseQuantity = { appState.decreaseQuantity(foodItem) },
                  onCardClick = { appState.openFoodDetail(foodItem) },
                  modifier = Modifier.fillMaxWidth(),
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

    // ── Sort Bottom Sheet ──────────────────────────────────────────────────
    if (appState.isMenuSortOpen) {
      SortBottomSheet(
        currentSort = activeSort,
        onSortSelected = { option ->
          appState.menuFilterSort = option
          appState.isMenuSortOpen = false
        },
        onDismiss = { appState.isMenuSortOpen = false },
      )
    }
  }
}

/**
 * Modern Quick Filter Pill for minimal screen space & fast single-tap actions
 */
@Composable
private fun QuickFilterPill(
  label: String,
  leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  isSelected: Boolean = false,
  isDestructive: Boolean = false,
  onClick: () -> Unit,
) {
  val bg = when {
    isSelected -> BlackPrimary
    isDestructive -> Color(0xFFFDE8E8)
    else -> Color(0xFFEFE2CD)
  }
  val borderColor = when {
    isSelected -> BlackPrimary
    isDestructive -> Color(0xFFF8B4B4)
    else -> Color(0xFFDFCFB7)
  }
  val contentColor = when {
    isSelected -> PureWhite
    isDestructive -> Color(0xFFC81E1E)
    else -> BlackPrimary
  }

  Box(
    modifier = Modifier
      .height(32.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(bg)
      .border(1.dp, borderColor, RoundedCornerShape(16.dp))
      .clickable { onClick() }
      .padding(horizontal = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (leadingIcon != null) {
        Icon(
          imageVector = leadingIcon,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(13.dp),
        )
      }
      Text(
        text = label,
        fontSize = 11.5.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
        color = contentColor,
      )
      if (trailingIcon != null) {
        Icon(
          imageVector = trailingIcon,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(15.dp),
        )
      }
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
 * Modern Sort Bottom Sheet Modal
 */
@Composable
private fun SortBottomSheet(
  currentSort: MenuSortOption,
  onSortSelected: (MenuSortOption) -> Unit,
  onDismiss: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = 0.5f))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
      ) { onDismiss() },
    contentAlignment = Alignment.BottomCenter,
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
        ) { /* Consume click so sheet doesn't close */ },
      shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
      colors = CardDefaults.cardColors(containerColor = WarmCream),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(top = 12.dp, bottom = 20.dp),
      ) {
        // Drag handle
        Box(
          modifier = Modifier
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFFD6C6AA))
            .align(Alignment.CenterHorizontally),
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Sort,
              contentDescription = null,
              tint = DrawerAmber,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Sort By",
              fontSize = 18.sp,
              fontWeight = FontWeight.ExtraBold,
              color = BlackPrimary,
            )
          }

          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(Color(0xFFEFE2CD))
              .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = TextDark,
              modifier = Modifier.size(16.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = BorderGray.copy(alpha = 0.5f))

        val sortOptions = listOf(
          MenuSortOption.RECOMMENDED to "Our top recommended dishes",
          MenuSortOption.POPULAR to "Most ordered items by students",
          MenuSortOption.PRICE_LOW_TO_HIGH to "Most budget-friendly dishes first",
          MenuSortOption.PRICE_HIGH_TO_LOW to "Premium & deluxe dishes first",
          MenuSortOption.NEWEST_FIRST to "Newest additions to the canteen",
        )

        Column(modifier = Modifier.fillMaxWidth()) {
          for ((option, subtitle) in sortOptions) {
            val isSelected = option == currentSort
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onSortSelected(option) }
                .background(if (isSelected) DrawerAmber.copy(alpha = 0.08f) else Color.Transparent)
                .padding(horizontal = 20.dp, vertical = 13.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = option.displayName,
                  fontSize = 14.sp,
                  fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                  color = if (isSelected) DrawerAmberDark else BlackPrimary,
                )
                Text(
                  text = subtitle,
                  fontSize = 11.5.sp,
                  color = TextMuted,
                )
              }

              Box(
                modifier = Modifier
                  .size(20.dp)
                  .clip(CircleShape)
                  .border(
                    width = if (isSelected) 5.dp else 1.5.dp,
                    color = if (isSelected) DrawerAmber else BorderGray,
                    shape = CircleShape,
                  )
              )
            }
          }
        }
      }
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
  var draftAvailableOnly by remember(appState.menuFilterAvailableOnly) { mutableStateOf(appState.menuFilterAvailableOnly) }
  var draftSort by remember(appState.menuFilterSort) { mutableStateOf(appState.menuFilterSort) }
  var draftMaxMinutes by remember(appState.menuFilterMaxMinutes) { mutableStateOf(appState.menuFilterMaxMinutes) }
  var draftMinPrice by remember(appState.menuFilterMinPrice) { mutableStateOf(appState.menuFilterMinPrice) }
  var draftMaxPrice by remember(appState.menuFilterMaxPrice) { mutableStateOf(appState.menuFilterMaxPrice) }
  var draftMinRating by remember(appState.menuFilterMinRating) { mutableStateOf(appState.menuFilterMinRating) }

  val draftActiveCount = (if (draftCategory != null) 1 else 0) +
    (if (draftDietary != MenuDietaryFilter.ALL) 1 else 0) +
    (if (draftAvailableOnly) 1 else 0) +
    (if (draftSort != MenuSortOption.RECOMMENDED) 1 else 0) +
    (if (draftMaxMinutes != null) 1 else 0) +
    (if (draftMinPrice != null || draftMaxPrice != null) 1 else 0) +
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
          text = "Refine dishes by dietary, availability, category & price",
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
            draftAvailableOnly = false
            draftSort = MenuSortOption.RECOMMENDED
            draftMaxMinutes = null
            draftMinPrice = null
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
      // ── Section 1: Dietary Preference ─────────────────────────────────────
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
            label = "Vegetarian",
            isVeg = true,
            isSelected = draftDietary == MenuDietaryFilter.VEG_ONLY,
            onClick = { draftDietary = MenuDietaryFilter.VEG_ONLY },
            modifier = Modifier.weight(1f),
          )
          DietaryOptionCard(
            label = "Non-Vegetarian",
            isVeg = false,
            isSelected = draftDietary == MenuDietaryFilter.NON_VEG,
            onClick = { draftDietary = MenuDietaryFilter.NON_VEG },
            modifier = Modifier.weight(1f),
          )
        }
      }

      // ── Section 2: Availability ───────────────────────────────────────────
      FilterSection(
        title = "Availability",
        icon = Icons.Default.Check,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterOptionPill(
            label = "All Items",
            isSelected = !draftAvailableOnly,
            onClick = { draftAvailableOnly = false },
            modifier = Modifier.weight(1f),
          )
          FilterOptionPill(
            label = "In Stock Only",
            isSelected = draftAvailableOnly,
            onClick = { draftAvailableOnly = true },
            modifier = Modifier.weight(1f),
          )
        }
      }

      // ── Section 3: Food Category ──────────────────────────────────────────
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

      // ── Section 4: Sort By ────────────────────────────────────────────────
      FilterSection(
        title = "Sort By",
        icon = Icons.AutoMirrored.Filled.Sort,
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
            FilterOptionPill(
              label = "Fastest Prep",
              isSelected = draftSort == MenuSortOption.FASTEST_PREP,
              onClick = { draftSort = MenuSortOption.FASTEST_PREP },
            )
          }
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
          val isAnyPrice = draftMinPrice == null && draftMaxPrice == null
          FilterOptionPill(
            label = "All Prices",
            isSelected = isAnyPrice,
            onClick = {
              draftMinPrice = null
              draftMaxPrice = null
            },
          )
          FilterOptionPill(
            label = "Under ₹50",
            isSelected = draftMinPrice == null && draftMaxPrice == 50,
            onClick = {
              draftMinPrice = null
              draftMaxPrice = 50
            },
          )
          FilterOptionPill(
            label = "₹50 - ₹100",
            isSelected = draftMinPrice == 50 && draftMaxPrice == 100,
            onClick = {
              draftMinPrice = 50
              draftMaxPrice = 100
            },
          )
          FilterOptionPill(
            label = "₹100 - ₹200",
            isSelected = draftMinPrice == 100 && draftMaxPrice == 200,
            onClick = {
              draftMinPrice = 100
              draftMaxPrice = 200
            },
          )
          FilterOptionPill(
            label = "Under ₹200",
            isSelected = draftMinPrice == null && draftMaxPrice == 200,
            onClick = {
              draftMinPrice = null
              draftMaxPrice = 200
            },
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
            label = "All Ratings",
            isSelected = draftMinRating == null,
            onClick = { draftMinRating = null },
            modifier = Modifier.weight(1f),
          )
          FilterOptionPill(
            label = "3.5+ Stars ★",
            isSelected = draftMinRating == 3.5,
            onClick = { draftMinRating = 3.5 },
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

      // ── Section 7: Maximum Preparation Time ───────────────────────────────
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

      Spacer(modifier = Modifier.height(10.dp))
    }

    // ── Sticky Bottom Action Bar ─────────────────────────────────────────────
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .shadow(8.dp)
        .background(WarmCream)
        .border(1.dp, Color(0xFFDFCFB7))
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
            .background(Color(0xFFEFE2CD))
            .border(1.dp, Color(0xFFDFCFB7), RoundedCornerShape(14.dp))
            .clickable {
              draftCategory = null
              draftDietary = MenuDietaryFilter.ALL
              draftAvailableOnly = false
              draftSort = MenuSortOption.RECOMMENDED
              draftMaxMinutes = null
              draftMinPrice = null
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
              appState.menuFilterAvailableOnly = draftAvailableOnly
              appState.menuFilterSort = draftSort
              appState.menuFilterMaxMinutes = draftMaxMinutes
              appState.menuFilterMinPrice = draftMinPrice
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
      .background(if (isSelected) BlackPrimary else Color(0xFFEFE2CD))
      .border(
        1.dp,
        if (isSelected) BlackPrimary else Color(0xFFDFCFB7),
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
      .background(if (isSelected) BlackPrimary else Color(0xFFEFE2CD))
      .border(
        1.5.dp,
        if (isSelected) BlackPrimary else Color(0xFFDFCFB7),
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
 * Modern Food Item Grid Card for MenuScreen
 * Designed according to top-tier food delivery standards:
 * - High quality image container with top rounded corners and aspect ratio preservation
 * - Floating favorite heart action button and star rating badge
 * - Out-of-stock overlay badge and state handling
 * - Vegetarian / Non-vegetarian indicator badge
 * - Prep time chip
 * - Food name with clean typography and text overflow prevention
 * - Short food description
 * - Price display
 * - Responsive "+ ADD" button or interactive quantity stepper [- qty +]
 * - Balanced layout with fillMaxHeight for equal card height alignment
 */
@Composable
private fun MenuItemGridCard(
  foodItem: FoodItem,
  isFavorite: Boolean = false,
  inCartCount: Int = 0,
  onToggleFavorite: () -> Unit = {},
  onAddToCart: () -> Unit,
  onDecreaseQuantity: () -> Unit = {},
  onCardClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isVeg = remember(foodItem.name, foodItem.description) {
    isItemVeg(foodItem)
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .clickable { onCardClick() },
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = CardSurface),
    border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp, pressedElevation = 3.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
      ) {
        // ── 1. Food Image Container ──────────────────────────────────────────
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(134.dp),
        ) {
          FoodImagePlaceholder(
            itemName = foodItem.name,
            imageUrl = foodItem.imageUrl,
            rating = null,
            compact = false,
            modifier = Modifier
              .fillMaxSize()
              .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
          )

          // Floating Favorite Heart Button (Top-Left)
          Box(
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(7.dp)
              .size(28.dp)
              .shadow(2.dp, CircleShape)
              .clip(CircleShape)
              .background(PureWhite.copy(alpha = 0.94f))
              .clickable { onToggleFavorite() },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
              contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
              tint = if (isFavorite) Color(0xFFEF4444) else Color(0xFF64748B),
              modifier = Modifier.size(15.dp),
            )
          }

          // Floating Rating Pill (Top-Right)
          if (foodItem.rating != null && foodItem.rating > 0.0) {
            Box(
              modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(7.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PureWhite.copy(alpha = 0.94f))
                .border(0.8.dp, BorderGray.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp, vertical = 2.5.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Star,
                  contentDescription = null,
                  tint = RatingStarYellow,
                  modifier = Modifier.size(11.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = String.format(java.util.Locale.US, "%.1f", foodItem.rating),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = TextDark,
                )
              }
            }
          }

          // Availability Overlay if item is out of stock
          if (!foodItem.isAvailable) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
              contentAlignment = Alignment.Center,
            ) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(Color(0xFFDC2626))
                  .padding(horizontal = 7.dp, vertical = 2.5.dp),
              ) {
                Text(
                  text = "OUT OF STOCK",
                  fontSize = 9.5.sp,
                  fontWeight = FontWeight.Black,
                  color = PureWhite,
                  letterSpacing = 0.5.sp,
                )
              }
            }
          }
        }

        // ── 2. Card Details ──────────────────────────────────────────────────
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
        ) {
          // Prep Time Row
          Row(
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.AccessTime,
              contentDescription = null,
              tint = TextMuted,
              modifier = Modifier.size(11.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = foodItem.prepTime.ifBlank { "${foodItem.prepMinutes}m" },
              fontSize = 10.5.sp,
              fontWeight = FontWeight.Medium,
              color = TextMuted,
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          // Food Item Name
          Text(
            text = foodItem.name,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = BlackPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )

          Spacer(modifier = Modifier.height(2.dp))

          // Short Description / Ingredients
          val shortDesc = remember(foodItem) {
            if (foodItem.description.isNotBlank()) {
              foodItem.description
            } else if (foodItem.ingredients.isNotEmpty()) {
              foodItem.ingredients.joinToString(", ")
            } else {
              "Fresh campus favorite"
            }
          }
          Text(
            text = shortDesc,
            fontSize = 10.5.sp,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
          )
        }
      }

      // ── 3. Price & Add / Stepper Row ──────────────────────────────────────
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "₹${foodItem.price}",
          fontSize = 15.sp,
          fontWeight = FontWeight.ExtraBold,
          color = BlackPrimary,
        )

        if (!foodItem.isAvailable) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(SoftGray)
              .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
              .padding(horizontal = 8.dp, vertical = 4.dp),
          ) {
            Text(
              text = "Sold Out",
              fontSize = 10.5.sp,
              fontWeight = FontWeight.Bold,
              color = TextMuted,
            )
          }
        } else if (inCartCount == 0) {
          // Modern "+ ADD" Pill Button
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(14.dp))
              .background(DrawerAmber)
              .clickable { onAddToCart() }
              .padding(horizontal = 11.dp, vertical = 5.5.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = "Add",
              tint = PureWhite,
              modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = "ADD",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.ExtraBold,
              color = PureWhite,
              letterSpacing = 0.4.sp,
            )
          }
        } else {
          // Interactive Quantity Stepper Pill
          Row(
            modifier = Modifier
              .height(28.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(Color(0xFF0F172A))
              .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(22.dp)
                .clickable { onDecreaseQuantity() },
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = if (inCartCount == 1) Icons.Default.DeleteOutline else Icons.Default.Remove,
                contentDescription = "Decrease",
                tint = PureWhite,
                modifier = Modifier.size(13.dp),
              )
            }

            Text(
              text = "$inCartCount",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = PureWhite,
              modifier = Modifier.padding(horizontal = 6.dp),
            )

            Box(
              modifier = Modifier
                .size(22.dp)
                .clickable { onAddToCart() },
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase",
                tint = PureWhite,
                modifier = Modifier.size(13.dp),
              )
            }
          }
        }
      }
    }
  }
}
