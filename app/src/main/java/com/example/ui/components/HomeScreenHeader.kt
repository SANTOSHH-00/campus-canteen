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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.NotificationsNone
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.state.CanteenAppState
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted

/**
 * Modern Top Bar for the Home Screen:
 * Displays the navigation drawer icon, QuickBite branding logo,
 * interactive Canteen location pill, and notifications bell with badge.
 * Designed with a flat, clean, premium aesthetic without unnecessary borders or shadows.
 */
@Composable
fun HomeScreenTopBar(
  appState: CanteenAppState,
  onProfileClick: () -> Unit = {},
  onNotificationClick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var showCanteenSelectorSheet by remember { mutableStateOf(false) }

  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Top Left: Staggered Menu Icon + App Brand Logo + QuickBite Name
    Row(
      verticalAlignment = Alignment.CenterVertically,
    ) {
      StaggeredMenuIcon(
        onClick = onProfileClick,
        tint = BlackPrimary,
      )
      Spacer(modifier = Modifier.width(8.dp))
      QuickBiteLogoIcon(modifier = Modifier.size(34.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "QuickBite",
        fontSize = 24.sp,
        fontWeight = FontWeight.Black,
        color = BlackPrimary,
        letterSpacing = (-0.5).sp,
      )
    }

    // Top Right: Location Pill + Notification Bell
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Interactive Block Canteen Location Dropdown / Pill
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(20.dp))
          .background(PureWhite)
          .border(1.2.dp, BorderGray.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
          .clickable { showCanteenSelectorSheet = true }
          .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(appState.selectedCanteen.icon, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(5.dp))
        Text(
          text = appState.selectedCanteen.betweenBlocks,
          fontSize = 13.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
          imageVector = if (showCanteenSelectorSheet) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
          contentDescription = "Select Canteen",
          tint = TextDark,
          modifier = Modifier.size(18.dp),
        )
      }

      // Bell Icon with unread count badge
      Box(contentAlignment = Alignment.Center) {
        IconButton(
          onClick = onNotificationClick,
          modifier = Modifier.size(42.dp),
        ) {
          Icon(
            imageVector = Icons.Default.NotificationsNone,
            contentDescription = "Notifications",
            tint = BlackPrimary,
            modifier = Modifier.size(26.dp),
          )
        }
        val unreadCount = appState.notifications.count { it.isUnread }
        if (unreadCount > 0) {
          Box(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(top = 4.dp, end = 4.dp)
              .size(if (unreadCount > 9) 18.dp else 16.dp)
              .clip(CircleShape)
              .background(GoldenAmber),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = if (unreadCount > 9) "9+" else unreadCount.toString(),
              fontSize = 9.5.sp,
              fontWeight = FontWeight.Bold,
              color = PureWhite,
            )
          }
        }
      }
    }
  }

  // Intermediate Block Canteen Selector Sheet
  if (showCanteenSelectorSheet) {
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
    )
  }
}

/**
 * Redesigned Home Greeting Section:
 * Features clear visual hierarchy, prominent student name typography,
 * dynamic local-time greetings (Good Morning / Good Afternoon / Good Evening),
 * and completely seamless background integration without box-like borders or horizontal lines.
 */
@Composable
fun HomeGreetingSection(
  userName: String = "Alex",
  modifier: Modifier = Modifier,
) {
  val timeGreeting = remember {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
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
    // ── 1. Salutation: Dynamic time greeting in elegant muted slate ────
    Text(
      text = salutation,
      fontSize = 15.sp,
      fontWeight = FontWeight.SemiBold,
      color = TextMuted,
      letterSpacing = 0.2.sp,
      lineHeight = 20.sp,
    )

    // ── 2. Prominent Student Name with friendly wave emoji ─────────────
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = studentName,
        fontSize = 26.sp,
        fontWeight = FontWeight.Black,
        color = BlackPrimary,
        letterSpacing = (-0.5).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = "👋",
        fontSize = 22.sp,
      )
    }

    // ── 3. Subtitle: Contextual Guidance ──────────────────────────────
    Text(
      text = "What would you like to eat today?",
      fontSize = 13.5.sp,
      fontWeight = FontWeight.Medium,
      color = TextMuted,
      letterSpacing = 0.1.sp,
      modifier = Modifier.padding(top = 2.dp),
    )
  }
}

/**
 * Legacy & backwards-compatible HomeScreenHeader.
 * Composes HomeScreenTopBar and optional HomeGreetingSection together.
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
