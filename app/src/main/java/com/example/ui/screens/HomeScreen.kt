// HomeScreen: Jetpack Compose screen for Home user interface
package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.ui.components.QuickBiteStateView
import com.example.ui.components.QuickBiteStateType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BottomNavTab
import com.example.data.FoodCategory
import com.example.data.FoodItem
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import com.example.ui.components.FeaturedSpecialBannerCard
import com.example.ui.components.HomeGreetingSection
import com.example.ui.components.SuggestProductSheet
import com.example.ui.components.HomeScreenHeader
import com.example.ui.components.HomeScreenTopBar
import com.example.ui.components.ModernNotificationsSheet
import com.example.ui.components.PopularCard
import com.example.ui.components.QuickOrderCard
import com.example.ui.components.YourUsualBanner
import com.example.ui.state.CanteenAppState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.CardSurface
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.DrawerAmberDark
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  userName: String = "Alex",
  appState: CanteenAppState,
  onNavigateToTab: (BottomNavTab) -> Unit = {},
  onOpenProfileDrawer: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  var showNotificationsDialog by remember { mutableStateOf(false) }
  var showLoginSheet by remember { mutableStateOf(false) }
  var showQuickFilterDialog by remember { mutableStateOf(false) }
  var showSuggestProductSheet by remember { mutableStateOf(false) }

  val currentCanteen = appState.selectedCanteen

  // Filter Quick Order items dynamically from the selected block canteen
  val quickItems = remember(currentCanteen, appState.selectedQuickFilterMinutes) {
    val maxMin = appState.selectedQuickFilterMinutes
    currentCanteen.quickOrderItems.filter { item ->
      val prepMin = item.prepMinutes
      prepMin <= maxMin
    }
  }
  val popularItems = currentCanteen.popularItems

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream)
  ) {
    HomeScreenTopBar(
      appState = appState,
      onProfileClick = onOpenProfileDrawer,
      onNotificationClick = {
        showNotificationsDialog = true
      },
      modifier = Modifier
        .fillMaxWidth()
        .background(WarmCream)
        .padding(horizontal = 16.dp, vertical = 6.dp),
    )

    PullToRefreshBox(
      isRefreshing = appState.isHomeRefreshing,
      onRefresh = { appState.refreshHomeData() },
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
    ) {
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
      ) {
      item(key = "home_greeting") {
        HomeGreetingSection(
          userName = userName,
          appState = appState,
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 12.dp),
        )
      }

      if (currentCanteen.allItems.isNotEmpty()) {
        item(key = "featured_special") {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp),
          ) {
            FeaturedSpecialBannerCard(
              onOrderClick = {
                val special = popularItems.firstOrNull() ?: quickItems.firstOrNull()
                if (special != null) {
                  appState.openFoodDetail(special)
                }
              }
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
        }
      }

      if (!currentCanteen.isOpen) {
        item(key = "closed_banner") {
          Spacer(modifier = Modifier.height(10.dp))
          androidx.compose.material3.Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.5f)),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(Color(0xFFFFCDD2)),
                contentAlignment = Alignment.Center,
              ) {
                Text("⛔", fontSize = 20.sp)
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${currentCanteen.name} is Currently Closed",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = Color(0xFFC62828),
                )
                Text(
                  text = if (!currentCanteen.closeReason.isNullOrBlank()) {
                    "Reason: ${currentCanteen.closeReason}"
                  } else {
                    "Orders are paused right now. You can still browse the menu."
                  },
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium,
                  color = Color(0xFFD32F2F),
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
        }
      }

      val isOffline = !com.example.util.NetworkMonitor.isCurrentlyOnline()
      if (isOffline || appState.homeError != null) {
        item(key = "home_error_state") {
          QuickBiteStateView(
            type = QuickBiteStateType.NO_INTERNET,
            title = "Unable to Load Home Content",
            message = appState.homeError ?: "It looks like you're offline. Please check your network connection and try again.",
            isRetrying = appState.isHomeRefreshing,
            onRetry = {
              com.example.util.NetworkMonitor.refreshConnectivity()
              appState.refreshHomeData()
            },
          )
        }
      } else if (currentCanteen.allItems.isEmpty()) {
        item(key = "empty_menu") {
          QuickBiteStateView(
            type = QuickBiteStateType.EMPTY_DATA,
            title = "No menu items available yet",
            message = "The menu for ${currentCanteen.name} is currently empty. Items added in the Owner Dashboard will appear here automatically.",
            onRetry = { appState.refreshHomeData() },
          )
        }
      } else {
        item(key = "quick_order_header") {
          SectionHeaderRow(
            iconVector = Icons.Default.FlashOn,
            title = "QUICK ORDER",
            onSeeAllClick = {
              appState.selectedMenuCategory = FoodCategory.QUICK_ORDER
              onNavigateToTab(BottomNavTab.MENU)
            },
          )
          Spacer(modifier = Modifier.height(10.dp))
        }

        if (quickItems.isNotEmpty()) {
          item(key = "quick_order_row") {
            LazyRow(
              modifier = Modifier.fillMaxWidth(),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
              horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              items(
                items = quickItems,
                key = { it.id },
              ) { item ->
                QuickOrderCard(
                  foodItem = item,
                  onAddToCart = { appState.addToCart(item) },
                  onCardClick = { appState.openFoodDetail(item) },
                )
              }
            }
            Spacer(modifier = Modifier.height(26.dp))
          }
        } else {
          item(key = "quick_order_empty") {
            QuickBiteStateView(
              type = QuickBiteStateType.EMPTY_DATA,
              title = "No items ready in ≤ ${appState.selectedQuickFilterMinutes} mins",
              message = "Tap 'All' or select a different prep time to explore more items.",
              modifier = Modifier.padding(vertical = 10.dp),
            )
            Spacer(modifier = Modifier.height(18.dp))
          }
        }

        if (popularItems.isNotEmpty()) {
          item(key = "popular_header") {
            SectionHeaderRow(
              iconVector = Icons.Default.LocalFireDepartment,
              title = "POPULAR RIGHT NOW",
              onSeeAllClick = {
                appState.selectedMenuCategory = FoodCategory.POPULAR
                onNavigateToTab(BottomNavTab.MENU)
              },
            )
            Spacer(modifier = Modifier.height(14.dp))
          }

          items(
            items = popularItems,
            key = { "pop_${it.id}" },
          ) { item ->
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
              PopularCard(
                foodItem = item,
                onAddToCart = { appState.addToCart(item) },
                onCardClick = { appState.openFoodDetail(item) },
              )
            }
          }

          item(key = "popular_spacer") {
            Spacer(modifier = Modifier.height(18.dp))
          }
        }



        val usual = appState.usualOrderInfo
        if (!appState.isUsualBannerDismissed && usual != null && usual.foodItem != null) {
          item(key = "usual_order") {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
            ) {
              val homeContext = LocalContext.current
              YourUsualBanner(
                usualInfo = usual,
                onReorder = {
                  appState.addToCart(usual.foodItem)
                  android.widget.Toast.makeText(homeContext, "${usual.itemName} added to cart!", android.widget.Toast.LENGTH_SHORT).show()
                },
                onDismiss = {
                  appState.dismissUsualBanner()
                }
              )
            }
            Spacer(modifier = Modifier.height(18.dp))
          }
        }
      }

      item(key = "suggest_product_section") {
        SuggestProductSection(
          onSuggestClick = { showSuggestProductSheet = true },
        )
      }

      item(key = "bottom_space") {
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
    }
  }

  // Suggest Product Sheet (Canteen-Specific Owner Routing with 3-Day Rate Limit)
  if (showSuggestProductSheet) {
    SuggestProductSheet(
      canteenId = currentCanteen.id,
      canteenName = currentCanteen.name,
      appState = appState,
      onDismiss = { showSuggestProductSheet = false },
    )
  }

  // Modern Notifications Bottom Sheet (Smooth slide-in-up transition)
  if (showNotificationsDialog) {
    ModernNotificationsSheet(
      appState = appState,
      onDismiss = { showNotificationsDialog = false },
      onLoginClick = {
        showNotificationsDialog = false
        showLoginSheet = true
      },
    )
  }

  // Login Modal Bottom Sheet when guest initiates sign in
  if (showLoginSheet) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0x80000000))
        .clickable(
          interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
          indication = null,
          onClick = { showLoginSheet = false },
        ),
      contentAlignment = Alignment.BottomCenter,
    ) {
      LoginContent(
        onDismiss = { showLoginSheet = false },
        onLoginSuccess = { profile ->
          appState.loginUser(profile)
          showLoginSheet = false
        },
      )
    }
  }

  // Quick Order Prep Time Filter Dialog
  if (showQuickFilterDialog) {
    QuickOrderFilterDialog(
      currentMinutes = appState.selectedQuickFilterMinutes,
      onSelectMinutes = { minutes ->
        appState.selectedQuickFilterMinutes = minutes
      },
      onDismiss = { showQuickFilterDialog = false },
    )
  }
}

