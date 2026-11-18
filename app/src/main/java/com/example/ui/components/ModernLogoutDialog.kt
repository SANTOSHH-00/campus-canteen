// ModernLogoutDialog: Reusable Jetpack Compose UI component
package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.BorderGray
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Refined theme-aligned color accents for the logout confirmation dialog
private val LogoutBadgeBackground = Color(0xFFFDF2F2)
private val LogoutBadgeBorder = Color(0xFFF9DADB)
private val LogoutAccent = Color(0xFFBA3838) // Muted elegant crimson, never aggressive neon red
private val AccountPillBg = Color(0xFFF8F5EE) // Subtle warm cream surface
private val CancelButtonBg = Color(0xFFF4EDE4) // Soft cream secondary button container

/**
 * Modern, premium Logout Confirmation Dialog for QuickBite.
 *
 * Design characteristics:
 * - Clean white card surface with rounded corners and subtle elevation.
 * - Outlined logout icon centered inside a delicate circular badge.
 * - Bold title and clear, reassuring secondary explanation.
 * - Subtle, non-action-styled account pill dynamically showing signed-in user name.
 * - Symmetrical, accessible Cancel (secondary) and Log Out (muted destructive) action buttons.
 * - Smooth entrance and exit animations (fade + scale).
 * - Debounced action handling to prevent repeated requests during logout.
 */
@Composable
fun ModernLogoutDialog(
  userName: String = "Student",
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  var isVisible by remember { mutableStateOf(false) }
  var isLoggingOut by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    isVisible = true
  }

  val handleDismiss: () -> Unit = {
    if (!isLoggingOut) {
      coroutineScope.launch {
        isVisible = false
        delay(140)
        onDismiss()
      }
    }
  }

  val handleConfirm: () -> Unit = {
    if (!isLoggingOut) {
      isLoggingOut = true
      onConfirm()
    }
  }

  Dialog(
    onDismissRequest = handleDismiss,
    properties = DialogProperties(
      dismissOnBackPress = !isLoggingOut,
      dismissOnClickOutside = !isLoggingOut,
    ),
  ) {
    AnimatedVisibility(
      visible = isVisible,
      enter = fadeIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)) +
              scaleIn(initialScale = 0.92f, animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)),
      exit = fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)) +
             scaleOut(targetScale = 0.94f, animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)),
    ) {
      Surface(
        shape = RoundedCornerShape(24.dp),
        color = PureWhite,
        shadowElevation = 10.dp,
        tonalElevation = 0.dp,
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 350.dp)
          .padding(horizontal = 6.dp),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          // 1. Redesigned Outline Logout Icon Badge
          Box(
            modifier = Modifier
              .size(52.dp)
              .clip(CircleShape)
              .background(LogoutBadgeBackground)
              .border(1.dp, LogoutBadgeBorder, CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.Logout,
              contentDescription = "Log out",
              tint = LogoutAccent,
              modifier = Modifier.size(24.dp),
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          // 2. Heading
          Text(
            text = "Log out of QuickBite?",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.2).sp,
          )

          Spacer(modifier = Modifier.height(8.dp))

          // 3. Confirmation Description
          Text(
            text = "Are you sure you want to log out? You'll need to sign in again to place orders and track your food.",
            fontSize = 13.5.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
          )

          Spacer(modifier = Modifier.height(14.dp))

          // 4. Current Signed-in Account Info Element
          val cleanName = userName.trim()
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(AccountPillBg)
              .border(0.8.dp, BorderGray.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
              .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(13.dp),
              )
              Spacer(modifier = Modifier.width(5.dp))
              if (cleanName.isNotBlank()) {
                Text(
                  text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = TextMuted, fontWeight = FontWeight.Normal)) {
                      append("Signed in as ")
                    }
                    withStyle(style = SpanStyle(color = TextDark, fontWeight = FontWeight.Bold)) {
                      append(cleanName)
                    }
                  },
                  fontSize = 12.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              } else {
                Text(
                  text = "Signed in to QuickBite",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextDark,
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(24.dp))

          // 5. Action Buttons: [ Cancel ] [ Log Out ]
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            // Cancel (Secondary Action)
            Button(
              onClick = handleDismiss,
              enabled = !isLoggingOut,
              modifier = Modifier
                .weight(1f)
                .height(44.dp),
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = CancelButtonBg,
                contentColor = TextDark,
                disabledContainerColor = CancelButtonBg.copy(alpha = 0.6f),
                disabledContentColor = TextDark.copy(alpha = 0.5f),
              ),
              elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
              ),
              contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
              Text(
                text = "Cancel",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark,
              )
            }

            // Log Out (Primary Destructive Action)
            Button(
              onClick = handleConfirm,
              enabled = !isLoggingOut,
              modifier = Modifier
                .weight(1f)
                .height(44.dp),
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = LogoutAccent,
                contentColor = PureWhite,
                disabledContainerColor = LogoutAccent.copy(alpha = 0.65f),
                disabledContentColor = PureWhite.copy(alpha = 0.8f),
              ),
              elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 1.dp,
              ),
              contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
              if (isLoggingOut) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  color = PureWhite,
                  strokeWidth = 2.dp,
                )
              } else {
                Text(
                  text = "Log Out",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = PureWhite,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun ModernLogoutDialogPreview() {
  MyApplicationTheme {
    ModernLogoutDialog(
      userName = "Santosh",
      onConfirm = {},
      onDismiss = {},
    )
  }
}
