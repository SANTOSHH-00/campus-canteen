// Customer Order Tracking: Displays real-time 5-stage order progression, FIFO queue position, and Code 128 barcode for counter pickup
package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import com.example.data.FulfillmentType
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.OrderRecord
import com.example.data.OrderStatus
import com.example.data.api.MongoRepository
import com.example.data.api.OrderBarcodeResponseDto
import com.example.data.api.OrderQueuePositionDto
import com.example.data.api.WebSocketManager
import com.example.data.api.formatUtcToLocalTime
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.DeliveryOrangeLight
import com.example.ui.theme.MintFresh
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.SpiceRed
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmCream
import com.example.util.BarcodeGenerator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
 * Redesigned, Professional QuickBite Order Tracking Screen:
 * - Clean layout with app's unified theme palette
 * - 5-Stage status timeline: Order Placed → Accepted → Preparing → Ready for Pickup → Picked Up
 * - Traditional 1D Code 128 barcode inside a clean card with zoom/enlarge preview
 * - Full order summary with items, prices, canteen & pickup information
 * - Real-time WebSocket and repository synchronization preserved
 */
@OptIn(ExperimentalMaterial3Api::class)
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
  var isScreenRefreshing by remember { mutableStateOf(false) }

  // Dynamic Code 128 1D Barcode Verification State
  var barcodeResponse by remember { mutableStateOf<OrderBarcodeResponseDto?>(null) }
  var isLoadingBarcode by remember { mutableStateOf(false) }
  var barcodeErrorMessage by remember { mutableStateOf<String?>(null) }
  var isRefreshingBarcode by remember { mutableStateOf(false) }

  var showEnlargedBarcode by remember { mutableStateOf(false) }

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

  fun refreshAllTrackingData() {
    val queryId = currentOrder.id.ifBlank { currentOrder.tokenNumber }
    if (queryId.isBlank()) return
    coroutineScope.launch {
      isScreenRefreshing = true
      fetchOrderBarcode(isManualRefresh = true)
      MongoRepository.getOrderQueuePosition(queryId).onSuccess {
        queuePositionData = it
        isLoadingQueue = false
      }.onFailure {
        isLoadingQueue = false
      }
      MongoRepository.getOrder(queryId).onSuccess { fetchedDto ->
        currentOrder = fetchedDto.toOrderRecord()
      }
      isScreenRefreshing = false
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
  val isRejected = currentOrder.status == OrderStatus.REJECTED
  val isCompleted = currentOrder.status == OrderStatus.COMPLETED
  val isPickedUp = isCompleted || barcodeResponse?.code == "ALREADY_COLLECTED"
  val isReady = currentOrder.status == OrderStatus.READY
  val isPreparing = currentOrder.status == OrderStatus.PREPARING
  val isAccepted = currentOrder.status == OrderStatus.ACCEPTED || isPreparing || isReady || isPickedUp
  val isPending = !isAccepted && !isRejected && !isCancelled

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
    val historyAccepted = queuePositionData?.statusHistory?.find {
      it.status.equals("ACCEPTED", ignoreCase = true) || it.status.equals("CONFIRMED", ignoreCase = true)
    }
    historyAccepted?.timestampLocal?.let { formatUtcToLocalTime(it) }?.ifBlank { null }
      ?: formatUtcToLocalTime(historyAccepted?.timestamp)
  }.ifBlank {
    if (isAccepted) currentOrder.acceptedAt.ifBlank { "" } else ""
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
    val historyCompleted = queuePositionData?.statusHistory?.find {
      it.status.equals("PICKED_UP", ignoreCase = true) || it.status.equals("COMPLETED", ignoreCase = true)
    }
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

  // Authoritative re-fetch directly from MongoDB upon network reconnection
  LaunchedEffect(Unit) {
    WebSocketManager.reconnectEvents.collect {
      val queryId = currentOrder.id.ifBlank { currentOrder.tokenNumber }
      MongoRepository.getOrder(queryId).onSuccess { fetchedDto ->
        currentOrder = fetchedDto.toOrderRecord()
      }
      refreshQueue()
      fetchOrderBarcode()
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("customer_order_tracking_screen"),
  ) {
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
        text = "Track Order",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TextDark,
        modifier = Modifier.weight(1f),
      )

      IconButton(
        onClick = { refreshAllTrackingData() },
        modifier = Modifier.size(36.dp),
      ) {
        if (isScreenRefreshing || isRefreshingBarcode) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = DeliveryOrange,
          )
        } else {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Refresh Order Status",
            tint = TextDark,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Spacer(Modifier.width(6.dp))

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(DeliveryOrangeLight)
          .padding(horizontal = 10.dp, vertical = 4.dp),
      ) {
        Text(
          text = tokenDisplay,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = DeliveryOrange,
        )
      }
    }

    HorizontalDivider(thickness = 1.dp, color = BorderGray.copy(alpha = 0.6f))

    PullToRefreshBox(
      isRefreshing = isScreenRefreshing,
      onRefresh = { refreshAllTrackingData() },
      modifier = Modifier
        .fillMaxSize()
        .weight(1f),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                    else -> DeliveryOrangeLight
                  }
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
              Text(
                text = when {
                  isCancelled -> "Cancelled"
                  isRejected -> "Rejected"
                  isPickedUp -> "Picked Up"
                  isReady -> "Ready for Pickup"
                  isPreparing -> "Preparing"
                  isAccepted -> "Order Accepted"
                  else -> "Waiting for Confirmation"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                  isCancelled || isRejected -> SpiceRed
                  isPickedUp || isReady -> MintFresh
                  isPreparing || isAccepted -> DeliveryOrange
                  else -> Color(0xFFD97706)
                },
              )
            }

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

          Spacer(Modifier.height(12.dp))

          Text(
            text = "Order #${currentOrder.id.ifBlank { currentOrder.tokenNumber }}",
            fontSize = 14.sp,
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
                .clip(CircleShape)
                .background(SoftGray),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Storefront,
                contentDescription = null,
                tint = TextDark,
                modifier = Modifier.size(20.dp),
              )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = currentOrder.pickupCanteenName.ifBlank { "Campus Canteen" },
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
              Text(
                text = currentOrder.pickupLocation.ifBlank { "Campus Food Court" },
                fontSize = 12.sp,
                color = TextMuted,
              )
            }

            val isDineIn = currentOrder.fulfillmentType == FulfillmentType.DINE_IN
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isDineIn) Color(0xFFF0FDF4) else Color(0xFFFFFBEB))
                  .border(0.8.dp, if (isDineIn) Color(0xFF86EFAC) else Color(0xFFFDE68A), RoundedCornerShape(8.dp))
                  .padding(horizontal = 7.dp, vertical = 3.dp),
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = if (isDineIn) Icons.Outlined.Restaurant else Icons.Outlined.ShoppingBag,
                    contentDescription = null,
                    tint = if (isDineIn) Color(0xFF16A34A) else Color(0xFFD97706),
                    modifier = Modifier.size(12.dp),
                  )
                  Spacer(Modifier.width(3.5.dp))
                  Text(
                    text = if (isDineIn) "Dine-in" else "Takeaway",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDineIn) Color(0xFF16A34A) else Color(0xFFB45309),
                  )
                }
              }

              Spacer(Modifier.width(6.dp))

              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(SoftGray)
                  .padding(horizontal = 8.dp, vertical = 4.dp),
              ) {
                Text(
                  text = currentOrder.pickupCounter.ifBlank { "Counter 1" },
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextSecondary,
                )
              }
            }
          }

          HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            thickness = 0.8.dp,
            color = BorderGray.copy(alpha = 0.4f),
          )

          val customerPickupDisplay = currentOrder.customerPickupTime.ifBlank {
            currentOrder.pickupPreference.ifBlank { "ASAP" }
          }
          val isScheduled = currentOrder.isScheduled || (!customerPickupDisplay.contains("ASAP", ignoreCase = true) && customerPickupDisplay.isNotBlank())
          val estDisplay = queuePositionData?.estimatedCompletionTime?.ifBlank { null }
            ?: currentOrder.estimatedCompletionTime.ifBlank { null }
            ?: currentOrder.estimatedReadyTime.ifBlank { null }
            ?: "5-10 min"

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = if (isScheduled) "Collection Time (Scheduled)" else "Collection Time",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isScheduled) Color(0xFFEA580C) else TextMuted,
              )
              Text(
                text = customerPickupDisplay,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isScheduled) Color(0xFFEA580C) else TextDark,
              )
            }

            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "Kitchen Prep",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted,
              )
              Text(
                text = estDisplay,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
            }
          }
        }
      }

      if (!isCancelled && !isRejected) {
        OrderBarcode1DCard(
          order = currentOrder,
          tokenDisplay = tokenDisplay,
          barcodeResponse = barcodeResponse,
          isLoading = isLoadingBarcode,
          isRefreshing = isRefreshingBarcode,
          errorMessage = barcodeErrorMessage,
          onRefreshBarcode = { fetchOrderBarcode(isManualRefresh = true) },
          onEnlarge = { showEnlargedBarcode = true },
        )
      }

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "Order Status",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )

          Spacer(Modifier.height(14.dp))

          if (isCancelled) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFEE2E2))
                .border(1.dp, SpiceRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(SpiceRed),
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
                  color = SpiceRed,
                )
                Text(
                  text = "This order was cancelled and cannot be picked up.",
                  fontSize = 12.sp,
                  color = Color(0xFF7F1D1D),
                )
              }
            }
          } else if (isRejected) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFEF2F2))
                .border(1.dp, SpiceRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(14.dp),
              verticalAlignment = Alignment.Top,
            ) {
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(SpiceRed),
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
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Order Rejected by Canteen",
                  fontSize = 14.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = SpiceRed,
                )
                Spacer(Modifier.height(4.dp))
                val reasonText = currentOrder.rejectionReason.ifBlank {
                  "The canteen kitchen is currently overloaded and cannot accept new orders."
                }
                Text(
                  text = "Reason: $reasonText",
                  fontSize = 12.5.sp,
                  color = TextDark,
                  fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                val refundMsg = if (currentOrder.refundStatus.equals("PROCESSED", ignoreCase = true)) {
                  "A full refund of ₹${currentOrder.totalPrice} has been processed back to your original payment method."
                } else {
                  "A full refund of ₹${currentOrder.totalPrice} has been initiated to your original payment method."
                }
                Text(
                  text = refundMsg,
                  fontSize = 12.sp,
                  color = MintFresh,
                  fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                  onClick = onOrderAgain,
                  colors = ButtonDefaults.buttonColors(containerColor = DeliveryOrange),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier.height(36.dp),
                ) {
                  Text("Explore Other Canteens", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          } else {
            // Student-facing status banner
            val bannerText = when {
              isPickedUp -> "Your order has been collected! Enjoy your meal."
              isReady -> "Your order is ready for pickup! Please show your barcode at ${currentOrder.pickupCounter}."
              isPreparing -> "Your order is being prepared freshly by the kitchen."
              isAccepted -> "Your order has been accepted by the canteen! Food preparation will begin shortly."
              else -> "Your order has been placed! Waiting for the canteen to confirm your order."
            }
            val bannerBg = when {
              isPickedUp || isReady -> Color(0xFFF0FDF4)
              isPreparing || isAccepted -> Color(0xFFFFF7ED)
              else -> Color(0xFFFEFCE8)
            }
            val bannerBorder = when {
              isPickedUp || isReady -> MintFresh.copy(alpha = 0.3f)
              isPreparing || isAccepted -> DeliveryOrange.copy(alpha = 0.3f)
              else -> Color(0xFFFACC15).copy(alpha = 0.4f)
            }
            val bannerTextColor = when {
              isPickedUp || isReady -> Color(0xFF166534)
              isPreparing || isAccepted -> Color(0xFF9A3412)
              else -> Color(0xFF854D0E)
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(bannerBg)
                .border(1.dp, bannerBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = if (isPickedUp || isReady) Icons.Default.CheckCircle else Icons.Default.AccessTime,
                  contentDescription = null,
                  tint = bannerTextColor,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                  text = bannerText,
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.Medium,
                  color = bannerTextColor,
                )
              }
            }

            Spacer(Modifier.height(14.dp))

            // 6-stage lifecycle timeline
            TimelineStage(
              stageName = "Order Placed",
              timestamp = placedTimeDisplay,
              isCompleted = true,
              isActive = false,
              isLast = false,
            )
            TimelineStage(
              stageName = "Waiting for Canteen Confirmation",
              timestamp = if (isAccepted) "Confirmed" else "Waiting for canteen...",
              isCompleted = isAccepted,
              isActive = isPending,
              isLast = false,
            )
            TimelineStage(
              stageName = "Order Accepted",
              timestamp = if (isAccepted) confirmedTimeDisplay else "",
              isCompleted = isPreparing || isReady || isPickedUp,
              isActive = isAccepted && !isPreparing && !isReady && !isPickedUp,
              isLast = false,
            )
            TimelineStage(
              stageName = "Preparing",
              timestamp = if (isPreparing || isReady || isPickedUp) preparingTimeDisplay else "",
              isCompleted = isReady || isPickedUp,
              isActive = isPreparing && !isReady && !isPickedUp,
              isLast = false,
            )
            TimelineStage(
              stageName = "Ready for Pickup",
              timestamp = if (isReady || isPickedUp) readyTimeDisplay else "",
              isCompleted = isPickedUp,
              isActive = isReady && !isPickedUp,
              isLast = false,
            )
            TimelineStage(
              stageName = "Picked Up",
              timestamp = if (isPickedUp) pickedUpTimeDisplay else "",
              isCompleted = isPickedUp,
              isActive = isPickedUp,
              isLast = true,
            )
          }
        }
      }

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
            }
          }

          Spacer(Modifier.height(8.dp))
          HorizontalDivider(thickness = 0.8.dp, color = BorderGray.copy(alpha = 0.4f))
          Spacer(Modifier.height(8.dp))

          val isDineIn = currentOrder.fulfillmentType == FulfillmentType.DINE_IN
          val itemsSubtotal = if (currentOrder.itemsSubtotal > 0) currentOrder.itemsSubtotal else currentOrder.items.sumOf { it.totalPrice }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Items Subtotal", fontSize = 13.sp, color = TextMuted)
            Text("₹$itemsSubtotal", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
          }

          Spacer(Modifier.height(4.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(if (isDineIn) "Packaging (Dine-in)" else "Packaging (Takeaway)", fontSize = 13.sp, color = TextMuted)
            Text(
              text = if (isDineIn || currentOrder.packagingCharge == 0) "FREE" else "₹${currentOrder.packagingCharge}",
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = if (isDineIn || currentOrder.packagingCharge == 0) MintFresh else TextDark,
            )
          }

          if (currentOrder.taxAmount > 0) {
            Spacer(Modifier.height(4.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Taxes (GST)", fontSize = 13.sp, color = TextMuted)
              Text("₹${currentOrder.taxAmount}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
            }
          }

          Spacer(Modifier.height(8.dp))
          HorizontalDivider(thickness = 0.8.dp, color = BorderGray.copy(alpha = 0.4f))
          Spacer(Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Total Amount",
              fontSize = 14.5.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark,
            )
            Text(
              text = "₹${currentOrder.totalPrice}",
              fontSize = 17.sp,
              fontWeight = FontWeight.ExtraBold,
              color = TextDark,
            )
          }
        }
      }

      Spacer(Modifier.height(8.dp))
    }
  }

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
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = BlackPrimary),
        ) {
          Text(
            text = "Order Again",
            fontSize = 14.5.sp,
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
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = BlackPrimary),
        ) {
          Text(
            text = "Back to Menu",
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = PureWhite,
          )
        }
      }
    }
  }

  if (showEnlargedBarcode) {
    val effectiveToken = remember(barcodeResponse?.verificationToken, currentOrder.id, currentOrder.tokenNumber) {
      barcodeResponse?.verificationToken?.ifBlank { null }
        ?: currentOrder.id.ifBlank { currentOrder.tokenNumber }
    }
    val largeBarcodeBitmap = remember(barcodeResponse?.barcodeDataUrl, effectiveToken) {
      decodeBase64DataUrl(barcodeResponse?.barcodeDataUrl)
        ?: BarcodeGenerator.generateCode128Barcode(effectiveToken, widthPx = 900, heightPx = 260)
    }

    Dialog(onDismissRequest = { showEnlargedBarcode = false }) {
      Card(
        modifier = Modifier
          .fillMaxWidth(0.96f)
          .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f)),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(22.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = "Pickup Barcode",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
              Text(
                text = "Show to canteen staff for pickup verification",
                fontSize = 11.5.sp,
                color = TextMuted,
              )
            }
            IconButton(
              onClick = { showEnlargedBarcode = false },
              modifier = Modifier.size(28.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = TextDark,
                modifier = Modifier.size(20.dp),
              )
            }
          }

          Spacer(Modifier.height(16.dp))

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(PureWhite)
              .border(1.dp, BorderGray.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
              .padding(14.dp),
            contentAlignment = Alignment.Center,
          ) {
            if (largeBarcodeBitmap != null) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                  bitmap = largeBarcodeBitmap.asImageBitmap(),
                  contentDescription = "Enlarged 1D Barcode",
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                  contentScale = ContentScale.FillBounds,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                  text = effectiveToken,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace,
                  color = TextDark,
                  letterSpacing = 2.sp,
                )
              }
            } else {
              CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = DeliveryOrange,
                strokeWidth = 2.dp,
              )
            }
          }

          Spacer(Modifier.height(18.dp))

          Button(
            onClick = { showEnlargedBarcode = false },
            modifier = Modifier
              .fillMaxWidth()
              .height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BlackPrimary),
          ) {
            Text(
              text = "Close",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = PureWhite,
            )
          }
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
 * - Tap to enlarge / zoom button for quick counter scanning
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
  onEnlarge: () -> Unit,
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
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = PureWhite),
    border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
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
              .size(30.dp)
              .clip(CircleShape)
              .background(SoftGray),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
              contentDescription = null,
              tint = TextDark,
              modifier = Modifier.size(16.dp),
            )
          }
          Spacer(Modifier.width(8.dp))
          Text(
            text = "Pickup Verification Barcode",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onEnlarge,
            modifier = Modifier.size(28.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Fullscreen,
              contentDescription = "Enlarge barcode",
              tint = TextSecondary,
              modifier = Modifier.size(18.dp),
            )
          }

          Spacer(Modifier.width(2.dp))

          if (isRefreshing || (isLoading && barcodeBitmap == null)) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
              color = DeliveryOrange,
            )
          } else {
            IconButton(
              onClick = onRefreshBarcode,
              modifier = Modifier.size(28.dp),
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
      }

      Spacer(Modifier.height(12.dp))

      if (isPickedUp) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFDCFCE7).copy(alpha = 0.5f))
            .border(1.dp, MintFresh.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(vertical = 16.dp),
          contentAlignment = Alignment.Center,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = MintFresh,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = "Order Verified & Picked Up",
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Bold,
              color = MintFresh,
            )
          }
        }
      } else {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PureWhite)
            .border(1.dp, BorderGray.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .clickable { onEnlarge() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                  .height(80.dp),
                contentScale = ContentScale.FillBounds,
              )
              Spacer(Modifier.height(6.dp))
              Text(
                text = effectiveToken,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TextDark,
                letterSpacing = 1.5.sp,
              )
            }
          } else {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.padding(14.dp),
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = DeliveryOrange,
                strokeWidth = 2.dp,
              )
              Spacer(Modifier.height(6.dp))
              Text(
                text = "Generating verification barcode...",
                fontSize = 11.5.sp,
                color = TextMuted,
              )
            }
          }
        }

        Spacer(Modifier.height(8.dp))

        Text(
          text = "Show this barcode to the canteen staff for pickup verification.",
          fontSize = 11.5.sp,
          color = TextMuted,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

/**
 * Clean Timeline Stage Component for the 5-stage lifecycle:
 * - Active: clearly highlighted with app's primary theme colour (DeliveryOrange)
 * - Completed: subtle and consistent with MintFresh checkmark
 * - Upcoming / Inactive: subtle neutral colours (BorderGray / Color(0xFFE2E8F0))
 */
@Composable
private fun TimelineStage(
  stageName: String,
  timestamp: String,
  isCompleted: Boolean,
  isActive: Boolean,
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
          .background(
            when {
              isCompleted -> MintFresh
              isActive -> DeliveryOrange
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
            modifier = Modifier.size(12.dp),
          )
        } else if (isActive) {
          Box(
            modifier = Modifier
              .size(7.dp)
              .clip(CircleShape)
              .background(PureWhite),
          )
        }
      }

      if (!isLast) {
        Box(
          modifier = Modifier
            .width(2.dp)
            .height(26.dp)
            .background(
              when {
                isCompleted -> MintFresh.copy(alpha = 0.4f)
                else -> Color(0xFFE2E8F0)
              }
            ),
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
        fontWeight = if (isActive || isCompleted) FontWeight.SemiBold else FontWeight.Normal,
        color = if (isActive || isCompleted) TextDark else TextMuted,
      )

      if (timestamp.isNotBlank() && timestamp != "-") {
        Text(
          text = timestamp,
          fontSize = 11.5.sp,
          fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
          color = if (isActive) DeliveryOrange else TextMuted,
        )
      }
    }
  }
}
