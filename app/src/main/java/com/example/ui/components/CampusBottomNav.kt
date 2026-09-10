package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BottomNavTab
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.DeliveryOrangeLight
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream

data class BottomBarTabItem(
  val tab: BottomNavTab,
  val activeIcon: ImageVector,
  val inactiveIcon: ImageVector,
)

/**
 * Bottom Navigation Bar:
 * - Restored to the previous clean bar with icon + text label underneath (Home, Menu, Cart, Orders)
 * - Styled with WarmCream background matching the page layout color seamlessly
 * - Retains the separate circular AI Assistant button on the right matching Image 2
 */
@Composable
fun CampusBottomNav(
  currentTab: BottomNavTab,
  onTabSelected: (BottomNavTab) -> Unit,
  onAiClick: () -> Unit = {},
  cartBadgeCount: Int = 0,
  modifier: Modifier = Modifier,
) {
  val tabs = listOf(
    BottomBarTabItem(BottomNavTab.HOME, Icons.Filled.Home, Icons.Outlined.Home),
    BottomBarTabItem(BottomNavTab.MENU, Icons.Filled.RestaurantMenu, Icons.Outlined.RestaurantMenu),
    BottomBarTabItem(BottomNavTab.CART, Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
    BottomBarTabItem(BottomNavTab.ORDERS, Icons.AutoMirrored.Filled.Assignment, Icons.AutoMirrored.Outlined.Assignment),
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .shadow(elevation = 10.dp)
      .background(WarmCream)
  ) {
    // Crisp top divider matching previous bottom nav
    HorizontalDivider(
      thickness = 1.dp,
      color = BorderGray.copy(alpha = 0.5f),
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // ── Previous-Style Bottom Navigation Tabs (Home, Menu, Cart, Orders) ──
      Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        tabs.forEach { item ->
          val isSelected = (currentTab == item.tab)
          val iconColor = if (isSelected) BlackPrimary else Color(0xFF757575)
          val textColor = if (isSelected) BlackPrimary else Color(0xFF757575)
          val textWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold

          Column(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .clickable { onTabSelected(item.tab) }
              .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            // Soft rounded container matching the circular AI button geometry when active
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) Color(0xFFE8E2D9) else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 3.dp),
              contentAlignment = Alignment.Center,
            ) {
              if (item.tab == BottomNavTab.CART && cartBadgeCount > 0) {
                BadgedBox(
                  badge = {
                    Badge(
                      containerColor = BlackPrimary,
                      contentColor = PureWhite,
                    ) {
                      Text(
                        text = if (cartBadgeCount > 99) "99+" else cartBadgeCount.toString(),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                      )
                    }
                  }
                ) {
                  Icon(
                    imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
                    contentDescription = item.tab.label,
                    tint = iconColor,
                    modifier = Modifier.size(23.dp),
                  )
                }
              } else {
                Icon(
                  imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
                  contentDescription = item.tab.label,
                  tint = iconColor,
                  modifier = Modifier.size(23.dp),
                )
              }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
              text = item.tab.label,
              fontSize = 11.5.sp,
              fontWeight = textWeight,
              color = textColor,
            )
          }
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      // ── Separate Circular AI Assistant Button (Image 2 style) ──────────────
      Box(
        modifier = Modifier
          .size(48.dp)
          .shadow(elevation = 5.dp, shape = CircleShape)
          .clip(CircleShape)
          .background(DrawerAmber)
          .clickable { onAiClick() },
        contentAlignment = Alignment.Center,
      ) {
        AiAssistantGlyph(
          modifier = Modifier.size(22.dp),
          tint = BlackPrimary,
        )
      }
    }
  }
}

/**
 * Double-loop AI Assistant Glyph exactly matching Image 2
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
