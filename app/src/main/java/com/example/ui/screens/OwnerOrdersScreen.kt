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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

// Tabs: Preparing, Ready, Picked Up (All tab removed as requested)
private val FilterTabs = listOf("Preparing", "Ready", "Picked Up")

@Composable
fun OwnerOrdersScreen(
  canteenId: String,
  onBack: () -> Unit,
  onNavigateToOrderDetails: (String) -> Unit = {},
  dashboardViewModel: OwnerDashboardViewModel = viewModel(),
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableStateOf("Preparing") }
  val allOrders by dashboardViewModel.orders.collectAsState()

  // Filter feature for Picked Up orders
  var pickedUpFilterTime by remember { mutableStateOf("All") } // "All", "Today", "Yesterday"
  var pickedUpSearchQuery by remember { mutableStateOf("") }
  var showPickedUpFilterDialog by remember { mutableStateOf(false) }

  val sourceOrders = remember(allOrders) {
    allOrders.filter { !dashboardViewModel.isGuestOrder(it) }
  }

  // Counts for each tab
  val preparingCount = sourceOrders.count { it.status.equals("PREPARING", ignoreCase = true) || it.status.equals("NEW", ignoreCase = true) }
  val readyCount = sourceOrders.count { it.status.equals("READY", ignoreCase = true) }
  val pickedUpCount = sourceOrders.count { it.status.equals("COMPLETED", ignoreCase = true) || it.status.equals("DELIVERED", ignoreCase = true) }

  val filteredOrders = remember(sourceOrders, selectedTab, pickedUpFilterTime, pickedUpSearchQuery) {
    when (selectedTab) {
      "Preparing" -> sourceOrders.filter { it.status.equals("PREPARING", ignoreCase = true) || it.status.equals("NEW", ignoreCase = true) }
      "Ready" -> sourceOrders.filter { it.status.equals("READY", ignoreCase = true) }
      "Picked Up" -> {
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        sourceOrders.filter { it.status.equals("COMPLETED", ignoreCase = true) || it.status.equals("DELIVERED", ignoreCase = true) }
          .filter { order ->
            val matchesTime = when (pickedUpFilterTime) {
              "Today" -> (now - order.createdAt) < oneDayMs
              "Yesterday" -> {
                val diff = now - order.createdAt
                diff in oneDayMs..(2 * oneDayMs)
              }
              else -> true
            }
            val matchesQuery = pickedUpSearchQuery.isBlank() ||
              order.tokenNumber.contains(pickedUpSearchQuery, ignoreCase = true) ||
              order.studentName.contains(pickedUpSearchQuery, ignoreCase = true) ||
              order.studentCourse.contains(pickedUpSearchQuery, ignoreCase = true)
            matchesTime && matchesQuery
          }
      }
      else -> sourceOrders.filter { it.status.equals("PREPARING", ignoreCase = true) }
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
      modifier = Modifier.fillMaxSize(),
    ) {
      // ── Clean Header (Without redundant constant top bar) ─────────────────
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(PureWhite)
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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

          Spacer(modifier = Modifier.width(10.dp))

          Column {
            Text(
              text = "Live Orders",
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark,
            )
            Text(
              text = "${preparingCount + readyCount} active in kitchen",
              fontSize = 12.sp,
              color = TextMuted,
            )
          }
        }

        // Filter icon feature for Picked Up orders
        if (selectedTab == "Picked Up") {
          val isFilterActive = pickedUpFilterTime != "All" || pickedUpSearchQuery.isNotBlank()
          IconButton(
            onClick = { showPickedUpFilterDialog = true },
            modifier = Modifier
              .clip(CircleShape)
              .background(if (isFilterActive) BadgePreparingBg else Color(0xFFF3F4F6)),
          ) {
            Icon(
              imageVector = Icons.Default.Tune,
              contentDescription = "Filter Picked Up Orders",
              tint = if (isFilterActive) OrangeAccent else TextDark,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      // ── Filter Tabs: Preparing (6), Ready (3), Picked Up (3)
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
              .padding(horizontal = 16.dp, vertical = 8.dp)
              .testTag("order_tab_$tab"),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "$tab ($count)",
              fontSize = 13.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) PureWhite else Color(0xFF4B5563),
            )
          }
        }
      }

      // Active filter chip banner for Picked Up tab
      if (selectedTab == "Picked Up" && (pickedUpFilterTime != "All" || pickedUpSearchQuery.isNotBlank())) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Filtered by: ${if (pickedUpFilterTime != "All") pickedUpFilterTime else ""} ${if (pickedUpSearchQuery.isNotBlank()) "\"$pickedUpSearchQuery\"" else ""}".trim(),
            fontSize = 12.sp,
            color = OrangeAccent,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "Clear Filter",
            fontSize = 12.sp,
            color = Color(0xFFDC2626),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable {
              pickedUpFilterTime = "All"
              pickedUpSearchQuery = ""
            },
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      // ── Live Orders List ──────────────────────────────────────────────────
      if (filteredOrders.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = if (selectedTab == "Picked Up" && (pickedUpFilterTime != "All" || pickedUpSearchQuery.isNotBlank()))
              "No completed orders match your filters."
            else
              "No $selectedTab orders right now.",
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

    // ── Picked Up Filter Dialog ─────────────────────────────────────────────
    if (showPickedUpFilterDialog) {
      AlertDialog(
        onDismissRequest = { showPickedUpFilterDialog = false },
        title = {
          Text(
            text = "Filter Picked Up Orders",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Search field
            OutlinedTextField(
              value = pickedUpSearchQuery,
              onValueChange = { pickedUpSearchQuery = it },
              placeholder = { Text("Search by Token or Student", fontSize = 13.sp) },
              leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
              trailingIcon = {
                if (pickedUpSearchQuery.isNotEmpty()) {
                  IconButton(onClick = { pickedUpSearchQuery = "" }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                  }
                }
              },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth(),
            )

            Text(
              text = "TIME RANGE",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = TextMuted,
              letterSpacing = 0.5.sp,
            )

            listOf("All", "Today", "Yesterday").forEach { timeOption ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .clickable { pickedUpFilterTime = timeOption }
                  .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(
                  selected = pickedUpFilterTime == timeOption,
                  onClick = { pickedUpFilterTime = timeOption },
                  colors = RadioButtonDefaults.colors(selectedColor = OrangeAccent),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = if (timeOption == "All") "All Completed Orders" else timeOption,
                  fontSize = 14.sp,
                  fontWeight = if (pickedUpFilterTime == timeOption) FontWeight.Bold else FontWeight.Normal,
                  color = TextDark,
                )
              }
            }
          }
        },
        confirmButton = {
          Button(
            onClick = { showPickedUpFilterDialog = false },
            colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text("Apply Filter", color = PureWhite, fontWeight = FontWeight.Bold)
          }
        },
        dismissButton = {
          TextButton(onClick = {
            pickedUpFilterTime = "All"
            pickedUpSearchQuery = ""
            showPickedUpFilterDialog = false
          }) {
            Text("Reset", color = TextMuted)
          }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = PureWhite,
      )
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
