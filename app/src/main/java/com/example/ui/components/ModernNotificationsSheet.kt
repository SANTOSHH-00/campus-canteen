package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.NotificationItem
import com.example.ui.state.CanteenAppState
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.DeliveryOrangeLight
import com.example.ui.theme.OpenGreen
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import kotlinx.coroutines.delay

/**
 * Modern Notifications Bottom Sheet with sleek slide-in-up animation,
 * clean typography, unread indicators, and categorization.
 */
@Composable
fun ModernNotificationsSheet(
  appState: CanteenAppState,
  onDismiss: () -> Unit,
) {
  var isVisible by remember { mutableStateOf(false) }
  var selectedTab by remember { mutableStateOf("All") } // "All" or "Unread"

  LaunchedEffect(Unit) {
    isVisible = true
  }

  fun triggerDismiss() {
    isVisible = false
  }

  LaunchedEffect(isVisible) {
    if (!isVisible) {
      delay(220)
      onDismiss()
    }
  }

  Dialog(
    onDismissRequest = { triggerDismiss() },
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.50f))
        .clickable { triggerDismiss() },
      contentAlignment = Alignment.BottomCenter,
    ) {
      AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
      ) {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.82f)
            .clickable(enabled = false) {}, // Prevent dismiss when tapping content
          shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
          color = PureWhite,
          shadowElevation = 24.dp,
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 20.dp),
          ) {
            // Drag Handle
            Spacer(modifier = Modifier.height(10.dp))
            Box(
              modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(44.dp)
                .height(4.5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFE2E8F0)),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Header Row: Icon + Title + Actions
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(DeliveryOrangeLight),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = DeliveryOrange,
                    modifier = Modifier.size(20.dp),
                  )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = "Notifications",
                      fontSize = 20.sp,
                      fontWeight = FontWeight.Black,
                      color = BlackPrimary,
                    )
                    val unreadCount = appState.notifications.count { it.isUnread }
                    if (unreadCount > 0) {
                      Spacer(modifier = Modifier.width(8.dp))
                      Box(
                        modifier = Modifier
                          .clip(RoundedCornerShape(12.dp))
                          .background(DeliveryOrange)
                          .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                      ) {
                        Text(
                          text = "$unreadCount new",
                          fontSize = 11.sp,
                          fontWeight = FontWeight.Bold,
                          color = PureWhite,
                        )
                      }
                    }
                  }
                  Text(
                    text = "Order alerts, tokens & kitchen messages",
                    fontSize = 11.5.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium,
                  )
                }
              }

              // Close circular button
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .clip(CircleShape)
                  .background(SoftGray)
                  .clickable { triggerDismiss() },
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Close",
                  tint = TextDark,
                  modifier = Modifier.size(18.dp),
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter Tabs & "Mark all read" row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NotificationTabChip(
                  label = "All (${appState.notifications.size})",
                  isSelected = selectedTab == "All",
                  onClick = { selectedTab = "All" },
                )
                val unreadTotal = appState.notifications.count { it.isUnread }
                if (unreadTotal > 0) {
                  NotificationTabChip(
                    label = "Unread ($unreadTotal)",
                    isSelected = selectedTab == "Unread",
                    onClick = { selectedTab = "Unread" },
                  )
                }
              }

              if (appState.notifications.any { it.isUnread }) {
                TextButton(
                  onClick = { appState.markNotificationsRead() },
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.DoneAll,
                      contentDescription = null,
                      tint = DeliveryOrange,
                      modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "Mark all read",
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = DeliveryOrange,
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val filteredList = if (selectedTab == "Unread") {
              appState.notifications.filter { it.isUnread }
            } else {
              appState.notifications
            }

            if (filteredList.isEmpty()) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .weight(1f),
                contentAlignment = Alignment.Center,
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier.padding(24.dp),
                ) {
                  Box(
                    modifier = Modifier
                      .size(64.dp)
                      .clip(CircleShape)
                      .background(WarmCream),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = Icons.Default.NotificationsNone,
                      contentDescription = null,
                      tint = Color(0xFF94A3B8),
                      modifier = Modifier.size(32.dp),
                    )
                  }
                  Spacer(modifier = Modifier.height(12.dp))
                  Text(
                    text = if (selectedTab == "Unread") "No unread notifications" else "No notifications yet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BlackPrimary,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "When canteen owners update your order, prepare meals, or rush status changes, they'll appear right here.",
                    fontSize = 12.5.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                  )
                }
              }
            } else {
              LazyColumn(
                modifier = Modifier
                  .fillMaxWidth()
                  .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                items(filteredList, key = { it.id }) { notif ->
                  NotificationCardItem(item = notif)
                }
                item {
                  Spacer(modifier = Modifier.height(16.dp))
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun NotificationTabChip(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(20.dp))
      .background(if (isSelected) BlackPrimary else SoftGray)
      .clickable { onClick() }
      .padding(horizontal = 14.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      fontSize = 12.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = if (isSelected) PureWhite else TextDark,
    )
  }
}

@Composable
private fun NotificationCardItem(item: NotificationItem) {
  val isReady = item.title.contains("Ready", ignoreCase = true)
  val isPreparing = item.title.contains("Kitchen", ignoreCase = true) || item.title.contains("Preparing", ignoreCase = true)
  val isOrder = item.title.contains("Order", ignoreCase = true) || item.title.contains("Token", ignoreCase = true)

  val iconColor = when {
    isReady -> OpenGreen
    isPreparing -> DeliveryOrange
    isOrder -> BlackPrimary
    else -> Color(0xFF64748B)
  }

  val iconBg = when {
    isReady -> Color(0xFFE8F5E9)
    isPreparing -> DeliveryOrangeLight
    else -> SoftGray
  }

  val iconVector = when {
    isReady -> Icons.Default.CheckCircle
    isPreparing -> Icons.Default.Fastfood
    isOrder -> Icons.AutoMirrored.Filled.ReceiptLong
    else -> Icons.Default.Notifications
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(if (item.isUnread) Color(0xFFFFFBF8) else PureWhite)
      .border(
        width = if (item.isUnread) 1.2.dp else 1.dp,
        color = if (item.isUnread) DeliveryOrange.copy(alpha = 0.35f) else BorderGray.copy(alpha = 0.7f),
        shape = RoundedCornerShape(16.dp),
      )
      .padding(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Top,
    ) {
      // Type Icon Badge
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(iconBg),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = iconVector,
          contentDescription = null,
          tint = iconColor,
          modifier = Modifier.size(20.dp),
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = item.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = BlackPrimary,
          )

          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = item.timeAgo,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              color = TextMuted,
            )
            if (item.isUnread) {
              Spacer(modifier = Modifier.width(6.dp))
              Box(
                modifier = Modifier
                  .size(7.dp)
                  .clip(CircleShape)
                  .background(DeliveryOrange),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = item.message,
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Normal,
          color = TextDark,
          lineHeight = 17.sp,
        )
      }
    }
  }
}
