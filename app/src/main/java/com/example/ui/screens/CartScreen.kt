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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BottomNavTab
import com.example.data.OrderRecord
import com.example.ui.components.FoodImagePlaceholder
import com.example.ui.components.PickupTimePickerSheet
import com.example.ui.state.CanteenAppState
import com.example.util.NetworkUtils
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.CardSurface
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.DeliveryOrangeLight
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.OpenGreen
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import androidx.compose.ui.text.style.TextAlign
import com.example.ui.state.UserProfile

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.data.api.CartQueueItemRequestDto
import com.example.data.api.CartQueueResponseDto
import com.example.data.api.CanteenQueueDto
import com.example.data.api.MongoRepository
import com.example.data.api.WebSocketManager

@Composable
fun CartScreen(
  appState: CanteenAppState,
  onNavigateToTab: (BottomNavTab) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var newlyPlacedOrder by remember { mutableStateOf<OrderRecord?>(null) }
  var showLoginPrompt by remember { mutableStateOf(false) }
  var showTimePickerDialog by remember { mutableStateOf(false) }
  var showNetworkErrorDialog by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val selectedCanteenId = appState.selectedCanteen.id
  var cartQueueData by remember { mutableStateOf<CartQueueResponseDto?>(null) }

  // Load and refresh item-specific queue data for items currently in cart
  val refreshQueue: () -> Unit = {
    coroutineScope.launch {
      val itemsDto = appState.cartItems.map {
        CartQueueItemRequestDto(
          itemId = it.foodItem.id,
          name = it.foodItem.name,
          quantity = it.quantity,
        )
      }
      MongoRepository.getCartQueue(selectedCanteenId, itemsDto).onSuccess {
        cartQueueData = it
      }
    }
  }

  // Refresh when cart items change or canteen changes
  LaunchedEffect(selectedCanteenId, appState.cartItems.size) {
    refreshQueue()
  }

  // Clean subscription lifecycle for canteen queue channel
  DisposableEffect(selectedCanteenId) {
    refreshQueue()
    WebSocketManager.subscribe("canteen:$selectedCanteenId")
    onDispose {
      WebSocketManager.unsubscribe("canteen:$selectedCanteenId")
    }
  }

  // Live WebSocket reactive update: when preceding order picked up / updated, auto-refresh
  LaunchedEffect(selectedCanteenId) {
    WebSocketManager.queueUpdates.collect { canteenId ->
      if (canteenId.isBlank() || canteenId == selectedCanteenId) {
        refreshQueue()
      }
    }
  }

  // Fallback periodic poll in case WebSocket is disconnected
  LaunchedEffect(selectedCanteenId) {
    while (true) {
      kotlinx.coroutines.delay(10000)
      refreshQueue()
    }
  }

  val cartListState = rememberLazyListState()

  LaunchedEffect(Unit) {
    appState.isBottomBarVisible = true
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream)
      .statusBarsPadding()
  ) {
    if (appState.cartItems.isEmpty()) {
      // Subtle, minimal background food emojis pattern (low alpha, non-intrusive)
      MinimalFoodEmojisPattern()

      // Empty Cart State
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Box(
          modifier = Modifier
            .size(90.dp)
            .clip(CircleShape)
            .background(SoftGray),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.ShoppingBag,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(44.dp),
          )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
          text = "Your Cart is Empty",
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = BlackPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Browse Quick Order or Popular items and add your favorites to pick up during break!",
          fontSize = 13.sp,
          color = TextMuted,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          lineHeight = 18.sp,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BlackPrimary)
            .clickable { onNavigateToTab(BottomNavTab.HOME) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "Browse Menu",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = PureWhite,
          )
        }
      }
    } else {
      // Active Cart Items List
      LazyColumn(
        state = cartListState,
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 20.dp)
          .padding(bottom = 80.dp), // space for docked bottom summary bar
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item {
          Spacer(modifier = Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Your Order Cart",
              fontSize = 22.sp,
              fontWeight = FontWeight.ExtraBold,
              color = BlackPrimary,
            )
            Text(
              text = "Clear All",
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = DrawerAmber,
              modifier = Modifier.clickable { appState.clearCart() },
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFFF7F3EC))
              .border(1.dp, BorderGray.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
              .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(appState.selectedCanteen.icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Pickup: ${appState.selectedCanteen.name}",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = BlackPrimary,
              )
              Text(
                text = "${appState.selectedCanteen.betweenBlocks} • ${appState.selectedCanteen.floorInfo}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
              )
            }
          }
          Spacer(modifier = Modifier.height(10.dp))

          // ── Kitchen Live Queue & Avg Wait Time Card (Normal neutral icons, clean layout) ──
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                val firstItemName = appState.cartItems.firstOrNull()?.foodItem?.name ?: "Item"
                val displayTitle = if (appState.cartItems.size == 1) "$firstItemName Queue" else "Item Live Queue"

                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = TextDark,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(Modifier.width(6.dp))
                  Text(
                    text = displayTitle,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                  )
                }

                // Live status dot
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .size(7.dp)
                      .clip(CircleShape)
                      .background(Color(0xFF16A34A))
                  )
                  Spacer(Modifier.width(5.dp))
                  Text(
                    text = "Live",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF16A34A),
                  )
                }
              }

              Spacer(Modifier.height(10.dp))

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(Color(0xFFF9FAFB))
                  .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                val queuePosition = cartQueueData?.queuePosition ?: 1
                val similarOrdersAhead = cartQueueData?.similarOrdersAhead ?: 0
                val waitTime = cartQueueData?.estWaitMinutes ?: 5
                val readyTime = cartQueueData?.estimatedCompletionTime?.ifBlank { "~$waitTime mins" } ?: "~$waitTime mins"
                val workloadText = cartQueueData?.workloadSummary?.ifBlank {
                  if (similarOrdersAhead == 0) "0 similar orders ahead" else "$similarOrdersAhead similar order${if (similarOrdersAhead > 1) "s" else ""} ahead"
                } ?: (if (similarOrdersAhead == 0) "0 similar orders ahead" else "$similarOrdersAhead similar order${if (similarOrdersAhead > 1) "s" else ""} ahead")

                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Queue: #$queuePosition",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDark,
                  )
                  Spacer(Modifier.height(2.dp))
                  Text(
                    text = if (similarOrdersAhead == 0) "No queue ahead (You're First)" else workloadText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4B5563),
                  )
                }

                Box(
                  modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(BorderGray)
                )

                Column(
                  modifier = Modifier.weight(1f),
                  horizontalAlignment = Alignment.End,
                ) {
                  Text(
                    text = "Estimated Ready",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted,
                  )
                  Spacer(Modifier.height(2.dp))
                  Text(
                    text = readyTime,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFEA580C),
                  )
                }
              }

              Spacer(Modifier.height(6.dp))
              Text(
                text = "Estimated preparation time for your items. Updates automatically in real-time.",
                fontSize = 10.5.sp,
                color = TextMuted,
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
        }

        items(appState.cartItems, key = { "${it.foodItem.id}_${it.selectedOption}_${it.selectedAddons.map { a -> a.name }}" }) { cartItem ->
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              FoodImagePlaceholder(
                itemName = cartItem.foodItem.name,
                compact = true,
                modifier = Modifier.size(54.dp),
              )

              Spacer(modifier = Modifier.width(12.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = cartItem.foodItem.name,
                  fontSize = 14.5.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = TextDark,
                )
                if (cartItem.selectedOption != null || cartItem.selectedAddons.isNotEmpty()) {
                  val optionsSummary = buildList {
                    if (cartItem.selectedOption != null) add(cartItem.selectedOption)
                    cartItem.selectedAddons.forEach { add("+${it.name}") }
                  }.joinToString(" • ")
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = optionsSummary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DrawerAmber,
                  )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "₹${cartItem.unitPrice} each",
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextMuted,
                )
              }

              // Quantity Stepper: [-] [qty] [+]
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .clip(RoundedCornerShape(20.dp))
                  .background(SoftGray)
                  .padding(horizontal = 6.dp, vertical = 4.dp),
              ) {
                Box(
                  modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(PureWhite)
                    .clickable { appState.decreaseCartItem(cartItem) },
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = if (cartItem.quantity == 1) Icons.Default.DeleteOutline else Icons.Default.Remove,
                    contentDescription = "Decrease",
                    tint = if (cartItem.quantity == 1) Color(0xFFDC2626) else TextDark,
                    modifier = Modifier.size(15.dp),
                  )
                }

                Text(
                  text = "${cartItem.quantity}",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = TextDark,
                  modifier = Modifier.padding(horizontal = 10.dp),
                )

                Box(
                  modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(PureWhite)
                    .clickable { appState.increaseCartItem(cartItem) },
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase",
                    tint = TextDark,
                    modifier = Modifier.size(15.dp),
                  )
                }
              }
            }
          }
        }

        // Bill Summary
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text(
                text = "Bill Details",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDark,
              )
              Spacer(modifier = Modifier.height(10.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("Item Subtotal", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
                Text("₹${appState.subtotal}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
              }
              Spacer(modifier = Modifier.height(6.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("Canteen Taxes & Packaging", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
                Text("₹${appState.taxAmount}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
              }
              Spacer(modifier = Modifier.height(8.dp))
              HorizontalDivider(color = BorderGray)
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("Grand Total", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
                Text("₹${appState.grandTotal}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = BlackPrimary)
              }
            }
          }
        }

        item {
          Spacer(modifier = Modifier.height(14.dp))

          // ── Pickup Preference Section (Exact Match to User Mock) ─────────
          Text(
            text = "Pickup Preference",
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextDark,
          )

          Spacer(modifier = Modifier.height(10.dp))

          // Radio option 1: Pickup
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { appState.pickupPreferenceType = "Pickup" }
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.RadioButtonChecked,
              contentDescription = "Pickup",
              tint = BlackPrimary,
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "Pickup",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark,
            )
          }

          Spacer(modifier = Modifier.height(6.dp))

          // Radio option 2: ASAP
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                appState.isPickupAsap = true
              }
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = if (appState.isPickupAsap) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
              contentDescription = "ASAP",
              tint = if (appState.isPickupAsap) BlackPrimary else BorderGray,
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "ASAP",
              fontSize = 15.sp,
              fontWeight = if (appState.isPickupAsap) FontWeight.Bold else FontWeight.Medium,
              color = TextDark,
            )
          }

          Spacer(modifier = Modifier.height(6.dp))

          // Radio option 3: Choose pickup time
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                appState.isPickupAsap = false
                showTimePickerDialog = true
              }
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = if (!appState.isPickupAsap) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
              contentDescription = "Choose pickup time",
              tint = if (!appState.isPickupAsap) BlackPrimary else BorderGray,
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = if (!appState.isPickupAsap && appState.customPickupTime != null) "Choose pickup time (${appState.customPickupTime})" else "Choose pickup time",
              fontSize = 15.sp,
              fontWeight = if (!appState.isPickupAsap) FontWeight.Bold else FontWeight.Medium,
              color = TextDark,
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          // ── Amber Estimated Ready Banner (Dashboard Theme Matching) ───────
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(Color(0xFFF7F3EC))
              .border(1.dp, BorderGray.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
              .padding(horizontal = 14.dp, vertical = 12.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                tint = DrawerAmber,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Estimated ready: ",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
              Text(
                text = appState.estimatedReadyTimeFormatted,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DrawerAmber,
              )
            }
          }

          Spacer(modifier = Modifier.height(20.dp))
        }
      }

      // Bottom Checkout Button Bar (Docked cleanly above bottom nav)
      val isCanteenOpen = appState.selectedCanteen.isOpen
      val closeReason = appState.selectedCanteen.closeReason

      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .background(PureWhite)
          .border(1.dp, BorderGray, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
          .padding(horizontal = 20.dp, vertical = 12.dp)
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          if (!isCanteenOpen) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFFFEBEE))
                .border(1.dp, Color(0xFFEF5350).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⛔", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Canteen is currently closed${if (!closeReason.isNullOrBlank()) ": $closeReason" else ". Checkout paused."}",
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFC62828),
                )
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = "₹${appState.grandTotal}",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDark,
              )
              Text(
                text = "${appState.totalCartCount} items",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
              )
            }

            val canPlaceOrder = isCanteenOpen && !appState.isPlacingOrder
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (canPlaceOrder) BlackPrimary else BorderGray)
                .clickable(enabled = canPlaceOrder) {
                  if (!com.example.util.NetworkMonitor.isCurrentlyOnline()) {
                    showNetworkErrorDialog = true
                    return@clickable
                  }
                  if (appState.isGuest) {
                    showLoginPrompt = true
                  } else {
                    appState.isPlacingOrder = true
                    try {
                      newlyPlacedOrder = appState.placeOrder()
                    } finally {
                      appState.isPlacingOrder = false
                    }
                  }
                }
                .padding(horizontal = 28.dp, vertical = 13.dp),
              contentAlignment = Alignment.Center,
            ) {
              if (appState.isPlacingOrder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  CircularProgressIndicator(
                    color = PureWhite,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Placing...",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                  )
                }
              } else {
                Text(
                  text = if (isCanteenOpen) "Place Order →" else "Canteen Closed",
                  fontSize = 14.5.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = if (isCanteenOpen) PureWhite else TextMuted,
                )
              }
            }
          }
        }
      }
    }

    // Success Order Token Dialog
    newlyPlacedOrder?.let { order ->
      AlertDialog(
        onDismissRequest = {
          newlyPlacedOrder = null
          onNavigateToTab(BottomNavTab.ORDERS)
        },
        title = {
          Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = OpenGreen,
              modifier = Modifier.size(52.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "Order Placed Successfully!",
              fontWeight = FontWeight.ExtraBold,
              fontSize = 18.sp,
              color = BlackPrimary,
            )
          }
        },
        text = {
          Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "Show this token at the counter:",
              fontSize = 12.sp,
              color = TextMuted,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(DeliveryOrangeLight)
                .border(2.dp, DeliveryOrange, RoundedCornerShape(16.dp))
                .padding(horizontal = 30.dp, vertical = 12.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "TOKEN #${order.tokenNumber}",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DeliveryOrange,
                letterSpacing = 1.sp,
              )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SoftGray)
                .padding(10.dp),
              contentAlignment = Alignment.Center,
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  text = "📍 Pickup: ${order.pickupCanteenName}",
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = TextDark,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = order.pickupLocation,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextMuted,
                  textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "Estimated Ready: ${order.estimatedReadyTime}",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = BlackPrimary,
            )
          }
        },
        confirmButton = {
          TextButton(
            onClick = {
              newlyPlacedOrder = null
              onNavigateToTab(BottomNavTab.ORDERS)
            },
          ) {
            Text("Track in Orders", fontWeight = FontWeight.Bold, color = DeliveryOrange)
          }
        },
        containerColor = PureWhite,
        shape = RoundedCornerShape(20.dp),
      )
    }

    // Network Issue Dialog (Offline Protection)
    if (showNetworkErrorDialog) {
      AlertDialog(
        onDismissRequest = { showNetworkErrorDialog = false },
        containerColor = PureWhite,
        shape = RoundedCornerShape(22.dp),
        title = {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          ) {
            Box(
              modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFFFEF2F2))
                .border(1.dp, Color(0xFFFCA5A5), CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = "Offline",
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(28.dp),
              )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
              text = "No Internet Connection",
              fontWeight = FontWeight.ExtraBold,
              fontSize = 18.sp,
              color = TextDark,
            )
          }
        },
        text = {
          Text(
            text = "Unable to connect to Quick Bite servers. Please check your cellular data or Wi-Fi to place your order. Your cart items are safely preserved.",
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = TextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
        },
        confirmButton = {
          Button(
            onClick = {
              if (NetworkUtils.isOnline(context)) {
                showNetworkErrorDialog = false
                if (appState.isGuest) {
                  showLoginPrompt = true
                } else {
                  newlyPlacedOrder = appState.placeOrder()
                }
              }
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = BlackPrimary,
              contentColor = PureWhite,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Retry Connection", fontWeight = FontWeight.Bold)
          }
        },
        dismissButton = {
          TextButton(
            onClick = { showNetworkErrorDialog = false },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Keep Items In Cart", color = TextMuted, fontWeight = FontWeight.SemiBold)
          }
        },
      )
    }

    // Modal Login Bottom Sheet when guest clicks Place Pre-Order
    if (showLoginPrompt) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0x80000000))
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { showLoginPrompt = false },
          ),
        contentAlignment = Alignment.BottomCenter,
      ) {
        LoginContent(
          onDismiss = { showLoginPrompt = false },
          onLoginSuccess = { profile ->
            appState.loginUser(profile)
            showLoginPrompt = false
            if (!NetworkUtils.isOnline(context)) {
              showNetworkErrorDialog = true
            } else {
              newlyPlacedOrder = appState.placeOrder()
            }
          },
          isSignUpDefault = false,
        )
      }
    }

    // Dedicated Food Pickup Arrival Time Picker (Minimal & Student Focused)
    if (showTimePickerDialog) {
      PickupTimePickerSheet(
        initialTimeStr = appState.estimatedReadyTimeFormatted,
        canteenName = appState.selectedCanteen.name,
        onTimeConfirmed = { confirmedTime ->
          appState.customPickupTime = confirmedTime
          appState.isPickupAsap = false
          showTimePickerDialog = false
        },
        onDismiss = { showTimePickerDialog = false },
      )
    }
  }
}

