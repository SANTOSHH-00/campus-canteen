// MenuScreen: Jetpack Compose screen for Menu user interface
package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.BottomNavTab
import com.example.data.FoodCategory
import com.example.data.FoodItem
import com.example.data.MenuDietaryFilter
import com.example.data.MenuSortOption
import com.example.ui.components.QuickBiteStateType
import com.example.ui.components.QuickBiteStateView
import com.example.ui.state.CanteenAppState
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.DeliveryOrangeLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RatingStarYellow
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmCream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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

  // Hardware/System back button closes filter or sort sheets
  BackHandler(enabled = appState.isMenuFilterOpen || appState.isMenuSortOpen) {
    if (appState.isMenuFilterOpen) appState.isMenuFilterOpen = false
    if (appState.isMenuSortOpen) appState.isMenuSortOpen = false
  }

  val isFilterActive = activeCategory != null || activeAvailableOnly || activeMinPrice != null || activeMaxPrice != null || activeDietary != MenuDietaryFilter.ALL || activeMaxMinutes != null
  val isSortActive = activeSort != MenuSortOption.RECOMMENDED

  // Root container matching the home screen WarmCream background
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding(),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(WarmCream)
          .padding(horizontal = 16.dp, vertical = 6.dp),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          // Back Button (<)
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(PureWhite)
              .border(1.dp, BorderGray.copy(alpha = 0.7f), CircleShape)
              .clickable { appState.currentTab = BottomNavTab.HOME },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = TextDark,
              modifier = Modifier.size(19.dp),
            )
          }

          // Canteen / App Name (Prominent & bolder)
          Text(
            text = currentCanteen.name,
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextDark,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
              .weight(1f)
              .padding(horizontal = 12.dp),
          )

          // Balanced spacer to keep the title perfectly centered
          Spacer(modifier = Modifier.size(38.dp))
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.ShoppingBag,
            contentDescription = "Collect from",
            tint = TextDark,
            modifier = Modifier.size(19.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Collect from",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )
        }

        // Location with Block Number, Floor, and Canteen Owner Name
        Text(
          text = "${currentCanteen.betweenBlocks} · ${currentCanteen.floorInfo} · Owner: ${currentCanteen.getOwnerDisplayName()}",
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Normal,
          color = TextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = 2.dp, top = 2.dp),
        )

        // Min order & Prep time / Distance details
        Text(
          text = "Min order ₹10.00 · Prep time ~${currentCanteen.avgWaitMinutes} mins · ${currentCanteen.crowdStatus.label}",
          fontSize = 12.sp,
          fontWeight = FontWeight.Normal,
          color = TextMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = 2.dp, top = 2.dp),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // Search Input Bar
          Box(
            modifier = Modifier
              .weight(1f)
              .height(44.dp)
              .clip(RoundedCornerShape(22.dp))
              .background(PureWhite)
              .border(1.dp, BorderGray.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
              .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search",
                tint = TextMuted,
                modifier = Modifier.size(19.dp),
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
                      text = "Search dishes, snacks, drinks...",
                      fontSize = 13.sp,
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
                    .background(SoftGray)
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

          // Filter Icon Button
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(22.dp))
              .background(if (isFilterActive) DeliveryOrangeLight else PureWhite)
              .border(1.dp, if (isFilterActive) DeliveryOrange else BorderGray.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
              .clickable { appState.isMenuFilterOpen = true },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Tune,
              contentDescription = "Filters",
              tint = if (isFilterActive) DeliveryOrange else TextDark,
              modifier = Modifier.size(19.dp),
            )
          }

          // Sort Icon Button
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(22.dp))
              .background(if (isSortActive) DeliveryOrangeLight else PureWhite)
              .border(1.dp, if (isSortActive) DeliveryOrange else BorderGray.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
              .clickable { appState.isMenuSortOpen = true },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Sort,
              contentDescription = "Sort By",
              tint = if (isSortActive) DeliveryOrange else TextDark,
              modifier = Modifier.size(19.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val categories = listOf(
            null to "ALL DISHES",
            FoodCategory.BREAKFAST to "BREAKFAST",
            FoodCategory.LUNCH to "LUNCH",
            FoodCategory.SNACKS to "SNACKS",
            FoodCategory.BEVERAGES to "BEVERAGES",
            FoodCategory.HEALTHY_BREAKFAST to "HEALTHY",
            FoodCategory.QUICK_ORDER to "QUICK ORDER",
            FoodCategory.POPULAR to "POPULAR",
            FoodCategory.READY_UNDER_10 to "READY < 10M",
          )

          for ((category, label) in categories) {
            val isSelected = activeCategory == category
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (isSelected) DeliveryOrange else PureWhite)
                .border(
                  width = 1.dp,
                  color = if (isSelected) DeliveryOrange else BorderGray.copy(alpha = 0.85f),
                  shape = RoundedCornerShape(20.dp),
                )
                .clickable { appState.selectedMenuCategory = category }
                .padding(horizontal = 14.dp, vertical = 7.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) PureWhite else TextSecondary,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))
      }

      HorizontalDivider(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 2.dp),
        color = BorderGray.copy(alpha = 0.85f),
        thickness = 2.5.dp,
      )

      PullToRefreshBox(
        isRefreshing = appState.isMenuRefreshing,
        onRefresh = { appState.refreshCurrentMenu() },
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
      ) {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        ) {
          // Category Section Title
          item(key = "section_header", contentType = "header") {
            val title = when (activeCategory) {
              null -> "ALL DISHES"
              FoodCategory.BREAKFAST -> "BREAKFAST"
              FoodCategory.LUNCH -> "LUNCH"
              FoodCategory.SNACKS -> "SNACKS"
              FoodCategory.BEVERAGES -> "BEVERAGES"
              FoodCategory.HEALTHY_BREAKFAST -> "HEALTHY SELECTION"
              FoodCategory.QUICK_ORDER -> "QUICK ORDER"
              FoodCategory.POPULAR -> "POPULAR DISHES"
              FoodCategory.READY_UNDER_10 -> "READY UNDER 10 MINS"
            }
            Text(
              text = title,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark,
              letterSpacing = 0.5.sp,
              modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
            )
          }

          val isOffline = !com.example.util.NetworkMonitor.isCurrentlyOnline()
          if (isOffline || appState.menuError != null) {
            item(key = "menu_error_state", contentType = "state") {
              QuickBiteStateView(
                type = QuickBiteStateType.NO_INTERNET,
                title = "Unable to Load Menu",
                message = appState.menuError ?: "It looks like you're offline. Please check your network connection and try again.",
                isRetrying = appState.isMenuRefreshing,
                onRetry = {
                  com.example.util.NetworkMonitor.refreshConnectivity()
                  appState.refreshCurrentMenu()
                },
              )
            }
          } else if (currentCanteen.allItems.isEmpty()) {
            item(key = "empty_menu", contentType = "state") {
              QuickBiteStateView(
                type = QuickBiteStateType.EMPTY_DATA,
                title = "No menu items available yet",
                message = "No items have been added to ${currentCanteen.name} yet.\nItems added in the Owner Dashboard will appear here.",
                onRetry = { appState.refreshCurrentMenu() },
              )
            }
          } else if (sortedItems.isEmpty()) {
            item(key = "no_filter_matches", contentType = "state") {
              QuickBiteStateView(
                type = QuickBiteStateType.EMPTY_DATA,
                title = "No dishes found",
                message = "Try changing your search or adjusting your filters.",
                customIcon = Icons.Default.Search,
                onRetry = {
                  searchQuery = ""
                  appState.resetAllMenuFilters()
                },
              )
            }
          } else {
            // Food Item Rows matching Reference Image 1 with zero lag
            items(
              items = sortedItems,
              key = { it.id },
              contentType = { "menu_item" },
            ) { item ->
              val inCartCount = cartCountsMap[item.id] ?: 0
              MenuItemRow(
                foodItem = item,
                inCartCount = inCartCount,
                onAddToCart = { appState.addToCart(item) },
                onDecreaseQuantity = { appState.decreaseQuantity(item) },
                onCardClick = { appState.openFoodDetail(item) },
              )
            }
          }
        }
      }
    }

    if (appState.isMenuFilterOpen) {
      FilterBottomSheet(
        appState = appState,
        onDismiss = { appState.isMenuFilterOpen = false },
      )
    }

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
 * Menu Item Row matching Reference Image 1:
 * - Left: Food Name with (v) or (nv) tag (NO favourite heart clutter), clean description, price
 * - Right: Improved, larger food image with NO boundary line/border so it mixes seamlessly
 *          with the WarmCream background, plus clean round (+) button at bottom right
 * - High-speed AsyncImage for lag-free 60/120fps scrolling
 */
