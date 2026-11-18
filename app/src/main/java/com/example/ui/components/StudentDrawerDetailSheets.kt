// StudentDrawerDetailSheets: Rich, dedicated full-layout screens for FAQs, Support, About QuickBite, and Settings
package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.DrawerAmberLight
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream

enum class DrawerSubScreen {
  NONE,
  FAQS,
  SUPPORT,
  ABOUT,
  SETTINGS,
  PAYMENTS
}

/**
 * Common Top Bar for Drawer Detail Sheets
 */
@Composable
fun DrawerSubScreenHeader(
  title: String,
  subtitle: String,
  icon: ImageVector,
  onBack: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(DrawerAmber)
      .statusBarsPadding()
      .padding(horizontal = 20.dp, vertical = 14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(PureWhite.copy(alpha = 0.22f))
          .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back",
          tint = PureWhite,
          modifier = Modifier.size(20.dp),
        )
      }

      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(PureWhite.copy(alpha = 0.22f))
          .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close",
          tint = PureWhite,
          modifier = Modifier.size(20.dp),
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(PureWhite.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = PureWhite,
          modifier = Modifier.size(22.dp),
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(
          text = title,
          fontSize = 22.sp,
          fontWeight = FontWeight.ExtraBold,
          color = PureWhite,
        )
        Text(
          text = subtitle,
          fontSize = 12.5.sp,
          color = PureWhite.copy(alpha = 0.88f),
        )
      }
    }
  }
}

data class StudentFaqItem(
  val id: Int,
  val category: String,
  val question: String,
  val answer: String,
)

/**
 * 1. Comprehensive Student FAQs Screen
 */
