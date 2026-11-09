package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.OrderRecord
import com.example.data.OrderStatus
import com.example.data.api.MongoRepository
import com.example.data.api.OrderBarcodeResponseDto
import com.example.data.api.OrderQrResponseDto
import androidx.compose.material.icons.filled.QrCodeScanner
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
import com.example.util.BarcodeGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val PageBackground = WarmCream
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

  // ── Dynamic Code 128 Barcode Verification State ─────────────────────────
  var barcodeResponse by remember { mutableStateOf<OrderBarcodeResponseDto?>(null) }
  var isLoadingBarcode by remember { mutableStateOf(false) }
  var barcodeErrorMessage by remember { mutableStateOf<String?>(null) }
  var isRefreshingBarcode by remember { mutableStateOf(false) }

  fun fetchOrderBarcode(isManualRefresh: Boolean = false) {
    val targetId = currentOrder.id.ifBlank { currentOrder.tokenNumber }
    if (targetId.isBlank()) return
    coroutineScope.launch {
      if (isManualRefresh) isRefreshingBarcode = true else isLoadingBarcode = true
      barcodeErrorMessage = null
      MongoRepository.getOrderBarcode(targetId).onSuccess { dto ->
        barcodeResponse = dto
        isLoadingBarcode = false
        isRefreshingBarcode = false
      }.onFailure { err ->
        barcodeErrorMessage = err.message ?: "Unable to load barcode."
        isLoadingBarcode = false
        isRefreshingBarcode = false
      }
    }
  }

  // ── Dynamic QR Verification State ─────────────────────────────────────────
  var qrResponse by remember { mutableStateOf<OrderQrResponseDto?>(null) }
  var isLoadingQr by remember { mutableStateOf(false) }
  var qrErrorMessage by remember { mutableStateOf<String?>(null) }
  var isRotatingQr by remember { mutableStateOf(false) }

  fun fetchOrderQr(isManualRefresh: Boolean = false) {
    val targetId = currentOrder.id.ifBlank { currentOrder.tokenNumber }
    if (targetId.isBlank()) return
    coroutineScope.launch {
      if (isManualRefresh) isRotatingQr = true else isLoadingQr = true
      qrErrorMessage = null
      MongoRepository.getOrderQr(targetId).onSuccess { dto ->
        qrResponse = dto
        isLoadingQr = false
        isRotatingQr = false
      }.onFailure { err ->
        qrErrorMessage = err.message ?: "Unable to load QR code."
        isLoadingQr = false
        isRotatingQr = false
      }
    }
  }

  LaunchedEffect(currentOrder.id, currentOrder.status) {
    fetchOrderBarcode()
    fetchOrderQr()
  }

  val effectiveCanteenId = queuePositionData?.canteenId?.ifBlank { null }
    ?: currentOrder.canteenId.ifBlank { "canteen_33" }

  val tokenRaw = currentOrder.tokenNumber.ifBlank { "1042" }
  val tokenDisplay = if (tokenRaw.startsWith("#")) tokenRaw else "#Q$tokenRaw"

  val isPickedUp = currentOrder.status == OrderStatus.COMPLETED
  val isReady = currentOrder.status == OrderStatus.READY
  val isPreparing = currentOrder.status == OrderStatus.PREPARING
  val hasStartedPreparing = isPreparing || isReady || isPickedUp

  // Server-authoritative status timestamps (exact milestone event times, never device clock)
  val placedTimeDisplay = formatUtcToLocalTime(currentOrder.orderPlacedAt).ifBlank {
    queuePositionData?.orderPlacedAtLocal?.let { formatUtcToLocalTime(it) }?.ifBlank { null }
      ?: queuePositionData?.orderTime?.let { formatUtcToLocalTime(it) }?.ifBlank { null }
      ?: formatUtcToLocalTime(queuePositionData?.orderPlacedAt)
  }.ifBlank {
    val fromOrderTime = currentOrder.orderTime.removePrefix("Today,").removePrefix("today,").trim()
    if (fromOrderTime.isNotBlank() && fromOrderTime != "Today" && fromOrderTime != "-") fromOrderTime else ""
  }.ifBlank {
    val epochFromId = com.example.data.api.extractEpochFromOrderId(currentOrder.id)
    if (epochFromId > 0) {
      val df = SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
      }
      df.format(Date(epochFromId))
    } else ""
  }.ifBlank { "-" }

  val confirmedTimeDisplay = formatUtcToLocalTime(currentOrder.confirmedAt).ifBlank {
    queuePositionData?.confirmedAtLocal?.let { formatUtcToLocalTime(it) }?.ifBlank { null }
      ?: formatUtcToLocalTime(queuePositionData?.confirmedAt)
  }.ifBlank {
    placedTimeDisplay
  }

  val preparingTimeDisplay = formatUtcToLocalTime(currentOrder.preparingAt).ifBlank {
    queuePositionData?.preparingAtLocal?.let { formatUtcToLocalTime(it) }?.ifBlank { null }
      ?: formatUtcToLocalTime(queuePositionData?.preparingAt)
  }.ifBlank {
    val historyPreparing = queuePositionData?.statusHistory?.find { it.status.equals("PREPARING", ignoreCase = true) }
    historyPreparing?.timestampLocal?.let { formatUtcToLocalTime(it) }?.ifBlank { null }
      ?: formatUtcToLocalTime(historyPreparing?.timestamp)
  }.ifBlank {
    if (hasStartedPreparing) placedTimeDisplay else "-"
  }

  val readyTimeDisplay = formatUtcToLocalTime(currentOrder.readyAt).ifBlank {
    queuePositionData?.readyAtLocal?.let { formatUtcToLocalTime(it) }?.ifBlank { null }
      ?: formatUtcToLocalTime(queuePositionData?.readyAt)
  }.ifBlank {
    val historyReady = queuePositionData?.statusHistory?.find { it.status.equals("READY", ignoreCase = true) }
    historyReady?.timestampLocal?.let { formatUtcToLocalTime(it) }?.ifBlank { null }
      ?: formatUtcToLocalTime(historyReady?.timestamp)
  }.ifBlank {
    "-"
  }

  val completedTimeDisplay = formatUtcToLocalTime(currentOrder.completedAt).ifBlank {
    queuePositionData?.completedAtLocal?.let { formatUtcToLocalTime(it) }?.ifBlank { null }
      ?: formatUtcToLocalTime(queuePositionData?.completedAt)
  }.ifBlank {
    val historyCompleted = queuePositionData?.statusHistory?.find { it.status.equals("COMPLETED", ignoreCase = true) }
    historyCompleted?.timestampLocal?.let { formatUtcToLocalTime(it) }?.ifBlank { null }
      ?: formatUtcToLocalTime(historyCompleted?.timestamp)
  }.ifBlank {
    "-"
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
              val waitMins = queuePositionData?.estWaitMinutes ?: 7
              val serverCompletion = queuePositionData?.estimatedCompletionTime?.ifBlank { null }
                ?: currentOrder.estimatedCompletionTime.ifBlank { null }
                ?: currentOrder.estimatedReadyTime.ifBlank { null }
                ?: "~$waitMins min"
              "$workload • Estimated ready: $serverCompletion (~$waitMins min)"
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
            val completionTimeStr = queuePositionData?.estimatedCompletionTime?.ifBlank { null }
              ?: currentOrder.estimatedCompletionTime.ifBlank { null }
              ?: currentOrder.estimatedReadyTime.ifBlank { null }
              ?: "~$estMins min"

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

      // ── Dynamic Code 128 Barcode & QR Verification Card ────────────────────
      OrderBarcodeCard(
        order = currentOrder,
        tokenDisplay = tokenDisplay,
        barcodeResponse = barcodeResponse,
        qrResponse = qrResponse,
        isLoading = isLoadingBarcode || isLoadingQr,
        isRefreshing = isRefreshingBarcode || isRotatingQr,
        errorMessage = barcodeErrorMessage ?: qrErrorMessage,
        onRefreshBarcode = {
          fetchOrderBarcode(isManualRefresh = true)
          fetchOrderQr(isManualRefresh = true)
        },
      )

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

          HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            thickness = 1.dp,
            color = Color(0xFFF3F4F6)
          )

          val customerPickupDisplay = queuePositionData?.customerPickupTime?.ifBlank { null }
            ?: currentOrder.customerPickupTime.ifBlank {
              currentOrder.pickupPreference.ifBlank { "ASAP" }
            }
          val estCompletionDisplay = queuePositionData?.estimatedCompletionTime?.ifBlank { null }
            ?: currentOrder.estimatedCompletionTime.ifBlank { null }
            ?: currentOrder.estimatedReadyTime.ifBlank { null }
            ?: "In kitchen queue"

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = "Customer Pickup",
                fontSize = 11.5.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium,
              )
              Spacer(Modifier.height(2.dp))
              Text(
                text = customerPickupDisplay,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
            }

            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "Est. Completion",
                fontSize = 11.5.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium,
              )
              Spacer(Modifier.height(2.dp))
              Text(
                text = estCompletionDisplay,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEA580C),
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
            title = "Order Received",
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

