package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.ShoppingCart
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
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.PureWhite

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
      BottomBarTabItem(BottomNavTab.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
      BottomBarTabItem(BottomNavTab.MENU, "Menu", Icons.Filled.RestaurantMenu, Icons.Outlined.RestaurantMenu),
      BottomBarTabItem(BottomNavTab.ORDERS, "Orders", Icons.AutoMirrored.Filled.Assignment, Icons.AutoMirrored.Outlined.Assignment),
      BottomBarTabItem(BottomNavTab.CART, "Cart", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
    )
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 14.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // ── 1. Floating Capsule for Main Destinations (Home, Menu, Orders, Cart) ──
    Surface(
      modifier = Modifier
        .weight(1f)
        .shadow(
          elevation = 8.dp,
          shape = RoundedCornerShape(28.dp),
          clip = false,
        ),
      shape = RoundedCornerShape(28.dp),
      color = PureWhite,
      tonalElevation = 0.dp,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        tabs.forEach { item ->
          val isSelected = (currentTab == item.tab) ||
              (item.tab == BottomNavTab.MENU && currentTab == BottomNavTab.CANTEENS)

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
            targetValue = if (isSelected) PureWhite else Color(0xFF6B6864),
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            label = "tabIconTint_${item.tab}",
          )

          Box(
            modifier = Modifier
              .weight(1f)
              .height(44.dp)
              .clip(RoundedCornerShape(16.dp))
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onTabSelected(item.tab) },
              )
              .semantics { contentDescription = item.label },
            contentAlignment = Alignment.Center,
          ) {
            // Subtle, balanced selected indicator pill
            Box(
              modifier = Modifier
                .size(width = 46.dp, height = 34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(DrawerAmber.copy(alpha = pillAlpha)),
            )

            // Medium-sized Icon (24.dp)
            Icon(
              imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
              contentDescription = item.label,
              tint = iconTint,
              modifier = Modifier
                .size(24.dp)
                .scale(iconScale),
            )

            // Live Red Badge for Cart
            if (item.tab == BottomNavTab.CART && cartBadgeCount > 0) {
              Box(
                modifier = Modifier
                  .align(Alignment.Center)
                  .offset(x = 12.dp, y = (-10).dp)
                  .size(16.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFE53935)),
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
      }
    }

    // ── 2. Distinctive MunchMate Floating Button (ONLY visible on Home screen) ──
    AnimatedVisibility(
      visible = isHome,
      enter = scaleIn(tween(durationMillis = 240, easing = FastOutSlowInEasing)) + fadeIn(tween(240)),
      exit = scaleOut(tween(durationMillis = 200, easing = FastOutSlowInEasing)) + fadeOut(tween(200)),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Spacer(modifier = Modifier.width(8.dp))

        Box(
          modifier = Modifier
            .size(48.dp)
            .shadow(
              elevation = 8.dp,
              shape = CircleShape,
              spotColor = DrawerAmber.copy(alpha = 0.35f),
            )
            .clip(CircleShape)
            .background(PureWhite)
            .border(
              width = 2.dp,
              brush = Brush.sweepGradient(
                colors = listOf(
                  Color(0xFFF59E0B),
                  Color(0xFFEA580C),
                  Color(0xFFE11D48),
                  Color(0xFF8B5CF6),
                  Color(0xFFF59E0B),
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
          // Exact recreated Fork and Knife MunchMate Vector Icon
          MunchMateIcon(
            modifier = Modifier.size(24.dp),
            tint = Color(0xFF2E5A44),
          )
        }
      }
    }
  }
}
