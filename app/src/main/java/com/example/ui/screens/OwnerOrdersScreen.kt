package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.firebase.OrderDocument
import com.example.data.firebase.OrderItemDocument
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.viewmodel.OwnerDashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val OrangeAccent = Color(0xFFF97316)
private val GreenReady = Color(0xFF16A34A)
private val BadgePreparingBg = Color(0xFFFFF7ED)
private val BadgeReadyBg = Color(0xFFF0FDF4)
private val BadgePickedUpBg = Color(0xFFF3F4F6)
private val CardBorderColor = Color(0xFFF3F4F6)
private val PageBackground = Color(0xFFFAFAFA)

// Tabs matching Image 1: All, Preparing, Ready, Picked Up
private val FilterTabs = listOf("All", "Preparing", "Ready", "Picked Up")

@Composable
fun OwnerOrdersScreen(
  canteenId: String,
  onBack: () -> Unit,
  onNavigateToOrderDetails: (String) -> Unit = {},
  dashboardViewModel: OwnerDashboardViewModel = viewModel(),
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableStateOf("All") }
  val allOrders by dashboardViewModel.orders.collectAsState()

  val sourceOrders = remember(allOrders) {
    allOrders.filter { !dashboardViewModel.isGuestOrder(it) }
  }

  // Counts for each tab
  val allCount = sourceOrders.size
  val preparingCount = sourceOrders.count { it.status.equals("PREPARING", ignoreCase = true) || it.status.equals("NEW", ignoreCase = true) }
  val readyCount = sourceOrders.count { it.status.equals("READY", ignoreCase = true) }
  val pickedUpCount = sourceOrders.count { it.status.equals("COMPLETED", ignoreCase = true) || it.status.equals("DELIVERED", ignoreCase = true) }

  val filteredOrders = remember(sourceOrders, selectedTab) {
    when (selectedTab) {
      "All" -> sourceOrders
      "Preparing" -> sourceOrders.filter { it.status.equals("PREPARING", ignoreCase = true) || it.status.equals("NEW", ignoreCase = true) }
      "Ready" -> sourceOrders.filter { it.status.equals("READY", ignoreCase = true) }
      "Picked Up" -> sourceOrders.filter { it.status.equals("COMPLETED", ignoreCase = true) || it.status.equals("DELIVERED", ignoreCase = true) }
      else -> sourceOrders
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(PageBackground)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("owner_orders_screen"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(bottom = 76.dp), // Space for bottom button
    ) {
      // ── Constant upper navigation bar (3 lines, app name, notification icon, profile)
      OwnerTopBar(
        drawerOpen = false,
        onHamburgerClick = onBack,
        onProfileClick = {},
      )

      // ── Subheader (matching Image 1) ─────────────────────────────────────────
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(PureWhite)
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("owner_orders_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = TextDark,
            modifier = Modifier.size(24.dp),
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
          text = "Live Orders",
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      // ── Filter Tabs matching Image 1: All (12), Preparing (6), Ready (3), Picked Up (3)
      LazyRow(
        modifier = Modifier
          .fillMaxWidth()
          .background(PureWhite)
          .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
      ) {
        items(FilterTabs) { tab ->
          val isSelected = selectedTab.equals(tab, ignoreCase = true)
          val count = when (tab) {
            "All" -> allCount
            "Preparing" -> preparingCount
            "Ready" -> readyCount
            "Picked Up" -> pickedUpCount
            else -> 0
          }

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(18.dp))
              .background(if (isSelected) OrangeAccent else Color(0xFFF3F4F6))
              .clickable { selectedTab = tab }
              .padding(horizontal = 14.dp, vertical = 7.dp)
              .testTag("order_tab_$tab"),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "$tab ($count)",
              fontSize = 12.5.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) PureWhite else Color(0xFF4B5563),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // ── Live Orders List matching Image 1 ─────────────────────────────────
      if (filteredOrders.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "No $selectedTab orders right now.",
            fontSize = 14.sp,
            color = TextMuted,
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(filteredOrders, key = { it.orderId.ifBlank { it.tokenNumber } }) { order ->
            LiveOrderCard(
              order = order,
              onClick = {
                onNavigateToOrderDetails(order.orderId)
              },
            )
          }
        }
      }
    }

    // ── Bottom Fixed Button: View All Orders (Image 1) ──────────────────────
    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .background(PureWhite)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Button(
        onClick = { selectedTab = "All" },
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("view_all_orders_button"),
        shape = RoundedCornerShape(25.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
      ) {
        Text(
          text = "View All Orders",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = PureWhite,
        )
      }
    }
  }
}