// ── Dynamic QR & Code 128 Barcode Verification Card ─────────────────────────

private fun decodeBase64DataUrl(dataUrl: String?): android.graphics.Bitmap? {
  if (dataUrl.isNullOrBlank()) return null
  return try {
    val clean = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
    val bytes = Base64.decode(clean.trim(), Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
  } catch (_: Exception) {
    null
  }
}

enum class VerificationType(val title: String) {
  QR_CODE("QR Code"),
  BARCODE("Barcode"),
}

@Composable
fun OrderBarcodeCard(
  order: OrderRecord,
  tokenDisplay: String,
  barcodeResponse: OrderBarcodeResponseDto?,
  qrResponse: OrderQrResponseDto? = null,
  isLoading: Boolean = false,
  isRefreshing: Boolean = false,
  errorMessage: String? = null,
  onRefreshBarcode: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var selectedType by remember { mutableStateOf(VerificationType.QR_CODE) }

  val isPickedUp = order.status == OrderStatus.COMPLETED || barcodeResponse?.code == "ALREADY_COLLECTED"
  val isReady = order.status == OrderStatus.READY

  // Cryptographically unique verification token, fallback to order.id / tokenNumber
  val effectiveToken = remember(barcodeResponse?.verificationToken, qrResponse?.rawToken, order.id, order.tokenNumber) {
    qrResponse?.rawToken?.ifBlank { null }
      ?: barcodeResponse?.verificationToken?.ifBlank { null }
      ?: order.id.ifBlank { order.tokenNumber }
  }

  // QR Code Bitmap: server dataUrl or locally generated via ZXing BarcodeGenerator directly on order.id
  val qrBitmap = remember(qrResponse?.qrDataUrl, effectiveToken, order.id) {
    decodeBase64DataUrl(qrResponse?.qrDataUrl)
      ?: BarcodeGenerator.generateQrCode(order.id.ifBlank { effectiveToken }, sizePx = 512)
  }

  // Code 128 Barcode Bitmap: server dataUrl or locally generated via ZXing BarcodeGenerator
  val barcodeBitmap = remember(barcodeResponse?.barcodeDataUrl, effectiveToken) {
    decodeBase64DataUrl(barcodeResponse?.barcodeDataUrl)
      ?: BarcodeGenerator.generateCode128Barcode(effectiveToken, widthPx = 640, heightPx = 180)
  }

  val borderColor = when {
    isPickedUp -> Color(0xFF86EFAC)
    isReady -> Color(0xFF22C55E)
    else -> Color(0xFFE5E7EB)
  }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = PureWhite),
    border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      // ── Top Header Row ───────────────────────────────────────────
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(34.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(if (isReady) Color(0xFFDCFCE7) else Color(0xFFFFF7ED)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.QrCodeScanner,
              contentDescription = null,
              tint = if (isReady) Color(0xFF16A34A) else Color(0xFFEA580C),
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(Modifier.width(10.dp))
          Column {
            Text(
              text = "Pickup Barcode & QR",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark
            )
            Text(
              text = tokenDisplay,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextMuted
            )
          }
        }

        // Status Badge + background sync indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (isRefreshing || (isLoading && qrBitmap == null && barcodeBitmap == null)) {
            CircularProgressIndicator(
              modifier = Modifier
                .padding(end = 8.dp)
                .size(14.dp),
              strokeWidth = 2.dp,
              color = Color(0xFFEA580C)
            )
          }
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(
                when {
                  isPickedUp -> Color(0xFFDCFCE7)
                  isReady -> Color(0xFFDCFCE7)
                  else -> Color(0xFFFFF7ED)
                }
              )
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = when {
                isPickedUp -> "COLLECTED"
                isReady -> "READY FOR PICKUP"
                else -> order.status.name
              },
              fontSize = 11.sp,
              fontWeight = FontWeight.ExtraBold,
              color = when {
                isPickedUp || isReady -> Color(0xFF16A34A)
                else -> Color(0xFFEA580C)
              }
            )
          }
        }
      }

      if (!isPickedUp) {
        Spacer(Modifier.height(12.dp))

        // ── Tab Switcher: QR Code vs Barcode ─────────────────────────
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF3F4F6))
            .padding(3.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          VerificationType.values().forEach { type ->
            val isSelected = selectedType == type
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) PureWhite else Color.Transparent)
                .clickable { selectedType = type }
                .padding(vertical = 7.dp),
              contentAlignment = Alignment.Center
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = if (type == VerificationType.QR_CODE) Icons.Default.QrCode else Icons.Default.QrCodeScanner,
                  contentDescription = null,
                  tint = if (isSelected) Color(0xFFEA580C) else Color(0xFF6B7280),
                  modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                  text = type.title,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) TextDark else Color(0xFF6B7280)
                )
              }
            }
          }
        }
      }

      Spacer(Modifier.height(14.dp))

      // ── Center Code Graphic Display (White background, black bars/modules) ──
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFFFFFFFF))
          .border(
            width = 1.dp,
            color = if (isPickedUp) Color(0xFFBBF7D0) else Color(0xFFE5E7EB),
            shape = RoundedCornerShape(12.dp)
          )
          .padding(vertical = 14.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
      ) {
        when {
          isPickedUp -> {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.padding(vertical = 16.dp)
            ) {
              Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF16A34A),
                modifier = Modifier.size(48.dp)
              )
              Spacer(Modifier.height(6.dp))
              Text(
                text = "Order Successfully Collected",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF15803D)
              )
              val pickupTime = barcodeResponse?.collectedAtLocal ?: ""
              if (pickupTime.isNotBlank()) {
                Text(
                  text = "Collected at $pickupTime",
                  fontSize = 11.sp,
                  color = TextMuted
                )
              }
            }
          }
          selectedType == VerificationType.QR_CODE && qrBitmap != null -> {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.fillMaxWidth()
            ) {
              // High-resolution ZXing QR Code
              Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "Order Pickup QR Code for Order ID ${order.id}",
                modifier = Modifier
                  .size(160.dp)
                  .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
              )
              Spacer(Modifier.height(8.dp))
              Text(
                text = "Order ID: ${order.id}",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                letterSpacing = 0.5.sp
              )
              Text(
                text = "Token: $tokenDisplay • Scan for instant pickup",
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp)
              )
            }
          }
          selectedType == VerificationType.BARCODE && barcodeBitmap != null -> {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.fillMaxWidth()
            ) {
              // High-resolution Code 128 Barcode Image
              Image(
                bitmap = barcodeBitmap.asImageBitmap(),
                contentDescription = "Order Pickup Barcode",
                modifier = Modifier
                  .fillMaxWidth()
                  .height(72.dp),
                contentScale = ContentScale.FillBounds
              )
              Spacer(Modifier.height(6.dp))
              Text(
                text = effectiveToken,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937),
                letterSpacing = 1.sp
              )
            }
          }
          isLoading -> {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.padding(vertical = 24.dp)
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color(0xFFEA580C),
                strokeWidth = 2.5.dp
              )
              Spacer(Modifier.height(8.dp))
              Text(
                text = "Generating verification code...",
                fontSize = 11.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium
              )
            }
          }
          else -> {
            // Instant fallback: generates QR code on device
            val fallbackBitmap = BarcodeGenerator.generateQrCode(effectiveToken, sizePx = 512)
            if (fallbackBitmap != null) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
              ) {
                Image(
                  bitmap = fallbackBitmap.asImageBitmap(),
                  contentDescription = "Order Pickup Code",
                  modifier = Modifier.size(160.dp),
                  contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(8.dp))
                Text(
                  text = effectiveToken,
                  fontSize = 13.sp,
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF1F2937),
                  letterSpacing = 1.sp
                )
              }
            } else {
              CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color(0xFFEA580C),
                strokeWidth = 2.dp
              )
            }
          }
        }
      }

      // If server error occurred, show subtle notice with retry without blocking code display
      if (errorMessage != null && !isPickedUp) {
        Spacer(Modifier.height(6.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onRefreshBarcode() }
            .padding(vertical = 2.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Refresh Server Token",
            tint = Color(0xFFEA580C),
            modifier = Modifier.size(13.dp)
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = "Ready to scan • Tap to re-sync server",
            fontSize = 10.5.sp,
            color = Color(0xFFEA580C),
            fontWeight = FontWeight.SemiBold
          )
        }
      }

      Spacer(Modifier.height(10.dp))

      // ── Clear Instruction Banner ──────────────────────────────────
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFFEFF6FF))
          .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(8.dp))
          .padding(horizontal = 10.dp, vertical = 7.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = Color(0xFF2563EB),
            modifier = Modifier.size(14.dp)
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = "Show this QR code or barcode to the canteen owner when collecting your order.",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1E40AF)
          )
        }
      }

      Spacer(Modifier.height(12.dp))
      HorizontalDivider(color = Color(0xFFF3F4F6))
      Spacer(Modifier.height(10.dp))

      // ── Order Details Section ─────────────────────────────────────
      // Order ID and Date/Time
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "Order ID: #${order.id.takeLast(8)}",
          fontSize = 11.5.sp,
          color = TextMuted,
          fontWeight = FontWeight.Medium
        )
        val dateDisplay = barcodeResponse?.orderDate
          ?: (if (order.placedEpoch > 0) SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(order.placedEpoch)) else null)
          ?: order.orderDate.ifBlank { null }
          ?: "Today"
        Text(
          text = dateDisplay,
          fontSize = 11.5.sp,
          color = TextMuted,
          fontWeight = FontWeight.Medium
        )
      }

      Spacer(Modifier.height(6.dp))

      // Order Items summary
      if (order.items.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth()) {
          order.items.forEach { item ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "${item.quantity}x ${item.foodItem.name}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
              Text(
                text = "₹${"%.2f".format(item.totalPrice.toDouble())}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextDark
              )
            }
          }
        }
      }

      Spacer(Modifier.height(8.dp))

      // Total Amount
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Total Amount",
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark
        )
        Text(
          text = "₹${"%.2f".format(order.totalPrice.toDouble())}",
          fontSize = 16.sp,
          fontWeight = FontWeight.ExtraBold,
          color = Color(0xFFEA580C)
        )
      }
    }
  }
}

