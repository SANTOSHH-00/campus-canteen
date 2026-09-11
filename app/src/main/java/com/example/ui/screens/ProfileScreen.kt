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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.example.data.BottomNavTab
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.state.CanteenAppState
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.CardSurface
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.DeliveryOrangeLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import kotlinx.coroutines.launch

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.ui.components.AnimatedStudentAvatar
import com.example.ui.components.BlockCanteenSelectorSheet
import com.example.ui.state.UserProfile

@Composable
fun ProfileScreen(
  userName: String = "Alex",
  appState: CanteenAppState,
  onLogout: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()
  var showLoginSheet by remember { mutableStateOf(false) }
  var showAvatarPicker by remember { mutableStateOf(false) }
  var showEditProfileDialog by remember { mutableStateOf(false) }
  var showAboutDialog by remember { mutableStateOf(false) }
  var showAddressesDialog by remember { mutableStateOf(false) }
  var showPaymentMethodsDialog by remember { mutableStateOf(false) }
  var showNotificationsDialog by remember { mutableStateOf(false) }
  var showHelpSupportDialog by remember { mutableStateOf(false) }

  val isGuest = appState.isGuest
  val currentUser = appState.currentUser

  val displayName = if (isGuest) "Guest User" else (currentUser?.name?.ifBlank { "Alex Kumar" } ?: "Alex Kumar")
  val displayCourse = if (isGuest) "Exploring Campus Canteens" else (if (currentUser?.course.isNullOrBlank()) "CSE – 3rd Year" else currentUser?.course ?: "CSE – 3rd Year")
  val displayCollege = if (isGuest) "QuickBite Guest Account" else (if (currentUser?.department.isNullOrBlank()) "Lovely Professional University" else currentUser?.department ?: "Lovely Professional University")
  val displayRollNo = if (isGuest) "Not signed in" else (if (currentUser?.registrationNumber.isNullOrBlank()) "220945" else currentUser?.registrationNumber ?: "220945")
  val activeAvatarId = currentUser?.avatarId ?: "alex"

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFFFCF8F5))
      .statusBarsPadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      // ── 1. Top Student Profile Card (Matching Image 1 Exactly) ─────────────────
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEEFE3)), // Soft peach cream
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
          ) {
            // Left: Avatar + Details
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f)
            ) {
              com.example.ui.components.Student3DAvatar(
                avatarId = activeAvatarId,
                size = 76.dp,
                showCameraBadge = true,
                onClick = { showAvatarPicker = true },
              )

              Spacer(modifier = Modifier.width(14.dp))

              Column {
                Text(
                  text = "Hello,",
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.Medium,
                  color = Color(0xFF6B7280),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = displayName,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1F2937),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(text = "👋", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Course info
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text("🎓", fontSize = 12.sp)
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = displayCourse,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF4B5563),
                  )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // University info
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text("🏛️", fontSize = 12.sp)
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = displayCollege,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6B7280),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                  )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Roll number info
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text("🪪", fontSize = 12.sp)
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = "Roll No: $displayRollNo",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF4B5563),
                  )
                }
              }
            }

            // Right: Edit Profile Pill Button
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(PureWhite.copy(alpha = 0.85f))
                .border(1.dp, Color(0xFFF3D5C0), RoundedCornerShape(16.dp))
                .clickable { showEditProfileDialog = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
              contentAlignment = Alignment.Center,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✏️", fontSize = 11.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Edit Profile",
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF374151),
                )
              }
            }
          }

          // Bottom Slogan matching image: "Good food fuels your dreams! 💫"
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Good food fuels your dreams! 💫",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF9A3412),
              fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // ── 2. Break Time Countdown Card (Matching Image 1 Dark Emerald Card) ──────
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF134E4A)), // Rich deep forest/emerald teal
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Left: Timer numbers
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("☕", fontSize = 14.sp)
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Your Break Time",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFCCFBF1),
              )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
              text = "${appState.breakMinutesLeft} min left",
              fontSize = 24.sp,
              fontWeight = FontWeight.ExtraBold,
              color = PureWhite,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
              text = "${appState.breakStartTime} – ${appState.breakEndTime}",
              fontSize = 12.sp,
              fontWeight = FontWeight.Normal,
              color = Color(0xFF99F6E4),
            )
          }

          // Right: Analog Clock Illustration + Tagline
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Order smart,\nfinish on time! ⏱️",
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFFE6FFFA),
              textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Clock Dial Graphic
            Box(
              modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xFFF97316)) // Warm vibrant orange clock
                .border(2.5.dp, Color(0xFFFDBA74), CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(PureWhite),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "⏰",
                  fontSize = 22.sp,
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // ── 3. Profile Options List (Matching Image 2 Exactly) ─────────────────────
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
          // Row 1: Order History (Orange Document Icon)
          ProfileFeatureRow(
            iconBg = Color(0xFFFFF1EB),
            iconTint = Color(0xFFEA580C),
            iconEmoji = "📄",
            title = "Order History",
            subtitle = "View your past orders and re-order",
            onClick = {
              appState.currentTab = BottomNavTab.ORDERS
            },
          )

          HorizontalDivider(color = Color(0xFFF3F4F6), modifier = Modifier.padding(horizontal = 4.dp))

          // Row 2: Saved Addresses (Blue Pin Icon)
          ProfileFeatureRow(
            iconBg = Color(0xFFEFF6FF),
            iconTint = Color(0xFF2563EB),
            iconEmoji = "📍",
            title = "Saved Addresses",
            subtitle = "Manage your delivery & pickup locations",
            onClick = { showAddressesDialog = true },
          )

          HorizontalDivider(color = Color(0xFFF3F4F6), modifier = Modifier.padding(horizontal = 4.dp))

          // Row 3: Payment Methods (Green Card Icon)
          ProfileFeatureRow(
            iconBg = Color(0xFFF0FDF4),
            iconTint = Color(0xFF16A34A),
            iconEmoji = "💳",
            title = "Payment Methods",
            subtitle = "UPI, Cards and more",
            onClick = { showPaymentMethodsDialog = true },
          )

          HorizontalDivider(color = Color(0xFFF3F4F6), modifier = Modifier.padding(horizontal = 4.dp))

          // Row 4: Notifications (Purple Bell Icon)
          ProfileFeatureRow(
            iconBg = Color(0xFFFAF5FF),
            iconTint = Color(0xFF9333EA),
            iconEmoji = "🔔",
            title = "Notifications",
            subtitle = "Order updates, offers and alerts",
            onClick = { showNotificationsDialog = true },
          )

          HorizontalDivider(color = Color(0xFFF3F4F6), modifier = Modifier.padding(horizontal = 4.dp))

          // Row 5: Help & Support (Teal Headset Icon)
          ProfileFeatureRow(
            iconBg = Color(0xFFF0FDFA),
            iconTint = Color(0xFF0D9488),
            iconEmoji = "🎧",
            title = "Help & Support",
            subtitle = "FAQs, contact us",
            onClick = { showHelpSupportDialog = true },
          )

          HorizontalDivider(color = Color(0xFFF3F4F6), modifier = Modifier.padding(horizontal = 4.dp))

          // Row 6: About QuickBite (Gray Info Icon)
          ProfileFeatureRow(
            iconBg = Color(0xFFF3F4F6),
            iconTint = Color(0xFF4B5563),
            iconEmoji = "ℹ️",
            title = "About QuickBite",
            subtitle = "App version, terms & privacy",
            onClick = { showAboutDialog = true },
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // ── 4. Log In / Log Out Button ─────────────────────────────────────────
      if (isGuest) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BlackPrimary)
            .clickable { showLoginSheet = true }
            .padding(vertical = 14.dp),
          contentAlignment = Alignment.Center,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Login,
              contentDescription = "Log In",
              tint = PureWhite,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Log In to QuickBite",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = PureWhite,
            )
          }
        }
      } else {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFEF2F2))
            .border(1.dp, Color(0xFFFEE2E2), RoundedCornerShape(16.dp))
            .clickable { onLogout() }
            .padding(vertical = 14.dp),
          contentAlignment = Alignment.Center,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Logout,
              contentDescription = "Logout",
              tint = Color(0xFFDC2626),
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Log Out from QuickBite",
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFDC2626),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }

    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 12.dp),
    )

    // ── Dialogs ─────────────────────────────────────────────────────────────
    // 1. Avatar Picker Dialog
    if (showAvatarPicker) {
      com.example.ui.components.StudentAvatarPickerDialog(
        currentAvatarId = activeAvatarId,
        onDismiss = { showAvatarPicker = false },
        onSelectAvatar = { newId ->
          appState.updateAvatar(newId)
          coroutineScope.launch {
            snackbarHostState.showSnackbar("Avatar updated to ${com.example.ui.components.Student3DAvatars.getById(newId).name}!")
          }
        },
      )
    }

    // 2. Edit Profile Dialog
    if (showEditProfileDialog) {
      EditProfileModal(
        initialName = displayName,
        initialCourse = displayCourse,
        initialDepartment = displayCollege,
        initialRollNo = displayRollNo,
        initialPhone = currentUser?.phone ?: "",
        onDismiss = { showEditProfileDialog = false },
        onSave = { name, course, dept, roll, phone ->
          appState.updateStudentProfile(name, course, dept, roll, phone)
          showEditProfileDialog = false
          coroutineScope.launch {
            snackbarHostState.showSnackbar("Profile updated successfully!")
          }
        },
      )
    }

    // 3. About QuickBite Dialog
    if (showAboutDialog) {
      AlertDialog(
        onDismissRequest = { showAboutDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🍔", fontSize = 22.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("About QuickBite", fontWeight = FontWeight.Bold)
          }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "QuickBite Campus Canteen App",
              fontWeight = FontWeight.ExtraBold,
              fontSize = 15.sp,
              color = Color(0xFF1F2937),
            )
            Text(
              text = "Version: 2.4.0 (Production Build)",
              fontSize = 13.sp,
              color = Color(0xFF4B5563),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "QuickBite simplifies dining across campus blocks with live wait times, instant food pre-orders, and digital tokens for zero wait lines.",
              fontSize = 12.5.sp,
              lineHeight = 18.sp,
              color = Color(0xFF6B7280),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "© 2026 QuickBite Technologies. Built with love for campus students.",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFF9CA3AF),
            )
          }
        },
        confirmButton = {
          TextButton(onClick = { showAboutDialog = false }) {
            Text("Close", color = DeliveryOrange, fontWeight = FontWeight.Bold)
          }
        },
        containerColor = PureWhite,
        shape = RoundedCornerShape(20.dp),
      )
    }

    // 4. Saved Addresses Dialog
    if (showAddressesDialog) {
      SimpleFeatureDialog(
        title = "Saved Locations",
        icon = "📍",
        message = "Active Campus: Lovely Professional University\n• Primary: Block 26 & 27 Canteen Pickup Counter\n• Secondary: Block 36 Central Food Court",
        onDismiss = { showAddressesDialog = false }
      )
    }

    // 5. Payment Methods Dialog
    if (showPaymentMethodsDialog) {
      SimpleFeatureDialog(
        title = "Payment Methods",
        icon = "💳",
        message = "Linked Methods:\n• UPI Instant Gateway (Google Pay, PhonePe, Paytm)\n• Campus Card & Net Banking\n• Cash on Counter Token Verification",
        onDismiss = { showPaymentMethodsDialog = false }
      )
    }

    // 6. Notifications Sheet
    if (showNotificationsDialog) {
      com.example.ui.components.ModernNotificationsSheet(
        appState = appState,
        onDismiss = { showNotificationsDialog = false }
      )
    }

    // 7. Help & Support Dialog
    if (showHelpSupportDialog) {
      SimpleFeatureDialog(
        title = "Help & Support",
        icon = "🎧",
        message = "Need assistance with an order?\n\n• Canteen Helpdesk: Counter 1 & 2\n• Email: support@quickbite.campus\n• WhatsApp Support: +91 98765 43210\nHours: 7:00 AM – 10:00 PM Daily",
        onDismiss = { showHelpSupportDialog = false }
      )
    }

    // 8. In-Screen Modal Login Sheet for Guest User
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
            coroutineScope.launch {
              snackbarHostState.showSnackbar("Welcome back, ${profile.name}!")
            }
          },
          isSignUpDefault = false,
        )
      }
    }
  }
}

