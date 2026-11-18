// OwnerHelpSupportScreen: Jetpack Compose screen for OwnerHelpSupport user interface
package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.DrawerAmberLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream

data class FaqItem(
  val question: String,
  val answer: String,
)

@Composable
fun OwnerHelpSupportScreen(
  canteenName: String = "Campus Canteen",
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val faqs = remember {
    listOf(
      FaqItem(
        question = "How do I verify customer pickup barcodes?",
        answer = "Tap 'Scan QR' on the bottom bar of your dashboard to open the camera scanner. Point the scanner at the 1D barcode on the student's phone. Once scanned, the order is verified in real time and automatically marked as 'Picked Up'."
      ),
      FaqItem(
        question = "What happens when an item runs out of stock?",
        answer = "Go to Menu Management, find the item, and toggle its switch to 'Unavailable'. The switch flips instantly and immediately hides the item from students browsing the canteen menu."
      ),
      FaqItem(
        question = "How are payouts and settlements handled?",
        answer = "Daily canteen settlements are automatically calculated and credited every morning to your registered bank account with complete transaction breakdown in your daily earnings card."
      ),
      FaqItem(
        question = "Can I temporarily close the canteen during rush?",
        answer = "Yes. In the sidebar or settings, toggle the canteen status to 'Closed'. You can select a reason such as 'Kitchen Maintenance' or 'Break' which will be shown to students."
      ),
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("owner_help_support_screen"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(PureWhite)
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack, modifier = Modifier.testTag("owner_help_back_button")) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back",
          tint = TextDark,
          modifier = Modifier.size(22.dp),
        )
      }
      Spacer(Modifier.width(6.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Help & Support",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
        )
        Text(
          text = "Partner Assistance & FAQs",
          fontSize = 12.sp,
          color = TextMuted,
        )
      }
    }

    HorizontalDivider(thickness = 0.8.dp, color = BorderGray.copy(alpha = 0.6f))

    Column(
      modifier = Modifier
        .fillMaxSize()
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(DrawerAmberLight),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.SupportAgent,
                contentDescription = null,
                tint = DrawerAmber,
                modifier = Modifier.size(24.dp),
              )
            }
            Spacer(Modifier.width(12.dp))
            Column {
              Text(
                text = "Canteen Partner Support",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
              Text(
                text = "Available Mon-Fri 7:30 AM – 9:30 PM | Weekends 8:00 AM – 8:00 PM",
                fontSize = 12.sp,
                color = TextMuted,
              )
            }
          }

          Spacer(Modifier.height(14.dp))
          HorizontalDivider(thickness = 0.6.dp, color = BorderGray.copy(alpha = 0.5f))
          Spacer(Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                tint = DrawerAmber,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(8.dp))
              Text(text = "Helpline: +91 98765 43210", fontSize = 13.5.sp, color = TextDark, fontWeight = FontWeight.Medium)
            }
          }

          Spacer(Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                tint = DrawerAmber,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(8.dp))
              Text(text = "Official: quickbite.connecting@gmail.com", fontSize = 13.5.sp, color = TextDark, fontWeight = FontWeight.Medium)
            }
          }

          Spacer(Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                tint = DrawerAmber,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(8.dp))
              Text(text = "Owner Desk: quickbite.connecting@gmail.com", fontSize = 13.5.sp, color = TextDark, fontWeight = FontWeight.Medium)
            }
          }
        }
      }

      Text(
        text = "FREQUENTLY ASKED QUESTIONS",
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp),
      )

      faqs.forEach { faq ->
        FaqCard(item = faq)
      }

      Spacer(Modifier.height(20.dp))
    }
  }
}

@Composable
private fun FaqCard(item: FaqItem) {
  var expanded by remember { mutableStateOf(false) }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .clickable { expanded = !expanded },
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = PureWhite),
    border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = item.question,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          color = TextDark,
          modifier = Modifier.weight(1f),
        )
        Icon(
          imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = null,
          tint = TextMuted,
          modifier = Modifier.size(20.dp),
        )
      }

      if (expanded) {
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = 0.5.dp, color = BorderGray.copy(alpha = 0.4f))
        Spacer(Modifier.height(8.dp))
        Text(
          text = item.answer,
          fontSize = 13.sp,
          color = Color(0xFF4B5563),
          lineHeight = 18.sp,
        )
      }
    }
  }
}