@Composable
private fun SectionHeaderRow(
  iconVector: ImageVector,
  title: String,
  onSeeAllClick: () -> Unit,
  onFilterClick: (() -> Unit)? = null,
  isFilterActive: Boolean = false,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = iconVector,
        contentDescription = null,
        tint = BlackPrimary,
        modifier = Modifier.size(22.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        color = BlackPrimary,
        letterSpacing = 0.5.sp,
      )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      if (onFilterClick != null) {
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (isFilterActive) BlackPrimary else PureWhite)
            .border(1.2.dp, if (isFilterActive) BlackPrimary else BorderGray.copy(alpha = 0.75f), CircleShape)
            .clickable { onFilterClick() },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = "Filter",
            tint = if (isFilterActive) PureWhite else BlackPrimary,
            modifier = Modifier.size(17.dp),
          )
        }
        Spacer(modifier = Modifier.width(10.dp))
      }

      Row(
        modifier = Modifier.clickable { onSeeAllClick() },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "See All",
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Bold,
          color = BlackPrimary,
        )
        Spacer(modifier = Modifier.width(2.dp))
        Icon(
          imageVector = Icons.Default.ChevronRight,
          contentDescription = "See All",
          tint = BlackPrimary,
          modifier = Modifier.size(15.dp),
        )
      }
    }
  }
}