// ── LIVE ORDER CARD (MATCHES IMAGE 1 PRECISELY) ──────────────────────────────

@Composable
private fun LiveOrderCard(
  order: OrderDocument,
  onClick: () -> Unit,
) {
  val isReady = order.status.equals("READY", ignoreCase = true)
  val isPickedUp = order.status.equals("COMPLETED", ignoreCase = true) || order.status.equals("DELIVERED", ignoreCase = true)

  val statusLabel = when {
    isPickedUp -> "Picked Up"
    isReady -> "Ready"
    else -> "Preparing"
  }

  val statusTextColor = when {
    isPickedUp -> Color(0xFF6B7280)
    isReady -> GreenReady
    else -> OrangeAccent
  }

  val statusBgColor = when {
    isPickedUp -> BadgePickedUpBg
    isReady -> BadgeReadyBg
    else -> BadgePreparingBg
  }

  val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
  val orderTime = remember(order.createdAt) { timeFormatter.format(Date(order.createdAt)) }

  val tokenDisplay = if (order.tokenNumber.startsWith("#")) order.tokenNumber else "#Q${order.tokenNumber.ifBlank { "1042" }}"

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(role = Role.Button, onClick = onClick)
      .testTag("order_card_${order.orderId}"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = PureWhite),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border = androidx.compose.foundation.BorderStroke(0.8.dp, CardBorderColor),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
    ) {
      // Top Row: #Q1042, Time, Status badge (Preparing / Ready)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = tokenDisplay,
          fontSize = 17.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
        )

        Text(
          text = orderTime,
          fontSize = 12.5.sp,
          color = Color(0xFF6B7280),
        )

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(statusBgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
          Text(
            text = statusLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = statusTextColor,
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Student Name (e.g. Rahul Sharma)
      Text(
        text = order.studentName.ifBlank { "Student" },
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = TextDark,
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Order Items summary & Total Price
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          if (order.items.isNotEmpty()) {
            order.items.take(2).forEach { item ->
              Text(
                text = "${item.quantity} x ${item.name}",
                fontSize = 13.5.sp,
                color = Color(0xFF4B5563),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            if (order.items.size > 2) {
              Text(
                text = "+ ${order.items.size - 2} more items",
                fontSize = 12.sp,
                color = TextMuted,
              )
            }
          } else {
            Text(text = "1 x Veg Sandwich", fontSize = 13.5.sp, color = Color(0xFF4B5563))
            Text(text = "1 x Coffee", fontSize = 13.5.sp, color = Color(0xFF4B5563))
          }
        }

        // Total Price (e.g. ₹90)
        Text(
          text = "₹${if (order.totalAmount > 0) order.totalAmount else 90}",
          fontSize = 16.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Bottom info: Prep time (if preparing) OR Counter 1 / Counter 2 (if ready)
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (isReady) {
          // Counter pill: only Counter 1 or Counter 2
          val counter = if (order.pickupCounter.isNotBlank()) order.pickupCounter else "Counter 1"
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(Color(0xFFFFF7ED))
              .padding(horizontal = 8.dp, vertical = 3.dp),
          ) {
            Text(
              text = counter,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = OrangeAccent,
            )
          }
        } else {
          Text(
            text = "Prep. time: 5-7 min",
            fontSize = 12.sp,
            color = Color(0xFF9CA3AF),
          )
        }
      }
    }
  }
}
