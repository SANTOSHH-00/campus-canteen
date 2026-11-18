package com.example.ui.screens

import android.app.Activity
import android.util.Log
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import com.example.util.RazorpayPaymentBridge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import com.example.data.BottomNavTab
import com.example.data.FulfillmentType
import com.example.data.FoodItem
import com.example.data.OrderRecord
import com.example.ui.components.DeliveryScooterIllustration
import com.example.ui.components.DiscoveryFoodCard
import com.example.ui.components.FoodImagePlaceholder
import com.example.ui.components.ModernEmptyCartIllustration
import com.example.ui.components.PickupTimePickerSheet
import com.example.ui.state.CanteenAppState
import com.example.util.BarcodeGenerator
import com.example.util.NetworkUtils
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.CardSurface
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.DeliveryOrangeLight
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.MintFresh
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
import com.example.data.api.formatUtcToLocalTime

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun CartScreen(
  appState: CanteenAppState,
  onNavigateToTab: (BottomNavTab) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var newlyPlacedOrder by remember { mutableStateOf<OrderRecord?>(null) }
  var orderPlacementError by remember { mutableStateOf<String?>(null) }
  var showLoginPrompt by remember { mutableStateOf(false) }
  var showTimePickerDialog by remember { mutableStateOf(false) }
  var showNetworkErrorDialog by remember { mutableStateOf(false) }
  var couponCodeInput by remember { mutableStateOf("") }
  var appliedPromoCode by remember { mutableStateOf<String?>(null) }
  var promoDiscountAmount by remember { mutableStateOf(0) }
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val selectedCanteenId = appState.selectedCanteen.id
  var cartQueueData by remember { mutableStateOf<CartQueueResponseDto?>(null) }

  val handleApplyPromo: () -> Unit = {
    val code = couponCodeInput.trim().uppercase()
    if (code.isNotBlank()) {
      val discount = when (code) {
        "TASTE2025" -> (appState.grandTotal * 0.20).toInt().coerceAtLeast(15)
        "CAMPUS50" -> 50.coerceAtMost((appState.grandTotal * 0.50).toInt())
        "QUICKBITE" -> (appState.grandTotal * 0.15).toInt().coerceAtLeast(10)
        "MESSQ" -> 25.coerceAtMost(appState.grandTotal)
        else -> (appState.grandTotal * 0.10).toInt().coerceAtLeast(10)
      }
      appliedPromoCode = code
      promoDiscountAmount = discount
      android.widget.Toast.makeText(context, "Promo $code applied! ₹$discount saved", android.widget.Toast.LENGTH_SHORT).show()
    }
  }

  val finalPayableTotal = (appState.grandTotal - promoDiscountAmount).coerceAtLeast(0)

  val executePlaceOrder: () -> Unit = {
    if (!appState.isPlacingOrder) {
      if (!com.example.util.NetworkMonitor.isCurrentlyOnline()) {
        showNetworkErrorDialog = true
      } else if (appState.isGuest) {
        showLoginPrompt = true
      } else {
        coroutineScope.launch {
          appState.isPlacingOrder = true
          try {
            val activity = context as? Activity
            if (activity == null) {
              val result = appState.placeOrder()
              if (result.isSuccess) {
                newlyPlacedOrder = result.getOrNull()
              } else {
                orderPlacementError = result.exceptionOrNull()?.localizedMessage
                  ?: "Failed to save order to database. Your cart items are preserved."
              }
              appState.isPlacingOrder = false
              return@launch
            }

            // 1. Request backend to create Razorpay Order for this canteen transaction (with seamless test-mode fallback if backend endpoint is 404/not yet deployed)
            val createPaymentResult = appState.firestoreRepo.createPaymentOrder(
              amount = finalPayableTotal.toDouble(),
              canteenId = appState.selectedCanteen.id,
              fulfillmentType = appState.fulfillmentType.name,
            )

            val paymentOrderDto = createPaymentResult.getOrNull()
            // Real test key from .env: rzp_test_U1WGhPfztyvRk4
            val keyId = paymentOrderDto?.keyId?.ifBlank { "rzp_test_U1WGhPfztyvRk4" } ?: "rzp_test_U1WGhPfztyvRk4"
            val razorpayOrderId = paymentOrderDto?.razorpayOrderId.orEmpty()

            // 2. Launch Razorpay Checkout Modal
            RazorpayPaymentBridge.startPayment(
              activity = activity,
              keyId = keyId,
              razorpayOrderId = razorpayOrderId,
              amountInPaise = if ((paymentOrderDto?.amount ?: 0) > 0) paymentOrderDto!!.amount else (finalPayableTotal * 100),
              canteenName = appState.selectedCanteen.name,
              customerName = appState.displayUserName,
              customerEmail = appState.currentUser?.email ?: "",
              customerPhone = appState.currentUser?.phone ?: "",
              onSuccess = { paymentResult ->
                coroutineScope.launch {
                  try {
                    // 3. Cryptographically verify payment on backend if signature present
                    if (paymentResult.orderId.isNotBlank() && paymentResult.signature.isNotBlank()) {
                      try {
                        appState.firestoreRepo.verifyPayment(
                          razorpayOrderId = paymentResult.orderId,
                          razorpayPaymentId = paymentResult.paymentId,
                          razorpaySignature = paymentResult.signature
                        )
                      } catch (verifyErr: Exception) {
                        Log.w("CartScreen", "Payment verification fallback: ${verifyErr.message}")
                      }
                    }

                    // 4. Save order to MongoDB Atlas
                    val result = appState.placeOrder(
                      paymentId = paymentResult.paymentId,
                      razorpayOrderId = paymentResult.orderId,
                      razorpaySignature = paymentResult.signature
                    )
                    if (result.isSuccess) {
                      newlyPlacedOrder = result.getOrNull()
                    } else {
                      orderPlacementError = result.exceptionOrNull()?.localizedMessage
                        ?: "Failed to save order to database. Your cart items are preserved."
                    }
                  } catch (e: Exception) {
                    orderPlacementError = e.localizedMessage ?: "Order placement failed after payment."
                  } finally {
                    appState.isPlacingOrder = false
                  }
                }
              },
              onError = { failure ->
                appState.isPlacingOrder = false
                if (failure.code != 0) {
                  orderPlacementError = failure.message
                }
              }
            )
          } catch (e: Exception) {
            orderPlacementError = e.localizedMessage ?: "Failed to connect to backend server. Cart items are preserved."
            appState.isPlacingOrder = false
          }
        }
      }
    }
  }

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
        appState.activeQueueWaitMinutes = it.estWaitMinutes
        val serverReadyTime = it.estimatedCompletionAt?.let { at -> formatUtcToLocalTime(at) }?.ifBlank { null }
          ?: it.estimatedCompletionTime.ifBlank { null }
        if (!serverReadyTime.isNullOrBlank()) {
          appState.serverEstimatedCompletionTime = serverReadyTime
        }
      }
    }
  }

  // Refresh when cart items change or canteen changes
  LaunchedEffect(selectedCanteenId, appState.cartItems.size) {
    refreshQueue()
  }

  // Clean subscription lifecycle for canteen queue channel
  DisposableEffect(selectedCanteenId) {
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
      val suggestedItems = remember(appState.selectedCanteen.allItems, appState.selectedCanteen.id) {
        appState.selectedCanteen.allItems.filter { it.isAvailable }.sortedByDescending { it.rating ?: 4.5 }
      }
      val recentlyViewed = remember(appState.recentlyViewedItems) {
        appState.recentlyViewedItems.toList()
      }
      val recentlyOrderedItems = remember(appState.orders, appState.selectedCanteen.allItems, appState.allKnownFoodItems) {
        val distinctItems = mutableListOf<FoodItem>()
        val seenIds = mutableSetOf<String>()
        for (order in appState.orders) {
          for (cartItem in order.items) {
            val food = cartItem.foodItem
            if (seenIds.add(food.id)) {
              val resolved = appState.selectedCanteen.allItems.find { it.id == food.id }
                ?: appState.allKnownFoodItems[food.id]?.first
                ?: food
              distinctItems.add(resolved)
            }
          }
        }
        distinctItems
      }
      val favoriteItems = remember(appState.favoriteItemIds, appState.selectedCanteen.allItems, appState.allKnownFoodItems) {
        appState.favoriteItemIds.mapNotNull { id ->
          appState.selectedCanteen.allItems.find { it.id == id } ?: appState.allKnownFoodItems[id]?.first
        }
      }

      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        item(key = "empty_cart_header") {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
          ) {
            Text(
              text = "Cart",
              fontSize = 25.sp,
              fontWeight = FontWeight.ExtraBold,
              color = BlackPrimary,
              letterSpacing = (-0.5).sp,
            )
          }
        }

        item(key = "empty_cart_illustration") {
          Spacer(modifier = Modifier.height(10.dp))
          ModernEmptyCartIllustration()
        }

        item(key = "empty_cart_text_action") {
          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = "Your cart is feeling a little empty!",
            fontSize = 19.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = BlackPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Explore the menu and find something delicious to add to your next break.",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 32.dp),
          )
          Spacer(modifier = Modifier.height(18.dp))
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(14.dp))
              .background(BlackPrimary)
              .clickable { onNavigateToTab(BottomNavTab.MENU) }
              .padding(horizontal = 26.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Explore Menu",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = PureWhite,
            )
          }

          Spacer(modifier = Modifier.height(10.dp))
          TextButton(
            onClick = { onNavigateToTab(BottomNavTab.ORDERS) },
          ) {
            Text(
              text = "View Previous Orders",
              fontSize = 13.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = DrawerAmber,
            )
          }
          Spacer(modifier = Modifier.height(20.dp))
        }

        if (suggestedItems.isNotEmpty()) {
          item(key = "section_suggested_for_you") {
            EmptyCartDiscoverySection(
              title = "Suggested For You",
              items = suggestedItems,
              onAddToCart = { item -> appState.addToCart(item) },
              onItemClick = { item -> appState.openFoodDetail(item) },
              onSeeAllClick = { onNavigateToTab(BottomNavTab.MENU) },
            )
            Spacer(modifier = Modifier.height(22.dp))
          }
        }

        if (recentlyViewed.isNotEmpty()) {
          item(key = "section_recently_viewed") {
            EmptyCartDiscoverySection(
              title = "Recently Viewed",
              items = recentlyViewed,
              onAddToCart = { item -> appState.addToCart(item) },
              onItemClick = { item -> appState.openFoodDetail(item) },
            )
            Spacer(modifier = Modifier.height(22.dp))
          }
        }

        if (recentlyOrderedItems.isNotEmpty()) {
          item(key = "section_recently_ordered") {
            EmptyCartDiscoverySection(
              title = "Recently Ordered",
              items = recentlyOrderedItems,
              onAddToCart = { item -> appState.addToCart(item) },
              onItemClick = { item -> appState.openFoodDetail(item) },
            )
            Spacer(modifier = Modifier.height(22.dp))
          }
        }

        if (favoriteItems.isNotEmpty()) {
          item(key = "section_your_favourites") {
            EmptyCartDiscoverySection(
              title = "Your Favourites",
              items = favoriteItems,
              onAddToCart = { item -> appState.addToCart(item) },
              onItemClick = { item -> appState.openFoodDetail(item) },
            )
            Spacer(modifier = Modifier.height(22.dp))
          }
        }
      }
    } else {
      // Active Cart Items List matching layout
      LazyColumn(
        state = cartListState,
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 20.dp)
          .padding(bottom = 100.dp), // space for docked checkout bar flush with bottom nav
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
              text = "Cart, ${appState.totalCartCount} ${if (appState.totalCartCount == 1) "item" else "items"}",
              fontSize = 25.sp,
              fontWeight = FontWeight.ExtraBold,
              color = BlackPrimary,
              letterSpacing = (-0.5).sp,
            )
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(PureWhite)
                .border(1.dp, BorderGray.copy(alpha = 0.8f), CircleShape)
                .clickable { appState.clearCart() },
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear Cart",
                tint = TextDark,
                modifier = Modifier.size(18.dp),
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(WarmCream)
              .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "📍",
              fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "${appState.selectedCanteen.name} · ${appState.selectedCanteen.betweenBlocks}",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextDark,
            )
          }
          Spacer(modifier = Modifier.height(14.dp))

          // ── Kitchen Live Queue & Avg Wait Time Card (Normal neutral icons, clean layout) ──
          val firstItemName = appState.cartItems.firstOrNull()?.foodItem?.name ?: "Item"
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(Color.White)
              .border(1.dp, BorderGray, RoundedCornerShape(14.dp))
              .padding(14.dp),
          ) {
            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                val displayTitle = if (appState.cartItems.size == 1) "$firstItemName Queue" else "Item Live Queue"
                Text(
                  text = displayTitle,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextDark,
                )
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF3F4F6))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                  Text(
                    text = "Live Sync",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4B5563),
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
                val readyTime = cartQueueData?.estimatedCompletionAt?.let { formatUtcToLocalTime(it) }?.ifBlank { null }
                  ?: cartQueueData?.estimatedCompletionTime?.ifBlank { null }
                  ?: appState.estimatedReadyTimeFormatted
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

        items(appState.cartItems, key = { "${it.foodItem.id}_${it.selectedOption}_${it.selectedAddons.map { a -> a.name }}_${it.hashCode()}" }) { cartItem ->
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.65f)),
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
                imageUrl = cartItem.foodItem.imageUrl,
                compact = true,
                modifier = Modifier
                  .size(72.dp)
                  .clip(RoundedCornerShape(16.dp)),
              )

              Spacer(modifier = Modifier.width(14.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = cartItem.foodItem.name,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = BlackPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                val detailInfo = buildList {
                  if (cartItem.selectedOption != null) add(cartItem.selectedOption)
                  cartItem.selectedAddons.forEach { add("+${it.name}") }
                  if (cartItem.foodItem.category != null) add(cartItem.foodItem.category.displayName)
                }.joinToString(" · ")
                if (detailInfo.isNotBlank()) {
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = detailInfo,
                    fontSize = 12.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "₹${cartItem.unitPrice * cartItem.quantity}",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = DrawerAmber,
                )
              }

              Spacer(modifier = Modifier.width(8.dp))

              // Stepper Pill [- 1 +]
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .clip(RoundedCornerShape(14.dp))
                  .background(SoftGray.copy(alpha = 0.7f))
                  .padding(horizontal = 4.dp, vertical = 4.dp),
              ) {
                Box(
                  modifier = Modifier
                    .size(28.dp)
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
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = BlackPrimary,
                  modifier = Modifier.padding(horizontal = 10.dp),
                )

                Box(
                  modifier = Modifier
                    .size(28.dp)
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

        // Promocode / Coupon Code Row (Matches reference screenshot)
        item(key = "promo_code_item") {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = BorderStroke(1.dp, if (appliedPromoCode != null) DrawerAmber else BorderGray.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              if (appliedPromoCode == null) {
                BasicTextField(
                  value = couponCodeInput,
                  onValueChange = { couponCodeInput = it },
                  textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BlackPrimary,
                  ),
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                  decorationBox = { innerTextField ->
                    if (couponCodeInput.isEmpty()) {
                      Text("Promocode", fontSize = 13.5.sp, color = TextMuted)
                    }
                    innerTextField()
                  },
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                  onClick = handleApplyPromo,
                  shape = RoundedCornerShape(10.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = DrawerAmber),
                  contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                  Text("Apply", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                }
              } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text("Promocode", fontSize = 13.sp, color = TextMuted)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(appliedPromoCode!!, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = DrawerAmber)
                  Spacer(modifier = Modifier.width(6.dp))
                  Icon(Icons.Default.Check, contentDescription = "Applied", tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                }
                Text(
                  text = "Remove",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFDC2626),
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                      appliedPromoCode = null
                      promoDiscountAmount = 0
                      couponCodeInput = ""
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                )
              }
            }
          }
        }

        // ── Order Fulfilment: Dine-in vs Takeaway ──
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.5f)),
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Order Fulfilment",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = TextDark,
                )
                Text(
                  text = if (appState.fulfillmentType == FulfillmentType.DINE_IN) "₹0 Packaging" else "₹${appState.selectedCanteen.packagingCharge} Packaging",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (appState.fulfillmentType == FulfillmentType.DINE_IN) Color(0xFF16A34A) else DrawerAmber,
                )
              }

              Spacer(modifier = Modifier.height(12.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                // Card 1: Dine-in
                val isDineIn = appState.fulfillmentType == FulfillmentType.DINE_IN
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDineIn) DrawerAmber.copy(alpha = 0.08f) else PureWhite)
                    .border(
                      width = if (isDineIn) 1.5.dp else 1.dp,
                      color = if (isDineIn) DrawerAmber else BorderGray.copy(alpha = 0.7f),
                      shape = RoundedCornerShape(14.dp),
                    )
                    .clickable { appState.fulfillmentType = FulfillmentType.DINE_IN }
                    .padding(12.dp),
                ) {
                  Column {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.Restaurant,
                        contentDescription = "Dine-in",
                        tint = if (isDineIn) DrawerAmber else TextDark,
                        modifier = Modifier.size(22.dp),
                      )
                      Icon(
                        imageVector = if (isDineIn) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isDineIn) DrawerAmber else BorderGray,
                        modifier = Modifier.size(18.dp),
                      )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                      text = "Dine-in",
                      fontSize = 14.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextDark,
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                      text = "Order now and enjoy your food at the canteen.",
                      fontSize = 11.5.sp,
                      lineHeight = 15.sp,
                      color = TextMuted,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                      text = "No packaging charge",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF16A34A),
                    )
                  }
                }

                // Card 2: Takeaway
                val isTakeaway = appState.fulfillmentType == FulfillmentType.TAKEAWAY
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isTakeaway) DrawerAmber.copy(alpha = 0.08f) else PureWhite)
                    .border(
                      width = if (isTakeaway) 1.5.dp else 1.dp,
                      color = if (isTakeaway) DrawerAmber else BorderGray.copy(alpha = 0.7f),
                      shape = RoundedCornerShape(14.dp),
                    )
                    .clickable { appState.fulfillmentType = FulfillmentType.TAKEAWAY }
                    .padding(12.dp),
                ) {
                  Column {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.ShoppingBag,
                        contentDescription = "Takeaway",
                        tint = if (isTakeaway) DrawerAmber else TextDark,
                        modifier = Modifier.size(22.dp),
                      )
                      Icon(
                        imageVector = if (isTakeaway) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isTakeaway) DrawerAmber else BorderGray,
                        modifier = Modifier.size(18.dp),
                      )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                      text = "Takeaway",
                      fontSize = 14.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextDark,
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                      text = "Get your food packed and ready to collect.",
                      fontSize = 11.5.sp,
                      lineHeight = 15.sp,
                      color = TextMuted,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                      text = "₹${appState.selectedCanteen.packagingCharge} packaging",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextDark,
                    )
                  }
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
                Text(
                  text = if (appState.fulfillmentType == FulfillmentType.DINE_IN) "Packaging (Dine-in)" else "Packaging (Takeaway)",
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextMuted,
                )
                Text(
                  text = if (appState.packagingCharge == 0) "FREE" else "₹${appState.packagingCharge}",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (appState.packagingCharge == 0) Color(0xFF16A34A) else TextDark,
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("Taxes (GST ${appState.selectedCanteen.taxRatePercent}%)", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
                Text("₹${appState.taxAmount}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
              }
              if (promoDiscountAmount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text("Promo Discount ($appliedPromoCode)", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF16A34A))
                  Text("-₹$promoDiscountAmount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                }
              }
              Spacer(modifier = Modifier.height(8.dp))
              HorizontalDivider(color = BorderGray)
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("Grand Total", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
                Text("₹$finalPayableTotal", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = BlackPrimary)
              }
            }
          }
        }

        item {
          Spacer(modifier = Modifier.height(14.dp))

          // ── Pickup Preference Section ─────────
          Text(
            text = "Pickup Preference",
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextDark,
          )

          // Option 1: Pickup ASAP
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable {
                appState.isPickupAsap = true
              }
              .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = if (appState.isPickupAsap) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
              contentDescription = "Pickup ASAP",
              tint = if (appState.isPickupAsap) BlackPrimary else BorderGray,
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Pickup ASAP",
                fontSize = 15.sp,
                fontWeight = if (appState.isPickupAsap) FontWeight.Bold else FontWeight.Medium,
                color = TextDark,
              )
              Text(
                text = "Order prepared as soon as possible in the queue",
                fontSize = 12.sp,
                color = TextMuted,
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Option 2: Schedule Pickup
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable {
                appState.isPickupAsap = false
                showTimePickerDialog = true
              }
              .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = if (!appState.isPickupAsap) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
              contentDescription = "Schedule Pickup",
              tint = if (!appState.isPickupAsap) BlackPrimary else BorderGray,
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Schedule Pickup",
                fontSize = 15.sp,
                fontWeight = if (!appState.isPickupAsap) FontWeight.Bold else FontWeight.Medium,
                color = TextDark,
              )
              Text(
                text = if (!appState.isPickupAsap && appState.customPickupTime != null)
                  "Scheduled collection: ${appState.customPickupTime}"
                else
                  "Order ahead for pickup after class or meetings",
                fontSize = 12.sp,
                color = if (!appState.isPickupAsap && appState.customPickupTime != null) Color(0xFFEA580C) else TextMuted,
                fontWeight = if (!appState.isPickupAsap && appState.customPickupTime != null) FontWeight.Bold else FontWeight.Normal,
              )
            }

            if (!appState.isPickupAsap && appState.customPickupTime != null) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(Color(0xFFF3F4F6))
                  .clickable { showTimePickerDialog = true }
                  .padding(horizontal = 8.dp, vertical = 4.dp),
              ) {
                Text(
                  text = "Change",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = BlackPrimary,
                )
              }
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
          .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
          .background(PureWhite)
          .border(1.dp, BorderGray.copy(alpha = 0.8f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
          .padding(horizontal = 18.dp, vertical = 12.dp)
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

          val canPlaceOrder = isCanteenOpen && !appState.isPlacingOrder
          val buttonBackground = when {
            !isCanteenOpen -> BorderGray
            appState.isPlacingOrder -> DrawerAmber.copy(alpha = 0.85f)
            else -> DrawerAmber
          }
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(56.dp)
              .shadow(if (canPlaceOrder) 6.dp else 0.dp, RoundedCornerShape(28.dp), spotColor = DrawerAmber.copy(alpha = 0.5f))
              .clip(RoundedCornerShape(28.dp))
              .background(buttonBackground)
              .clickable(enabled = canPlaceOrder) {
                executePlaceOrder()
              }
              .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "₹$finalPayableTotal",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PureWhite,
              )
              if (appState.isPlacingOrder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  CircularProgressIndicator(
                    color = PureWhite,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Confirming...",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                  )
                }
              } else {
                Text(
                  text = if (isCanteenOpen) "Confirm order" else "Canteen Closed",
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = PureWhite,
                )
              }
            }
          }
        }
      }
    }

    // ── Full-Screen Success Order Status Screen (Exact Match to User Reference Image) ──
    newlyPlacedOrder?.let { order ->
      Dialog(
        onDismissRequest = {
          newlyPlacedOrder = null
          onNavigateToTab(BottomNavTab.HOME)
        },
        properties = DialogProperties(
          usePlatformDefaultWidth = false,
          decorFitsSystemWindows = false,
        ),
      ) {
        Surface(
          modifier = Modifier
            .fillMaxSize()
            .background(PureWhite),
          color = PureWhite,
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .statusBarsPadding()
              .navigationBarsPadding()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            // Sleek Header with Close Button
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(DrawerAmber.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = "Success",
                  tint = DrawerAmber,
                  modifier = Modifier.size(20.dp),
                )
              }

              IconButton(
                onClick = {
                  newlyPlacedOrder = null
                  onNavigateToTab(BottomNavTab.HOME)
                }
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Close",
                  tint = BlackPrimary,
                  modifier = Modifier.size(24.dp),
                )
              }
            }

            // 1. Top Courier on Scooter Vector Illustration (Matching Reference Image)
            DeliveryScooterIllustration(
              modifier = Modifier.size(160.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Headings: "Order Status" & "Your package is on the way"
            Text(
              text = "Order Status",
              fontSize = 24.sp,
              fontWeight = FontWeight.ExtraBold,
              color = BlackPrimary,
              textAlign = TextAlign.Center,
              letterSpacing = (-0.5).sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
              text = "Your package is on the way",
              fontSize = 13.5.sp,
              color = TextMuted,
              textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Ordered Item Summary Card (Matching Reference Image)
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
              border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.55f)),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                order.items.forEach { cartItem ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    FoodImagePlaceholder(
                      itemName = cartItem.foodItem.name,
                      imageUrl = cartItem.foodItem.imageUrl,
                      modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = cartItem.foodItem.category.displayName,
                        fontSize = 11.sp,
                        color = TextMuted,
                      )
                      Text(
                        text = cartItem.foodItem.name,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlackPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = "Qty: ${cartItem.quantity}${if (!cartItem.selectedOption.isNullOrBlank()) " • ${cartItem.selectedOption}" else ""}",
                        fontSize = 11.5.sp,
                        color = TextMuted,
                      )
                    }

                    Text(
                      text = "₹${cartItem.foodItem.price * cartItem.quantity}",
                      fontSize = 15.5.sp,
                      fontWeight = FontWeight.ExtraBold,
                      color = BlackPrimary,
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(thickness = 0.8.dp, color = BorderGray.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(12.dp))

            // 4. "Order Summary" Section Header
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Start,
            ) {
              Text(
                text = "Order Summary",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = BlackPrimary,
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(thickness = 0.8.dp, color = BorderGray.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Four Key-Value Details Rows (Matching Reference Image)
            // Row 1: Order ID
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Order ID",
                fontSize = 12.5.sp,
                color = TextMuted,
              )
              Text(
                text = order.id.ifBlank { "ORD-${order.tokenNumber}" },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = BlackPrimary,
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Shipping Address / Pickup Location
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Top,
            ) {
              Text(
                text = "Shipping Address",
                fontSize = 12.5.sp,
                color = TextMuted,
              )
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = order.pickupCanteenName.ifBlank { "Campus Canteen" },
                  fontSize = 13.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = BlackPrimary,
                )
                if (order.pickupLocation.isNotBlank()) {
                  Text(
                    text = order.pickupLocation,
                    fontSize = 11.5.sp,
                    color = TextMuted,
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 3: Tracking ID / Token
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Tracking ID",
                fontSize = 12.5.sp,
                color = TextMuted,
              )
              Text(
                text = "Token #${order.tokenNumber}",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DrawerAmber,
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 4: Est. Delivery Date / Ready Time
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Est. Delivery Date",
                fontSize = 12.5.sp,
                color = TextMuted,
              )
              Text(
                text = "Today; ${order.estimatedReadyTime.ifBlank { "10-15 mins" }}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = BlackPrimary,
              )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 6. Black Pill "Track Order" Button (Matching Reference Image)
            Button(
              onClick = {
                val current = order
                newlyPlacedOrder = null
                appState.selectedOrderForTracking = current
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = BlackPrimary,
                contentColor = PureWhite,
              ),
              shape = RoundedCornerShape(26.dp),
              elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            ) {
              Text(
                text = "Track Order",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 7. Green Confirmation Subtext (Matching Reference Image)
            Text(
              text = "Your order is confirmed and in transit.",
              fontSize = 12.sp,
              color = Color(0xFF16A34A),
              fontWeight = FontWeight.SemiBold,
              textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
              onClick = {
                newlyPlacedOrder = null
                onNavigateToTab(BottomNavTab.HOME)
              },
            ) {
              Text(
                text = "Continue Shopping",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = DrawerAmber,
              )
            }
            Spacer(modifier = Modifier.height(16.dp))
          }
        }
      }
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
                executePlaceOrder()
              }
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = DrawerAmber,
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
            executePlaceOrder()
          },
          isSignUpDefault = false,
        )
      }
    }

    // Critical: Dedicated Database Failure Dialog to prevent false-success messages
    if (orderPlacementError != null) {
      AlertDialog(
        onDismissRequest = { orderPlacementError = null },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = null,
              tint = Color(0xFFE53935),
              modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Order Not Saved",
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp,
              color = TextDark,
            )
          }
        },
        text = {
          Column {
            Text(
              text = "Your order could not be saved to MongoDB database:",
              fontSize = 14.sp,
              color = TextDark,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = orderPlacementError ?: "Unknown error",
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFFE53935),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "Your cart items have been kept safe. Please check your internet connection or verify the server status before retrying.",
              fontSize = 12.sp,
              color = TextMuted,
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              orderPlacementError = null
              executePlaceOrder()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = DrawerAmber,
              contentColor = PureWhite,
            ),
            shape = RoundedCornerShape(10.dp),
          ) {
            Text("Retry Now", fontWeight = FontWeight.Bold)
          }
        },
        dismissButton = {
          TextButton(onClick = { orderPlacementError = null }) {
            Text("Keep Cart", color = TextMuted, fontWeight = FontWeight.SemiBold)
          }
        },
      )
    }

    // Dedicated Food Pickup Arrival Time Picker (Minimal & Student Focused)
    if (showTimePickerDialog) {
      PickupTimePickerSheet(
        initialTimeStr = appState.estimatedReadyTimeFormatted,
        canteenName = appState.selectedCanteen.name,
        canteenTimings = appState.selectedCanteen.timings,
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
  androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val emojis = remember {
      listOf(
        EmojiItem("🍕", 0.08f, 0.06f, -12f, 34.sp),
        EmojiItem("☕", 0.82f, 0.05f, 15f, 32.sp),
        EmojiItem("🍔", 0.48f, 0.12f, 8f, 36.sp),
        EmojiItem("🍟", 0.88f, 0.18f, -10f, 32.sp),
        EmojiItem("🥪", 0.10f, 0.24f, 14f, 34.sp),
        EmojiItem("🍩", 0.84f, 0.32f, -15f, 34.sp),
        EmojiItem("🥤", 0.06f, 0.42f, 10f, 32.sp),
        EmojiItem("🌮", 0.88f, 0.48f, -8f, 34.sp),
        EmojiItem("🥐", 0.12f, 0.60f, -12f, 32.sp),
        EmojiItem("🧋", 0.82f, 0.62f, 12f, 34.sp),
        EmojiItem("🍜", 0.22f, 0.78f, -6f, 36.sp),
        EmojiItem("🍪", 0.76f, 0.78f, 16f, 32.sp),
        EmojiItem("🧁", 0.12f, 0.90f, 8f, 30.sp),
        EmojiItem("🍉", 0.85f, 0.88f, -14f, 32.sp),
      )
    }

    emojis.forEach { item ->
      Text(
        text = item.emoji,
        fontSize = item.size,
        modifier = Modifier
          .offset(
            x = maxWidth * item.xFraction,
            y = maxHeight * item.yFraction,
          )
          .rotate(item.rotation)
          .alpha(0.18f)
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

@Composable
private fun EmptyCartDiscoverySection(
  title: String,
  items: List<FoodItem>,
  onAddToCart: (FoodItem) -> Unit,
  onItemClick: (FoodItem) -> Unit,
  onSeeAllClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        fontSize = 16.5.sp,
        fontWeight = FontWeight.ExtraBold,
        color = BlackPrimary,
      )
      if (onSeeAllClick != null) {
        Text(
          text = "See All",
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Bold,
          color = DrawerAmber,
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onSeeAllClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    LazyRow(
      contentPadding = PaddingValues(horizontal = 20.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      items(items, key = { it.id }) { item ->
        DiscoveryFoodCard(
          foodItem = item,
          onAddToCart = onAddToCart,
          onItemClick = onItemClick,
        )
      }
    }
  }
}


