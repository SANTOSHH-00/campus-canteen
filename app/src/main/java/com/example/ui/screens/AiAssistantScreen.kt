package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.R
import com.example.data.CartItem
import com.example.data.FoodCategory
import com.example.data.FoodItem
import com.example.data.OrderRecord
import com.example.data.OrderStatus
import com.example.data.api.AiRecommendedItemDto
import com.example.data.api.AiTrackedOrderDto
import com.example.data.api.ChatMessage
import com.example.data.api.MessageRole
import com.example.ui.components.MunchMateIcon
import com.example.ui.state.CanteenAppState
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.CardSurface
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.DrawerAmberDark
import com.example.ui.theme.DrawerAmberLight
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.MintFresh
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.SpiceRed
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import com.example.ui.viewmodel.AiAssistantViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
  appState: CanteenAppState,
  onBack: (() -> Unit)? = null,
  onClose: (() -> Unit)? = onBack,
  viewModel: AiAssistantViewModel = viewModel(),
  modifier: Modifier = Modifier,
) {
  val messages by viewModel.messages.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val suggestions by viewModel.suggestions.collectAsState()
  val inputText by viewModel.inputText.collectAsState()
  val errorMessage by viewModel.errorMessage.collectAsState()

  val listState = rememberLazyListState()

  // Auto-scroll to latest message
  LaunchedEffect(messages.size, isLoading) {
    if (messages.isNotEmpty() || isLoading) {
      val targetIndex = if (isLoading) messages.size else maxOf(0, messages.size - 1)
      listState.animateScrollToItem(targetIndex)
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream)
      .imePadding()
  ) {
    // ── 1. MunchMate Top Bar Header ──────────────────────────────────────────
    MunchMateTopBar(
      onBack = onBack,
      onClose = onClose,
      onClearChat = { viewModel.clearConversation() },
      hasMessages = messages.isNotEmpty(),
    )

    // ── 2. Chat Conversation Body / Welcome Screen ──────────────────────────
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
    ) {
      if (messages.isEmpty()) {
        // Welcome Screen State
        MunchMateWelcomeContent(
          suggestions = suggestions,
          appState = appState,
          onSuggestionClick = { prompt ->
            viewModel.sendMessage(
              promptText = prompt,
              canteenId = appState.selectedCanteen.id,
            )
          },
        )
      } else {
        // Active Messages List
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          items(messages, key = { it.id }) { message ->
            ChatBubbleItem(
              message = message,
              appState = appState,
              onRetry = { viewModel.retryLastMessage(canteenId = appState.selectedCanteen.id) },
              onFeedback = { msg, type, reason ->
                viewModel.onFeedbackSelected(msg, type, reason)
              },
              onSelectOrder = { orderDto ->
                viewModel.sendMessage(
                  promptText = "Track order #${orderDto.orderId}",
                  canteenId = appState.selectedCanteen.id,
                )
              },
            )
          }

          if (isLoading) {
            item(key = "loading_indicator") {
              MunchMateThinkingBubble()
            }
          }
        }
      }
    }

    // ── 3. Bottom Contextual Suggestions (when chatting) ────────────────────
    if (messages.isNotEmpty() && suggestions.isNotEmpty()) {
      LazyRow(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(suggestions) { chip ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = PureWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
            modifier = Modifier.clickable {
              viewModel.sendMessage(
                promptText = chip,
                canteenId = appState.selectedCanteen.id,
              )
            },
          ) {
            Text(
              text = chip,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = BlackPrimary,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        }
      }
    }

    // ── 4. Chat Input Bar ───────────────────────────────────────────────────
    MunchMateChatInputBar(
      inputText = inputText,
      onTextChanged = { viewModel.onInputTextChanged(it) },
      onSend = {
        viewModel.sendMessage(canteenId = appState.selectedCanteen.id)
      },
      isLoading = isLoading,
    )
  }
}