@Composable
private fun QuickOrderFilterDialog(
  currentMinutes: Int,
  onSelectMinutes: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  val options = listOf(
    20 to "All Quick Items (Up to 20 mins)",
    5 to "Under 5 mins ⚡ (Ultra Fast)",
    10 to "Under 10 mins ⏱️ (Fast Bites)",
    15 to "Under 15 mins 🍽️ (Fresh Cooked)",
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Filter by Prep Time",
          fontWeight = FontWeight.Black,
          fontSize = 18.sp,
          color = BlackPrimary,
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = BlackPrimary)
        }
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Choose your preferred preparation time to quickly grab food during break.",
          fontSize = 12.5.sp,
          color = TextMuted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        options.forEach { (minutes, label) ->
          val isSelected = (currentMinutes == minutes)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(if (isSelected) BlackPrimary.copy(alpha = 0.06f) else SoftGray.copy(alpha = 0.4f))
              .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) BlackPrimary else BorderGray.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
              )
              .clickable {
                onSelectMinutes(minutes)
                onDismiss()
              }
              .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = label,
              fontSize = 13.5.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) BlackPrimary else TextDark,
            )
            if (isSelected) {
              Box(
                modifier = Modifier
                  .size(20.dp)
                  .clip(CircleShape)
                  .background(BlackPrimary),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  tint = PureWhite,
                  modifier = Modifier.size(13.dp),
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = {
        onSelectMinutes(20)
        onDismiss()
      }) {
        Text("Reset Filter", color = TextDark, fontWeight = FontWeight.Bold)
      }
    },
    containerColor = PureWhite,
    shape = RoundedCornerShape(22.dp),
  )
}

/**
 * Suggest a Product section at the bottom of Home screen scrolling content
 * matching Reference Image 1:
 * - Bold muted headline: "didn't find\nwhat you were\nlooking for?"
 * - Friendly subtitle: "Suggest something & we'll look into it"
 * - Rounded white button with pink accent text: "Suggest a Product"
 */
@Composable
private fun SuggestProductSection(
  onSuggestClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 18.dp)
  ) {
    Text(
      text = "didn't find\nwhat you were\nlooking for?",
      fontSize = 24.sp,
      fontWeight = FontWeight.ExtraBold,
      color = Color(0xFF4B4844),
      lineHeight = 30.sp,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Suggest something & we'll look into it",
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      color = TextMuted,
    )

    Spacer(modifier = Modifier.height(14.dp))

    // Blends seamlessly into WarmCream background as pure text (no box) with theme color
    Row(
      modifier = Modifier
        .clickable { onSuggestClick() }
        .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Suggest a Dish or Drink →",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = DrawerAmber,
      )
    }
  }
}
