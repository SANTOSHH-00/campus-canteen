package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BottomNavTab
import com.example.ui.state.CanteenAppState
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.ui.theme.DarkPillBackground
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.DrawerAmberDark
import com.example.ui.theme.DrawerAmberLight
import com.example.ui.theme.MintFresh
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.GoldenAmberDark
import com.example.ui.theme.GoldenAmberLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted

/**
 * Student Profile Navigation Drawer matching Image 1:
 * - Warm golden amber background filter (#F5B843)
 * - Top-left close button (✕)
 * - Profile info: small avatar icon, "Hello, Justin" (or user display name), Branch, Roll number (no avatar selection)
 * - Break Time Banner: styled with matching layout theme/color, opens minimal clock picker
 * - Menu Items: Favourites, Order History, Payment Methods, FAQ's, Support, About QuickBite, Settings
 * - Log out at the bottom
 */
@Composable
fun StudentProfileDrawer(
  isOpen: Boolean,
  onClose: () -> Unit,
  appState: CanteenAppState,
  userName: String = "Justin",
  onNavigateToTab: (BottomNavTab) -> Unit,
  onLogout: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showTimeDialog by remember { mutableStateOf(false) }
  var showLogoutConfirm by remember { mutableStateOf(false) }
  var activeDialogTitle by remember { mutableStateOf<String?>(null) }
  var activeDialogContent by remember { mutableStateOf<String?>(null) }

  val currentUser = appState.currentUser
  val resolvedName = if (!currentUser?.name.isNullOrBlank()) currentUser!!.name else if (userName.isNotBlank()) userName else "Justin"
  val resolvedBranch = if (!currentUser?.course.isNullOrBlank()) currentUser!!.course else "CSE • B.Tech"
  val resolvedRollNo = if (!currentUser?.registrationNumber.isNullOrBlank()) currentUser!!.registrationNumber else "21BCE1042"

  AnimatedVisibility(
    visible = isOpen,
    enter = fadeIn(animationSpec = tween(280)),
    exit = fadeOut(animationSpec = tween(220)),
    modifier = modifier,
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.5f))
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onClose,
        ),
    ) {
      Box(
        modifier = Modifier
          .align(Alignment.CenterStart)
          .animateEnterExit(
            enter = slideInHorizontally(
              initialOffsetX = { -it },
              animationSpec = tween(320, easing = FastOutSlowInEasing),
            ),
            exit = slideOutHorizontally(
              targetOffsetX = { -it },
              animationSpec = tween(260, easing = FastOutSlowInEasing),
            ),
          ),
      ) {
        Surface(
          modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.82f)
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = {}, // Prevent closing when tapping inside the drawer
            ),
          color = DrawerAmber,
          shape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp),
          shadowElevation = 24.dp,
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .statusBarsPadding()
              .navigationBarsPadding()
              .padding(horizontal = 22.dp, vertical = 18.dp),
          ) {
            // ── 1. Top Close Button (✕) ──────────────────────────────────────
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Start,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(PureWhite.copy(alpha = 0.25f))
                  .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Close Profile",
                  tint = BlackPrimary,
                  modifier = Modifier.size(20.dp),
                )
              }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── 2. Profile Info Section (Matching Image 1 Boy Avatar) ───────
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              // Decent boy student profile image inside white circular frame
              Box(
                modifier = Modifier
                  .size(64.dp)
                  .shadow(6.dp, CircleShape)
                  .clip(CircleShape)
                  .background(PureWhite)
                  .border(2.5.dp, PureWhite, CircleShape),
                contentAlignment = Alignment.Center,
              ) {
                Image(
                  painter = painterResource(id = R.drawable.avatar_alex),
                  contentDescription = "Profile Picture",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                )
              }

              Spacer(modifier = Modifier.width(14.dp))

              Column {
                Text(
                  text = "Hello,",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Medium,
                  color = BlackPrimary.copy(alpha = 0.75f),
                )
                Text(
                  text = resolvedName,
                  fontSize = 21.sp,
                  fontWeight = FontWeight.Black,
                  color = BlackPrimary,
                  maxLines = 1,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = resolvedBranch,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = BlackPrimary.copy(alpha = 0.8f),
                )
                Text(
                  text = "Roll No: $resolvedRollNo",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = BlackPrimary.copy(alpha = 0.7f),
                )
              }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── 3. Upgraded Break Schedule Card (Modern Food App Style) ──────
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(DarkPillBackground)
                .clickable { showTimeDialog = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
              Column(modifier = Modifier.fillMaxWidth()) {
                // Header Row: Badge + Status Dot + Edit Pill
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                      .clip(RoundedCornerShape(8.dp))
                      .background(Color(0xFF2C2C30))
                      .padding(horizontal = 8.dp, vertical = 4.dp),
                  ) {
                    Box(
                      modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MintFresh),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "CAMPUS BREAK",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.ExtraBold,
                      color = DrawerAmber,
                      letterSpacing = 0.5.sp,
                    )
                  }

                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                      .clip(RoundedCornerShape(10.dp))
                      .background(DrawerAmber.copy(alpha = 0.2f))
                      .padding(horizontal = 8.dp, vertical = 4.dp),
                  ) {
                    Text(
                      text = "Edit ⏱️",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = PureWhite,
                    )
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Middle Row: Big clean schedule timings
                Text(
                  text = "${appState.breakStartTime} – ${appState.breakEndTime}",
                  fontSize = 17.sp,
                  fontWeight = FontWeight.Black,
                  color = PureWhite,
                  letterSpacing = (-0.3).sp,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Row: Sleek countdown badge with icon
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF28282C))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = DrawerAmber,
                    modifier = Modifier.size(14.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "${appState.breakMinutesLeft} mins remaining in break",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite.copy(alpha = 0.9f),
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(14.dp))


            // ── 4. Scrollable Drawer Menu Items (Image 1 Style) ──────────────
            Column(
              modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              DrawerMenuItem(
                icon = Icons.Outlined.FavoriteBorder,
                title = "Favorites",
                onClick = {
                  activeDialogTitle = "Favorites"
                  activeDialogContent = "You have ${appState.favoriteItemIds.size} favorite items saved in QuickBite."
                },
              )

              DrawerMenuItem(
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                title = "Order History",
                onClick = {
                  onClose()
                  onNavigateToTab(BottomNavTab.ORDERS)
                },
              )

              DrawerMenuItem(
                icon = Icons.Outlined.AccountBalanceWallet,
                title = "Payment Methods",
                onClick = {
                  activeDialogTitle = "Payment Methods"
                  activeDialogContent = "Available Methods:\n• UPI (GPay, PhonePe, Paytm)\n• Campus Meal Card (Balance: ₹450)\n• Cash on Counter"
                },
              )

              DrawerMenuItem(
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                title = "FAQ's",
                onClick = {
                  activeDialogTitle = "Frequently Asked Questions"
                  activeDialogContent = "Q: How do I collect my food?\nA: Show your 4-digit order token at the canteen pickup counter.\n\nQ: Can I cancel an order?\nA: Orders can be cancelled within 1 minute of placing before preparation starts."
                },
              )


              DrawerMenuItem(
                icon = Icons.Outlined.SupportAgent,
                title = "Support",
                onClick = {
                  activeDialogTitle = "Support & Help Desk"
                  activeDialogContent = "Need help with your canteen order?\n\n• Email: support@quickbite.campus.in\n• Helpline: +91 98765 43210\n• Operating Hours: 8:00 AM – 9:00 PM"
                },
              )

              DrawerMenuItem(
                icon = Icons.Outlined.Info,
                title = "About QuickBite",
                onClick = {
                  activeDialogTitle = "About QuickBite"
                  activeDialogContent = "QuickBite v2.4\nSmart Campus Canteen Food Ordering & Queue Management System."
                },
              )

              DrawerMenuItem(
                icon = Icons.Outlined.Settings,
                title = "Settings",
                onClick = {
                  activeDialogTitle = "App Settings"
                  activeDialogContent = "Preferences:\n• Notification alerts: ON\n• Sound effects: ON\n• Dietary filter: All Items\n• Theme: Warm Amber"
                },
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── 5. Log Out at the Bottom ────────────────────────────────────
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PureWhite.copy(alpha = 0.25f))
                .clickable { showLogoutConfirm = true }
                .padding(horizontal = 16.dp, vertical = 13.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = "Logout",
                tint = BlackPrimary,
                modifier = Modifier.size(22.dp),
              )
              Spacer(modifier = Modifier.width(14.dp))
              Text(
                text = "Log Out",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = BlackPrimary,
              )
            }
          }
        }
      }
    }
  }

  // ── Minimal Break Clock Dialog ─────────────────────────────────────────────
  if (showTimeDialog) {
    MinimalBreakClockDialog(
      initialStart = appState.breakStartTime,
      initialEnd = appState.breakEndTime,
      onDismiss = { showTimeDialog = false },
      onConfirm = { start, end, minLeft ->
        appState.updateBreakSchedule(start, end, minLeft)
        showTimeDialog = false
      },
    )
  }

  // ── Info / Sheet Dialog for Drawer Options ─────────────────────────────────
  if (activeDialogTitle != null) {
    AlertDialog(
      onDismissRequest = { activeDialogTitle = null },
      title = {
        Text(
          text = activeDialogTitle ?: "",
          fontWeight = FontWeight.ExtraBold,
          fontSize = 18.sp,
          color = BlackPrimary,
        )
      },
      text = {
        Text(
          text = activeDialogContent ?: "",
          fontSize = 14.sp,
          color = TextDark,
          lineHeight = 20.sp,
        )
      },
      confirmButton = {
        Button(
          onClick = { activeDialogTitle = null },
          colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text("OK", fontWeight = FontWeight.Bold, color = PureWhite)
        }
      },
      containerColor = PureWhite,
      shape = RoundedCornerShape(22.dp),
    )
  }

  // ── Logout Confirmation Dialog ─────────────────────────────────────────────
  if (showLogoutConfirm) {
    AlertDialog(
      onDismissRequest = { showLogoutConfirm = false },
      title = {
        Text(
          text = "Confirm Log Out",
          fontWeight = FontWeight.ExtraBold,
          fontSize = 18.sp,
          color = BlackPrimary,
        )
      },
      text = {
        Text(
          text = "Are you sure you want to log out of QuickBite?",
          fontSize = 14.sp,
          color = TextDark,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showLogoutConfirm = false
            onClose()
            onLogout()
          },
          colors = ButtonDefaults.buttonColors(containerColor = BlackPrimary),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text("Log Out", fontWeight = FontWeight.Bold, color = PureWhite)
        }
      },
      dismissButton = {
        TextButton(onClick = { showLogoutConfirm = false }) {
          Text("Cancel", fontWeight = FontWeight.Bold, color = TextMuted)
        }
      },
      containerColor = PureWhite,
      shape = RoundedCornerShape(22.dp),
    )
  }
}

/**
 * Individual Drawer Item with Clean Material Outline Vector Icon & High Contrast Text
 */
@Composable
private fun DrawerMenuItem(
  icon: ImageVector,
  title: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {

    Icon(
      imageVector = icon,
      contentDescription = title,
      tint = BlackPrimary,
      modifier = Modifier.size(22.dp),
    )
    Spacer(modifier = Modifier.width(16.dp))
    Text(
      text = title,
      fontSize = 15.sp,
      fontWeight = FontWeight.Bold,
      color = BlackPrimary,
      modifier = Modifier.weight(1f),
    )
    Icon(
      imageVector = Icons.Default.ChevronRight,
      contentDescription = null,
      tint = BlackPrimary.copy(alpha = 0.5f),
      modifier = Modifier.size(18.dp),
    )
  }
}