// ── Top Bar ─────────────────────────────────────────────────────────────────
@Composable
private fun MunchMateTopBar(
  onClearChat: () -> Unit,
  hasMessages: Boolean,
  onBack: (() -> Unit)? = null,
  onClose: (() -> Unit)? = null,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(WarmCream)
      .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (onBack != null && onClose == null) {
        IconButton(
          onClick = onBack,
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(SoftGray.copy(alpha = 0.6f)),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = BlackPrimary,
            modifier = Modifier.size(18.dp),
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
      }

      // MunchMate Identity Icon (Native Vector Fork & Knife Artwork)
      Box(
        modifier = Modifier
          .size(40.dp)
          .shadow(2.dp, CircleShape)
          .clip(CircleShape)
          .background(PureWhite)
          .border(1.5.dp, DrawerAmberLight, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        MunchMateIcon(
          modifier = Modifier.size(24.dp),
          tint = Color(0xFF2E5A44),
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column {
        Text(
          text = "MunchMate",
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold,
          color = BlackPrimary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(6.dp)
              .clip(CircleShape)
              .background(MintFresh),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Online · Food & Order Assistant",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted,
          )
        }
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      if (hasMessages) {
        IconButton(onClick = onClearChat) {
          Icon(
            imageVector = Icons.Default.DeleteSweep,
            contentDescription = "Clear Chat",
            tint = TextMuted,
            modifier = Modifier.size(22.dp),
          )
        }
      }
      if (onClose != null) {
        IconButton(
          onClick = onClose,
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(SoftGray.copy(alpha = 0.6f)),
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = BlackPrimary,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }

  HorizontalDivider(
    thickness = 1.dp,
    color = BorderGray.copy(alpha = 0.6f),
  )
}

// ── Welcome Screen Content ──────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MunchMateWelcomeContent(
  suggestions: List<String>,
  appState: CanteenAppState? = null,
  onSuggestionClick: (String) -> Unit,
) {
  val studentName = appState?.currentUser?.name?.trim() ?: appState?.displayUserName?.trim().orEmpty()
  val firstName = studentName
    .split(" ")
    .firstOrNull { it.isNotBlank() && !it.equals("Guest", ignoreCase = true) }

  val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
  val timeGreeting = when (hour) {
    in 4..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hello"
  }

  val welcomeGreeting = if (!firstName.isNullOrBlank()) {
    "$timeGreeting, $firstName!"
  } else {
    "$timeGreeting!"
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 24.dp)
      .padding(top = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Large MunchMate Icon
    Box(
      modifier = Modifier
        .size(76.dp)
        .shadow(6.dp, CircleShape, spotColor = DrawerAmber.copy(alpha = 0.5f))
        .clip(CircleShape)
        .background(PureWhite)
        .border(2.dp, DrawerAmberLight, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      MunchMateIcon(
        modifier = Modifier.size(46.dp),
        tint = Color(0xFF2E5A44),
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = welcomeGreeting,
      fontSize = 24.sp,
      fontWeight = FontWeight.Black,
      color = BlackPrimary,
      textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = "I'm MunchMate by QuickBite • Your smart campus food buddy.\nAsk me about food recommendations, canteen menus, or track your live orders in real time!",
      fontSize = 13.5.sp,
      fontWeight = FontWeight.Normal,
      color = TextMuted,
      lineHeight = 20.sp,
      textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(28.dp))

    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Center,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      suggestions.forEach { chipText ->
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = PureWhite,
          shadowElevation = 1.dp,
          border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
          modifier = Modifier
            .padding(horizontal = 4.dp)
            .clickable { onSuggestionClick(chipText) },
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            MunchMateIcon(
              modifier = Modifier.size(16.dp),
              tint = Color(0xFF2E5A44),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = chipText,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = BlackPrimary,
            )
          }
        }
      }
    }
  }
}

// ── Chat Bubble Item ────────────────────────────────────────────────────────
@Composable
private fun ChatBubbleItem(
  message: ChatMessage,
  appState: CanteenAppState,
  onRetry: () -> Unit,
  onFeedback: (ChatMessage, String, String?) -> Unit,
  onSelectOrder: (AiTrackedOrderDto) -> Unit,
) {
  val isUser = message.role == MessageRole.USER
  val timeFormatted = remember(message.timestamp) {
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
  ) {
    if (isUser) {
      // ── Student Message (Aligned Right) ──
      Box(
        modifier = Modifier
          .fillMaxWidth(0.85f)
          .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
          .background(BlackPrimary)
          .padding(horizontal = 14.dp, vertical = 10.dp),
      ) {
        Text(
          text = message.content,
          fontSize = 14.sp,
          color = PureWhite,
          lineHeight = 20.sp,
        )
      }
    } else {
      // ── MunchMate AI Response (Aligned Left) ──
      Column(
        modifier = Modifier.fillMaxWidth(0.95f),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          MunchMateIcon(
            modifier = Modifier.size(16.dp),
            tint = Color(0xFF2E5A44),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "MunchMate",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = DrawerAmberDark,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = timeFormatted,
            fontSize = 10.sp,
            color = TextMuted,
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
            .background(if (message.isError) Color(0xFFFEF2F2) else PureWhite)
            .border(
              width = 1.dp,
              color = if (message.isError) SpiceRed.copy(alpha = 0.4f) else BorderGray,
              shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            )
            .padding(14.dp),
        ) {
          Column {
            Text(
              text = message.content,
              fontSize = 14.sp,
              color = if (message.isError) SpiceRed else TextDark,
              lineHeight = 21.sp,
            )

            // Retry action if there was an error
            if (message.isError) {
              Spacer(modifier = Modifier.height(10.dp))
              Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = DrawerAmber),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Refresh,
                  contentDescription = null,
                  tint = PureWhite,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retry", fontSize = 12.sp, color = PureWhite, fontWeight = FontWeight.Bold)
              }
            }
          }
        }

        // ── Thumbs Up / Thumbs Down Feedback Row (Only for successful assistant answers) ──
        if (!message.isError) {
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier.padding(start = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            val isThumbsUp = message.userFeedback == "positive"
            val isThumbsDown = message.userFeedback == "negative"

            // 👍 Thumbs Up Button
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isThumbsUp) DrawerAmber.copy(alpha = 0.15f) else PureWhite)
                .border(
                  width = 1.dp,
                  color = if (isThumbsUp) DrawerAmber else BorderGray,
                  shape = RoundedCornerShape(12.dp),
                )
                .clickable {
                  onFeedback(message, "positive", null)
                }
                .padding(horizontal = 8.dp, vertical = 5.dp),
              contentAlignment = Alignment.Center,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = if (isThumbsUp) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                  contentDescription = "Helpful answer",
                  tint = if (isThumbsUp) DrawerAmberDark else Color(0xFF6B6864),
                  modifier = Modifier.size(15.dp),
                )
                if (isThumbsUp) {
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Helpful",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DrawerAmberDark,
                  )
                }
              }
            }

            // 👎 Thumbs Down Button
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isThumbsDown) SpiceRed.copy(alpha = 0.12f) else PureWhite)
                .border(
                  width = 1.dp,
                  color = if (isThumbsDown) SpiceRed else BorderGray,
                  shape = RoundedCornerShape(12.dp),
                )
                .clickable {
                  onFeedback(message, "negative", null)
                }
                .padding(horizontal = 8.dp, vertical = 5.dp),
              contentAlignment = Alignment.Center,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = if (isThumbsDown) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                  contentDescription = "Not helpful answer",
                  tint = if (isThumbsDown) SpiceRed else Color(0xFF6B6864),
                  modifier = Modifier.size(15.dp),
                )
                if (isThumbsDown) {
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Reported",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpiceRed,
                  )
                }
              }
            }
          }
        }

        // ── 1. Real-Time Order Tracking Progress Card (Scenario A & C) ────────
        if (message.trackedOrder != null) {
          Spacer(modifier = Modifier.height(10.dp))
          AiOrderTrackingCard(
            order = message.trackedOrder,
            onOpenFullTracking = {
              val existing = appState.orders.find { it.id == message.trackedOrder.orderId }
              if (existing != null) {
                appState.openOrderTracking(existing)
              } else {
                val synthetic = OrderRecord(
                  id = message.trackedOrder.orderId,
                  tokenNumber = message.trackedOrder.tokenNumber,
                  items = emptyList(),
                  totalPrice = message.trackedOrder.totalAmount.toInt(),
                  status = when (message.trackedOrder.status.uppercase()) {
                    "PREPARING" -> OrderStatus.PREPARING
                    "READY" -> OrderStatus.READY
                    "COMPLETED", "PICKED_UP" -> OrderStatus.COMPLETED
                    "CANCELLED" -> OrderStatus.CANCELLED
                    else -> OrderStatus.NEW
                  },
                  orderTime = message.trackedOrder.orderDate,
                  estimatedReadyTime = message.trackedOrder.estimatedReadyTime,
                  pickupCanteenName = message.trackedOrder.canteenName,
                  pickupLocation = message.trackedOrder.pickupLocation,
                  canteenId = message.trackedOrder.canteenId,
                )
                appState.openOrderTracking(synthetic)
              }
            },
          )
        }

        // ── 2. Selectable Orders List (Scenario B - Multiple Active Orders) ───
        if (message.selectableOrders.isNotEmpty()) {
          Spacer(modifier = Modifier.height(10.dp))
          AiSelectableOrdersCard(
            orders = message.selectableOrders,
            onSelectOrder = onSelectOrder,
          )
        }

        // ── 3. Recommended Food Items Carousel / List ────────────────────────
        if (message.recommendedItems.isNotEmpty()) {
          Spacer(modifier = Modifier.height(10.dp))
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            items(message.recommendedItems) { recItem ->
              AiFoodRecommendationCard(
                item = recItem,
                onAddToCart = {
                  val foodItem = FoodItem(
                    id = recItem.id,
                    name = recItem.name,
                    price = recItem.price.toInt(),
                    prepTime = recItem.prepTime.ifBlank { "5-7 min" },
                    prepMinutes = 7,
                    rating = recItem.rating,
                    category = FoodCategory.QUICK_ORDER,
                    imageLabel = recItem.name,
                    imageUrl = recItem.imageUrl,
                    description = "Available at ${recItem.canteenName}",
                    isAvailable = recItem.available,
                  )
                  appState.addToCart(foodItem)
                },
              )
            }
          }
        }
      }
    }
  }
}

