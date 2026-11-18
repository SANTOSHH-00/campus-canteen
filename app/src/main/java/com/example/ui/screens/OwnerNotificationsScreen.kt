// OwnerNotificationsScreen: Jetpack Compose screen for OwnerNotifications user interface
package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.DrawerAmberLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream

data class OwnerNotificationItem(
  val id: String,
  val title: String,
  val description: String,
  val timeAgo: String,
  val icon: ImageVector,
  val iconTint: Color,
  val iconBg: Color,
  var isRead: Boolean = false,
)

@Composable
fun OwnerNotificationsScreen(
  canteenName: String = "Campus Canteen",
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val notifications = remember {
    mutableStateListOf(
      OwnerNotificationItem(
        id = "n1",
        title = "New Order Placed",
        description = "Order #Q1048 received with 2 items (₹180). Estimated prep: 10 min.",
        timeAgo = "5m ago",
        icon = Icons.Default.Notifications,
        iconTint = DrawerAmber,
        iconBg = DrawerAmberLight,
      ),
      OwnerNotificationItem(
        id = "n2",
        title = "Order Picked Up",
        description = "Token #Q1042 was successfully verified and collected at Counter 1.",
        timeAgo = "20m ago",
        icon = Icons.Default.CheckCircle,
        iconTint = Color(0xFF16A34A),
        iconBg = Color(0xFFF0FDF4),
      ),
      OwnerNotificationItem(
        id = "n3",
        title = "Low Stock Alert",
        description = "Masala Dosa is running low on ingredients (3 portions left).",
        timeAgo = "1h ago",
        icon = Icons.Default.Warning,
        iconTint = Color(0xFFEA580C),
        iconBg = Color(0xFFFFF7ED),
      ),
      OwnerNotificationItem(
        id = "n4",
        title = "New Product Suggestion",
        description = "A student suggested adding 'Iced Hazelnut Latte' to the menu.",
        timeAgo = "3h ago",
        icon = Icons.Default.Lightbulb,
        iconTint = Color(0xFF3B82F6),
        iconBg = Color(0xFFEFF6FF),
      ),
      OwnerNotificationItem(
        id = "n5",
        title = "Daily Settlement Ready",
        description = "Yesterday's payout of ₹14,250 has been processed to your bank account.",
        timeAgo = "Yesterday",
        icon = Icons.Default.Fastfood,
        iconTint = Color(0xFF8B5CF6),
        iconBg = Color(0xFFF5F3FF),
        isRead = true,
      ),
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("owner_notifications_screen"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(PureWhite)
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack, modifier = Modifier.testTag("owner_notifications_back_button")) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back",
          tint = TextDark,
          modifier = Modifier.size(22.dp),
        )
      }
      Spacer(Modifier.width(6.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Notifications",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
        )
        Text(
          text = canteenName,
          fontSize = 12.sp,
          color = TextMuted,
        )
      }
      TextButton(
        onClick = {
          notifications.forEachIndexed { i, item ->
            notifications[i] = item.copy(isRead = true)
          }
        },
      ) {
        Text(
          text = "Mark all read",
          fontSize = 12.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = DrawerAmber,
        )
      }
    }

    HorizontalDivider(thickness = 0.8.dp, color = BorderGray.copy(alpha = 0.6f))

    if (notifications.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .weight(1f)
          .padding(32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Outlined.Notifications,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(48.dp),
          )
          Spacer(Modifier.height(10.dp))
          Text(
            text = "No notifications yet",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextDark,
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = "You will be notified when new orders or alerts arrive.",
            fontSize = 13.sp,
            color = TextMuted,
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .weight(1f)
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        items(notifications, key = { it.id }) { item ->
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                val idx = notifications.indexOfFirst { it.id == item.id }
                if (idx >= 0) {
                  notifications[idx] = item.copy(isRead = true)
                }
              },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (item.isRead) PureWhite else Color(0xFFFFFBF5),
            ),
            border = BorderStroke(
              width = 1.dp,
              color = if (item.isRead) BorderGray.copy(alpha = 0.6f) else DrawerAmber.copy(alpha = 0.4f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.Top,
            ) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(item.iconBg),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = item.icon,
                  contentDescription = null,
                  tint = item.iconTint,
                  modifier = Modifier.size(20.dp),
                )
              }

              Spacer(Modifier.width(12.dp))

              Column(modifier = Modifier.weight(1f)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = item.title,
                    fontSize = 14.5.sp,
                    fontWeight = if (item.isRead) FontWeight.SemiBold else FontWeight.Bold,
                    color = TextDark,
                  )
                  Text(
                    text = item.timeAgo,
                    fontSize = 11.5.sp,
                    color = TextMuted,
                  )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                  text = item.description,
                  fontSize = 13.sp,
                  color = if (item.isRead) TextMuted else TextDark,
                  lineHeight = 17.sp,
                )
              }
            }
          }
        }

        item {
          Spacer(Modifier.height(24.dp))
        }
      }
    }
  }
}