/**
 * Food emojis pattern rendered in the background of the empty cart.
 * Made bright and clearly visible with increased size (32-38sp) and enhanced alpha (0.20f).
 */
@Composable
private fun MinimalFoodEmojisPattern(modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize()) {
    val emojis = listOf(
      EmojiItem("🍕", 0.08f, 0.06f, -12f, 36.sp),
      EmojiItem("☕", 0.82f, 0.05f, 15f, 34.sp),
      EmojiItem("🍔", 0.48f, 0.12f, 8f, 38.sp),
      EmojiItem("🍟", 0.88f, 0.18f, -10f, 34.sp),
      EmojiItem("🥪", 0.10f, 0.24f, 14f, 36.sp),
      EmojiItem("🍩", 0.84f, 0.32f, -15f, 36.sp),
      EmojiItem("🥤", 0.06f, 0.42f, 10f, 34.sp),
      EmojiItem("🌮", 0.88f, 0.48f, -8f, 36.sp),
      EmojiItem("🥐", 0.12f, 0.60f, -12f, 34.sp),
      EmojiItem("🧋", 0.82f, 0.62f, 12f, 36.sp),
      EmojiItem("🍜", 0.22f, 0.78f, -6f, 38.sp),
      EmojiItem("🍪", 0.76f, 0.78f, 16f, 34.sp),
      EmojiItem("🧁", 0.12f, 0.90f, 8f, 32.sp),
      EmojiItem("🍉", 0.85f, 0.88f, -14f, 34.sp),
    )

    emojis.forEach { item ->
      Text(
        text = item.emoji,
        fontSize = item.size,
        modifier = Modifier
          .align { _, space, _ ->
            IntOffset(
              x = (space.width * item.xFraction).toInt(),
              y = (space.height * item.yFraction).toInt(),
            )
          }
          .rotate(item.rotation)
          .alpha(0.20f)
      )
    }
  }
}

private data class EmojiItem(
  val emoji: String,
  val xFraction: Float,
  val yFraction: Float,
  val rotation: Float,
  val size: androidx.compose.ui.unit.TextUnit,
)

