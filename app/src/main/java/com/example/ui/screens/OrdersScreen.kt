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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.data.OrdersDateFilterOption
import com.example.data.OrdersFilterState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val OrangeAccent = Color(0xFFF97316)
private val GreenReady = Color(0xFF16A34A)
private val BadgePreparingBg = Color(0xFFFFF7ED)
private val BadgeReadyBg = Color(0xFFF0FDF4)
private val BadgePickedUpBg = Color(0xFFEFF6FF)
private val BluePickedUp = Color(0xFF2563EB)
private val PageBackground = WarmCream
private val CardBorderColor = Color(0xFFE5DFD5)

@OptIn(ExperimentalMaterial3Api::class)
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

  // ── Filter State ──
  var filterState by remember { mutableStateOf(OrdersFilterState()) }
  var isFilterSheetOpen by remember { mutableStateOf(false) }

  // Use real orders from database (no predefined demo orders)
  val sourceOrders = appState.orders

  val activeOrders = sourceOrders.filter { it.status != OrderStatus.COMPLETED }
  val pastOrders = sourceOrders.filter { it.status == OrderStatus.COMPLETED }

  val ordersListState = rememberLazyListState()

  LaunchedEffect(Unit) {
    appState.isBottomBarVisible = true
  }

  // Helper date calculation for epoch boundaries (IST / local device midnight)
  val calendar = Calendar.getInstance()
  calendar.set(Calendar.HOUR_OF_DAY, 0)
  calendar.set(Calendar.MINUTE, 0)
  calendar.set(Calendar.SECOND, 0)
  calendar.set(Calendar.MILLISECOND, 0)
  val startOfToday = calendar.timeInMillis
  val endOfToday = startOfToday + 24L * 60 * 60 * 1000 - 1L
  val startOfYesterday = startOfToday - 24L * 60 * 60 * 1000
  val endOfYesterday = startOfToday - 1L
  val startOfLast7Days = startOfToday - 6L * 24 * 60 * 60 * 1000
  val startOfLast30Days = startOfToday - 29L * 24 * 60 * 60 * 1000

  // ── Orders List with Live Search & Date/Canteen Filtering ──
  val isViewingActive = selectedTab == "Active Orders"
  val displayedOrders = if (isViewingActive) activeOrders else pastOrders

  val q = searchQuery.trim().lowercase()

  val filteredOrders = remember(displayedOrders, q, filterState) {
    displayedOrders.filter { order ->
      // 1. Search Query
      val matchesQuery = if (q.isBlank()) true else {
        order.tokenNumber.lowercase().contains(q) ||
        order.id.lowercase().contains(q) ||
        order.pickupCanteenName.lowercase().contains(q) ||
        order.pickupLocation.lowercase().contains(q) ||
        order.items.any { it.foodItem.name.lowercase().contains(q) }
      }

      // 2. Canteen Filter
      val matchesCanteen = if (filterState.selectedCanteenIds.isEmpty()) true else {
        filterState.selectedCanteenIds.contains(order.canteenId) ||
        filterState.selectedCanteenIds.any { id ->
          order.pickupCanteenName.contains(id, ignoreCase = true)
        }
      }

      // 3. Date Filter
      val orderEpoch = order.placedEpoch.takeIf { it > 0 }
        ?: com.example.data.api.extractEpochFromOrderId(order.id).takeIf { it > 0 }
        ?: System.currentTimeMillis()

      val matchesDate = when (filterState.dateOption) {
        OrdersDateFilterOption.ALL -> true
        OrdersDateFilterOption.TODAY -> orderEpoch in startOfToday..endOfToday
        OrdersDateFilterOption.YESTERDAY -> orderEpoch in startOfYesterday..endOfYesterday
        OrdersDateFilterOption.LAST_7_DAYS -> orderEpoch in startOfLast7Days..endOfToday
        OrdersDateFilterOption.LAST_30_DAYS -> orderEpoch in startOfLast30Days..endOfToday
        OrdersDateFilterOption.CUSTOM -> {
          val start = filterState.customStartDateEpoch ?: 0L
          val end = filterState.customEndDateEpoch?.plus(24L * 60 * 60 * 1000 - 1L) ?: Long.MAX_VALUE
          orderEpoch in start..end
        }
      }

      matchesQuery && matchesCanteen && matchesDate
    }
  }

  // Derive unique canteens previously ordered from to show in filter sheet
  val userOrderedCanteens = remember(sourceOrders, appState.allCanteens) {
    val canteenIdsFromOrders = sourceOrders.map { it.canteenId }.filter { it.isNotBlank() }.distinct()
    val fromAppState = appState.allCanteens.filter { canteenIdsFromOrders.contains(it.id) }
    if (fromAppState.isNotEmpty()) fromAppState
    else if (canteenIdsFromOrders.isNotEmpty()) {
      canteenIdsFromOrders.map { cid ->
        val name = sourceOrders.firstOrNull { it.canteenId == cid }?.pickupCanteenName?.ifBlank { "Canteen $cid" } ?: "Canteen $cid"
        com.example.data.CampusBlockCanteen(
          id = cid,
          name = name,
          betweenBlocks = "",
          floorInfo = "",
          primaryBlocks = emptyList(),
          specialty = "",
          crowdStatus = com.example.data.CrowdStatus.LOW,
          avgWaitMinutes = 5,
        )
      }
    } else {
      appState.allCanteens.take(4)
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

        // 1. Search Icon Button
        IconButton(
          onClick = { isSearchActive = true },
          modifier = Modifier.testTag("my_orders_search_button"),
        ) {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = TextDark,
            modifier = Modifier.size(22.dp),
          )
        }

        // 2. Monochrome Filter Button with Badge
        Box(
          modifier = Modifier.padding(end = 6.dp),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(if (filterState.isActive) BlackPrimary else Color(0xFFF3F4F6))
              .clickable { isFilterSheetOpen = true }
              .testTag("my_orders_filter_button"),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.FilterList,
              contentDescription = "Filter Orders",
              tint = if (filterState.isActive) PureWhite else BlackPrimary,
              modifier = Modifier.size(20.dp),
            )
          }

          if (filterState.activeFilterCount > 0) {
            Box(
              modifier = Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .clip(CircleShape)
                .background(BlackPrimary)
                .border(1.5.dp, PureWhite, CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "${filterState.activeFilterCount}",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PureWhite,
              )
            }
          }
        }
      }
    }

    // ── Active Filter Chips Row ──
    if (filterState.isActive) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(PureWhite)
          .padding(horizontal = 14.dp, vertical = 8.dp)
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Date active chip
        if (filterState.dateOption != OrdersDateFilterOption.ALL) {
          val label = if (filterState.dateOption == OrdersDateFilterOption.CUSTOM) {
            val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
            val s = filterState.customStartDateEpoch?.let { sdf.format(Date(it)) } ?: ""
            val e = filterState.customEndDateEpoch?.let { sdf.format(Date(it)) } ?: ""
            if (s.isNotBlank() && e.isNotBlank()) "$s - $e" else "Custom Date"
          } else {
            filterState.dateOption.displayName
          }
          ActiveOrderFilterChip(
            label = label,
            onRemove = {
              filterState = filterState.copy(
                dateOption = OrdersDateFilterOption.ALL,
                customStartDateEpoch = null,
                customEndDateEpoch = null,
              )
            },
          )
        }

        // Canteen active chips
        for (canteenId in filterState.selectedCanteenIds) {
          val canteenName = userOrderedCanteens.find { it.id == canteenId }?.name
            ?: sourceOrders.firstOrNull { it.canteenId == canteenId }?.pickupCanteenName
            ?: canteenId
          ActiveOrderFilterChip(
            label = canteenName,
            onRemove = {
              filterState = filterState.copy(
                selectedCanteenIds = filterState.selectedCanteenIds - canteenId
              )
            },
          )
        }

        // Clear all button
        Text(
          text = "Clear All",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = TextMuted,
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { filterState = OrdersFilterState() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        )
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

    // ── Orders Content List or Monochrome Empty State ──
    if (filteredOrders.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(horizontal = 16.dp)
        ) {
          // Monochrome black-and-white icon
          Box(
            modifier = Modifier
              .size(72.dp)
              .clip(CircleShape)
              .background(Color(0xFFEFEFEF)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = if (filterState.isActive) Icons.AutoMirrored.Filled.ReceiptLong else Icons.Default.ShoppingBag,
              contentDescription = null,
              tint = BlackPrimary,
              modifier = Modifier.size(36.dp),
            )
          }

          Spacer(Modifier.height(16.dp))

          Text(
            text = if (filterState.isActive) "No orders found" else if (searchQuery.isNotBlank()) "No Matching Orders" else if (isViewingActive) "No Active Orders" else "No Past Orders",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = BlackPrimary,
          )

          Spacer(Modifier.height(8.dp))

          Text(
            text = if (filterState.isActive) {
              "No orders match your selected filters.\nTry adjusting date range or canteens."
            } else if (searchQuery.isNotBlank()) {
              "No orders found matching \"$searchQuery\".\nTry searching for another item or canteen."
            } else if (isViewingActive) {
              "Your active preparing or ready order will appear here.\nOnce picked up, it moves to Order History."
            } else {
              "Your past picked up orders and receipts are saved here."
            },
            fontSize = 13.5.sp,
            color = TextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 19.sp,
          )

          if (filterState.isActive) {
            Spacer(Modifier.height(18.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(BlackPrimary)
                .clickable { filterState = OrdersFilterState() }
                .padding(horizontal = 22.dp, vertical = 11.dp)
                .testTag("orders_clear_filters_button"),
            ) {
              Text(
                text = "Clear Filters",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = PureWhite,
              )
            }
          } else if (searchQuery.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(BlackPrimary)
                .clickable { searchQuery = "" }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
              Text(
                text = "Clear Search",
                fontSize = 13.5.sp,
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
          Spacer(Modifier.height(20.dp))
        }
      }
    }
  }

  // ── Filter Bottom Sheet Modal ──
  if (isFilterSheetOpen) {
    OrdersFilterBottomSheet(
      currentState = filterState,
      availableCanteens = userOrderedCanteens,
      onDismiss = { isFilterSheetOpen = false },
      onApply = { newState ->
        filterState = newState
        isFilterSheetOpen = false
      },
    )
  }
}

