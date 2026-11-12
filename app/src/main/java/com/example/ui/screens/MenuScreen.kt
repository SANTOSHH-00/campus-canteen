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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.ui.theme.DeliveryOrangeLight
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.DrawerAmberDark
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RatingStarYellow
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
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

        // ── Full-Width Clean Modern Search Input ──
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(PureWhite)
            .border(1.dp, BorderGray, RoundedCornerShape(20.dp))
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
              tint = TextMuted,
              modifier = Modifier.size(17.dp),
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
                  .size(18.dp)
                  .clip(CircleShape)
                  .background(SoftGray)
                  .clickable { searchQuery = "" },
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Clear",
                  tint = TextDark,
                  modifier = Modifier.size(11.dp),
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── Compact Filter Row: Filters, Popular, Price, Sort By (Minimal & Professional) ──
        val isSheetFilterActive = activeCategory != null || activeAvailableOnly || activeMinPrice != null || activeMaxPrice != null
        val sheetFilterCount = (if (activeCategory != null) 1 else 0) +
          (if (activeAvailableOnly) 1 else 0) +
          (if (activeMinPrice != null || activeMaxPrice != null) 1 else 0)

        val isPopularActive = activeSort == MenuSortOption.POPULAR
        val isPriceActive = activeMinPrice != null || activeMaxPrice != null
        val isSortActive = activeSort != MenuSortOption.RECOMMENDED && !isPopularActive

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // 1. Filters Chip (opens clean Filter Bottom Sheet)
          MenuFilterChip(
            label = if (sheetFilterCount > 0) "Filters ($sheetFilterCount)" else "Filters",
            leadingIcon = Icons.Default.Tune,
            isSelected = isSheetFilterActive,
            onClick = { appState.isMenuFilterOpen = true },
          )

          // 2. Popular Chip
          MenuFilterChip(
            label = "Popular",
            isSelected = isPopularActive,
            onClick = {
              appState.menuFilterSort = if (isPopularActive) MenuSortOption.RECOMMENDED else MenuSortOption.POPULAR
            },
          )

          // 3. Price Chip
          val priceLabel = when {
            activeMinPrice == null && activeMaxPrice != null -> "Under ₹$activeMaxPrice"
            activeMinPrice != null && activeMaxPrice != null -> "₹$activeMinPrice - ₹$activeMaxPrice"
            activeMinPrice != null && activeMaxPrice == null -> "₹$activeMinPrice+"
            else -> "Price"
          }
          MenuFilterChip(
            label = priceLabel,
            trailingIcon = if (isPriceActive) Icons.Default.Close else Icons.Default.ArrowDropDown,
            isSelected = isPriceActive,
            onClick = {
              if (isPriceActive) {
                appState.menuFilterMinPrice = null
                appState.menuFilterMaxPrice = null
              } else {
                appState.menuFilterMaxPrice = 100
              }
            },
          )

          // 4. Sort By Chip (opens clean Sort Bottom Sheet)
          val sortLabel = if (isSortActive) activeSort.shortLabel else "Sort By"
          MenuFilterChip(
            label = sortLabel,
            leadingIcon = Icons.AutoMirrored.Filled.Sort,
            trailingIcon = Icons.Default.ArrowDropDown,
            isSelected = isSortActive,
            onClick = { appState.isMenuSortOpen = true },
          )

          // 5. Clear All Chip (visible when any filter or search is active)
          if (appState.isAnyMenuFilterActive || searchQuery.isNotEmpty()) {
            MenuFilterChip(
              label = "Clear All",
              leadingIcon = Icons.Default.Close,
              isSelected = false,
              isClearAction = true,
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

    // ── Modern Filter Bottom Sheet ──
    if (appState.isMenuFilterOpen) {
      FilterBottomSheet(
        appState = appState,
        onDismiss = { appState.isMenuFilterOpen = false },
      )
    }

    // ── Modern Sort Bottom Sheet ──
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
 * Modern Compact Filter Chip for MenuScreen
 * - Uses application's existing theme colours
 * - Selected: subtle primary-colour background (DeliveryOrangeLight) & primary border (DeliveryOrange)
 * - Unselected: neutral background (PureWhite) & subtle border (BorderGray)
 * - Compact height (32.dp) and comfortable pill shape (16.dp)
 */
@Composable
private fun MenuFilterChip(
  label: String,
  leadingIcon: ImageVector? = null,
  trailingIcon: ImageVector? = null,
  isSelected: Boolean = false,
  isClearAction: Boolean = false,
  onClick: () -> Unit,
) {
  val backgroundColor = when {
    isSelected -> DeliveryOrangeLight
    isClearAction -> Color(0xFFFDE8E8)
    else -> PureWhite
  }
  val borderColor = when {
    isSelected -> DeliveryOrange
    isClearAction -> Color(0xFFFCA5A5)
    else -> BorderGray
  }
  val contentColor = when {
    isSelected -> DeliveryOrange
    isClearAction -> Color(0xFFDC2626)
    else -> TextSecondary
  }

  Box(
    modifier = Modifier
      .height(32.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(backgroundColor)
      .border(1.dp, borderColor, RoundedCornerShape(16.dp))
      .clickable { onClick() }
      .padding(horizontal = 11.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
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
        fontSize = 12.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        color = contentColor,
      )
      if (trailingIcon != null) {
        Icon(
          imageVector = trailingIcon,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(14.dp),
        )
      }
    }
  }
}

/**
 * Clean & Professional Filter Bottom Sheet
 * Keeps only useful filter options for the menu:
 * 1. Category (Breakfast, Lunch, Snacks, Beverages, Healthy)
 * 2. Availability (All Items, In Stock Only)
 * 3. Price Range (All Prices, Under ₹50, ₹50-₹100, ₹100-₹200, Under ₹200)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
  appState: CanteenAppState,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var draftCategory by remember(appState.selectedMenuCategory) { mutableStateOf(appState.selectedMenuCategory) }
  var draftAvailableOnly by remember(appState.menuFilterAvailableOnly) { mutableStateOf(appState.menuFilterAvailableOnly) }
  var draftMinPrice by remember(appState.menuFilterMinPrice) { mutableStateOf(appState.menuFilterMinPrice) }
  var draftMaxPrice by remember(appState.menuFilterMaxPrice) { mutableStateOf(appState.menuFilterMaxPrice) }

  val draftActiveCount = (if (draftCategory != null) 1 else 0) +
    (if (draftAvailableOnly) 1 else 0) +
    (if (draftMinPrice != null || draftMaxPrice != null) 1 else 0)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = PureWhite,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    dragHandle = {
      Box(
        modifier = Modifier
          .padding(vertical = 10.dp)
          .size(width = 36.dp, height = 4.dp)
          .clip(CircleShape)
          .background(BorderGray),
      )
    },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
      // ── Header: Title, Clear All, and Close ──
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "Filters",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )
          if (draftActiveCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(DeliveryOrangeLight)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
              Text(
                text = "$draftActiveCount",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = DeliveryOrange,
              )
            }
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (draftActiveCount > 0) {
            Text(
              text = "Clear All",
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = DeliveryOrange,
              modifier = Modifier
                .clickable {
                  draftCategory = null
                  draftAvailableOnly = false
                  draftMinPrice = null
                  draftMaxPrice = null
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = TextDark,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))
      HorizontalDivider(color = BorderGray.copy(alpha = 0.5f))
      Spacer(modifier = Modifier.height(16.dp))

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        // ── 1. Category ──
        Column {
          Text(
            text = "Category",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            val categories = listOf(
              null to "All Categories",
              FoodCategory.BREAKFAST to "Breakfast",
              FoodCategory.LUNCH to "Lunch",
              FoodCategory.SNACKS to "Snacks",
              FoodCategory.BEVERAGES to "Beverages",
              FoodCategory.HEALTHY_BREAKFAST to "Healthy",
            )
            for ((cat, catLabel) in categories) {
              val isSelected = draftCategory == cat
              FilterPillChoice(
                label = catLabel,
                isSelected = isSelected,
                onClick = { draftCategory = cat },
              )
            }
          }
        }

        // ── 2. Availability ──
        Column {
          Text(
            text = "Availability",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            FilterPillChoice(
              label = "All Items",
              isSelected = !draftAvailableOnly,
              onClick = { draftAvailableOnly = false },
              modifier = Modifier.weight(1f),
            )
            FilterPillChoice(
              label = "In Stock Only",
              isSelected = draftAvailableOnly,
              onClick = { draftAvailableOnly = true },
              modifier = Modifier.weight(1f),
            )
          }
        }

        // ── 3. Price Range ──
        Column {
          Text(
            text = "Price Range",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            val priceOptions = listOf(
              Triple(null, null, "All Prices"),
              Triple(null, 50, "Under ₹50"),
              Triple(50, 100, "₹50 - ₹100"),
              Triple(100, 200, "₹100 - ₹200"),
              Triple(null, 200, "Under ₹200"),
            )
            for ((min, max, label) in priceOptions) {
              val isSelected = draftMinPrice == min && draftMaxPrice == max
              FilterPillChoice(
                label = label,
                isSelected = isSelected,
                onClick = {
                  draftMinPrice = min
                  draftMaxPrice = max
                },
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))
      }

      Spacer(modifier = Modifier.height(14.dp))

      // ── Apply Button ──
      Button(
        onClick = {
          appState.selectedMenuCategory = draftCategory
          appState.menuFilterAvailableOnly = draftAvailableOnly
          appState.menuFilterMinPrice = draftMinPrice
          appState.menuFilterMaxPrice = draftMaxPrice
          onDismiss()
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(46.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = BlackPrimary,
          contentColor = PureWhite,
        ),
      ) {
        Text(
          text = if (draftActiveCount > 0) "Apply Filters ($draftActiveCount)" else "Apply Filters",
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
        )
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

@Composable
private fun FilterPillChoice(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(if (isSelected) DeliveryOrangeLight else PureWhite)
      .border(
        1.dp,
        if (isSelected) DeliveryOrange else BorderGray,
        RoundedCornerShape(12.dp),
      )
      .clickable { onClick() }
      .padding(horizontal = 14.dp, vertical = 9.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      fontSize = 12.5.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = if (isSelected) TextDark else TextSecondary,
    )
  }
}

/**
 * Clean & Professional Sorting Bottom Sheet
 * Simple radio-style selection for:
 * - Recommended
 * - Popular
 * - Price: Low to High
 * - Price: High to Low
 * - Recently Added
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortBottomSheet(
  currentSort: MenuSortOption,
  onSortSelected: (MenuSortOption) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val sortOptions = listOf(
    MenuSortOption.RECOMMENDED to "Recommended",
    MenuSortOption.POPULAR to "Popular",
    MenuSortOption.PRICE_LOW_TO_HIGH to "Price: Low to High",
    MenuSortOption.PRICE_HIGH_TO_LOW to "Price: High to Low",
    MenuSortOption.NEWEST_FIRST to "Recently Added",
  )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = PureWhite,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    dragHandle = {
      Box(
        modifier = Modifier
          .padding(vertical = 10.dp)
          .size(width = 36.dp, height = 4.dp)
          .clip(CircleShape)
          .background(BorderGray),
      )
    },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(top = 4.dp, bottom = 18.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Sort By",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
        )

        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(28.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = TextDark,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))
      HorizontalDivider(color = BorderGray.copy(alpha = 0.5f))
      Spacer(modifier = Modifier.height(4.dp))

      Column(modifier = Modifier.fillMaxWidth()) {
        for ((option, label) in sortOptions) {
          val isSelected = option == currentSort
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSortSelected(option) }
              .background(if (isSelected) DeliveryOrangeLight.copy(alpha = 0.4f) else Color.Transparent)
              .padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = label,
              fontSize = 14.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = TextDark,
            )

            RadioButton(
              selected = isSelected,
              onClick = { onSortSelected(option) },
              colors = RadioButtonDefaults.colors(
                selectedColor = DeliveryOrange,
                unselectedColor = BorderGray,
              ),
            )
          }
        }
      }
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
