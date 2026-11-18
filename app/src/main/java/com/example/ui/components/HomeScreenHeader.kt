// HomeScreenHeader: Jetpack Compose screen for HomeHeader user interface
package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.state.CanteenAppState
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import java.util.Calendar

/**
 * Modern Top Bar for the Home Screen:
 * - Restored 3-lines staggered menu icon on the left
 * - Bolder, larger QuickBite branding logo & typography
 * - Bell icon with unread notification badge on the right
 */
@Composable
fun HomeScreenTopBar(
  appState: CanteenAppState,
  onProfileClick: () -> Unit = {},
  onNotificationClick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Top Left: 3-lines Staggered Menu Icon + QuickBite Logo + Bold App Name
    Row(
      verticalAlignment = Alignment.CenterVertically,
    ) {
      StaggeredMenuIcon(
        onClick = onProfileClick,
        tint = BlackPrimary,
      )

      Spacer(modifier = Modifier.width(8.dp))

      QuickBiteLogoIcon(modifier = Modifier.size(36.dp))

      Spacer(modifier = Modifier.width(8.dp))

      Text(
        text = "QuickBite",
        fontSize = 26.sp,
        fontWeight = FontWeight.Black,
        color = BlackPrimary,
        letterSpacing = (-0.6).sp,
      )
    }

    // Top Right: Modern Premium Tactile Notification Bell with Badge (Guest mode strictly suppresses badges)
    val unreadCount = if (appState.isGuest) 0 else appState.notifications.count { it.isUnread }
    Box(contentAlignment = Alignment.Center) {
      Box(
        modifier = Modifier
          .size(42.dp)
          .clip(CircleShape)
          .background(PureWhite)
          .border(1.dp, if (unreadCount > 0) DrawerAmber.copy(alpha = 0.5f) else BorderGray.copy(alpha = 0.85f), CircleShape)
          .clickable(onClick = onNotificationClick),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Outlined.Notifications,
          contentDescription = "Notifications",
          tint = if (unreadCount > 0) DrawerAmber else BlackPrimary,
          modifier = Modifier.size(22.dp),
        )
      }
      if (unreadCount > 0) {
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 2.dp, y = (-2).dp)
            .size(if (unreadCount > 9) 19.dp else 17.dp)
            .clip(CircleShape)
            .background(DrawerAmber)
            .border(1.5.dp, PureWhite, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = if (unreadCount > 9) "9+" else unreadCount.toString(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = PureWhite,
          )
        }
      }
    }
  }
}

/**
 * Home Greeting Section:
 * - Restored original dynamic time greetings (Good Morning / Afternoon / Evening)
 * - Prominent student name with wave emoji
 * - Subtitle guidance
 * - Canteen selection placed beside the greetings, seamlessly combined with WarmCream background (no box card)
 */
@Composable
fun HomeGreetingSection(
  userName: String = "Alex",
  appState: CanteenAppState? = null,
  modifier: Modifier = Modifier,
) {
  var showCanteenSelectorSheet by remember { mutableStateOf(false) }

  val timeGreeting = remember {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    when {
      hour < 12 -> "Good Morning"
      hour < 17 -> "Good Afternoon"
      else -> "Good Evening"
    }
  }

  val isGuest = userName.isBlank() || userName.equals("Guest", ignoreCase = true)
  val salutation = if (isGuest) "Welcome," else "$timeGreeting,"
  val studentName = if (isGuest) "Food Lover" else userName.trim()

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(1.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = salutation,
        fontSize = 15.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextMuted,
        letterSpacing = 0.2.sp,
      )

      // Canteen selection combined seamlessly with background (no box/card)
      if (appState != null) {
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { showCanteenSelectorSheet = true }
            .padding(horizontal = 6.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = "Location",
            tint = DrawerAmber,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "${appState.selectedCanteen.name}, ${appState.selectedCanteen.betweenBlocks}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(modifier = Modifier.width(2.dp))
          Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Change Canteen",
            tint = TextDark,
            modifier = Modifier.size(17.dp),
          )
        }
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = studentName,
        fontSize = 28.sp,
        fontWeight = FontWeight.Black,
        color = BlackPrimary,
        letterSpacing = (-0.5).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "👋",
        fontSize = 24.sp,
      )
    }

    Text(
      text = "What would you like to eat today?",
      fontSize = 14.5.sp,
      fontWeight = FontWeight.Medium,
      color = TextMuted,
      letterSpacing = 0.1.sp,
      modifier = Modifier.padding(top = 2.dp),
    )
  }

  // Intermediate Block Canteen Selector Sheet
  if (showCanteenSelectorSheet && appState != null) {
    BlockCanteenSelectorSheet(
      currentCanteen = appState.selectedCanteen,
      currentBlock = appState.studentCurrentBlock,
      allCanteens = appState.allCanteens,
      onCanteenSelected = { canteen, block ->
        appState.selectCanteen(canteen)
        if (block != null) appState.studentCurrentBlock = block
        showCanteenSelectorSheet = false
      },
      onDismiss = { showCanteenSelectorSheet = false },
      onRefresh = { appState.refreshHomeData() },
      isRefreshing = appState.isHomeRefreshing,
    )
  }
}

/**
 * Legacy & backwards-compatible HomeScreenHeader.
 */
@Composable
fun HomeScreenHeader(
  userName: String = "Alex",
  appState: CanteenAppState,
  onProfileClick: () -> Unit = {},
  onNotificationClick: () -> Unit = {},
  includeGreeting: Boolean = true,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth()
  ) {
    HomeScreenTopBar(
      appState = appState,
      onProfileClick = onProfileClick,
      onNotificationClick = onNotificationClick,
    )

    if (includeGreeting) {
      Spacer(modifier = Modifier.height(10.dp))
      HomeGreetingSection(
        userName = userName,
        appState = appState,
      )
    }
  }
}

/**
 * Modern design logo icon beside QuickBite app name:
 * Stylish squircle with obsidian slate fill and food glyph.
 */
@Composable
fun QuickBiteLogoIcon(
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .size(34.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(BlackPrimary),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Default.Fastfood,
      contentDescription = "QuickBite Logo",
      tint = PureWhite,
      modifier = Modifier.size(20.dp),
    )
  }
}