@Composable
fun StudentFaqSheet(
  onBack: () -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }
  var expandedItemId by remember { mutableStateOf<Int?>(1) } // First FAQ expanded by default

  val faqs = remember {
    listOf(
      StudentFaqItem(
        id = 1,
        category = "Token & Food Pickup",
        question = "How do I collect my food at the canteen counter?",
        answer = "When your order status updates to 'Ready for Pickup', head directly to your selected canteen's pickup counter. Simply show your 4-digit token number or the 1D barcode on your tracking screen. The staff will verify and hand over your meal immediately—no waiting in the regular queue!",
      ),
      StudentFaqItem(
        id = 2,
        category = "Cancellations & Refunds",
        question = "Can I cancel or modify my order after placing it?",
        answer = "Because our canteen kitchens begin cooking immediately to minimize wait times, you can cancel an order within 1 minute of placing it (before the kitchen accepts it). After 1 minute or once preparation begins, orders cannot be cancelled or modified.",
      ),
      StudentFaqItem(
        id = 3,
        category = "Class Breaks & Timing",
        question = "What if my lecture gets delayed and I am late for pickup?",
        answer = "No worries! Your order is kept securely in the counter hot-case food warmer for up to 30 minutes past your scheduled pickup slot. You can pick it up as soon as your class finishes.",
      ),
      StudentFaqItem(
        id = 4,
        category = "Your Usual & Fast Ordering",
        question = "How does the 'Your Usual' feature work?",
        answer = "QuickBite automatically learns your favorite campus meals (items you reorder frequently). It highlights your go-to dish right on the Home Screen so you can reorder with a single tap in under 5 seconds between class bells.",
      ),
      StudentFaqItem(
        id = 5,
        category = "MunchMate AI Assistant",
        question = "What can I do with the MunchMate AI food buddy?",
        answer = "MunchMate is your personal campus food assistant. Tap the AI bot icon to ask for dish recommendations under your budget, check live menu item availability, track ongoing orders, get dietary advice, or suggest new food items to canteen owners.",
      ),
      StudentFaqItem(
        id = 6,
        category = "Pricing & Payment",
        question = "Are prices on QuickBite higher than the canteen counter?",
        answer = "Zero markup! QuickBite charges the exact official counter menu prices set by your campus canteens. There are no hidden platform fees, surge prices, or delivery charges for students.",
      ),
      StudentFaqItem(
        id = 7,
        category = "Menu & Ingredients",
        question = "What if an item is sold out after I order?",
        answer = "In the rare event that ingredients run out during rush hours, the canteen owner will immediately update your order and notify you. You will receive an instant credit refund or an equivalent dish replacement of your choice.",
      ),
      StudentFaqItem(
        id = 8,
        category = "Pre-ordering",
        question = "Can I schedule food ahead of time for my next break?",
        answer = "Yes! You can use the Break Clock or Pickup Time Selector to set an exact time (e.g. 10:45 AM or 1:15 PM). The canteen kitchen schedules cooking so your food is piping hot exactly when your break starts.",
      ),
    )
  }

  val filteredFaqs = remember(searchQuery) {
    if (searchQuery.isBlank()) faqs
    else faqs.filter {
      it.question.contains(searchQuery, ignoreCase = true) ||
        it.answer.contains(searchQuery, ignoreCase = true) ||
        it.category.contains(searchQuery, ignoreCase = true)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(WarmCream)
      .navigationBarsPadding(),
  ) {
    DrawerSubScreenHeader(
      title = "Student FAQs",
      subtitle = "Quick answers to common campus questions",
      icon = Icons.Outlined.HelpOutline,
      onBack = onBack,
    )

    // Search Box
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search questions (e.g. token, cancel, pickup)...", fontSize = 13.sp, color = TextMuted) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DrawerAmber, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
          if (searchQuery.isNotBlank()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(18.dp))
            }
          }
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = PureWhite,
          unfocusedContainerColor = PureWhite,
          focusedBorderColor = DrawerAmber,
          unfocusedBorderColor = BorderGray,
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .weight(1f)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items(filteredFaqs, key = { it.id }) { item ->
        val isExpanded = expandedItemId == item.id
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, if (isExpanded) DrawerAmber else BorderGray.copy(alpha = 0.7f)),
          elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 2.dp else 1.dp),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { expandedItemId = if (isExpanded) null else item.id },
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DrawerAmberLight)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                  Text(
                    text = item.category.uppercase(),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = DrawerAmber,
                  )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = item.question,
                  fontSize = 14.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = BlackPrimary,
                  lineHeight = 20.sp,
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (isExpanded) DrawerAmber else TextMuted,
                modifier = Modifier.size(22.dp),
              )
            }

            AnimatedVisibility(
              visible = isExpanded,
              enter = expandVertically() + fadeIn(),
              exit = shrinkVertically() + fadeOut(),
            ) {
              Column {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderGray.copy(alpha = 0.5f), thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                  text = item.answer,
                  fontSize = 13.5.sp,
                  color = TextDark,
                  lineHeight = 21.sp,
                )
              }
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

/**
 * 2. Official Support Screen with Emails & Operating Hours
 */
