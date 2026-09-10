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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.DeliveryOrangeLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream

@Composable
fun RoleSelectionScreen(
  onSelectStudent: () -> Unit,
  onSelectOwner: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream)
      .statusBarsPadding()
      .navigationBarsPadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 22.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(modifier = Modifier.height(12.dp))

      // ── MESSQ Top Brand Badge ──────────────────────────────────────────────
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
          .clip(RoundedCornerShape(30.dp))
          .background(BlackPrimary)
          .padding(horizontal = 16.dp, vertical = 8.dp)
          .testTag("brand_badge")
      ) {
        Box(
          modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(DeliveryOrange),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.Fastfood,
            contentDescription = null,
            tint = PureWhite,
            modifier = Modifier.size(14.dp),
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "QUICKBITE",
          fontSize = 14.sp,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 2.sp,
          color = PureWhite,
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      // ── Main Header Title ──────────────────────────────────────────────────
      Text(
        text = "Select Account Type",
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        color = TextDark,
        textAlign = TextAlign.Center,
        lineHeight = 34.sp,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "Choose your role to access your personalized campus dining experience",
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = TextMuted,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp,
        modifier = Modifier.padding(horizontal = 16.dp),
      )

      Spacer(modifier = Modifier.height(32.dp))

      // ── Role 1: Student Account Card ───────────────────────────────────────
      RoleOptionCard(
        badgeTitle = "STUDENT PORTAL",
        badgeColor = DeliveryOrange,
        icon = Icons.Default.School,
        iconBackground = DeliveryOrangeLight,
        iconTint = DeliveryOrange,
        title = "Campus Student",
        subtitle = "Pre-order hot meals, skip 20-min lunch rush lines, and pick up your order with a live counter token.",
        perks = listOf(
          "Pre-order before class breaks",
          "Real-time token & counter pickup alerts",
          "Order history, favorites & fast reordering",
        ),
        buttonText = "Continue as Student",
        onClick = onSelectStudent,
        testTag = "role_student_button",
      )

      Spacer(modifier = Modifier.height(22.dp))

      // ── Role 2: Canteen Owner Account Card ──────────────────────────────────
      RoleOptionCard(
        badgeTitle = "CANTEEN OWNER / STAFF",
        badgeColor = BlackPrimary,
        icon = Icons.Default.Storefront,
        iconBackground = SoftGray,
        iconTint = BlackPrimary,
        title = "Canteen Owner / Staff",
        subtitle = "Manage live kitchen incoming orders, call out tokens, update item availability, and control break rush.",
        perks = listOf(
          "Live digital token queue & preparation dispatch",
          "Toggle item stock & menu price updates",
          "Open / close canteen & rush hour broadcast",
        ),
        buttonText = "Continue as Owner",
        onClick = onSelectOwner,
        testTag = "role_owner_button",
      )

      Spacer(modifier = Modifier.height(30.dp))

      // ── Bottom Campus Disclaimer ───────────────────────────────────────────
      Text(
        text = "QuickBite Campus Canteen Platform · Smart Dining Hub",
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Medium,
        color = TextMuted,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun RoleOptionCard(
  badgeTitle: String,
  badgeColor: Color,
  icon: ImageVector,
  iconBackground: Color,
  iconTint: Color,
  title: String,
  subtitle: String,
  perks: List<String>,
  buttonText: String,
  onClick: () -> Unit,
  testTag: String,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .shadow(6.dp, RoundedCornerShape(24.dp), spotColor = Color(0x1A000000))
      .clip(RoundedCornerShape(24.dp))
      .background(PureWhite)
      .border(1.dp, BorderGray, RoundedCornerShape(24.dp))
      .clickable(role = Role.Button, onClick = onClick)
      .testTag(testTag)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Icon Badge
        Box(
          modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(iconBackground),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(28.dp),
          )
        }

        // Category Tag
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(badgeColor)
            .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
          Text(
            text = badgeTitle,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = PureWhite,
            letterSpacing = 0.8.sp,
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = TextDark,
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = subtitle,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        color = TextMuted,
        lineHeight = 19.sp,
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Perks Checklist
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        perks.forEach { perk ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = AccentGreen,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = perk,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.Medium,
              color = TextDark,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Action Button
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .clip(RoundedCornerShape(24.dp))
          .background(BlackPrimary),
        contentAlignment = Alignment.Center,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Text(
            text = buttonText,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = PureWhite,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = PureWhite,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}