@Composable
private fun MenuItemRow(
  foodItem: FoodItem,
  inCartCount: Int,
  onAddToCart: () -> Unit,
  onDecreaseQuantity: () -> Unit,
  onCardClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { onCardClick() }
        .padding(vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(end = 12.dp),
      ) {
        // Name
        Text(
          text = foodItem.name,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Food Description (2-3 lines max, muted readable text)
        val descriptionText = foodItem.description.ifBlank {
          foodItem.displaySubtitle.ifBlank {
            "Prepared fresh daily with authentic ingredients and seasoned for a delicious taste."
          }
        }
        Text(
          text = descriptionText,
          fontSize = 12.5.sp,
          color = TextMuted,
          lineHeight = 17.5.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Clean reviews & prep time indicator on item card
        Row(
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "Rating",
            tint = RatingStarYellow,
            modifier = Modifier.size(13.dp),
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = String.format(Locale.US, "%.1f", foodItem.rating ?: 4.8),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )
          Spacer(modifier = Modifier.width(3.dp))
          val reviewCount = if (foodItem.reviewCount > 0) foodItem.reviewCount else (foodItem.dailyOrdersToday / 5 + 14)
          Text(
            text = "($reviewCount)",
            fontSize = 11.5.sp,
            color = TextMuted,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "·",
            fontSize = 12.sp,
            color = BorderGray,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (foodItem.prepMinutes > 0) "${foodItem.prepMinutes}m" else "15m",
            fontSize = 11.5.sp,
            color = TextMuted,
          )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Price
        Text(
          text = "₹${foodItem.price}",
          fontSize = 15.5.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
        )
      }

      Box(
        modifier = Modifier.size(width = 110.dp, height = 104.dp),
        contentAlignment = Alignment.Center,
      ) {
        // Dish Image (total full image extending up to the rounded border)
        Box(
          modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF3ECE4))
            .border(1.dp, BorderGray.copy(alpha = 0.8f), RoundedCornerShape(14.dp)),
          contentAlignment = Alignment.Center,
        ) {
          if (foodItem.imageUrl.isNotBlank()) {
            AsyncImage(
              model = ImageRequest.Builder(context)
                .data(foodItem.imageUrl)
                .crossfade(true)
                .build(),
              contentDescription = foodItem.name,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            DishImagePlaceholder(foodItem = foodItem)
          }
        }

        // Round Orange (+) Button or Compact Stepper (Bottom-Right aligned)
        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(4.dp),
        ) {
          if (inCartCount == 0) {
            Box(
              modifier = Modifier
                .shadow(3.dp, CircleShape)
                .size(34.dp)
                .clip(CircleShape)
                .background(DeliveryOrange)
                .border(1.5.dp, PureWhite, CircleShape)
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
          } else {
            Row(
              modifier = Modifier
                .shadow(3.dp, RoundedCornerShape(15.dp))
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(DeliveryOrange)
                .border(1.5.dp, PureWhite, RoundedCornerShape(15.dp))
                .padding(horizontal = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(20.dp)
                  .clickable { onDecreaseQuantity() },
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Remove,
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
              )
              Box(
                modifier = Modifier
                  .size(20.dp)
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

    // Clean subtle hairline divider between items
    HorizontalDivider(color = BorderGray.copy(alpha = 0.5f), thickness = 0.8.dp)
  }
}

/**
 * Dish Image Placeholder when owner hasn't uploaded a photo
 * Soft natural styling with full rounded rectangular container extending up to the border
 */
@Composable
private fun DishImagePlaceholder(foodItem: FoodItem) {
  val emoji = when (foodItem.category) {
    FoodCategory.BREAKFAST -> "☕"
    FoodCategory.LUNCH -> "🍛"
    FoodCategory.SNACKS -> "🥪"
    FoodCategory.BEVERAGES -> "🥤"
    FoodCategory.HEALTHY_BREAKFAST -> "🥗"
    FoodCategory.POPULAR -> "🍲"
    else -> "🍽️"
  }
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFFF3ECE4)),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = emoji,
      fontSize = 38.sp,
    )
  }
}

/**
 * Filter Bottom Sheet
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
                text = "$draftActiveCount active",
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
              text = "Reset",
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

      Spacer(modifier = Modifier.height(12.dp))
      HorizontalDivider(color = BorderGray.copy(alpha = 0.5f))
      Spacer(modifier = Modifier.height(14.dp))

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Availability
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

        // Price Range
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
      }

      Spacer(modifier = Modifier.height(20.dp))

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
          containerColor = DeliveryOrange,
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
 * Sort Bottom Sheet
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
    MenuSortOption.FASTEST_PREP to "Fastest Prep Time",
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
