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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.OrderRecord
import com.example.data.OrderStatus
import com.example.data.api.MongoRepository
import com.example.data.api.OrderBarcodeResponseDto
import com.example.data.api.OrderQueuePositionDto
import com.example.data.api.WebSocketManager
import com.example.data.api.formatUtcToLocalTime
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.DrawerAmberDark
import com.example.ui.theme.DrawerAmberLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import com.example.util.BarcodeGenerator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val PageBackground = WarmCream
private val GreenSuccess = Color(0xFF16A34A)
private val AlertRed = Color(0xFFDC2626)

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

/**
 * Modern, Professional Student Order Tracking Screen for QuickBite:
 * 1. Order Number & Canteen Name
 * 2. Order Date and Time
 * 3. Ordered Food Items with quantity and price
 * 4. Total Amount & Payment Status
 * 5. Pickup Information & Counter
 * 6. Code 128 1D Barcode with token verification
 * 7. Order Progress Timeline:
 *    Order Placed → Order Accepted → Preparing → Ready for Pickup → Picked Up → Completed
 */
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

  // ── Dynamic Code 128 1D Barcode Verification State ────────────────────────
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

  LaunchedEffect(currentOrder.id, currentOrder.status) {
    fetchOrderBarcode()
  }

  val effectiveCanteenId = queuePositionData?.canteenId?.ifBlank { null }
    ?: currentOrder.canteenId.ifBlank { "canteen_33" }

  val tokenRaw = currentOrder.tokenNumber.ifBlank { "1042" }
  val tokenDisplay = if (tokenRaw.startsWith("#")) tokenRaw else "#Q$tokenRaw"

  val isCancelled = currentOrder.status == OrderStatus.CANCELLED
  val isCompleted = currentOrder.status == OrderStatus.COMPLETED
  val isPickedUp = isCompleted || barcodeResponse?.code == "ALREADY_COLLECTED"
  val isReady = currentOrder.status == OrderStatus.READY
  val isPreparing = currentOrder.status == OrderStatus.PREPARING
  val isAccepted = isPreparing || isReady || isPickedUp

  // Server-authoritative status timestamps
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
  }.ifBlank { "Just now" }

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
    if (isPreparing || isReady || isPickedUp) placedTimeDisplay else "-"
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

  val pickedUpTimeDisplay = formatUtcToLocalTime(currentOrder.completedAt).ifBlank {
    queuePositionData?.completedAtLocal?.let { formatUtcToLocalTime(it) }?.ifBlank { null }
      ?: formatUtcToLocalTime(queuePositionData?.completedAt)
  }.ifBlank {
    val historyCompleted = queuePositionData?.statusHistory?.find { it.status.equals("PICKED_UP", ignoreCase = true) || it.status.equals("COMPLETED", ignoreCase = true) }
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
        isLoadingQueue = false
      }
    }
  }

  // Live WebSocket updates
  DisposableEffect(currentOrder.id, effectiveCanteenId) {
    refreshQueue()
    WebSocketManager.subscribe("order:${currentOrder.id}")
    WebSocketManager.subscribe("canteen:$effectiveCanteenId")
    onDispose {
      WebSocketManager.unsubscribe("order:${currentOrder.id}")
      WebSocketManager.unsubscribe("canteen:$effectiveCanteenId")
    }
  }

  LaunchedEffect(currentOrder.id) {
    WebSocketManager.observeOrder(currentOrder.id).collect { updatedDto ->
      currentOrder = updatedDto.toOrderRecord()
      refreshQueue()
    }
  }

  LaunchedEffect(order.id) {
    val queryId = order.id.ifBlank { order.tokenNumber }
    MongoRepository.getOrder(queryId).onSuccess { fetchedDto ->
      currentOrder = fetchedDto.toOrderRecord()
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
    // ── Top App Bar ─────────────────────────────────────────────────────────
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(PureWhite)
        .padding(horizontal = 14.dp, vertical = 10.dp),
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
        text = "Order Details",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TextDark,
        modifier = Modifier.weight(1f),
      )

      // Token Badge in Top Bar
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(DrawerAmberLight)
          .border(1.dp, DrawerAmberDark.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
          .padding(horizontal = 10.dp, vertical = 4.dp),
      ) {
        Text(
          text = tokenDisplay,
          fontSize = 13.sp,
          fontWeight = FontWeight.ExtraBold,
          color = DrawerAmberDark,
        )
      }
    }

    HorizontalDivider(thickness = 1.dp, color = BorderGray.copy(alpha = 0.6f))

    // ── Scrollable Body ─────────────────────────────────────────────────────
    Column(
      modifier = Modifier
        .fillMaxSize()
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {

      // ── Card 1: Order Information & Header (Items 1, 2, 3, 7, 8) ───────────
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          // Status Pill & Order Date
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
                    isCancelled -> Color(0xFFFEE2E2)
                    isPickedUp -> Color(0xFFDCFCE7)
                    isReady -> Color(0xFFDCFCE7)
                    else -> DrawerAmberLight
                  }
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
              Text(
                text = when {
                  isCancelled -> "Cancelled"
                  isPickedUp -> "Picked Up"
                  isReady -> "Ready for Pickup"
                  isPreparing -> "Preparing"
                  else -> "Order Confirmed"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                  isCancelled -> AlertRed
                  isPickedUp || isReady -> GreenSuccess
                  else -> DrawerAmberDark
                },
              )
            }

            // Order Date & Time
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(13.dp),
              )
              Spacer(Modifier.width(4.dp))
              Text(
                text = placedTimeDisplay,
                fontSize = 12.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium,
              )
            }
          }

          Spacer(Modifier.height(14.dp))

          // Order Title / Canteen Info
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(DrawerAmber.copy(alpha = 0.12f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Storefront,
                contentDescription = null,
                tint = DrawerAmberDark,
                modifier = Modifier.size(22.dp),
              )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = currentOrder.pickupCanteenName.ifBlank { "Campus Canteen" },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
              Text(
                text = currentOrder.pickupLocation.ifBlank { "Campus Food Court" },
                fontSize = 12.5.sp,
                color = TextMuted,
              )
            }

            // Pickup Counter Pill
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(WarmCream)
                .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
              Text(
                text = currentOrder.pickupCounter.ifBlank { "Counter 1" },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BlackPrimary,
              )
            }
          }

          HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            thickness = 0.8.dp,
            color = BorderGray.copy(alpha = 0.4f),
          )

          // Payment Status & Pickup Information
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Payment,
                contentDescription = null,
                tint = GreenSuccess,
                modifier = Modifier.size(15.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = "Payment: Paid Online",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = GreenSuccess,
              )
            }

            val estDisplay = queuePositionData?.estimatedCompletionTime?.ifBlank { null }
              ?: currentOrder.estimatedCompletionTime.ifBlank { null }
              ?: currentOrder.estimatedReadyTime.ifBlank { null }
              ?: "5-10 min"

            Text(
              text = "Ready in: $estDisplay",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = DrawerAmberDark,
            )
          }
        }
      }

      // ── Card 2: 1D Code 128 Barcode for Order Verification (Item 9) ─────────
      if (!isCancelled) {
        OrderBarcode1DCard(
          order = currentOrder,
          tokenDisplay = tokenDisplay,
          barcodeResponse = barcodeResponse,
          isLoading = isLoadingBarcode,
          isRefreshing = isRefreshingBarcode,
          errorMessage = barcodeErrorMessage,
          onRefreshBarcode = { fetchOrderBarcode(isManualRefresh = true) },
        )
      }

      // ── Card 3: Order Progress Timeline (Item 10) ──────────────────────────
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text(
            text = "Order Progress",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = BlackPrimary,
          )

          Spacer(Modifier.height(14.dp))

          if (isCancelled) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFEE2E2))
                .border(1.dp, AlertRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(AlertRed),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = null,
                  tint = PureWhite,
                  modifier = Modifier.size(18.dp),
                )
              }
              Spacer(Modifier.width(12.dp))
              Column {
                Text(
                  text = "Order Cancelled",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = AlertRed,
                )
                Text(
                  text = "This order was cancelled and cannot be picked up.",
                  fontSize = 12.sp,
                  color = Color(0xFF7F1D1D),
                )
              }
            }
          } else {
            // 6-stage requested timeline:
            // Order Placed → Order Accepted → Preparing → Ready for Pickup → Picked Up → Completed
            TimelineStage(
              stageName = "Order Placed",
              timestamp = placedTimeDisplay,
              isCompleted = true,
              isCurrent = !isAccepted,
              isLast = false,
            )
            TimelineStage(
              stageName = "Order Accepted",
              timestamp = confirmedTimeDisplay,
              isCompleted = isAccepted,
              isCurrent = isAccepted && !isPreparing && !isReady && !isPickedUp,
              isLast = false,
            )
            TimelineStage(
              stageName = "Preparing",
              timestamp = preparingTimeDisplay,
              isCompleted = isPreparing || isReady || isPickedUp,
              isCurrent = isPreparing && !isReady && !isPickedUp,
              isLast = false,
            )
            TimelineStage(
              stageName = "Ready for Pickup",
              timestamp = readyTimeDisplay,
              isCompleted = isReady || isPickedUp,
              isCurrent = isReady && !isPickedUp,
              isLast = false,
            )
            TimelineStage(
              stageName = "Picked Up",
              timestamp = pickedUpTimeDisplay,
              isCompleted = isPickedUp,
              isCurrent = isPickedUp && !isCompleted,
              isLast = false,
            )
            TimelineStage(
              stageName = "Completed",
              timestamp = pickedUpTimeDisplay,
              isCompleted = isCompleted,
              isCurrent = isCompleted,
              isLast = true,
            )
          }
        }
      }

      // ── Card 4: Ordered Food Items & Total Amount (Items 4, 5, 6) ───────────
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Ordered Items",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = BlackPrimary,
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
                .padding(vertical = 5.dp),
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
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = BlackPrimary,
              )
            }
          }

          Spacer(Modifier.height(10.dp))
          HorizontalDivider(thickness = 0.8.dp, color = BorderGray.copy(alpha = 0.5f))
          Spacer(Modifier.height(10.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Total Amount",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark,
            )
            Text(
              text = "₹${currentOrder.totalPrice}",
              fontSize = 18.sp,
              fontWeight = FontWeight.ExtraBold,
              color = BlackPrimary,
            )
          }
        }
      }

      Spacer(Modifier.height(10.dp))
    }

    // ── Bottom Action Button ────────────────────────────────────────────────
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

