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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.state.CanteenAppState
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BreakCardBg
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.OpenGreen
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted

@Composable
fun HomeScreenHeader(
  userName: String = "Alex",
  appState: CanteenAppState,
  onProfileClick: () -> Unit = {},
  onNotificationClick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var showCanteenSelectorSheet by remember { mutableStateOf(false) }

  Column(
    modifier = modifier.fillMaxWidth()
  ) {
    // ── Row 1: Staggered Menu Icon (Image 3) + App Brand & Logo + Location & Bell ───
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Top Left: 3-half design staggered icon + Design Logo + QuickBite Name
      Row(
        verticalAlignment = Alignment.CenterVertically,
      ) {
        StaggeredMenuIcon(
          onClick = onProfileClick,
          tint = BlackPrimary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        QuickBiteLogoIcon(modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(7.dp))
        Text(
          text = "QuickBite",
          fontSize = 24.sp,
          fontWeight = FontWeight.Black,
          color = BlackPrimary,
          letterSpacing = (-0.6).sp,
        )
      }

      // Top Right: Location Pill (Image 3) + Notification Bell
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Interactive Block Canteen Location Dropdown / Pill
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(PureWhite)
            .border(1.2.dp, BorderGray.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
            .clickable { showCanteenSelectorSheet = true }
            .padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(appState.selectedCanteen.icon, fontSize = 13.sp)
          Spacer(modifier = Modifier.width(5.dp))
          Text(
            text = appState.selectedCanteen.betweenBlocks,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextDark,
          )
          Spacer(modifier = Modifier.width(4.dp))
          Icon(
            imageVector = if (showCanteenSelectorSheet) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
            contentDescription = "Select Canteen",
            tint = TextDark,
            modifier = Modifier.size(16.dp),
          )
        }

        // Bell Icon with unread count badge
        Box(contentAlignment = Alignment.Center) {
          IconButton(
            onClick = onNotificationClick,
            modifier = Modifier.size(38.dp),
          ) {
            Icon(
              imageVector = Icons.Default.NotificationsNone,
              contentDescription = "Notifications",
              tint = BlackPrimary,
              modifier = Modifier.size(24.dp),
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
                .background(DeliveryOrange),
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

    Spacer(modifier = Modifier.height(8.dp))

    val timeGreeting = remember {
      val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
      when {
        hour < 12 -> "Good Morning"
        hour < 17 -> "Good Afternoon"
        else -> "Good Evening"
      }
    }

    val greeting = if (userName.isBlank() || userName.equals("Guest", ignoreCase = true)) {
      "Welcome to QuickBite 👋"
    } else {
      "$timeGreeting, $userName 👋"
    }

    Text(
      text = greeting,
      fontSize = 24.sp,
      fontWeight = FontWeight.ExtraBold,
      color = BlackPrimary,
      letterSpacing = (-0.3).sp,
    )

    Spacer(modifier = Modifier.height(2.dp))

    Text(
      text = "What would you like to eat today?",
      fontSize = 13.sp,
      fontWeight = FontWeight.Medium,
      color = TextMuted,
    )
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
 * Modern design logo icon beside QuickBite app name:
 * Stylish squircle with flame-orange gradient and bite/food glyph.
 */
@Composable
fun QuickBiteLogoIcon(
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .size(28.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(BlackPrimary),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Default.Fastfood,
      contentDescription = "QuickBite Logo",
      tint = PureWhite,
      modifier = Modifier.size(17.dp),
    )
  }
}


