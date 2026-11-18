// StudentProfileDrawer: Reusable Jetpack Compose UI component
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
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import com.example.ui.screens.LoginContent
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
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
  var showLogoutConfirm by remember { mutableStateOf(false) }
  var showLoginSheet by remember { mutableStateOf(false) }
  var activeSubScreen by remember { mutableStateOf(DrawerSubScreen.NONE) }
  var activeDialogTitle by remember { mutableStateOf<String?>(null) }
  var activeDialogContent by remember { mutableStateOf<String?>(null) }

  val isGuest = appState.isGuest
  val currentUser = appState.currentUser
  val resolvedName = if (isGuest) "Guest User" else if (!currentUser?.name.isNullOrBlank()) currentUser!!.name else if (userName.isNotBlank()) userName else "Justin"
  val resolvedBranch = if (isGuest) "Welcome to QuickBite" else if (!currentUser?.course.isNullOrBlank()) currentUser!!.course else "CSE • B.Tech"
  val resolvedRollNo = if (isGuest) "Log in to track orders" else if (!currentUser?.registrationNumber.isNullOrBlank()) "Roll No: ${currentUser!!.registrationNumber}" else "Roll No: 21BCE1042"

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

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                  onClose()
                  appState.openProfileScreen()
                }
                .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              // Theme-matched student profile icon
              Box(
                modifier = Modifier
                  .size(64.dp)
                  .shadow(6.dp, CircleShape)
                  .clip(CircleShape)
                  .background(DrawerAmberLight)
                  .border(2.5.dp, PureWhite, CircleShape),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Person,
                  contentDescription = "Profile Picture",
                  tint = DrawerAmber,
                  modifier = Modifier.size(36.dp),
                )
              }

              Spacer(modifier = Modifier.width(14.dp))

              Column {
                Text(
                  text = "Hello,",
                  fontSize = 16.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = BlackPrimary.copy(alpha = 0.8f),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = resolvedName,
                  fontSize = 24.sp,
                  fontWeight = FontWeight.Black,
                  color = BlackPrimary,
                  maxLines = 1,
                  overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
              }
            }

            Spacer(modifier = Modifier.height(18.dp))


            Column(
              modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              DrawerMenuItem(
                icon = Icons.Filled.Person,
                title = "My Profile",
                onClick = {
                  onClose()
                  appState.openProfileScreen()
                },
              )

              DrawerMenuItem(
                icon = Icons.Outlined.FavoriteBorder,
                title = "Favorites",
                onClick = {
                  onClose()
                  appState.openFavoritesScreen()
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

              androidx.compose.material3.HorizontalDivider(
                thickness = 0.7.dp,
                color = PureWhite.copy(alpha = 0.25f),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              )

              DrawerMenuItem(
                icon = Icons.Outlined.AccountBalanceWallet,
                title = "Payment Methods",
                onClick = {
                  activeDialogTitle = "Payment Methods"
                  activeDialogContent = "Available Methods:\n• Razorpay (UPI, GPay, PhonePe, Cards)\n• Campus Meal Card\n• Cash at Counter"
                },
              )

              DrawerMenuItem(
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                title = "FAQ's",
                onClick = {
                  activeSubScreen = DrawerSubScreen.FAQS
                },
              )

              DrawerMenuItem(
                icon = Icons.Outlined.SupportAgent,
                title = "Support",
                onClick = {
                  activeSubScreen = DrawerSubScreen.SUPPORT
                },
              )

              androidx.compose.material3.HorizontalDivider(
                thickness = 0.7.dp,
                color = PureWhite.copy(alpha = 0.25f),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              )

              DrawerMenuItem(
                icon = Icons.Outlined.Info,
                title = "About QuickBite",
                onClick = {
                  activeSubScreen = DrawerSubScreen.ABOUT
                },
              )

              DrawerMenuItem(
                icon = Icons.Outlined.Settings,
                title = "Settings",
                onClick = {
                  activeSubScreen = DrawerSubScreen.SETTINGS
                },
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isGuest) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(16.dp))
                  .background(BlackPrimary)
                  .clickable { showLoginSheet = true }
                  .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Login,
                  contentDescription = "Log In",
                  tint = PureWhite,
                  modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                  text = "Log In to QuickBite",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = PureWhite,
                )
              }
            } else {
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
  }

  // Full Rich Screens for FAQs, Support, About QuickBite, and Settings
  when (activeSubScreen) {
    DrawerSubScreen.FAQS -> {
      StudentFaqSheet(onBack = { activeSubScreen = DrawerSubScreen.NONE })
    }
    DrawerSubScreen.SUPPORT -> {
      StudentSupportSheet(onBack = { activeSubScreen = DrawerSubScreen.NONE })
    }
    DrawerSubScreen.ABOUT -> {
      StudentAboutSheet(onBack = { activeSubScreen = DrawerSubScreen.NONE })
    }
    DrawerSubScreen.SETTINGS -> {
      StudentSettingsSheet(onBack = { activeSubScreen = DrawerSubScreen.NONE })
    }
    else -> {}
  }

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

  if (showLogoutConfirm) {
    ModernLogoutDialog(
      userName = resolvedName,
      onConfirm = {
        showLogoutConfirm = false
        onClose()
        onLogout()
      },
      onDismiss = { showLogoutConfirm = false },
    )
  }

  if (showLoginSheet) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.55f))
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = { showLoginSheet = false },
        ),
      contentAlignment = Alignment.BottomCenter,
    ) {
      LoginContent(
        onDismiss = { showLoginSheet = false },
        onLoginSuccess = { profile ->
          appState.loginUser(profile)
          showLoginSheet = false
        },
        isSignUpDefault = false,
      )
    }
  }
}

/**
 * Individual Drawer Item with Clean Material Outline Vector Icon & High Contrast Text (Sleek Flat List)
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
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(PureWhite.copy(alpha = 0.22f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = BlackPrimary,
        modifier = Modifier.size(20.dp),
      )
    }
    Spacer(modifier = Modifier.width(14.dp))
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
      tint = BlackPrimary.copy(alpha = 0.45f),
      modifier = Modifier.size(18.dp),
    )
  }
}