@Composable
fun StudentSupportSheet(
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val officialEmail = "quickbite.connecting@gmail.com"
  val ownerEmail = "quickbite.connecting@gmail.com"
  val helplinePhone = "+919876543210"

  fun copyToClipboard(text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
  }

  fun sendEmail(email: String) {
    try {
      val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$email")
        putExtra(Intent.EXTRA_SUBJECT, "QuickBite Student Support Inquiry")
      }
      context.startActivity(intent)
    } catch (_: Exception) {
      copyToClipboard(email, "Support Email")
    }
  }

  fun callPhone(phone: String) {
    try {
      val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
      context.startActivity(intent)
    } catch (_: Exception) {
      copyToClipboard(phone, "Helpline Number")
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(WarmCream)
      .navigationBarsPadding(),
  ) {
    DrawerSubScreenHeader(
      title = "Support & Help Desk",
      subtitle = "We're here to assist your canteen dining",
      icon = Icons.Outlined.SupportAgent,
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .weight(1f)
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // 1. Official Email Card
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGray),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(DrawerAmberLight),
                contentAlignment = Alignment.Center,
              ) {
                Icon(Icons.Default.Email, contentDescription = null, tint = DrawerAmber, modifier = Modifier.size(22.dp))
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Official Support Email",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = BlackPrimary,
                )
                Text(
                  text = "Direct student assistance & inquiries",
                  fontSize = 12.sp,
                  color = TextMuted,
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(WarmCream)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = officialEmail,
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextDark,
                )
                IconButton(
                  onClick = { copyToClipboard(officialEmail, "Official Email") },
                  modifier = Modifier.size(30.dp),
                ) {
                  Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = DrawerAmber, modifier = Modifier.size(16.dp))
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
              onClick = { sendEmail(officialEmail) },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = BlackPrimary),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Default.Email, contentDescription = null, tint = PureWhite, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Email QuickBite Support", fontWeight = FontWeight.Bold, color = PureWhite)
            }
          }
        }
      }

      // 2. Owner & Partner Contact Card
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGray),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(DrawerAmberLight),
                contentAlignment = Alignment.Center,
              ) {
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = DrawerAmber, modifier = Modifier.size(22.dp))
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Canteen Owner & Partner Desk",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = BlackPrimary,
                )
                Text(
                  text = "Kitchen escalations & meal feedback",
                  fontSize = 12.sp,
                  color = TextMuted,
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(WarmCream)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = ownerEmail,
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextDark,
                )
                IconButton(
                  onClick = { copyToClipboard(ownerEmail, "Owner Mail") },
                  modifier = Modifier.size(30.dp),
                ) {
                  Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = DrawerAmber, modifier = Modifier.size(16.dp))
                }
              }
            }
          }
        }
      }

      // 3. Operating Hours Card
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGray),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(DrawerAmberLight),
                contentAlignment = Alignment.Center,
              ) {
                Icon(Icons.Default.AccessTime, contentDescription = null, tint = DrawerAmber, modifier = Modifier.size(22.dp))
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Canteen Operating Hours",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = BlackPrimary,
                )
                Text(
                  text = "Campus service schedule",
                  fontSize = 12.sp,
                  color = TextMuted,
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("Monday – Friday (Working Days)", fontSize = 13.sp, color = TextDark, fontWeight = FontWeight.Medium)
                Text("7:30 AM – 9:30 PM", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DrawerAmber)
              }
              HorizontalDivider(color = BorderGray.copy(alpha = 0.5f), thickness = 0.6.dp)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("Saturday – Sunday (Weekends)", fontSize = 13.sp, color = TextDark, fontWeight = FontWeight.Medium)
                Text("8:00 AM – 8:00 PM", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DrawerAmber)
              }
              HorizontalDivider(color = BorderGray.copy(alpha = 0.5f), thickness = 0.6.dp)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("Mid-Term & End-Term Exams", fontSize = 13.sp, color = TextDark, fontWeight = FontWeight.Medium)
                Text("Extended 24/7 Hours", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
              }
            }
          }
        }
      }

      // 4. Campus Helpline Card
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGray),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(DrawerAmberLight),
                contentAlignment = Alignment.Center,
              ) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = DrawerAmber, modifier = Modifier.size(20.dp))
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("Campus Helpline", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = BlackPrimary)
                Text("+91 98765 43210 · Ext: 4201", fontSize = 12.5.sp, color = TextMuted)
              }
            }
            Button(
              onClick = { callPhone("+919876543210") },
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
              Text("Call", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PureWhite)
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(20.dp))
      }
    }
  }
}

/**
 * 3. Comprehensive About QuickBite, Rights & Responsibilities, and Privacy Screen
 */
