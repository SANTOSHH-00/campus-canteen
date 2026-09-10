package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firebase.OrderDocument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Theme Palette ─────────────────────────────────────────────────────────────
private val PrimaryOrange = Color(0xFFFF6433)
private val LightOrangeBg = Color(0xFFFFF1EB)
private val DarkText = Color(0xFF111827)
private val GrayText = Color(0xFF6B7280)
private val CardBg = Color(0xFFFFFFFF)
private val ScreenBg = Color(0xFFF8F9FA)
private val GreenBadge = Color(0xFF10B981)

data class CustomerSummary(
  val id: String,
  val name: String,
  val phone: String,
  val course: String,
  val totalOrders: Int,
  val totalSpent: Int,
  val favoriteItem: String,
  val lastOrderTime: Long,
)

@Composable
fun OwnerCustomersScreen(
  orders: List<OrderDocument>,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }

  // Aggregate orders by student
  val customerList = remember(orders) {
    val grouped = orders.groupBy { order ->
      order.studentId.ifBlank { order.studentName.ifBlank { order.studentPhone } }.ifBlank { "guest" }
    }

    grouped.mapNotNull { (key, studentOrders) ->
      if (key == "guest" && studentOrders.all { it.studentName.isBlank() }) return@mapNotNull null
      val first = studentOrders.first()
      val name = studentOrders.firstOrNull { it.studentName.isNotBlank() }?.studentName ?: "Campus Student"
      val phone = studentOrders.firstOrNull { it.studentPhone.isNotBlank() }?.studentPhone ?: ""
      val course = studentOrders.firstOrNull { it.studentCourse.isNotBlank() }?.studentCourse ?: "Student"
      val totalSpent = studentOrders.sumOf { it.totalAmount }
      val totalOrders = studentOrders.size
      val lastOrder = studentOrders.maxOfOrNull { it.createdAt } ?: 0L

      // Determine favorite dish
      val itemCounts = mutableMapOf<String, Int>()
      studentOrders.forEach { o ->
        o.items.forEach { item ->
          itemCounts[item.name] = (itemCounts[item.name] ?: 0) + item.quantity
        }
      }
      val fav = itemCounts.maxByOrNull { it.value }?.key ?: "Meal"

      CustomerSummary(
        id = key,
        name = name,
        phone = phone,
        course = course,
        totalOrders = totalOrders,
        totalSpent = totalSpent,
        favoriteItem = fav,
        lastOrderTime = lastOrder,
      )
    }.sortedByDescending { it.totalOrders }
  }

  val displayCustomers = remember(customerList, searchQuery) {
    val baseList = customerList

    if (searchQuery.isBlank()) {
      baseList
    } else {
      baseList.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
          it.course.contains(searchQuery, ignoreCase = true) ||
          it.phone.contains(searchQuery)
      }
    }
  }

  val totalUnique = displayCustomers.size
  val repeatCount = displayCustomers.count { it.totalOrders >= 2 }
  val topSpender = displayCustomers.maxByOrNull { it.totalSpent }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(ScreenBg)
      .statusBarsPadding()
      .navigationBarsPadding(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
      // ── Top Bar with Back Arrow ────────────────────────────────────────────
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE5E7EB), CircleShape),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = DarkText,
            modifier = Modifier.size(20.dp),
          )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
          Text(
            text = "Customers & Regulars",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = DarkText,
          )
          Text(
            text = "Campus students ordering from your canteen",
            fontSize = 12.5.sp,
            color = GrayText,
          )
        }
      }

      // ── KPI Summary Cards ──────────────────────────────────────────────────
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Card(
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = CardBg),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Total Customers", fontSize = 11.5.sp, color = GrayText)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "$totalUnique", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkText)
          }
        }

        Card(
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = CardBg),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Repeat Regulars", fontSize = 11.5.sp, color = GrayText)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "$repeatCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryOrange)
          }
        }

        Card(
          modifier = Modifier.weight(1.1f),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = CardBg),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Top Regular", fontSize = 11.5.sp, color = GrayText)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = topSpender?.name?.split(" ")?.firstOrNull() ?: "None",
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              color = GreenBadge,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      // ── Search Bar ─────────────────────────────────────────────────────────
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search by student name, course or phone...", fontSize = 13.sp, color = GrayText) },
        leadingIcon = {
          Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = GrayText, modifier = Modifier.size(20.dp))
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = Color.White,
          unfocusedContainerColor = Color.White,
          focusedBorderColor = PrimaryOrange,
          unfocusedBorderColor = Color(0xFFE5E7EB),
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
      )

      // ── Customer List ──────────────────────────────────────────────────────
      if (displayCustomers.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = Icons.Default.Group, contentDescription = null, tint = GrayText, modifier = Modifier.size(44.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "No matching customers found", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(displayCustomers, key = { it.id }) { customer ->
            CustomerCardItem(
              customer = customer,
              onCallStudent = {
                if (customer.phone.isNotBlank()) {
                  val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                  try {
                    context.startActivity(intent)
                  } catch (e: Exception) {
                    Toast.makeText(context, "Student phone: ${customer.phone}", Toast.LENGTH_SHORT).show()
                  }
                } else {
                  Toast.makeText(context, "Phone number not registered", Toast.LENGTH_SHORT).show()
                }
              }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CustomerCardItem(
  customer: CustomerSummary,
  onCallStudent: () -> Unit,
) {
  val initials = customer.name.split(" ").filter { it.isNotBlank() }.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifBlank { "ST" }
  val formattedDate = remember(customer.lastOrderTime) {
    if (customer.lastOrderTime > 0) {
      SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(Date(customer.lastOrderTime))
    } else "Recently"
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = CardBg),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFF1F2F4)),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Avatar initials
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(LightOrangeBg),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = initials,
          fontSize = 16.sp,
          fontWeight = FontWeight.ExtraBold,
          color = PrimaryOrange,
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = customer.name,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          if (customer.totalOrders >= 3) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFEF3C7))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(11.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(text = "Regular", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = customer.course,
          fontSize = 12.sp,
          color = GrayText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "${customer.totalOrders} orders",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = PrimaryOrange,
          )
          Text(text = " • ", fontSize = 11.sp, color = Color(0xFFD1D5DB))
          Text(
            text = "₹${customer.totalSpent} spent",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkText,
          )
          Text(text = " • ", fontSize = 11.sp, color = Color(0xFFD1D5DB))
          Icon(imageVector = Icons.Default.Fastfood, contentDescription = null, tint = GrayText, modifier = Modifier.size(11.dp))
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = customer.favoriteItem,
            fontSize = 11.sp,
            color = GrayText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // Quick Call Button
      IconButton(
        onClick = onCallStudent,
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(Color(0xFFF3F4F6)),
      ) {
        Icon(
          imageVector = Icons.Default.Call,
          contentDescription = "Call Student",
          tint = DarkText,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}
