package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BottomNavTab
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.DrawerAmberDark
import com.example.ui.theme.PureWhite
import com.example.ui.theme.WarmCream

data class BottomBarTabItem(
  val tab: BottomNavTab,
  val activeIcon: ImageVector,
  val inactiveIcon: ImageVector,
)

/**
 * Modern QuickBite Bottom Navigation Bar:
 * - Clean light WarmCream surface blending seamlessly with page layout canvas (#F6ECDA).
 * - Subtle top divider and soft elevation for clear separation.
 * - Active tab indicator matches the sidebar amber theme (DrawerAmber #BA7A18) with smooth, lag-free transitions.
 * - Dynamic cart counter badge and circular AI Assistant quick-action button.
 */
@Composable
fun CampusBottomNav(
  currentTab: BottomNavTab,
  onTabSelected: (BottomNavTab) -> Unit,
  onAiClick: () -> Unit = {},
  cartBadgeCount: Int = 0,
  modifier: Modifier = Modifier,
) {
  val tabs = remember {
    listOf(
      BottomBarTabItem(BottomNavTab.HOME, Icons.Filled.Home, Icons.Outlined.Home),
      BottomBarTabItem(BottomNavTab.MENU, Icons.Filled.RestaurantMenu, Icons.Outlined.RestaurantMenu),
      BottomBarTabItem(BottomNavTab.CART, Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
      BottomBarTabItem(BottomNavTab.ORDERS, Icons.AutoMirrored.Filled.Assignment, Icons.AutoMirrored.Outlined.Assignment),
    )
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .shadow(elevation = 8.dp)
      .background(WarmCream),
  ) {
    // Crisp top divider
    HorizontalDivider(
      thickness = 1.dp,
      color = BorderGray.copy(alpha = 0.5f),
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // ── Bottom Navigation Tabs (Home, Menu, Cart, Orders) ────────────────
      Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        tabs.forEach { item ->
          val isSelected = (currentTab == item.tab)

          // Smooth transitions matching sidebar theme
          val iconColor by animateColorAsState(
            targetValue = if (isSelected) PureWhite else Color(0xFF757575),
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "tabIconColor",
          )
          val textColor by animateColorAsState(
            targetValue = if (isSelected) DrawerAmberDark else Color(0xFF757575),
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "tabTextColor",
          )
          val pillAlpha by animateFloatAsState(
            targetValue = if (isSelected) 1f else 0f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "tabPillAlpha",
          )
          val iconScale by animateFloatAsState(
            targetValue = if (isSelected) 1.05f else 1.0f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "tabIconScale",
          )

          Column(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onTabSelected(item.tab) },
              )
              .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            // Container with active indicator pill & cart badge
            Box(
              contentAlignment = Alignment.Center,
            ) {
              // Active pill matching sidebar theme
              Box(
                modifier = Modifier
                  .size(width = 46.dp, height = 28.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(DrawerAmber.copy(alpha = pillAlpha)),
              )

              // Balanced iconography
              Icon(
                imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
                contentDescription = item.tab.label,
                tint = iconColor,
                modifier = Modifier
                  .size(22.dp)
                  .scale(iconScale),
              )

              // Cart count badge
              if (item.tab == BottomNavTab.CART && cartBadgeCount > 0) {
                Box(
                  modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-6).dp)
                    .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(CircleShape)
                    .background(DrawerAmber)
                    .border(1.2.dp, PureWhite, CircleShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = if (cartBadgeCount > 99) "99+" else cartBadgeCount.toString(),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                    color = PureWhite,
                    textAlign = TextAlign.Center,
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Label
            Text(
              text = item.tab.label,
              fontSize = 11.5.sp,
              fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
              color = textColor,
              letterSpacing = 0.2.sp,
            )
          }
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      // ── Circular AI Assistant Quick-Action Button ────────────────────────
      Box(
        modifier = Modifier
          .size(46.dp)
          .shadow(elevation = 4.dp, shape = CircleShape)
          .clip(CircleShape)
          .background(DrawerAmber)
          .clickable { onAiClick() },
        contentAlignment = Alignment.Center,
      ) {
        AiAssistantGlyph(
          modifier = Modifier.size(21.dp),
          tint = BlackPrimary,
        )
      }
    }
  }
}

/**
 * Double-loop AI Assistant Glyph matching QuickBite brand design
 */
@Composable
fun AiAssistantGlyph(
  modifier: Modifier = Modifier,
  tint: Color = BlackPrimary,
) {
  Canvas(modifier = modifier) {
    val strokeWidth = 2.4.dp.toPx()
    val pillWidth = size.width * 0.82f
    val pillHeight = size.height * 0.34f
    val cornerRadius = CornerRadius(pillHeight / 2, pillHeight / 2)
    val left = (size.width - pillWidth) / 2

    // Top loop
    drawRoundRect(
      color = tint,
      topLeft = Offset(left, size.height * 0.12f),
      size = Size(pillWidth, pillHeight),
      cornerRadius = cornerRadius,
      style = Stroke(width = strokeWidth),
    )

    // Bottom loop
    drawRoundRect(
      color = tint,
      topLeft = Offset(left, size.height * 0.54f),
      size = Size(pillWidth, pillHeight),
      cornerRadius = cornerRadius,
      style = Stroke(width = strokeWidth),
    )
  }
}