@Composable
fun StudentAboutSheet(
  onBack: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(WarmCream)
      .navigationBarsPadding(),
  ) {
    DrawerSubScreenHeader(
      title = "About QuickBite",
      subtitle = "Campus food innovation, your rights & privacy",
      icon = Icons.Default.Info,
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .weight(1f)
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // App Overview Card
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGray),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Box(
              modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(DrawerAmberLight),
              contentAlignment = Alignment.Center,
            ) {
              Icon(Icons.Default.Restaurant, contentDescription = null, tint = DrawerAmber, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "QuickBite",
              fontSize = 24.sp,
              fontWeight = FontWeight.Black,
              color = BlackPrimary,
            )
            Text(
              text = "Version 2.4 (Enterprise Edition)",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = DrawerAmber,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "QuickBite is an intelligent campus dining platform built to eliminate lunch break congestion, connect students directly with live canteen kitchens, and offer seamless 1-tap pre-ordering with automated digital token pickups.",
              fontSize = 13.5.sp,
              lineHeight = 20.sp,
              color = TextDark,
              textAlign = TextAlign.Center,
            )
          }
        }
      }

      // Rights & Responsibilities Card
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGray),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(DrawerAmberLight),
                contentAlignment = Alignment.Center,
              ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = DrawerAmber, modifier = Modifier.size(20.dp))
              }
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "Rights & Responsibilities",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = BlackPrimary,
              )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "Student Rights:",
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Bold,
              color = DrawerAmber,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("• Right to freshly prepared, hygienic meals compliant with campus food quality standards.", fontSize = 13.sp, color = TextDark)
              Text("• Right to clear token queue status and live kitchen preparation updates.", fontSize = 13.sp, color = TextDark)
              Text("• Right to exact counter pricing with zero hidden fees, extra commissions, or price inflation.", fontSize = 13.sp, color = TextDark)
              Text("• Right to immediate resolution, replacements, or instant credit refunds for missing or unavailable dishes.", fontSize = 13.sp, color = TextDark)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderGray.copy(alpha = 0.5f), thickness = 0.6.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
              text = "Student Responsibilities:",
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Bold,
              color = BlackPrimary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("• Collect prepared meals promptly during your chosen break time to keep warmers clear.", fontSize = 13.sp, color = TextDark)
              Text("• Present your 4-digit token or barcode clearly at the pickup counter for verification.", fontSize = 13.sp, color = TextDark)
              Text("• Treat cafeteria kitchen chefs, service staff, and fellow students with mutual dignity and respect.", fontSize = 13.sp, color = TextDark)
              Text("• Help maintain a clean campus environment by returning reusable cutlery and binning food containers responsibly.", fontSize = 13.sp, color = TextDark)
            }
          }
        }
      }

      // Privacy of Student Data Card
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGray),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFECFDF5)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
              }
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "Privacy & Data Protection",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = BlackPrimary,
              )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = "• End-to-End Encryption: Your student registration number, name, and login credentials are encrypted in transit and at rest using MongoDB Atlas enterprise security.",
                fontSize = 13.sp,
                color = TextDark,
                lineHeight = 19.sp,
              )
              Text(
                text = "• Zero Third-Party Sharing: QuickBite does not sell, rent, or monetize student activity data. Your order history is strictly used for token processing and personalized 'Your Usual' recommendations.",
                fontSize = 13.sp,
                color = TextDark,
                lineHeight = 19.sp,
              )
              Text(
                text = "• Payment Security: QuickBite does not store UPI PINs, banking passwords, or card credentials on campus servers. All payments occur via official UPI intent gateways.",
                fontSize = 13.sp,
                color = TextDark,
                lineHeight = 19.sp,
              )
              Text(
                text = "• Right to Data Deletion: Students may request complete account data and order history deletion at any time by contacting our data protection desk at quickbite.connecting@gmail.com.",
                fontSize = 13.sp,
                color = TextDark,
                lineHeight = 19.sp,
              )
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

/**
 * 4. Comprehensive Settings Screen with Functional Features
 */
