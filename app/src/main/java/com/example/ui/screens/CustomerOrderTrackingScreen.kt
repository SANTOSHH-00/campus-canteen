package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.OrderRecord
import com.example.data.OrderStatus
import com.example.data.api.MongoRepository
import com.example.data.api.OrderQueuePositionDto
import com.example.data.api.WebSocketManager
import com.example.data.api.formatUtcToLocalTime
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PageBackground = Color(0xFFF9FAFB)
private val GreenReady = Color(0xFF16A34A)
private val DarkCardBg = PureWhite

@Composable
fun CustomerOrderTrackingScreen(
  order: OrderRecord,
  onBack: () -> Unit,
  onOrderAgain: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val coroutineScope = rememberCoroutineScope()
  var currentOrder by remember(order.id) { mutableStateOf(order) }
  var queuePositionData by remember { mutableStateOf<OrderQueuePositionDto?>(null) }
  var isLoadingQueue by remember { mutableStateOf(true) }

  val effectiveCanteenId = queuePositionData?.canteenId?.ifBlank { null }
    ?: currentOrder.canteenId.ifBlank { "canteen_33" }

  val tokenRaw = currentOrder.tokenNumber.ifBlank { "1042" }
  val tokenDisplay = if (tokenRaw.startsWith("#")) tokenRaw else "#Q$tokenRaw"

  val isPickedUp = currentOrder.status == OrderStatus.COMPLETED
  val isReady = currentOrder.status == OrderStatus.READY
  val isPreparing = currentOrder.status == OrderStatus.PREPARING
  val hasStartedPreparing = isPreparing || isReady || isPickedUp

  // Server-authoritative status timestamps (exact milestone event times, never device clock)
  val placedTimeDisplay = currentOrder.orderPlacedAt.ifBlank {
    formatUtcToLocalTime(queuePositionData?.orderPlacedAt)
  }.ifBlank {
    currentOrder.orderTime.removePrefix("Today, ")
  }.ifBlank { "-" }

  val confirmedTimeDisplay = currentOrder.confirmedAt.ifBlank {
    formatUtcToLocalTime(queuePositionData?.confirmedAt)
  }.ifBlank {
    placedTimeDisplay
  }

  val preparingTimeDisplay = currentOrder.preparingAt.ifBlank {
    formatUtcToLocalTime(queuePositionData?.preparingAt)
  }.ifBlank {
    val historyPreparing = queuePositionData?.statusHistory?.find { it.status.equals("PREPARING", ignoreCase = true) }?.timestamp
    formatUtcToLocalTime(historyPreparing)
  }.ifBlank {
    if (hasStartedPreparing) placedTimeDisplay else "-"
  }

  val readyTimeDisplay = currentOrder.readyAt.ifBlank {
    formatUtcToLocalTime(queuePositionData?.readyAt)
  }.ifBlank {
    val historyReady = queuePositionData?.statusHistory?.find { it.status.equals("READY", ignoreCase = true) }?.timestamp
    formatUtcToLocalTime(historyReady)
  }.ifBlank {
    if (isReady || isPickedUp) preparingTimeDisplay else "-"
  }

  val completedTimeDisplay = currentOrder.completedAt.ifBlank {
    formatUtcToLocalTime(queuePositionData?.completedAt)
  }.ifBlank {
    val historyCompleted = queuePositionData?.statusHistory?.find { it.status.equals("COMPLETED", ignoreCase = true) }?.timestamp
    formatUtcToLocalTime(historyCompleted)
  }.ifBlank {
    if (isPickedUp) readyTimeDisplay else "-"
  }

  // Load and refresh real-time queue position
  val refreshQueue: () -> Unit = {
    coroutineScope.launch {
      val queryId = currentOrder.id.ifBlank { currentOrder.tokenNumber }
      MongoRepository.getOrderQueuePosition(queryId).onSuccess {
        queuePositionData = it
        isLoadingQueue = false
      }.onFailure {
        // Fallback: try by raw token number if distinct from orderId
        val rawToken = currentOrder.tokenNumber.replace("^[^0-9]+".toRegex(), "")
        if (rawToken.isNotBlank() && rawToken != queryId) {
          MongoRepository.getOrderQueuePosition(rawToken).onSuccess {
            queuePositionData = it
            isLoadingQueue = false
          }.onFailure {
            // Further fallback: fetch canteen overall active queue to estimate position
            MongoRepository.getCanteenQueue(effectiveCanteenId).onSuccess { cq ->
              queuePositionData = OrderQueuePositionDto(
                orderId = currentOrder.id,
                tokenNumber = currentOrder.tokenNumber,
                status = currentOrder.status.name,
                canteenId = effectiveCanteenId,
                queuePosition = cq.queueCount.coerceAtLeast(1),
                ordersAhead = (cq.queueCount - 1).coerceAtLeast(0),
                estWaitMinutes = cq.avgWaitMinutes.coerceAtLeast(5),
              )
              isLoadingQueue = false
            }.onFailure {
              isLoadingQueue = false
            }
          }
        } else {
          isLoadingQueue = false
        }
      }
    }
  }

  // Subscribe to both order channel and canteen queue channel for live kitchen advancement
  DisposableEffect(currentOrder.id, effectiveCanteenId) {
    refreshQueue()
    WebSocketManager.subscribe("order:${currentOrder.id}")
    WebSocketManager.subscribe("canteen:$effectiveCanteenId")
    onDispose {
      WebSocketManager.unsubscribe("order:${currentOrder.id}")
      WebSocketManager.unsubscribe("canteen:$effectiveCanteenId")
    }
  }

  // Live WebSocket queue updates when kitchen updates/completes preceding orders
  LaunchedEffect(effectiveCanteenId) {
    WebSocketManager.queueUpdates.collect { updatedCanteenId ->
      if (updatedCanteenId.isBlank() || updatedCanteenId == effectiveCanteenId) {
        refreshQueue()
      }
    }
  }

  // Live Order Status updates for this order specifically via WebSocket
  LaunchedEffect(currentOrder.id) {
    WebSocketManager.observeOrder(currentOrder.id).collect { updatedDto ->
      currentOrder = updatedDto.toOrderRecord()
      refreshQueue()
    }
  }

  // Fetch initial authoritative order data from server on launch to ensure latest status & timestamps
  LaunchedEffect(order.id) {
    val queryId = order.id.ifBlank { order.tokenNumber }
    MongoRepository.getOrder(queryId).onSuccess { fetchedDto ->
      currentOrder = fetchedDto.toOrderRecord()
    }
  }

  // Graceful API polling fallback every 8 seconds if WebSocket is interrupted
  LaunchedEffect(currentOrder.id) {
    while (true) {
      kotlinx.coroutines.delay(8000)
      if (!isPickedUp) {
        refreshQueue()
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(PageBackground)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("customer_order_tracking_screen"),
  ) {
    // ── Clean Minimal Top Bar ───────────────────────────────────────────────
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(PureWhite)
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = onBack,
        modifier = Modifier.testTag("order_tracking_back_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back",
          tint = TextDark,
          modifier = Modifier.size(22.dp),
        )
      }

      Spacer(Modifier.width(6.dp))

      Text(
        text = "Order Tracking",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TextDark,
        modifier = Modifier.weight(1f),
      )

      // Token Badge in Top Bar
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFFF3F4F6))
          .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
          .padding(horizontal = 10.dp, vertical = 4.dp),
      ) {
        Text(
          text = tokenDisplay,
          fontSize = 13.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
        )
      }
    }

    HorizontalDivider(thickness = 1.dp, color = Color(0xFFE5E7EB))

    // ── Scrollable Body ─────────────────────────────────────────────────────
    Column(
      modifier = Modifier
        .fillMaxSize()
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {

      // ── Card 1: Queue Feedback & Status Hero Card ─────────────────────────
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          // Status Badge + Live Dot
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                  when {
                    isPickedUp -> Color(0xFFF3F4F6)
                    isReady -> Color(0xFFF0FDF4)
                    else -> Color(0xFFFFF7ED)
                  }
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
              Text(
                text = when {
                  isPickedUp -> "Picked Up"
                  isReady -> "Ready for Pickup"
                  else -> "In Kitchen Queue"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                  isPickedUp -> Color(0xFF4B5563)
                  isReady -> GreenReady
                  else -> Color(0xFFEA580C)
                },
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(7.dp)
                  .clip(CircleShape)
                  .background(if (isPickedUp) Color(0xFF9CA3AF) else GreenReady)
              )
              Spacer(Modifier.width(5.dp))
              Text(
                text = if (isPickedUp) "Completed" else "Live Tracking",
                fontSize = 11.5.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium,
              )
            }
          }

          Spacer(Modifier.height(14.dp))

          // Main Headline with Item-Aware Queue Feedback
          val headlineText = when {
            isPickedUp -> "Order Picked Up ✓"
            isReady -> "Your order is ready for pickup!"
            isLoadingQueue && queuePositionData == null -> "Checking Kitchen Queue..."
            else -> {
              val similarAhead = queuePositionData?.similarOrdersAhead ?: queuePositionData?.ordersAhead ?: 0
              val pos = queuePositionData?.queuePosition ?: 1
              if (similarAhead == 0) "You are next in line"
              else "You are #$pos in line"
            }
          }

          Text(
            text = headlineText,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextDark,
          )

          Spacer(Modifier.height(6.dp))

          val sublineText = when {
            isPickedUp -> "Handed over to you. Thank you for dining with QuickBite!"
            isReady -> "Please show Token $tokenDisplay at ${order.pickupCounter.ifBlank { "Counter 1" }} to collect."
            isLoadingQueue && queuePositionData == null -> "Retrieving live wait time and position from canteen..."
            else -> {
              val similarAhead = queuePositionData?.similarOrdersAhead ?: queuePositionData?.ordersAhead ?: 0
              val workload = queuePositionData?.workloadSummary?.ifBlank {
                if (similarAhead == 0) "0 orders ahead of you" else "$similarAhead order${if (similarAhead > 1) "s" else ""} ahead of you"
              } ?: if (similarAhead == 0) "0 orders ahead of you" else "$similarAhead order${if (similarAhead > 1) "s" else ""} ahead of you"
              val completionText = queuePositionData?.estimatedCompletionTime?.ifBlank { "~${queuePositionData?.estWaitMinutes ?: 7} min" } ?: "~${queuePositionData?.estWaitMinutes ?: 7} min"
              "$workload • Estimated completion: $completionText"
            }
          }

          Text(
            text = sublineText,
            fontSize = 13.5.sp,
            color = Color(0xFF4B5563),
            lineHeight = 18.sp,
          )

          // Live Anonymous Queue Statistics Bar (Active kitchen queue)
          if (isPreparing) {
            val similarAhead = queuePositionData?.similarOrdersAhead ?: queuePositionData?.ordersAhead ?: 0
            val pos = queuePositionData?.queuePosition ?: 1
            val estMins = queuePositionData?.estWaitMinutes ?: 7
            val completionTimeStr = queuePositionData?.estimatedCompletionTime?.ifBlank { "~$estMins min" } ?: "~$estMins min"

            Spacer(Modifier.height(14.dp))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF9FAFB))
                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = "Queue Position",
                  fontSize = 11.sp,
                  color = TextMuted,
                  fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                  text = if (similarAhead == 0) "Next in Line" else "#$pos in Line",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = TextDark,
                )
              }

              Box(
                modifier = Modifier
                  .width(1.dp)
                  .height(24.dp)
                  .background(Color(0xFFE5E7EB))
              )

              Column {
                Text(
                  text = "Workload Ahead",
                  fontSize = 11.sp,
                  color = TextMuted,
                  fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                  text = if (similarAhead == 0) "0 orders" else "$similarAhead order${if (similarAhead > 1) "s" else ""}",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = TextDark,
                )
              }

              Box(
                modifier = Modifier
                  .width(1.dp)
                  .height(24.dp)
                  .background(Color(0xFFE5E7EB))
              )

              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "Est. Completion",
                  fontSize = 11.sp,
                  color = TextMuted,
                  fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                  text = completionTimeStr,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = Color(0xFFEA580C),
                )
              }
            }
          }
        }
      }

      // ── Card 2: Pickup Location & Counter ─────────────────────────────────
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "Pickup Details",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )
          Spacer(Modifier.height(10.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF3F4F6)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = TextDark,
                modifier = Modifier.size(20.dp),
              )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = currentOrder.pickupCanteenName.ifBlank { "Campus Canteen" },
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
              Text(
                text = currentOrder.pickupLocation.ifBlank { "Block 26-27 Food Court" },
                fontSize = 12.5.sp,
                color = TextMuted,
              )
            }

            // Counter Assigned Badge
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF9FAFB))
                .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
              Text(
                text = currentOrder.pickupCounter.ifBlank { "Counter 1" },
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
            }
          }
        }
      }

      // ── Card 3: Minimal Timeline Milestones ────────────────────────────────
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text(
            text = "Order Progress",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )
          Spacer(Modifier.height(14.dp))

          TrackingMilestone(
            title = "Order Placed",
            time = placedTimeDisplay,
            isCompleted = true,
            isLast = false,
          )
          TrackingMilestone(
            title = "Payment Confirmed",
            time = confirmedTimeDisplay,
            isCompleted = true,
            isLast = false,
          )
          TrackingMilestone(
            title = "Kitchen Preparing",
            time = if (hasStartedPreparing) preparingTimeDisplay else "-",
            isCompleted = hasStartedPreparing,
            isLast = false,
          )
          TrackingMilestone(
            title = "Ready for Pickup",
            time = if (isReady || isPickedUp) readyTimeDisplay else "-",
            isCompleted = isReady || isPickedUp,
            isLast = false,
          )
          TrackingMilestone(
            title = "Picked Up",
            time = if (isPickedUp) completedTimeDisplay else "-",
            isCompleted = isPickedUp,
            isLast = true,
          )
        }
      }

      // ── Card 4: Order Summary ─────────────────────────────────────────────
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Ordered Items",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark,
            )
            Text(
              text = "${currentOrder.items.size} item(s)",
              fontSize = 12.sp,
              color = TextMuted,
            )
          }

          Spacer(Modifier.height(10.dp))

          currentOrder.items.forEach { item ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${item.quantity}x  ${item.foodItem.name}",
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextDark,
                )
                if (item.selectedOption != null || item.selectedAddons.isNotEmpty()) {
                  val details = buildList {
                    if (item.selectedOption != null) add(item.selectedOption)
                    item.selectedAddons.forEach { add("+${it.name}") }
                  }.joinToString(" • ")
                  Text(
                    text = details,
                    fontSize = 11.5.sp,
                    color = TextMuted,
                  )
                }
              }
              Text(
                text = "₹${item.totalPrice}",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
            }
          }

          Spacer(Modifier.height(10.dp))
          HorizontalDivider(thickness = 0.8.dp, color = Color(0xFFE5E7EB))
          Spacer(Modifier.height(10.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Total Paid",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark,
            )
            Text(
              text = "₹${currentOrder.totalPrice}",
              fontSize = 18.sp,
              fontWeight = FontWeight.ExtraBold,
              color = TextDark,
            )
          }
        }
      }

      Spacer(Modifier.height(10.dp))
    }

    // ── Bottom Clean Action Bar ─────────────────────────────────────────────
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(PureWhite)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      if (isPickedUp) {
        Button(
          onClick = onOrderAgain,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          shape = RoundedCornerShape(24.dp),
          colors = ButtonDefaults.buttonColors(containerColor = BlackPrimary),
        ) {
          Text(
            text = "Order Again",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = PureWhite,
          )
        }
      } else {
        Button(
          onClick = onBack,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          shape = RoundedCornerShape(24.dp),
          colors = ButtonDefaults.buttonColors(containerColor = BlackPrimary),
        ) {
          Text(
            text = "Back to Menu",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = PureWhite,
          )
        }
      }
    }
  }
}

// ── Clean Minimal Timeline Milestone ────────────────────────────────────────
@Composable
private fun TrackingMilestone(
  title: String,
  time: String,
  isCompleted: Boolean,
  isLast: Boolean,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 3.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Box(
        modifier = Modifier
          .size(20.dp)
          .clip(CircleShape)
          .background(if (isCompleted) Color(0xFF16A34A) else Color(0xFFE5E7EB)),
        contentAlignment = Alignment.Center,
      ) {
        if (isCompleted) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = PureWhite,
            modifier = Modifier.size(12.dp),
          )
        }
      }

      if (!isLast) {
        Box(
          modifier = Modifier
            .width(1.5.dp)
            .height(26.dp)
            .background(if (isCompleted) Color(0xFF16A34A).copy(alpha = 0.4f) else Color(0xFFE5E7EB))
        )
      }
    }

    Spacer(Modifier.width(12.dp))

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 1.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = if (isCompleted) FontWeight.Bold else FontWeight.Medium,
        color = if (isCompleted) TextDark else Color(0xFF9CA3AF),
      )

      Text(
        text = time,
        fontSize = 12.sp,
        color = Color(0xFF6B7280),
      )
    }
  }
}
