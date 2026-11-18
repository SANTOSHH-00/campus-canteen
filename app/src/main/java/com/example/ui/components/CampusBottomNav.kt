// CampusBottomNav: Reusable Jetpack Compose UI component
package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.DrawerAmberDark
import com.example.ui.theme.DrawerAmberLight
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.PureWhite
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.BottomNavTab

data class BottomBarTabItem(
  val tab: BottomNavTab,
  val label: String,
  val activeIcon: ImageVector,
  val inactiveIcon: ImageVector,
)

/**
 * Modern QuickBite Dynamic Bottom Navigation Bar (Minimal Icon-Only Design):
 *
 * Requirements:
 * - Icon-only design (no text labels).
 * - Moderately larger balanced icons (24.dp).
 * - Subtle background pill indicator for selected icon.
 * - 4 core destinations inside the navigation capsule:
 *   1. Home
 *   2. Menu
 *   3. Orders
 *   4. Cart (with live red item badge when count > 0)
 *
 * - MunchMate AI Assistant:
 *   - On the Home screen: MunchMate is visible at the extreme right as a dedicated
 *     circular floating button displaying the exact original fork-and-knife logo.
 *   - On other screens (Menu, Orders, Cart): MunchMate is hidden, allowing the 4 tabs
 *     to expand smoothly across the width.
 *   - When MunchMate bottom sheet is open: The bottom navigation bar is completely hidden.
 */
@Composable
fun CampusBottomNav(
  currentTab: BottomNavTab,
  onTabSelected: (BottomNavTab) -> Unit,
  onAiClick: () -> Unit = {},
  cartBadgeCount: Int = 0,
  modifier: Modifier = Modifier,
) {
  val isHome = (currentTab == BottomNavTab.HOME)

  val tabs = remember {
    listOf(
      BottomBarTabItem(BottomNavTab.HOME, "Home", Icons.Outlined.Home, Icons.Outlined.Home),
      BottomBarTabItem(BottomNavTab.MENU, "Search", Icons.Outlined.Search, Icons.Outlined.Search),
      BottomBarTabItem(BottomNavTab.CART, "Cart", Icons.Outlined.ShoppingCart, Icons.Outlined.ShoppingCart),
      BottomBarTabItem(BottomNavTab.ORDERS, "Orders", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    )
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      modifier = Modifier
        .weight(1f)
        .shadow(
          elevation = 10.dp,
          shape = RoundedCornerShape(30.dp),
          clip = false,
        ),
      shape = RoundedCornerShape(30.dp),
      color = PureWhite,
      tonalElevation = 0.dp,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        tabs.forEach { item ->
          val isSelected = (currentTab == item.tab) ||
              (item.tab == BottomNavTab.MENU && currentTab == BottomNavTab.CANTEENS)

          BottomNavTabCell(
            item = item,
            isSelected = isSelected,
            cartBadgeCount = cartBadgeCount,
            onClick = { onTabSelected(item.tab) },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    // Smooth width expansion & shrinking synchronized with the main capsule's weight layout
    AnimatedVisibility(
      visible = isHome,
      enter = expandHorizontally(
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        expandFrom = Alignment.End,
        clip = true,
      ) + scaleIn(
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        initialScale = 0.6f,
      ) + fadeIn(
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
      ),
      exit = shrinkHorizontally(
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        shrinkTowards = Alignment.End,
        clip = true,
      ) + scaleOut(
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        targetScale = 0.6f,
      ) + fadeOut(
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
      ),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Spacer(modifier = Modifier.width(8.dp))

        Box(
          modifier = Modifier
            .size(54.dp)
            .shadow(
              elevation = 10.dp,
              shape = CircleShape,
              spotColor = DrawerAmber.copy(alpha = 0.35f),
            )
            .clip(CircleShape)
            .background(PureWhite)
            .border(
              width = 2.dp,
              brush = Brush.sweepGradient(
                colors = listOf(
                  DrawerAmber,
                  GoldenAmber,
                  DrawerAmberDark,
                  GoldenAmber,
                  DrawerAmber,
                )
              ),
              shape = CircleShape,
            )
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = { onAiClick() },
            )
            .semantics { contentDescription = "Open MunchMate AI Assistant" },
          contentAlignment = Alignment.Center,
        ) {
          // Extracted Chef Bot MunchMate Vector Icon
          MunchMateIcon(
            modifier = Modifier.size(40.dp),
          )
        }
      }
    }
  }
}

/**
 * Isolated Tab Item Composable to optimize recomposition performance:
 * Only the tab that changed selection re-evaluates its animation states,
 * completely avoiding row-wide recomposition cascades during navigation.
 */
@Composable
private fun BottomNavTabCell(
  item: BottomBarTabItem,
  isSelected: Boolean,
  cartBadgeCount: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val iconScale by animateFloatAsState(
    targetValue = if (isSelected) 1.08f else 1.0f,
    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
    label = "tabScale_${item.tab}",
  )

  val pillAlpha by animateFloatAsState(
    targetValue = if (isSelected) 1f else 0f,
    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
    label = "tabPillAlpha_${item.tab}",
  )

  val iconTint by animateColorAsState(
    targetValue = if (isSelected) DrawerAmber else Color(0xFF8E8E93),
    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
    label = "tabIconTint_${item.tab}",
  )

  Box(
    modifier = modifier
      .height(48.dp)
      .clip(RoundedCornerShape(18.dp))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
      )
      .semantics { contentDescription = item.label },
    contentAlignment = Alignment.Center,
  ) {
    // Subtle, balanced selected indicator pill in app theme
    Box(
      modifier = Modifier
        .size(width = 50.dp, height = 36.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(DrawerAmberLight.copy(alpha = pillAlpha)),
    )

    // Medium-sized Icon (25.dp)
    Icon(
      imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
      contentDescription = item.label,
      tint = iconTint,
      modifier = Modifier
        .size(25.dp)
        .scale(iconScale),
    )

    // Live Badge for Cart
    if (item.tab == BottomNavTab.CART && cartBadgeCount > 0) {
      Box(
        modifier = Modifier
          .align(Alignment.Center)
          .offset(x = 12.dp, y = (-10).dp)
          .size(16.dp)
          .clip(CircleShape)
          .background(DrawerAmber),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = if (cartBadgeCount > 99) "99+" else cartBadgeCount.toString(),
          color = PureWhite,
          fontSize = 8.5.sp,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}
