// OwnerSettingsScreen: Jetpack Compose screen for OwnerSettings user interface
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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

@Composable
fun OwnerSettingsScreen(
  canteenName: String = "Campus Canteen",
  isCanteenOpen: Boolean = true,
  onToggleCanteenStatus: (Boolean) -> Unit = {},
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var autoAcceptOrders by remember { mutableStateOf(true) }
  var orderChimeSound by remember { mutableStateOf(true) }
  var lowStockAlerts by remember { mutableStateOf(true) }
  var prepBufferMinutes by remember { mutableStateOf("0 min (Standard)") }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("owner_settings_screen"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(PureWhite)
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack, modifier = Modifier.testTag("owner_settings_back_button")) {
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
          text = "Settings",
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
    }

    HorizontalDivider(thickness = 0.8.dp, color = BorderGray.copy(alpha = 0.6f))

    Column(
      modifier = Modifier
        .fillMaxSize()
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = "CANTEEN OPERATIONS",
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp),
      )

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Canteen Acceptance Status",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
              Text(
                text = if (isCanteenOpen) "Open — currently accepting online orders" else "Closed — students cannot place orders",
                fontSize = 12.5.sp,
                color = if (isCanteenOpen) Color(0xFF16A34A) else Color(0xFFDC2626),
              )
            }
            Switch(
              checked = isCanteenOpen,
              onCheckedChange = onToggleCanteenStatus,
              colors = SwitchDefaults.colors(
                checkedThumbColor = PureWhite,
                checkedTrackColor = DrawerAmber,
                uncheckedThumbColor = PureWhite,
                uncheckedTrackColor = Color(0xFFD1D5DB),
              ),
            )
          }

          HorizontalDivider(
            thickness = 0.6.dp,
            color = BorderGray.copy(alpha = 0.5f),
            modifier = Modifier.padding(vertical = 12.dp),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Auto-Accept Orders",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark,
              )
              Text(
                text = "Automatically move paid orders to Preparing status",
                fontSize = 12.sp,
                color = TextMuted,
              )
            }
            Switch(
              checked = autoAcceptOrders,
              onCheckedChange = { autoAcceptOrders = it },
              colors = SwitchDefaults.colors(
                checkedThumbColor = PureWhite,
                checkedTrackColor = DrawerAmber,
                uncheckedThumbColor = PureWhite,
                uncheckedTrackColor = Color(0xFFD1D5DB),
              ),
            )
          }

          HorizontalDivider(
            thickness = 0.6.dp,
            color = BorderGray.copy(alpha = 0.5f),
            modifier = Modifier.padding(vertical = 12.dp),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = "Operating Timings",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark,
              )
              Text(
                text = "8:00 AM – 9:00 PM (Monday – Saturday)",
                fontSize = 12.sp,
                color = TextMuted,
              )
            }
            Icon(
              imageVector = Icons.Default.ChevronRight,
              contentDescription = null,
              tint = TextMuted,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }

      Text(
        text = "ALERTS & AUDIO",
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp),
      )

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Order Arrival Audio Chime",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark,
              )
              Text(
                text = "Play sound when a new order token is created",
                fontSize = 12.sp,
                color = TextMuted,
              )
            }
            Switch(
              checked = orderChimeSound,
              onCheckedChange = { orderChimeSound = it },
              colors = SwitchDefaults.colors(
                checkedThumbColor = PureWhite,
                checkedTrackColor = DrawerAmber,
                uncheckedThumbColor = PureWhite,
                uncheckedTrackColor = Color(0xFFD1D5DB),
              ),
            )
          }

          HorizontalDivider(
            thickness = 0.6.dp,
            color = BorderGray.copy(alpha = 0.5f),
            modifier = Modifier.padding(vertical = 12.dp),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Low Stock Notifications",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark,
              )
              Text(
                text = "Alert when any item has fewer than 5 portions left",
                fontSize = 12.sp,
                color = TextMuted,
              )
            }
            Switch(
              checked = lowStockAlerts,
              onCheckedChange = { lowStockAlerts = it },
              colors = SwitchDefaults.colors(
                checkedThumbColor = PureWhite,
                checkedTrackColor = DrawerAmber,
                uncheckedThumbColor = PureWhite,
                uncheckedTrackColor = Color(0xFFD1D5DB),
              ),
            )
          }
        }
      }

      Text(
        text = "ABOUT & SYSTEM",
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp),
      )

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(text = "QuickBite Partner Version", fontSize = 14.sp, color = TextDark)
            Text(text = "v2.5.0-production", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DrawerAmber)
          }
          Spacer(Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(text = "Backend Node Sync", fontSize = 14.sp, color = TextDark)
            Text(text = "Connected (MongoDB Atlas)", fontSize = 12.5.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.SemiBold)
          }
        }
      }

      Spacer(Modifier.height(18.dp))
    }
  }
}

