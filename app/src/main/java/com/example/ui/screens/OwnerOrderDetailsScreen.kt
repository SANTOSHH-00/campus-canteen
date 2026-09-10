package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.firebase.OrderDocument
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.viewmodel.OwnerDashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val OrangeAccent = Color(0xFFF97316)
private val GreenReady = Color(0xFF16A34A)
private val BadgePreparingBg = Color(0xFFFFF7ED)
private val BadgeReadyBg = Color(0xFFF0FDF4)
private val BadgePickedUpBg = Color(0xFFF3F4F6)
private val CardBorderColor = Color(0xFFF3F4F6)
private val DividerColor = Color(0xFFF3F4F6)
private val PageBackground = Color(0xFFFAFAFA)

@Composable
fun OwnerOrderDetailsScreen(
  orderId: String,
  onBack: () -> Unit,
  dashboardViewModel: OwnerDashboardViewModel = viewModel(),
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val allOrders by dashboardViewModel.orders.collectAsState()
  var fetchedOrder by remember { mutableStateOf<OrderDocument?>(null) }
  var isFetching by remember { mutableStateOf(false) }

  val order = remember(allOrders, fetchedOrder, orderId) {
    allOrders.find { it.orderId == orderId }
      ?: com.example.data.api.MongoRepository.getCachedOrder(orderId)
      ?: fetchedOrder
  }

  LaunchedEffect(orderId) {
    if (order == null) {
      isFetching = true
      dashboardViewModel.observeOrder(orderId).collect {
        fetchedOrder = it
        isFetching = false
      }
    }
  }

  if (order == null) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .background(PageBackground)
        .statusBarsPadding()
        .navigationBarsPadding(),
      contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.CircularProgressIndicator(color = OrangeAccent)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = if (isFetching) "Loading order details..." else "Order not found",
          color = TextMuted,
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
          onClick = onBack,
          colors = ButtonDefaults.buttonColors(containerColor = BlackPrimary),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text("Go Back", color = PureWhite)
        }
      }
    }
    return
  }

  val isPreparing = order.status.equals("PREPARING", ignoreCase = true)
  val isReady = order.status.equals("READY", ignoreCase = true)
  val isPickedUp = order.status.equals("COMPLETED", ignoreCase = true) || order.status.equals("DELIVERED", ignoreCase = true)

  val statusLabel = when {
    isPickedUp -> "Picked Up"
    isReady -> "Ready"
    else -> "Preparing"
  }

  val statusTextColor = when {
    isPickedUp -> Color(0xFF6B7280)
    isReady -> GreenReady
    else -> OrangeAccent
  }

  val statusBgColor = when {
    isPickedUp -> BadgePickedUpBg
    isReady -> BadgeReadyBg
    else -> BadgePreparingBg
  }

  val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
  val dateFormatter = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
  val orderTime = remember(order.createdAt) { timeFormatter.format(Date(order.createdAt)) }
  val orderDate = remember(order.createdAt) { dateFormatter.format(Date(order.createdAt)) }

  // Phone calling & copying action
  val effectivePhone = order.studentPhone
  val onCallStudent = {
    if (effectivePhone.isNotBlank()) {
      try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Student Phone", effectivePhone)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied $effectivePhone to clipboard", Toast.LENGTH_SHORT).show()

        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
          data = Uri.parse("tel:$effectivePhone")
        }
        context.startActivity(dialIntent)
      } catch (e: Exception) {
        Toast.makeText(context, "Could not open dialer: ${e.message}", Toast.LENGTH_SHORT).show()
      }
    } else {
      Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show()
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(PageBackground)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("owner_order_details_screen"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(bottom = 120.dp) // Space for bottom action buttons
        .verticalScroll(rememberScrollState()),
    ) {

      // ── Header matching Image 2 ───────────────────────────────────────────
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(PureWhite)
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("order_details_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = TextDark,
            modifier = Modifier.size(24.dp),
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
          text = "Order Details (Owner)",
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // ── Card 1: Order & Student Profile Summary (Image 2) ─────────────────
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, CardBorderColor),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          // Token and status badge
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            val tokenDisplay = if (order.tokenNumber.startsWith("#")) order.tokenNumber else "#Q${order.tokenNumber.ifBlank { "1042" }}"
            Text(
              text = tokenDisplay,
              fontSize = 22.sp,
              fontWeight = FontWeight.ExtraBold,
              color = TextDark,
            )

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(statusBgColor)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
              Text(
                text = statusLabel,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = statusTextColor,
              )
            }
          }

          Spacer(modifier = Modifier.height(4.dp))

          // Date & Time
          Text(
            text = "$orderTime • $orderDate",
            fontSize = 13.sp,
            color = TextMuted,
          )

          Spacer(modifier = Modifier.height(14.dp))
          HorizontalDivider(thickness = 0.8.dp, color = DividerColor)
          Spacer(modifier = Modifier.height(14.dp))

          // Student profile row with Phone Call button
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            // Student avatar
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFF7ED)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = OrangeAccent,
                modifier = Modifier.size(24.dp),
              )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Student Name, ID, Course, Phone
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = order.studentName.ifBlank { "Rahul Sharma" },
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = effectivePhone,
                fontSize = 13.sp,
                color = TextMuted,
              )
              if (order.studentId.isNotBlank() || order.studentCourse.isNotBlank()) {
                val details = listOf(
                  if (order.studentId.isNotBlank()) "ID: ${order.studentId}" else "",
                  order.studentCourse.ifBlank { "" },
                ).filter { it.isNotBlank() }.joinToString(" • ")

                Text(
                  text = details,
                  fontSize = 11.5.sp,
                  color = Color(0xFF6B7280),
                )
              }
            }

            // Call Button (phone icon)
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFF7ED))
                .clickable { onCallStudent() }
                .testTag("call_student_button"),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = "Call Student",
                tint = OrangeAccent,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // ── Card 2: Order Items (Image 2) ─────────────────────────────────────
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, CardBorderColor),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text(
            text = "Order Items",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )

          Spacer(modifier = Modifier.height(12.dp))

          val displayItems = order.items

          displayItems.forEach { item ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = item.name,
                fontSize = 14.sp,
                color = Color(0xFF374151),
                modifier = Modifier.weight(1f),
              )
              Text(
                text = "₹${item.price}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF374151),
                modifier = Modifier.padding(end = 16.dp),
              )
              Text(
                text = "x${item.quantity}",
                fontSize = 13.sp,
                color = TextMuted,
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
          HorizontalDivider(thickness = 0.8.dp, color = DividerColor)
          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Total",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark,
            )
            Text(
              text = "₹${if (order.totalAmount > 0) order.totalAmount else 90}",
              fontSize = 20.sp,
              fontWeight = FontWeight.ExtraBold,
              color = TextDark,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // ── Card 3: Order Status Timeline (Image 2) ───────────────────────────
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, CardBorderColor),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text(
            text = "Order Status",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )

          Spacer(modifier = Modifier.height(16.dp))

          // Step 1: Order Received
          StatusTimelineStep(
            title = "Order Received",
            subtitle = null,
            time = orderTime,
            isCompleted = true,
            isActive = false,
            showConnectingLine = true,
          )

          // Step 2: Preparing
          StatusTimelineStep(
            title = "Preparing",
            subtitle = "Kitchen started",
            time = if (isPreparing || isReady || isPickedUp) orderTime else "-",
            isCompleted = isPreparing || isReady || isPickedUp,
            isActive = isPreparing,
            showConnectingLine = true,
          )

          // Step 3: Ready for Pickup (Only Counter 1 or Counter 2)
          val counterAssigned = if (order.pickupCounter.isNotBlank()) order.pickupCounter else "Counter 1"
          StatusTimelineStep(
            title = "Ready for Pickup",
            subtitle = if (isReady || isPickedUp) "$counterAssigned assigned" else null,
            time = if (isReady || isPickedUp) orderTime else "-",
            isCompleted = isReady || isPickedUp,
            isActive = isReady,
            showConnectingLine = true,
          )

          // Step 4: Picked Up
          StatusTimelineStep(
            title = "Picked Up",
            subtitle = if (isPickedUp) "Handed over to student" else null,
            time = if (isPickedUp) orderTime else "-",
            isCompleted = isPickedUp,
            isActive = isPickedUp,
            showConnectingLine = false,
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))
    }

    // ── Bottom Fixed Action Buttons (Image 2) ───────────────────────────────
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .background(PureWhite)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // 1. Mark as Ready (Outlined White with Orange border)
      OutlinedButton(
        onClick = {
          dashboardViewModel.updateOrderStatus(order.orderId, "READY")
          Toast.makeText(context, "Order ${order.tokenNumber} marked Ready at Counter 1", Toast.LENGTH_SHORT).show()
        },
        enabled = !isPickedUp,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("mark_ready_button"),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, OrangeAccent),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeAccent),
      ) {
        Text(
          text = if (isReady) "Order Ready (Counter 1) ✓" else "Mark as Ready",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = OrangeAccent,
        )
      }

      // 2. Mark as Picked Up (Solid Orange button)
      Button(
        onClick = {
          dashboardViewModel.updateOrderStatus(order.orderId, "COMPLETED")
          Toast.makeText(context, "Order ${order.tokenNumber} marked Picked Up", Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("mark_picked_up_button"),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
      ) {
        Text(
          text = if (isPickedUp) "Picked Up ✓" else "Mark as Picked Up",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = PureWhite,
        )
      }
    }
  }
}

