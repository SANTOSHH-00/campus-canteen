package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BottomNavTab
import com.example.ui.components.CampusBottomNav
import com.example.ui.state.CanteenAppState
import com.example.ui.state.rememberCanteenAppState
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.PureWhite
import com.example.ui.theme.WarmCream
import kotlinx.coroutines.delay

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.components.AiAssistantGlyph
import com.example.ui.components.StudentProfileDrawer
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.GoldenAmber

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext

@Composable
fun MainScreen(
  appState: CanteenAppState = rememberCanteenAppState(),
  userName: String = appState.displayUserName,
  onLogout: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var isProfileDrawerOpen by remember { mutableStateOf(false) }
  var showAiAssistantDialog by remember { mutableStateOf(false) }
  val context = LocalContext.current

  // Handle system back swiping / back gesture gracefully
  BackHandler(enabled = true) {
    when {
      isProfileDrawerOpen -> {
        isProfileDrawerOpen = false
      }
      appState.selectedOrderForTracking != null -> {
        appState.closeOrderTracking()
      }
      appState.selectedFoodForDetail != null -> {
        appState.closeFoodDetail()
      }
      appState.isMenuFilterOpen -> {
        appState.isMenuFilterOpen = false
      }
      appState.currentTab != BottomNavTab.HOME -> {
        // When swiping back from Menu, Orders, or Cart, navigate back to Home
        appState.currentTab = BottomNavTab.HOME
        appState.isBottomBarVisible = true
      }
      else -> {
        // When already on Home, minimize app to background instead of popping back to Welcome/Login
        (context as? android.app.Activity)?.moveTaskToBack(true)
      }
    }
  }

  // Auto-dismiss floating cart pill after 3.5 seconds
  LaunchedEffect(appState.cartNotificationMessage) {
    if (appState.cartNotificationMessage != null) {
      delay(3500)
      appState.dismissCartNotification()
    }
  }

  val isDetailOrTracking = appState.selectedFoodForDetail != null || appState.selectedOrderForTracking != null || appState.isMenuFilterOpen
  val isHomeTab = appState.currentTab == BottomNavTab.HOME && !isDetailOrTracking

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream)
  ) {
    // ── Screen Content Container ──────────────────────────────────────────────
    Box(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .padding(
          bottom = if (!isDetailOrTracking && !isHomeTab) 78.dp else 0.dp
        )
    ) {
      if (appState.selectedOrderForTracking != null) {
        val trackingOrder = appState.selectedOrderForTracking!!
        CustomerOrderTrackingScreen(
          order = trackingOrder,
          onBack = { appState.closeOrderTracking() },
          onOrderAgain = {
            appState.closeOrderTracking()
            appState.currentTab = BottomNavTab.MENU
          },
        )
      } else if (appState.selectedFoodForDetail != null) {
        val selectedItem = appState.selectedFoodForDetail!!
        FoodDetailScreen(
          foodItem = selectedItem,
          isFavorite = appState.isItemFavorite(selectedItem.id),
          onBack = { appState.closeFoodDetail() },
          onToggleFavorite = { appState.toggleFavorite(it) },
          onAddToCart = { qty, option, addons ->
            appState.addCustomizedToCart(
              item = selectedItem,
              quantity = qty,
              selectedOption = option,
              selectedAddons = addons,
            )
          },
        )
      } else {
        // ── Tab Screen Content ──────────────────────────────────────────────
        when (appState.currentTab) {
          BottomNavTab.HOME -> {
            HomeScreen(
              userName = userName,
              appState = appState,
              onNavigateToTab = { tab -> appState.currentTab = tab },
              onOpenProfileDrawer = { isProfileDrawerOpen = true },
            )
          }
          BottomNavTab.MENU -> {
            MenuScreen(
              appState = appState,
            )
          }
          BottomNavTab.CART -> {
            CartScreen(
              appState = appState,
              onNavigateToTab = { tab -> appState.currentTab = tab },
            )
          }
          BottomNavTab.ORDERS -> {
            OrdersScreen(
              appState = appState,
              onNavigateToTab = { tab -> appState.currentTab = tab },
              onOrderClick = { order -> appState.openOrderTracking(order) },
            )
          }
          BottomNavTab.PROFILE -> {
            // Direct to home and open side drawer seamlessly
            LaunchedEffect(Unit) {
              appState.currentTab = BottomNavTab.HOME
              isProfileDrawerOpen = true
            }
            HomeScreen(
              userName = userName,
              appState = appState,
              onNavigateToTab = { tab -> appState.currentTab = tab },
              onOpenProfileDrawer = { isProfileDrawerOpen = true },
            )
          }
        }
      }
    }

    // ── Bottom Navigation Bar (Animated slide down on Home tab only) ─────────
    if (!isDetailOrTracking) {
      val slideFraction by animateFloatAsState(
        targetValue = if (isHomeTab && !appState.isBottomBarVisible) 1f else 0f,
        animationSpec = tween(
          durationMillis = 280,
          easing = FastOutSlowInEasing
        ),
        label = "bottomBarSlide"
      )

      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .graphicsLayer {
            translationY = slideFraction * size.height
          }
      ) {
        CampusBottomNav(
          currentTab = appState.currentTab,
          onTabSelected = { tab ->
            appState.currentTab = tab
            appState.isBottomBarVisible = true
            appState.dismissCartNotification()
          },
          onAiClick = {
            showAiAssistantDialog = true
          },
          cartBadgeCount = appState.totalCartCount,
        )
      }
    }

    // ── Modern Floating Quick-Cart Capsule (Replaces ugly black snackbar) ──
    AnimatedVisibility(
      visible = appState.cartNotificationMessage != null && appState.currentTab != BottomNavTab.CART && !appState.isMenuFilterOpen,
      enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
      exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(
          start = 16.dp,
          end = 16.dp,
          bottom = if (isHomeTab && !appState.isBottomBarVisible) 16.dp else 88.dp
        ),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(14.dp, RoundedCornerShape(22.dp))
          .clip(RoundedCornerShape(22.dp))
            .background(BlackPrimary)
            .border(1.dp, androidx.compose.ui.graphics.Color(0xFF2A2826), RoundedCornerShape(22.dp))
            .clickable {
              appState.dismissCartNotification()
              appState.currentTab = BottomNavTab.CART
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f),
            ) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(DrawerAmber),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.ShoppingCart,
                  contentDescription = null,
                  tint = PureWhite,
                  modifier = Modifier.size(20.dp),
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Added ${appState.cartNotificationItemName}!",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = PureWhite,
                  maxLines = 1,
                )
                Text(
                  text = "${appState.totalCartCount} item${if (appState.totalCartCount > 1) "s" else ""} in cart · ₹${appState.grandTotal}",
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = PureWhite.copy(alpha = 0.8f),
                )
              }
            }

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(DrawerAmber)
                .padding(horizontal = 14.dp, vertical = 8.dp),
              contentAlignment = Alignment.Center,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "View Cart",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = PureWhite,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                  imageVector = Icons.Default.ChevronRight,
                  contentDescription = null,
                  tint = PureWhite,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }
        }
      }

    // ── Student Profile Side Drawer (Image 1) ────────────────────────────────
    StudentProfileDrawer(
      isOpen = isProfileDrawerOpen,
      onClose = { isProfileDrawerOpen = false },
      appState = appState,
      userName = userName,
      onNavigateToTab = { tab -> appState.currentTab = tab },
      onLogout = onLogout,
    )

    // ── Clean AI Assistant Feature Placeholder (Image 2) ─────────────────────
    if (showAiAssistantDialog) {
      AlertDialog(
        onDismissRequest = { showAiAssistantDialog = false },
        icon = {
          Box(
            modifier = Modifier
              .size(54.dp)
              .clip(CircleShape)
              .background(DrawerAmber),
            contentAlignment = Alignment.Center,
          ) {
            AiAssistantGlyph(
              modifier = Modifier.size(24.dp),
              tint = BlackPrimary,
            )
          }
        },
        title = {
          Text(
            text = "QuickBite AI Assistant",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            color = BlackPrimary,
          )
        },
        text = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(WarmCream)
              .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = "Feature Ready",
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = DrawerAmber,
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "The AI Assistant module has been reserved for your custom smart features. You can integrate meal recommendations, queue estimates, and smart voice ordering here.",
              fontSize = 13.sp,
              color = BlackPrimary.copy(alpha = 0.8f),
              lineHeight = 18.sp,
            )
          }
        },
        confirmButton = {
          Button(
            onClick = { showAiAssistantDialog = false },
            colors = ButtonDefaults.buttonColors(containerColor = BlackPrimary),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text("Got it", fontWeight = FontWeight.Bold, color = PureWhite)
          }
        },
        containerColor = PureWhite,
        shape = RoundedCornerShape(24.dp),
      )
    }
  }
}