/**
 * Clean 1D Code 128 Barcode Card:
 * - Traditional high-contrast 1D barcode generated from backend verification token
 * - Displays token in clean monospace font
 * - Pickup instruction for canteen collection
 */
@Composable
private fun OrderBarcode1DCard(
  order: OrderRecord,
  tokenDisplay: String,
  barcodeResponse: OrderBarcodeResponseDto?,
  isLoading: Boolean,
  isRefreshing: Boolean,
  errorMessage: String?,
  onRefreshBarcode: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isPickedUp = order.status == OrderStatus.COMPLETED || barcodeResponse?.code == "ALREADY_COLLECTED"

  val effectiveToken = remember(barcodeResponse?.verificationToken, order.id, order.tokenNumber) {
    barcodeResponse?.verificationToken?.ifBlank { null }
      ?: order.id.ifBlank { order.tokenNumber }
  }

  // Generate or decode 1D Code 128 Barcode
  val barcodeBitmap = remember(barcodeResponse?.barcodeDataUrl, effectiveToken) {
    decodeBase64DataUrl(barcodeResponse?.barcodeDataUrl)
      ?: BarcodeGenerator.generateCode128Barcode(effectiveToken, widthPx = 640, heightPx = 180)
  }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = PureWhite),
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(DrawerAmberLight),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
              contentDescription = null,
              tint = DrawerAmberDark,
              modifier = Modifier.size(17.dp),
            )
          }
          Spacer(Modifier.width(10.dp))
          Text(
            text = "Pickup Verification Barcode",
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = BlackPrimary,
          )
        }

        if (isRefreshing || (isLoading && barcodeBitmap == null)) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = DrawerAmber,
          )
        } else {
          IconButton(
            onClick = onRefreshBarcode,
            modifier = Modifier.size(24.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh barcode",
              tint = TextMuted,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }

      Spacer(Modifier.height(14.dp))

      if (isPickedUp) {
        // Collected State
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFDCFCE7).copy(alpha = 0.5f))
            .border(1.dp, GreenSuccess.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(vertical = 18.dp),
          contentAlignment = Alignment.Center,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = GreenSuccess,
              modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = "Order Verified & Picked Up",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = GreenSuccess,
            )
          }
        }
      } else {
        // High-contrast 1D Barcode Container
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PureWhite)
            .border(1.dp, BorderGray.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
          contentAlignment = Alignment.Center,
        ) {
          if (barcodeBitmap != null) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              Image(
                bitmap = barcodeBitmap.asImageBitmap(),
                contentDescription = "1D Order Barcode",
                modifier = Modifier
                  .fillMaxWidth()
                  .height(84.dp),
                contentScale = ContentScale.FillBounds,
              )
              Spacer(Modifier.height(6.dp))
              Text(
                text = effectiveToken,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = BlackPrimary,
                letterSpacing = 1.5.sp,
              )
            }
          } else {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.padding(16.dp),
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = DrawerAmber,
                strokeWidth = 2.dp,
              )
              Spacer(Modifier.height(8.dp))
              Text(
                text = "Generating verification barcode...",
                fontSize = 12.sp,
                color = TextMuted,
              )
            }
          }
        }

        Spacer(Modifier.height(10.dp))

        // Short Pickup Instruction
        Text(
          text = "Show this barcode at the canteen counter for pickup verification.",
          fontSize = 12.sp,
          color = TextMuted,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

/**
 * Clean Timeline Milestone Component for the 6-stage lifecycle
 */
@Composable
private fun TimelineStage(
  stageName: String,
  timestamp: String,
  isCompleted: Boolean,
  isCurrent: Boolean,
  isLast: Boolean,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Box(
        modifier = Modifier
          .size(22.dp)
          .clip(CircleShape)
          .background(
            when {
              isCompleted -> GreenSuccess
              isCurrent -> DrawerAmber
              else -> Color(0xFFE2E8F0)
            }
          ),
        contentAlignment = Alignment.Center,
      ) {
        if (isCompleted) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = PureWhite,
            modifier = Modifier.size(13.dp),
          )
        } else if (isCurrent) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(PureWhite),
          )
        }
      }

      if (!isLast) {
        Box(
          modifier = Modifier
            .width(2.dp)
            .height(28.dp)
            .background(if (isCompleted) GreenSuccess.copy(alpha = 0.5f) else Color(0xFFE2E8F0)),
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
        text = stageName,
        fontSize = 13.5.sp,
        fontWeight = if (isCompleted || isCurrent) FontWeight.Bold else FontWeight.Medium,
        color = if (isCompleted || isCurrent) BlackPrimary else TextMuted,
      )

      if (timestamp.isNotBlank() && timestamp != "-") {
        Text(
          text = timestamp,
          fontSize = 11.5.sp,
          color = TextMuted,
        )
      }
    }
  }
}