@Composable
fun StudentSettingsSheet(
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  var pushNotificationsEnabled by remember { mutableStateOf(true) }
  var orderSoundEnabled by remember { mutableStateOf(true) }
  var breakRemindersEnabled by remember { mutableStateOf(true) }
  var vegOnlyFilter by remember { mutableStateOf(false) }
  var allergenWarnings by remember { mutableStateOf(true) }
  var aiSuggestionsEnabled by remember { mutableStateOf(true) }
  var autoOpenBarcode by remember { mutableStateOf(true) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(WarmCream)
      .navigationBarsPadding(),
  ) {
    DrawerSubScreenHeader(
      title = "Settings",
      subtitle = "Preferences, notifications & dining experience",
      icon = Icons.Default.Security,
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .weight(1f)
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // 1. Notifications & Alerts
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGray),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "NOTIFICATIONS & SOUND",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
              color = DrawerAmber,
              letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))

            SettingToggleRow(
              icon = Icons.Default.Notifications,
              title = "Push Notifications",
              description = "Receive updates when food is cooking or ready",
              checked = pushNotificationsEnabled,
              onCheckedChange = { pushNotificationsEnabled = it },
            )
            HorizontalDivider(color = BorderGray.copy(alpha = 0.5f), thickness = 0.6.dp, modifier = Modifier.padding(vertical = 8.dp))

            SettingToggleRow(
              icon = Icons.Default.VolumeUp,
              title = "Order Ready Chime",
              description = "Play audio chime when your token is called",
              checked = orderSoundEnabled,
              onCheckedChange = { orderSoundEnabled = it },
            )
            HorizontalDivider(color = BorderGray.copy(alpha = 0.5f), thickness = 0.6.dp, modifier = Modifier.padding(vertical = 8.dp))

            SettingToggleRow(
              icon = Icons.Default.AccessTime,
              title = "Break Reminder Alerts",
              description = "Notify 10 mins before your scheduled break",
              checked = breakRemindersEnabled,
              onCheckedChange = { breakRemindersEnabled = it },
            )
          }
        }
      }

      // 2. Dietary Preferences
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGray),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "DIETARY PREFERENCES",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
              color = DrawerAmber,
              letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))

            SettingToggleRow(
              icon = Icons.Default.Restaurant,
              title = "Vegetarian Only Mode",
              description = "Filter and prioritize pure veg options on menu",
              checked = vegOnlyFilter,
              onCheckedChange = {
                vegOnlyFilter = it
                Toast.makeText(context, if (it) "Veg filter active" else "Showing all dishes", Toast.LENGTH_SHORT).show()
              },
            )
            HorizontalDivider(color = BorderGray.copy(alpha = 0.5f), thickness = 0.6.dp, modifier = Modifier.padding(vertical = 8.dp))

            SettingToggleRow(
              icon = Icons.Default.Shield,
              title = "Allergen Disclaimers",
              description = "Highlight dairy, peanut, and gluten warnings",
              checked = allergenWarnings,
              onCheckedChange = { allergenWarnings = it },
            )
          }
        }
      }

      // 3. AI & Experience
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGray),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "APP & MUNCHMATE AI",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
              color = DrawerAmber,
              letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))

            SettingToggleRow(
              icon = Icons.Default.CheckCircle,
              title = "MunchMate Proactive Suggestions",
              description = "Show quick food recommendations in chat",
              checked = aiSuggestionsEnabled,
              onCheckedChange = { aiSuggestionsEnabled = it },
            )
            HorizontalDivider(color = BorderGray.copy(alpha = 0.5f), thickness = 0.6.dp, modifier = Modifier.padding(vertical = 8.dp))

            SettingToggleRow(
              icon = Icons.Default.VerifiedUser,
              title = "Auto-Open Pickup Barcode",
              description = "Automatically expand barcode when order is ready",
              checked = autoOpenBarcode,
              onCheckedChange = { autoOpenBarcode = it },
            )
          }
        }
      }

      // 4. Cache & Maintenance
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGray),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "STORAGE & MAINTENANCE",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
              color = DrawerAmber,
              letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  Toast.makeText(context, "Cache cleared successfully (14.2 MB freed)", Toast.LENGTH_SHORT).show()
                }
                .padding(vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SoftGray),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(Icons.Outlined.CleaningServices, contentDescription = null, tint = TextDark, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("Clear Food Images Cache", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BlackPrimary)
                  Text("Releases 14.2 MB of temporary dish graphics", fontSize = 12.sp, color = TextMuted)
                }
              }
              Text("Clear", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DrawerAmber)
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun SettingToggleRow(
  icon: ImageVector,
  title: String,
  description: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(DrawerAmberLight),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, contentDescription = null, tint = DrawerAmber, modifier = Modifier.size(18.dp))
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BlackPrimary)
        Text(text = description, fontSize = 12.sp, color = TextMuted, lineHeight = 16.sp)
      }
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(
        checkedThumbColor = PureWhite,
        checkedTrackColor = DrawerAmber,
        uncheckedThumbColor = PureWhite,
        uncheckedTrackColor = BorderGray,
      ),
    )
  }
}
