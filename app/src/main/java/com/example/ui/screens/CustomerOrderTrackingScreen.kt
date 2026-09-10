package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.OrderRecord
import com.example.data.OrderStatus
import com.example.ui.components.CustomerThumbsUpIllustration
import com.example.ui.components.TakeoutBagIllustration
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val OrangeAccent = Color(0xFFF97316)
private val GreenReady = Color(0xFF16A34A)
private val BluePickedUp = Color(0xFF2563EB)
private val BadgeReadyBg = Color(0xFFF0FDF4)
private val BadgePickedUpBg = Color(0xFFEFF6FF)
private val BadgePreparingBg = Color(0xFFFFF7ED)
private val CounterCardBg = PureWhite
private val CounterTitleColor = BlackPrimary
private val PageBackground = WarmCream

@Composable
fun CustomerOrderTrackingScreen(
  order: OrderRecord,
  onBack: () -> Unit,
  onOrderAgain: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  // If order is already picked up, start in Detailed Timeline state (Image 3)
  // Otherwise if ready, start in Ready state (Image 2) and user can click Navigate
  val isPickedUp = order.status == OrderStatus.COMPLETED
  var showDetailedTimeline by remember(isPickedUp) { mutableStateOf(isPickedUp) }

  val tokenRaw = order.tokenNumber.ifBlank { "1042" }
  val tokenDisplay = if (tokenRaw.startsWith("#")) tokenRaw else "#Q$tokenRaw"
  val tokenPlain = tokenRaw.replace("#", "").replace("Q", "")

  // Format device local time for realistic milestones
  val deviceTimeFormat = remember {
    SimpleDateFormat("h:mm a", Locale.getDefault())
  }
  val nowTime = remember { deviceTimeFormat.format(Date()) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(PageBackground)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("customer_order_tracking_screen"),
  ) {
    // ── Top Bar matching Images 2 & 3: ← Order Tracking  🔍 ─────────────
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(PureWhite)
        .padding(horizontal = 8.dp, vertical = 10.dp),
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
          modifier = Modifier.size(24.dp),
        )
      }

      Spacer(Modifier.width(4.dp))

      Text(
        text = "Order Tracking",
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        color = TextDark,
        modifier = Modifier.weight(1f),
      )

      IconButton(onClick = {}) {
        Icon(
          imageVector = Icons.Default.Search,
          contentDescription = "Search",
          tint = TextDark,
          modifier = Modifier.size(24.dp),
        )
      }
    }

    // ── Animated Switcher Between Image 2 (Ready) & Image 3 (Navigate / Timeline)
    AnimatedContent(
      targetState = showDetailedTimeline,
      transitionSpec = { fadeIn() togetherWith fadeOut() },
      label = "order_tracking_state_anim",
    ) { inTimelineState ->
      if (!inTimelineState) {
        // ── STATE A: READY STATE (Matches Image 2) ──────────────────────────
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
          // Token and Status Badge
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = tokenDisplay,
              fontSize = 24.sp,
              fontWeight = FontWeight.ExtraBold,
              color = TextDark,
            )

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(BlackPrimary)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  tint = Color(0xFF4ADE80),
                  modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                  text = "Ready",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = PureWhite,
                )
              }
            }
          }

          Spacer(Modifier.height(14.dp))

          // Headline & Subtitle
          Text(
            text = "Your order is ready!",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextDark,
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = "Please pick up your order.",
            fontSize = 14.5.sp,
            color = Color(0xFF4B5563),
          )

          Spacer(Modifier.height(28.dp))

          // Center Takeout Bag Illustration (Image 2)
          Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            TakeoutBagIllustration()
          }

          Spacer(Modifier.height(32.dp))

          // Pickup Counter Card (Clean theme matching card)
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
          ) {
            Column(modifier = Modifier.padding(20.dp)) {
              Text(
                text = "Pickup Counter",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = BlackPrimary,
              )
              Spacer(Modifier.height(4.dp))
              Text(
                text = if (order.pickupPreference.contains("Counter 1", ignoreCase = true)) "Counter 1" else "Counter 2",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )

              val cafeName = order.pickupCanteenName.ifBlank { "Campus Canteen" }
              val blockLoc = order.pickupLocation.substringBefore("(").trim()
              val displayLoc = if (blockLoc.isNotBlank() && !cafeName.contains(blockLoc, ignoreCase = true)) {
                val cleanCafe = cafeName.replace(" 26-27", "").trim()
                val cleanBlock = blockLoc.replace("Between Block", "Block", ignoreCase = true).trim()
                "$cleanCafe • $cleanBlock"
              } else {
                cafeName
              }

              Spacer(Modifier.height(6.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.LocationOn,
                  contentDescription = null,
                  tint = Color(0xFF6B7280),
                  modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                  text = displayLoc,
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.Medium,
                  color = Color(0xFF6B7280),
                )
              }

              Spacer(Modifier.height(18.dp))

              Text(
                text = "Show this code at the counter",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF6B7280),
              )
              Spacer(Modifier.height(8.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                // Code box with Q1042
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF7F3EC))
                    .border(1.dp, BorderGray.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = "Q$tokenPlain",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDark,
                  )
                }

                // Sleek Black Primary "Navigate" Button
                Button(
                  onClick = { showDetailedTimeline = true },
                  shape = RoundedCornerShape(14.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = BlackPrimary),
                  modifier = Modifier.testTag("navigate_order_button"),
                ) {
                  Text(
                    text = "Navigate",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                  )
                }
              }
            }
          }
        }
      } else {
        // ── STATE B: DETAILED TIMELINE / PICKED UP STATE (Matches Image 3) ───
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
          // Token & Status Badge
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = tokenDisplay,
              fontSize = 24.sp,
              fontWeight = FontWeight.ExtraBold,
              color = TextDark,
            )

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(BlackPrimary)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  tint = if (order.status == OrderStatus.COMPLETED) PureWhite else DrawerAmber,
                  modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                  text = when (order.status) {
                    OrderStatus.COMPLETED -> "Picked Up"
                    OrderStatus.READY -> "Ready"
                    OrderStatus.PREPARING -> "Preparing"
                    else -> "Placed"
                  },
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = PureWhite,
                )
              }
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

          Spacer(Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = null,
              tint = Color(0xFF6B7280),
              modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = displayLoc,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFF6B7280),
            )
          }

          Spacer(Modifier.height(14.dp))

          // Headline matching Image 3
          Text(
            text = if (order.status == OrderStatus.COMPLETED) "Enjoy your meal!" else "Tracking your order",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextDark,
          )

          Spacer(Modifier.height(16.dp))

          // Center Thumbs Up Illustration (Image 3)
          Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            CustomerThumbsUpIllustration()
          }

          Spacer(Modifier.height(20.dp))

          // Vertical Step-by-Step Milestones Progress (Image 3)
          TrackingMilestone(
            title = "Order Placed",
            time = nowTime,
            isCompleted = true,
            isLast = false,
          )
          TrackingMilestone(
            title = "Payment Confirmed",
            time = nowTime,
            isCompleted = true,
            isLast = false,
          )
          TrackingMilestone(
            title = "Preparing Your Food",
            time = nowTime,
            isCompleted = order.status == OrderStatus.PREPARING || order.status == OrderStatus.READY || order.status == OrderStatus.COMPLETED,
            isLast = false,
          )
          TrackingMilestone(
            title = "Ready for Pickup",
            time = if (order.status == OrderStatus.READY || order.status == OrderStatus.COMPLETED) nowTime else "-",
            isCompleted = order.status == OrderStatus.READY || order.status == OrderStatus.COMPLETED,
            isLast = false,
          )
          TrackingMilestone(
            title = "Picked Up",
            time = if (order.status == OrderStatus.COMPLETED) nowTime else "-",
            isCompleted = order.status == OrderStatus.COMPLETED,
            isLast = true,
          )

          Spacer(Modifier.height(28.dp))

          // Solid Black Primary "Order Again" Button
          Button(
            onClick = {
              onOrderAgain()
              onBack()
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BlackPrimary),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("order_again_button"),
          ) {
            Text(
              text = "Order Again",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = PureWhite,
            )
          }

          Spacer(Modifier.height(16.dp))
        }
      }
    }
  }
}

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
      .padding(vertical = 2.dp),
    verticalAlignment = Alignment.Top,
  ) {
    // Checkmark circle & vertical connecting line
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Box(
        modifier = Modifier
          .size(22.dp)
          .clip(CircleShape)
          .background(if (isCompleted) Color(0xFF16A34A) else Color(0xFFE5E7EB)),
        contentAlignment = Alignment.Center,
      ) {
        if (isCompleted) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = PureWhite,
            modifier = Modifier.size(13.dp),
          )
        }
      }

      if (!isLast) {
        Box(
          modifier = Modifier
            .width(2.dp)
            .height(24.dp)
            .background(if (isCompleted) Color(0xFF16A34A).copy(alpha = 0.5f) else Color(0xFFE5E7EB))
        )
      }
    }

    Spacer(Modifier.width(14.dp))

    // Milestone Title & Time
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 1.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        fontSize = 14.5.sp,
        fontWeight = if (isCompleted) FontWeight.Bold else FontWeight.Medium,
        color = if (isCompleted) TextDark else Color(0xFF9CA3AF),
      )

      Text(
        text = time,
        fontSize = 12.5.sp,
        color = Color(0xFF6B7280),
      )
    }
  }
}