@Composable
private fun ProfileFeatureRow(
  iconBg: Color,
  iconTint: Color,
  iconEmoji: String,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(vertical = 12.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Round icon badge
    Box(
      modifier = Modifier
        .size(42.dp)
        .clip(CircleShape)
        .background(iconBg),
      contentAlignment = Alignment.Center,
    ) {
      Text(text = iconEmoji, fontSize = 20.sp)
    }

    Spacer(modifier = Modifier.width(14.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1F2937),
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = subtitle,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = Color(0xFF6B7280),
      )
    }

    Icon(
      imageVector = Icons.Default.ChevronRight,
      contentDescription = "Open",
      tint = Color(0xFF9CA3AF),
      modifier = Modifier.size(20.dp),
    )
  }
}

@Composable
private fun SimpleFeatureDialog(
  title: String,
  icon: String,
  message: String,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 22.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
      }
    },
    text = {
      Text(
        text = message,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
        color = Color(0xFF4B5563),
      )
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Done", color = DeliveryOrange, fontWeight = FontWeight.Bold)
      }
    },
    containerColor = PureWhite,
    shape = RoundedCornerShape(20.dp),
  )
}

@Composable
private fun EditProfileModal(
  initialName: String,
  initialCourse: String,
  initialDepartment: String,
  initialRollNo: String,
  initialPhone: String,
  onDismiss: () -> Unit,
  onSave: (name: String, course: String, dept: String, roll: String, phone: String) -> Unit,
) {
  var name by remember { mutableStateOf(initialName) }
  var course by remember { mutableStateOf(initialCourse) }
  var department by remember { mutableStateOf(initialDepartment) }
  var rollNo by remember { mutableStateOf(initialRollNo) }
  var phone by remember { mutableStateOf(initialPhone) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Edit Student Profile",
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = Color(0xFF1F2937),
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        androidx.compose.material3.OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Full Name") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
        )

        androidx.compose.material3.OutlinedTextField(
          value = course,
          onValueChange = { course = it },
          label = { Text("Course & Year (e.g. CSE – 3rd Year)") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
        )

        androidx.compose.material3.OutlinedTextField(
          value = department,
          onValueChange = { department = it },
          label = { Text("University / Department") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
        )

        androidx.compose.material3.OutlinedTextField(
          value = rollNo,
          onValueChange = { rollNo = it },
          label = { Text("Roll No / Reg Number") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
        )

        androidx.compose.material3.OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          label = { Text("Phone Number") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
        )
      }
    },
    confirmButton = {
      androidx.compose.material3.Button(
        onClick = {
          if (name.isNotBlank()) {
            onSave(name.trim(), course.trim(), department.trim(), rollNo.trim(), phone.trim())
          }
        },
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DeliveryOrange),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text("Save Changes", color = PureWhite, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = Color(0xFF6B7280))
      }
    },
    containerColor = PureWhite,
    shape = RoundedCornerShape(22.dp),
  )
}