/**
 * Modern, monochrome Active Filter Chip with clear remove button.
 */
@Composable
private fun ActiveOrderFilterChip(
  label: String,
  onRemove: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .clip(RoundedCornerShape(18.dp))
      .background(Color(0xFF1E1E1E))
      .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
  ) {
    Text(
      text = label,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      color = PureWhite,
    )
    Spacer(Modifier.width(6.dp))
    Box(
      modifier = Modifier
        .size(16.dp)
        .clip(CircleShape)
        .background(Color(0xFF333333))
        .clickable { onRemove() },
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "Remove",
        tint = PureWhite,
        modifier = Modifier.size(10.dp),
      )
    }
  }
}

/**
 * Filter Bottom Sheet with Monochrome Black-and-White Aesthetic
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrdersFilterBottomSheet(
  currentState: OrdersFilterState,
  availableCanteens: List<com.example.data.CampusBlockCanteen>,
  onDismiss: () -> Unit,
  onApply: (OrdersFilterState) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var draftState by remember { mutableStateOf(currentState) }
  var showDatePicker by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = PureWhite,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    dragHandle = {
      Box(
        modifier = Modifier
          .padding(vertical = 10.dp)
          .size(width = 36.dp, height = 4.dp)
          .clip(CircleShape)
          .background(Color(0xFFD1D5DB))
      )
    },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
      // ── Sheet Header ──
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "Filters",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = BlackPrimary,
          )
          if (draftState.activeFilterCount > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF3F4F6))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
              Text(
                text = "${draftState.activeFilterCount} selected",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BlackPrimary,
              )
            }
          }
        }

        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(32.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = BlackPrimary,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Spacer(Modifier.height(16.dp))

      // ── Scrollable Content ──
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        // ── Section 1: DATE ──
        Column {
          Text(
            text = "DATE",
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = Color(0xFF6B7280),
          )
          Spacer(Modifier.height(10.dp))

          // 2x2 grid / wrapped pills for dates
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterDatePill(
              label = "Today",
              isSelected = draftState.dateOption == OrdersDateFilterOption.TODAY,
              onClick = { draftState = draftState.copy(dateOption = OrdersDateFilterOption.TODAY) },
              modifier = Modifier.weight(1f),
            )
            FilterDatePill(
              label = "Yesterday",
              isSelected = draftState.dateOption == OrdersDateFilterOption.YESTERDAY,
              onClick = { draftState = draftState.copy(dateOption = OrdersDateFilterOption.YESTERDAY) },
              modifier = Modifier.weight(1f),
            )
          }

          Spacer(Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterDatePill(
              label = "Last 7 Days",
              isSelected = draftState.dateOption == OrdersDateFilterOption.LAST_7_DAYS,
              onClick = { draftState = draftState.copy(dateOption = OrdersDateFilterOption.LAST_7_DAYS) },
              modifier = Modifier.weight(1f),
            )
            FilterDatePill(
              label = "Last 30 Days",
              isSelected = draftState.dateOption == OrdersDateFilterOption.LAST_30_DAYS,
              onClick = { draftState = draftState.copy(dateOption = OrdersDateFilterOption.LAST_30_DAYS) },
              modifier = Modifier.weight(1f),
            )
          }

          Spacer(Modifier.height(8.dp))

          // Custom Date Range Pill
          val customLabel = if (draftState.dateOption == OrdersDateFilterOption.CUSTOM && draftState.customStartDateEpoch != null) {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            "Date: ${sdf.format(Date(draftState.customStartDateEpoch!!))}"
          } else {
            "Custom Date Range"
          }

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(if (draftState.dateOption == OrdersDateFilterOption.CUSTOM) BlackPrimary else Color(0xFFF9FAFB))
              .border(
                1.dp,
                if (draftState.dateOption == OrdersDateFilterOption.CUSTOM) BlackPrimary else Color(0xFFE5E7EB),
                RoundedCornerShape(12.dp),
              )
              .clickable {
                draftState = draftState.copy(dateOption = OrdersDateFilterOption.CUSTOM)
                showDatePicker = true
              }
              .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = if (draftState.dateOption == OrdersDateFilterOption.CUSTOM) PureWhite else BlackPrimary,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = customLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (draftState.dateOption == OrdersDateFilterOption.CUSTOM) PureWhite else BlackPrimary,
                modifier = Modifier.weight(1f),
              )
              if (draftState.dateOption == OrdersDateFilterOption.CUSTOM) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  tint = PureWhite,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }
        }

        // ── Section 2: CANTEEN ──
        Column {
          Text(
            text = "CANTEEN",
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = Color(0xFF6B7280),
          )
          Spacer(Modifier.height(10.dp))

          // "All Canteens" row
          val isAllSelected = draftState.selectedCanteenIds.isEmpty()
          CanteenSelectRow(
            name = "All Canteens",
            isSelected = isAllSelected,
            isMultiSelect = false,
            onClick = {
              draftState = draftState.copy(selectedCanteenIds = emptySet())
            },
          )

          // Individual Canteens (Strictly monochrome vector icons - NO food photos)
          for (canteen in availableCanteens) {
            val isSelected = draftState.selectedCanteenIds.contains(canteen.id)
            CanteenSelectRow(
              name = canteen.name,
              isSelected = isSelected,
              isMultiSelect = true,
              onClick = {
                val updated = if (isSelected) {
                  draftState.selectedCanteenIds - canteen.id
                } else {
                  draftState.selectedCanteenIds + canteen.id
                }
                draftState = draftState.copy(selectedCanteenIds = updated)
              },
            )
          }
        }
      }

      Spacer(Modifier.height(20.dp))

      // ── Sticky Action Buttons [Clear All] [Apply] ──
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Clear All Button
        Box(
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF3F4F6))
            .clickable {
              draftState = OrdersFilterState()
            },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "Clear All",
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = BlackPrimary,
          )
        }

        // Apply Button
        Box(
          modifier = Modifier
            .weight(1.5f)
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BlackPrimary)
            .clickable {
              onApply(draftState)
            },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "Apply Filters",
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = PureWhite,
          )
        }
      }
    }
  }

  // ── Date Picker Dialog for Custom Date Range ──
  if (showDatePicker) {
    val datePickerState = rememberDatePickerState(
      initialSelectedDateMillis = draftState.customStartDateEpoch ?: System.currentTimeMillis()
    )
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            val selected = datePickerState.selectedDateMillis
            if (selected != null) {
              draftState = draftState.copy(
                dateOption = OrdersDateFilterOption.CUSTOM,
                customStartDateEpoch = selected,
                customEndDateEpoch = selected,
              )
            }
            showDatePicker = false
          }
        ) {
          Text("Select", fontWeight = FontWeight.Bold, color = BlackPrimary)
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) {
          Text("Cancel", color = Color(0xFF6B7280))
        }
      }
    ) {
      DatePicker(state = datePickerState)
    }
  }
}

@Composable
private fun FilterDatePill(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .height(42.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(if (isSelected) BlackPrimary else Color(0xFFF9FAFB))
      .border(
        1.dp,
        if (isSelected) BlackPrimary else Color(0xFFE5E7EB),
        RoundedCornerShape(12.dp),
      )
      .clickable { onClick() }
      .padding(horizontal = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      fontSize = 13.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = if (isSelected) PureWhite else BlackPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun CanteenSelectRow(
  name: String,
  isSelected: Boolean,
  isMultiSelect: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable { onClick() }
      .padding(vertical = 10.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Monochrome store / canteen vector icon (Strictly NO food graphics)
    Box(
      modifier = Modifier
        .size(34.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(if (isSelected) BlackPrimary else Color(0xFFF3F4F6)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = if (name.contains("Kitchen", ignoreCase = true) || name.contains("Dhaba", ignoreCase = true)) Icons.Default.Storefront else Icons.Default.Business,
        contentDescription = null,
        tint = if (isSelected) PureWhite else BlackPrimary,
        modifier = Modifier.size(18.dp),
      )
    }

    Spacer(Modifier.width(12.dp))

    Text(
      text = name,
      fontSize = 14.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = BlackPrimary,
      modifier = Modifier.weight(1f),
    )

    // Selection Indicator (Monochrome circle / radio)
    Box(
      modifier = Modifier
        .size(20.dp)
        .clip(CircleShape)
        .background(if (isSelected) BlackPrimary else PureWhite)
        .border(1.5.dp, if (isSelected) BlackPrimary else Color(0xFFD1D5DB), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      if (isSelected) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = null,
          tint = PureWhite,
          modifier = Modifier.size(12.dp),
        )
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