// ── TIMELINE STEP COMPONENT ──────────────────────────────────────────────────

@Composable
private fun StatusTimelineStep(
  title: String,
  subtitle: String?,
  time: String,
  isCompleted: Boolean,
  isActive: Boolean,
  showConnectingLine: Boolean,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.Top,
  ) {
    // Indicator Column
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Box(
        modifier = Modifier
          .size(20.dp)
          .clip(CircleShape)
          .background(if (isCompleted) OrangeAccent else Color(0xFFE5E7EB)),
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

      if (showConnectingLine) {
        Box(
          modifier = Modifier
            .width(2.dp)
            .height(28.dp)
            .background(if (isCompleted) OrangeAccent else Color(0xFFE5E7EB)),
        )
      }
    }

    Spacer(modifier = Modifier.width(12.dp))

    // Step Details
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = if (isActive || isCompleted) FontWeight.Bold else FontWeight.Medium,
        color = if (isActive || isCompleted) TextDark else Color(0xFF9CA3AF),
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          fontSize = 12.sp,
          color = TextMuted,
        )
      }
    }

    // Time
    Text(
      text = time,
      fontSize = 13.sp,
      color = if (time == "-") Color(0xFF9CA3AF) else Color(0xFF4B5563),
      textAlign = TextAlign.End,
    )
  }
}
