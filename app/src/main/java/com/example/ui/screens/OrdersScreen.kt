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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BottomNavTab
import com.example.data.CartItem
import com.example.data.FoodCategory
import com.example.data.FoodItem
import com.example.data.OrderStatus
import com.example.data.OrderRecord
import com.example.ui.state.CanteenAppState
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream

private val OrangeAccent = Color(0xFFF97316)
private val GreenReady = Color(0xFF16A34A)
private val BadgePreparingBg = Color(0xFFFFF7ED)
private val BadgeReadyBg = Color(0xFFF0FDF4)
private val BadgePickedUpBg = Color(0xFFEFF6FF)
private val BluePickedUp = Color(0xFF2563EB)
private val PageBackground = WarmCream
private val CardBorderColor = Color(0xFFE5DFD5)

@Composable
fun OrdersScreen(
  appState: CanteenAppState,
  onNavigateToTab: (BottomNavTab) -> Unit = {},
  onOrderClick: (OrderRecord) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableStateOf("Active Orders") }
  var isSearchActive by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }

  // Use real orders from database (no predefined demo orders)
  val sourceOrders = appState.orders

  val activeOrders = sourceOrders.filter { it.status != OrderStatus.COMPLETED }
  val pastOrders = sourceOrders.filter { it.status == OrderStatus.COMPLETED }

  val ordersListState = rememberLazyListState()

  // Hide bottom navigation bar when scrolling down, show when scrolling up
  LaunchedEffect(ordersListState) {
    var lastIndex = ordersListState.firstVisibleItemIndex
    var lastOffset = ordersListState.firstVisibleItemScrollOffset
    var accumulatedDown = 0
    var accumulatedUp = 0

    snapshotFlow { ordersListState.firstVisibleItemIndex to ordersListState.firstVisibleItemScrollOffset }.collect { (currentIndex, currentOffset) ->
      val delta = if (currentIndex == lastIndex) {
        currentOffset - lastOffset
      } else {
        (currentIndex - lastIndex) * 200 + (currentOffset - lastOffset)
      }

      if (delta > 0) {
        accumulatedDown += delta
        accumulatedUp = 0
        if (accumulatedDown >= 40 && (currentIndex > 0 || currentOffset > 60)) {
          appState.isBottomBarVisible = false
        }
      } else if (delta < 0) {
        accumulatedUp += (-delta)
        accumulatedDown = 0
        if (accumulatedUp >= 25 || (currentIndex == 0 && currentOffset < 40)) {
          appState.isBottomBarVisible = true
        }
      }

      if (currentIndex == 0 && currentOffset < 20) {
        appState.isBottomBarVisible = true
        accumulatedDown = 0
        accumulatedUp = 0
      }

      lastIndex = currentIndex
      lastOffset = currentOffset
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(PageBackground)
      .statusBarsPadding()
      .testTag("customer_my_orders_screen"),
  ) {
    // ── Header: Normal Bar vs Interactive Search Bar ─────────────────────
    if (isSearchActive) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(PureWhite)
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = {
            isSearchActive = false
            searchQuery = ""
          },
          modifier = Modifier.testTag("close_orders_search"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = TextDark,
            modifier = Modifier.size(24.dp),
          )
        }

        Box(
          modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFF3F4F6))
            .padding(horizontal = 14.dp),
          contentAlignment = Alignment.CenterStart,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = null,
              tint = Color(0xFF9CA3AF),
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              singleLine = true,
              textStyle = TextStyle(fontSize = 14.sp, color = TextDark, fontWeight = FontWeight.Medium),
              decorationBox = { innerTextField ->
                if (searchQuery.isEmpty()) {
                  Text(
                    text = "Search by item, canteen, #token...",
                    fontSize = 13.sp,
                    color = Color(0xFF9CA3AF),
                  )
                }
                innerTextField()
              },
              modifier = Modifier.weight(1f).testTag("orders_search_input_field"),
            )
            if (searchQuery.isNotEmpty()) {
              IconButton(
                onClick = { searchQuery = "" },
                modifier = Modifier.size(28.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Clear",
                  tint = Color(0xFF6B7280),
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }
        }
      }
    } else {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(PureWhite)
          .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = { onNavigateToTab(BottomNavTab.HOME) },
          modifier = Modifier.testTag("my_orders_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = TextDark,
            modifier = Modifier.size(24.dp),
          )
        }

        Spacer(Modifier.width(4.dp))

        Text(
          text = "My Orders",
          fontSize = 19.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
          modifier = Modifier.weight(1f),
        )

        IconButton(
          onClick = { isSearchActive = true },
          modifier = Modifier.testTag("my_orders_search_button"),
        ) {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = TextDark,
            modifier = Modifier.size(24.dp),
          )
        }
      }
    }

    // ── Tabs: Active Orders (Current) | Order History (Completed/Picked Up) ──
    val tabs = listOf("Active Orders", "Order History")
    TabRow(
      selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
      containerColor = PureWhite,
      contentColor = BlackPrimary,
      indicator = { tabPositions ->
        val idx = tabs.indexOf(selectedTab).coerceAtLeast(0)
        TabRowDefaults.SecondaryIndicator(
          modifier = Modifier.tabIndicatorOffset(tabPositions[idx]),
          color = BlackPrimary,
          height = 3.dp,
        )
      },
      divider = {},
    ) {
      tabs.forEach { tab ->
        val isSelected = selectedTab == tab
        Tab(
          selected = isSelected,
          onClick = { selectedTab = tab },
          text = {
            Text(
              text = tab,
              fontSize = 14.5.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) BlackPrimary else Color(0xFF6B7280),
            )
          },
        )
      }
    }

    Spacer(Modifier.height(14.dp))

    // ── Orders List with Live Search Filtering ─────────────────────────────
    val isViewingActive = selectedTab == "Active Orders"
    val displayedOrders = if (isViewingActive) activeOrders else pastOrders

    val q = searchQuery.trim().lowercase()
    val filteredOrders = displayedOrders.filter { order ->
      if (q.isBlank()) true
      else {
        order.tokenNumber.lowercase().contains(q) ||
        order.id.lowercase().contains(q) ||
        order.pickupCanteenName.lowercase().contains(q) ||
        order.pickupLocation.lowercase().contains(q) ||
        order.items.any { it.foodItem.name.lowercase().contains(q) }
      }
    }

    if (filteredOrders.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = if (searchQuery.isNotBlank()) "No Matching Orders" else if (isViewingActive) "No Active Orders" else "No Past Orders",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text = if (searchQuery.isNotBlank()) {
              "No orders found matching \"$searchQuery\".\nTry searching for another item or canteen."
            } else if (isViewingActive) {
              "Your active preparing or ready order will appear here.\nOnce picked up, it moves to Order History."
            } else {
              "Your past picked up orders and receipts are saved here."
            },
            fontSize = 13.sp,
            color = TextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          )
          if (searchQuery.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(BlackPrimary)
                .clickable { searchQuery = "" }
                .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
              Text(
                text = "Clear Search",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PureWhite,
              )
            }
          }
        }
      }
    } else {
      LazyColumn(
        state = ordersListState,
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        items(filteredOrders, key = { it.id }) { order ->
          CustomerOrderCard(
            order = order,
            onClick = { onOrderClick(order) },
          )
        }

        item {
          Spacer(Modifier.height(16.dp))
        }
      }
    }
  }
}