// ── Rectangular Dynamic QR Verification Card ─────────────────────────────────

@Composable
private fun RectangularOrderQrCard(
  order: OrderRecord,
  tokenDisplay: String,
  qrResponse: OrderQrResponseDto?,
  isLoading: Boolean,
  isRotating: Boolean,
  errorMessage: String?,
  onRefreshQr: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isPickedUp = order.status == OrderStatus.COMPLETED
  val isReady = order.status == OrderStatus.READY
  val qrBitmap = remember(qrResponse?.qrDataUrl, order.id, order.tokenNumber) {
    decodeBase64DataUrl(qrResponse?.qrDataUrl)
      ?: BarcodeGenerator.generateQrCode(order.id.ifBlank { order.tokenNumber }, sizePx = 512)
  }

  val borderColor = when {
    isPickedUp -> Color(0xFFDCFCE7)
    isReady -> Color(0xFF16A34A).copy(alpha = 0.5f)
    else -> Color(0xFFE5E7EB)
  }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = PureWhite),
    border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // ── Left: QR Code Box or Picked Up Verified Badge ─────────
      Box(
        modifier = Modifier
          .size(112.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(if (isPickedUp) Color(0xFFF0FDF4) else Color(0xFFF9FAFB))
          .border(
            1.dp,
            if (isPickedUp) Color(0xFFDCFCE7) else Color(0xFFE5E7EB),
            RoundedCornerShape(14.dp)
          ),
        contentAlignment = Alignment.Center
      ) {
        when {
          isPickedUp -> {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF16A34A),
                modifier = Modifier.size(42.dp)
              )
              Spacer(Modifier.height(4.dp))
              Text(
                text = "VERIFIED",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF15803D)
              )
              Text(
                text = "Picked Up",
                fontSize = 10.sp,
                color = Color(0xFF16A34A)
              )
            }
          }
          isLoading && qrBitmap == null -> {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = Color(0xFFEA580C),
                strokeWidth = 2.5.dp
              )
              Spacer(Modifier.height(6.dp))
              Text(
                text = "Loading QR",
                fontSize = 10.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium
              )
            }
          }
          qrBitmap != null -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PureWhite),
              contentAlignment = Alignment.Center
            ) {
              Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "Order Pickup QR Code",
                modifier = Modifier.fillMaxSize()
              )
            }
          }
          errorMessage != null -> {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier
                .clickable { onRefreshQr() }
                .padding(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Retry QR",
                tint = Color(0xFFEA580C),
                modifier = Modifier.size(24.dp)
              )
              Spacer(Modifier.height(4.dp))
              Text(
                text = "Tap to load",
                fontSize = 10.sp,
                color = Color(0xFFEA580C),
                fontWeight = FontWeight.SemiBold
              )
            }
          }
          else -> {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              color = Color(0xFFEA580C),
              strokeWidth = 2.dp
            )
          }
        }
      }

      // ── Right: Ticket Details & Validity (Rectangular Layout) ─
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(start = 14.dp),
        verticalArrangement = Arrangement.Center
      ) {
        // Token + Status Tag
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = tokenDisplay,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextDark
          )

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(
                when {
                  isPickedUp -> Color(0xFFDCFCE7)
                  isReady -> Color(0xFFDCFCE7)
                  else -> Color(0xFFFFF7ED)
                }
              )
              .padding(horizontal = 7.dp, vertical = 2.dp)
          ) {
            Text(
              text = when {
                isPickedUp -> "COLLECTED"
                isReady -> "READY"
                else -> "ACTIVE"
              },
              fontSize = 10.sp,
              fontWeight = FontWeight.ExtraBold,
              color = when {
                isPickedUp || isReady -> Color(0xFF16A34A)
                else -> Color(0xFFEA580C)
              }
            )
          }
        }

        Spacer(Modifier.height(4.dp))

        // Canteen Name & Pickup Counter
        Text(
          text = order.pickupCanteenName.ifBlank { "Campus Canteen" },
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "${order.pickupCounter.ifBlank { "Counter 1" }} • Scan for Pickup",
          fontSize = 11.5.sp,
          color = TextMuted,
          maxLines = 1
        )

        Spacer(Modifier.height(8.dp))

        // Pickup Instruction or Refresh Bar
        when {
          isPickedUp -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF16A34A),
                modifier = Modifier.size(14.dp)
              )
              Spacer(Modifier.width(4.dp))
              Text(
                text = "Order verified & collected",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF16A34A)
              )
            }
          }
          isReady -> {
            Text(
              text = "Show this QR at counter for instant pickup!",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color(0xFF15803D),
              lineHeight = 15.sp
            )
          }
          else -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Single-use dynamic token",
                fontSize = 11.sp,
                color = TextMuted
              )

              // Rotate / Refresh QR button
              Row(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .clickable(enabled = !isRotating) { onRefreshQr() }
                  .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (isRotating) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = Color(0xFFEA580C)
                  )
                } else {
                  Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Rotate QR",
                    tint = Color(0xFFEA580C),
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(Modifier.width(3.dp))
                  Text(
                    text = "Refresh",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEA580C)
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
