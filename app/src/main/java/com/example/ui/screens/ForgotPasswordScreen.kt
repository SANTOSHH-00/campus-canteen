package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.WarmCream
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.PasswordResetState
import kotlinx.coroutines.launch

private val PillBackground = Color(0xFFF3EFEA)
private val BorderMuted = Color(0xFFE5DECE)

@Composable
fun ForgotPasswordScreen(
  onBackToLogin: () -> Unit,
  viewModel: AuthViewModel = viewModel(),
  initialEmail: String = "",
) {
  var email by remember { mutableStateOf(initialEmail) }
  val isLoading by viewModel.loading.collectAsState()
  val errorMessage by viewModel.errorMessage.collectAsState()
  val successMessage by viewModel.successMessage.collectAsState()
  val resetState by viewModel.resetState.collectAsState()

  val keyboardController = LocalSoftwareKeyboardController.current
  val coroutineScope = rememberCoroutineScope()
  val scrollState = rememberScrollState()

  DisposableEffect(Unit) {
    onDispose {
      viewModel.clearState()
    }
  }

  val isSuccess = resetState is PasswordResetState.EmailSent || successMessage != null

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(WarmCream)
      .statusBarsPadding()
      .navigationBarsPadding(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(horizontal = 24.dp),
    ) {
      Spacer(modifier = Modifier.height(16.dp))

      // Top bar with Back Button
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(PureWhite)
            .border(1.dp, BorderMuted, CircleShape)
            .clickable(
              role = Role.Button,
              onClick = onBackToLogin,
            )
            .testTag("forgot_password_back_button"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back to Login",
            tint = TextDark,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(28.dp))

      // Main Card Container (QuickBite style)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(6.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = 0.08f))
          .clip(RoundedCornerShape(28.dp))
          .background(PureWhite)
          .border(1.dp, BorderMuted.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
          .padding(26.dp),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
        ) {
          // Card Header: Sleek dark icon badge on left + Luxury Account Recovery chip on right
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(54.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(BlackPrimary),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.LockReset,
                contentDescription = null,
                tint = PureWhite,
                modifier = Modifier.size(26.dp),
              )
            }

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF18181B))
                .border(1.dp, Color(0xFF27272A), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Box(
                  modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981)),
                )
                Text(
                  text = "ACCOUNT RECOVERY",
                  fontSize = 10.5.sp,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 1.2.sp,
                  color = PureWhite,
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(18.dp))

          // Title
          Text(
            text = "Forgot Password?",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextDark,
            modifier = Modifier.testTag("forgot_password_title"),
          )

          Spacer(modifier = Modifier.height(8.dp))

          // Description
          Text(
            text = "Enter your registered email and we'll send you a password reset link.",
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Color(0xFF6B7280),
            modifier = Modifier.testTag("forgot_password_description"),
          )

          Spacer(modifier = Modifier.height(24.dp))

          // Success state alert
          AnimatedVisibility(
            visible = isSuccess,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut(),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFECFDF5))
                .border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(16.dp))
                .padding(16.dp)
                .testTag("forgot_password_success_banner"),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = Color(0xFF059669),
                  modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = "Reset link sent!",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF065F46),
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Check your inbox. If you don't see it, check your spam folder.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = Color(0xFF047857),
              )
            }
          }

          if (isSuccess) {
            Spacer(modifier = Modifier.height(20.dp))
          }

          // Error Banner
          AnimatedVisibility(
            visible = errorMessage != null && !isSuccess,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut(),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF9F6F0))
                .border(1.dp, BorderMuted, RoundedCornerShape(16.dp))
                .padding(14.dp)
                .testTag("forgot_password_error_banner"),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(BlackPrimary),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.ErrorOutline,
                  contentDescription = null,
                  tint = PureWhite,
                  modifier = Modifier.size(18.dp),
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = errorMessage ?: "",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp,
                color = TextDark,
                modifier = Modifier
                  .weight(1f)
                  .testTag("forgot_password_error_text"),
              )
            }
          }

          if (errorMessage != null && !isSuccess) {
            Spacer(modifier = Modifier.height(16.dp))
          }

          // Email Input field
          Text(
            text = "REGISTERED EMAIL",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color(0xFF6B7280),
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
          )

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .clip(RoundedCornerShape(26.dp))
              .background(PillBackground)
              .border(1.dp, BorderMuted, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.CenterStart,
          ) {
            TextField(
              value = email,
              onValueChange = {
                email = it
                if (errorMessage != null) viewModel.clearErrorMessage()
              },
              placeholder = {
                Text("name@college.edu or personal email", color = Color(0xFF9CA3AF), fontSize = 14.sp)
              },
              leadingIcon = {
                Icon(
                  Icons.Default.Email,
                  contentDescription = null,
                  tint = Color(0xFF9CA3AF),
                  modifier = Modifier.size(20.dp),
                )
              },
              singleLine = true,
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
              ),
              keyboardActions = KeyboardActions(
                onDone = {
                  keyboardController?.hide()
                  com.example.data.session.SessionManager.savePendingResetEmail(email)
                  coroutineScope.launch {
                    viewModel.sendPasswordResetEmail(email)
                  }
                },
              ),
              colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = TextDark,
                unfocusedTextColor = TextDark,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("forgot_password_email_input"),
            )
          }

          Spacer(modifier = Modifier.height(26.dp))

          // Action Button: Send Reset Link (or Resend if already sent)
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp)
              .shadow(4.dp, RoundedCornerShape(27.dp))
              .clip(RoundedCornerShape(27.dp))
              .background(BlackPrimary)
              .clickable(
                role = Role.Button,
                enabled = !isLoading,
                onClick = {
                  keyboardController?.hide()
                  com.example.data.session.SessionManager.savePendingResetEmail(email)
                  coroutineScope.launch {
                    viewModel.sendPasswordResetEmail(email)
                  }
                },
              )
              .testTag("send_reset_link_button"),
            contentAlignment = Alignment.Center,
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                color = PureWhite,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
              )
            } else {
              Text(
                text = if (isSuccess) "Resend Reset Link" else "Send Reset Link",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = PureWhite,
              )
            }
          }

          Spacer(modifier = Modifier.height(20.dp))

          // Back to Login text button
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clickable(
                role = Role.Button,
                onClick = onBackToLogin,
              )
              .padding(vertical = 8.dp)
              .testTag("back_to_login_button"),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "← Back to Login",
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF4B5563),
              textAlign = TextAlign.Center,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}