/**
 * Customer Order Card matching Image 1:
 * - Token: #Q1042
 * - Status badge: Preparing (orange pill) or Ready (green pill)
 * - Items list: Veg Sandwich, Coffee
 * - Price: ₹90
 * - Bottom Chip: 🔥 Ready in 6-8 min or 🟢 Ready for pickup
 */
@Composable
private fun CustomerOrderCard(
  order: OrderRecord,
  onClick: () -> Unit,
) {
  val isPreparing = order.status == OrderStatus.PREPARING || order.status == OrderStatus.NEW
  val isReady = order.status == OrderStatus.READY
  val isPickedUp = order.status == OrderStatus.COMPLETED

  // Soft peach background tint for preparing card (matching Image 1 top card)
  val cardBg = PureWhite

  val tokenRaw = order.tokenNumber.ifBlank { "1042" }
  val tokenDisplay = if (tokenRaw.startsWith("#")) tokenRaw else "#Q$tokenRaw"

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .clickable { onClick() }
      .testTag("customer_order_card_${order.id}"),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = cardBg),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border = androidx.compose.foundation.BorderStroke(0.8.dp, CardBorderColor),
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      // Header: #Q1042  Preparing / Ready (Decent Black Badge with Good Icons)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = tokenDisplay,
          fontSize = 20.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = when {
              isReady -> Icons.Default.CheckCircle
              isPickedUp -> Icons.Default.ShoppingBag
              else -> Icons.Default.AccessTime
            },
            contentDescription = null,
            tint = when {
              isReady -> GreenReady
              isPickedUp -> Color(0xFF6B7280)
              else -> DrawerAmber
            },
            modifier = Modifier.size(15.dp),
          )
          Spacer(Modifier.width(5.dp))
          Text(
            text = when {
              isReady -> "Ready"
              isPickedUp -> "Picked Up"
              else -> "Preparing"
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = when {
              isReady -> GreenReady
              isPickedUp -> Color(0xFF6B7280)
              else -> DrawerAmber
            },
          )
        }
      }

      Spacer(Modifier.height(14.dp))

      // Items list & Price
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          if (order.items.isNotEmpty()) {
            order.items.forEach { item ->
              Text(
                text = item.foodItem.name,
                fontSize = 14.5.sp,
                color = Color(0xFF374151),
                fontWeight = FontWeight.Medium,
              )
              Spacer(Modifier.height(4.dp))
            }
          } else {
            Text(text = "Veg Sandwich", fontSize = 14.5.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(text = "Coffee", fontSize = 14.5.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
          }
        }

        Text(
          text = "₹${order.totalPrice}",
          fontSize = 18.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
        )
      }

      Spacer(Modifier.height(14.dp))

      // Bottom Row: Status Indicator & Pickup Location (Mixed with card background)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (isReady) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = GreenReady,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Ready for pickup",
              fontSize = 12.5.sp,
              fontWeight = FontWeight.Bold,
              color = GreenReady,
            )
          } else if (isPickedUp) {
            Icon(
              imageVector = Icons.Default.ShoppingBag,
              contentDescription = null,
              tint = Color(0xFF6B7280),
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Picked up",
              fontSize = 12.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF6B7280),
            )
          } else {
            Icon(
              imageVector = Icons.Default.AccessTime,
              contentDescription = null,
              tint = DrawerAmber,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Preparing · Ready in 6-8 min",
              fontSize = 12.5.sp,
              fontWeight = FontWeight.Bold,
              color = DrawerAmber,
            )
          }
        }

        val cafeName = order.pickupCanteenName.ifBlank { "Campus Canteen" }
        val blockLoc = order.pickupLocation.substringBefore("(").trim()
        val displayLoc = if (blockLoc.isNotBlank() && !cafeName.contains(blockLoc, ignoreCase = true)) {
          val cleanCafe = cafeName.replace(" 26-27", "").trim()
          val cleanBlock = blockLoc.replace("Between Block", "Block", ignoreCase = true).trim()
          "$cleanCafe • $cleanBlock"
        } else {
          cafeName
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .weight(1f, fill = false)
            .padding(start = 8.dp),
        ) {
          Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = Color(0xFF9CA3AF),
            modifier = Modifier.size(13.dp),
          )
          Spacer(Modifier.width(3.dp))
          Text(
            text = displayLoc,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF6B7280),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