// ── Real-Time Order Tracking Progress Card (5-Stage Visual Timeline) ────────
@Composable
private fun AiOrderTrackingCard(
  order: AiTrackedOrderDto,
  onOpenFullTracking: () -> Unit,
) {
  val stepIndex = when (order.status.uppercase()) {
    "NEW", "WAITING" -> 0
    "CONFIRMED" -> 1
    "PREPARING" -> 2
    "READY" -> 3
    "COMPLETED", "PICKED_UP" -> 4
    else -> 0
  }

  val stages = listOf(
    "Placed",
    "Accepted",
    "Preparing",
    "Ready",
    "Picked Up",
  )

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = PureWhite,
    border = androidx.compose.foundation.BorderStroke(1.dp, DrawerAmberLight),
    shadowElevation = 2.dp,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
    ) {
      // Header: Order ID & Token Badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "Order #${order.orderId}",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            color = BlackPrimary,
          )
          Text(
            text = "${order.canteenName} • ${order.pickupLocation}",
            fontSize = 11.5.sp,
            color = TextMuted,
          )
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DrawerAmber)
            .padding(horizontal = 10.dp, vertical = 4.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "Token #${order.tokenNumber}",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = PureWhite,
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // 5-Stage Tracking Timeline Indicator
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        stages.forEachIndexed { index, name ->
          val isCompleted = index < stepIndex
          val isCurrent = index == stepIndex
          val stepColor = when {
            isCompleted || isCurrent -> DrawerAmber
            else -> BorderGray
          }

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
          ) {
            Box(
              modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isCompleted || isCurrent) DrawerAmber else PureWhite)
                .border(1.5.dp, stepColor, CircleShape),
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
                    .background(PureWhite)
                )
              }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
              text = name,
              fontSize = 9.sp,
              fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
              color = if (isCurrent) DrawerAmberDark else (if (isCompleted) TextDark else TextMuted),
              textAlign = TextAlign.Center,
              maxLines = 1,
            )
          }

          // Connecting Line between stage dots
          if (index < stages.size - 1) {
            Box(
              modifier = Modifier
                .weight(0.6f)
                .height(2.5.dp)
                .offset(y = (-8).dp)
                .background(if (index < stepIndex) DrawerAmber else BorderGray.copy(alpha = 0.5f)),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Status Note Box
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(
            if (order.status.uppercase() == "READY") Color(0xFFECFDF5) else WarmCream
          )
          .padding(horizontal = 10.dp, vertical = 8.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = if (order.status.uppercase() == "READY") "🔔" else "⏳",
            fontSize = 13.sp,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (order.status.uppercase() == "READY") {
              "Ready for Pickup! Show token #${order.tokenNumber} at the counter."
            } else {
              "Estimated ready time: ${order.estimatedReadyTime}"
            },
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (order.status.uppercase() == "READY") Color(0xFF065F46) else TextDark,
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Button: View Full Tracking Screen
      Button(
        onClick = onOpenFullTracking,
        colors = ButtonDefaults.buttonColors(containerColor = BlackPrimary),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
      ) {
        Text(
          text = "Open Full Tracking Screen",
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Bold,
          color = PureWhite,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
          imageVector = Icons.Default.ChevronRight,
          contentDescription = null,
          tint = PureWhite,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

// ── Selectable Active Orders Card (Scenario B) ──────────────────────────────
@Composable
private fun AiSelectableOrdersCard(
  orders: List<AiTrackedOrderDto>,
  onSelectOrder: (AiTrackedOrderDto) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 4.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "Select an order to track live:",
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      color = TextDark,
    )

    orders.forEach { order ->
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .clickable { onSelectOrder(order) },
        shape = RoundedCornerShape(12.dp),
        color = PureWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
        shadowElevation = 1.dp,
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "Order #${order.orderId}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.5.sp,
                color = BlackPrimary,
              )
              Spacer(modifier = Modifier.width(6.dp))
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(DrawerAmber.copy(alpha = 0.15f))
                  .padding(horizontal = 6.dp, vertical = 2.dp),
              ) {
                Text(
                  text = order.status,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = DrawerAmberDark,
                )
              }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
              text = "${order.canteenName} • Token #${order.tokenNumber}",
              fontSize = 11.5.sp,
              color = TextMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Track",
            tint = DrawerAmber,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

// ── Food Recommendation Card ────────────────────────────────────────────────
@Composable
private fun AiFoodRecommendationCard(
  item: AiRecommendedItemDto,
  onAddToCart: () -> Unit,
) {
  Box(
    modifier = Modifier
      .width(180.dp)
      .shadow(2.dp, RoundedCornerShape(14.dp))
      .clip(RoundedCornerShape(14.dp))
      .background(PureWhite)
      .border(1.dp, BorderGray, RoundedCornerShape(14.dp))
      .padding(10.dp),
  ) {
    Column {
      // Image or Food Icon
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(90.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(WarmCream),
        contentAlignment = Alignment.Center,
      ) {
        if (item.imageUrl.isNotBlank()) {
          AsyncImage(
            model = item.imageUrl,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Icon(
            imageVector = Icons.Default.Fastfood,
            contentDescription = null,
            tint = DrawerAmber,
            modifier = Modifier.size(36.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = item.name,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = BlackPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      Text(
        text = item.canteenName,
        fontSize = 11.sp,
        color = TextMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      Spacer(modifier = Modifier.height(6.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "₹${item.price.toInt()}",
          fontSize = 14.sp,
          fontWeight = FontWeight.ExtraBold,
          color = DrawerAmberDark,
        )

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DrawerAmber)
            .clickable { onAddToCart() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
          contentAlignment = Alignment.Center,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.ShoppingCart,
              contentDescription = null,
              tint = PureWhite,
              modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = "Add",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = PureWhite,
            )
          }
        }
      }
    }
  }
}

// ── MunchMate Thinking Indicator ─────────────────────────────────────────────
@Composable
private fun MunchMateThinkingBubble() {
  val infiniteTransition = rememberInfiniteTransition(label = "munchmate_pulse")
  val alpha by infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "dotAlpha",
  )

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(start = 4.dp),
  ) {
    MunchMateIcon(
      modifier = Modifier.size(16.dp),
      tint = Color(0xFF2E5A44),
    )
    Spacer(modifier = Modifier.width(6.dp))
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(12.dp))
        .background(PureWhite)
        .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
        .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "MunchMate is thinking",
          fontSize = 12.sp,
          color = TextMuted,
          fontWeight = FontWeight.Medium,
        )
        CircularProgressIndicator(
          color = DrawerAmber,
          strokeWidth = 2.dp,
          modifier = Modifier.size(12.dp),
        )
      }
    }
  }
}

// ── Chat Input Bar ──────────────────────────────────────────────────────────
@Composable
private fun MunchMateChatInputBar(
  inputText: String,
  onTextChanged: (String) -> Unit,
  onSend: () -> Unit,
  isLoading: Boolean,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = WarmCream,
    tonalElevation = 2.dp,
  ) {
    Column {
      HorizontalDivider(
        thickness = 1.dp,
        color = BorderGray.copy(alpha = 0.5f),
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = inputText,
          onValueChange = onTextChanged,
          placeholder = {
            Text(
              text = "Ask about food, canteens or track orders...",
              fontSize = 13.sp,
              color = TextMuted,
            )
          },
          modifier = Modifier
            .weight(1f)
            .height(50.dp),
          shape = RoundedCornerShape(25.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = PureWhite,
            unfocusedContainerColor = PureWhite,
            focusedBorderColor = DrawerAmber,
            unfocusedBorderColor = BorderGray,
            cursorColor = DrawerAmber,
          ),
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = {
            if (inputText.isNotBlank() && !isLoading) {
              onSend()
            }
          }),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
          modifier = Modifier
            .size(46.dp)
            .shadow(
              elevation = if (inputText.isNotBlank() && !isLoading) 3.dp else 0.dp,
              shape = CircleShape,
              spotColor = DrawerAmber.copy(alpha = 0.5f),
            )
            .clip(CircleShape)
            .background(
              if (inputText.isNotBlank() && !isLoading) DrawerAmber else SoftGray
            )
            .clickable(enabled = inputText.isNotBlank() && !isLoading) {
              onSend()
            },
          contentAlignment = Alignment.Center,
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              color = PureWhite,
              strokeWidth = 2.dp,
              modifier = Modifier.size(20.dp),
            )
          } else {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = "Send",
              tint = if (inputText.isNotBlank()) PureWhite else TextMuted,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}
