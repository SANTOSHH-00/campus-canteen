package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.firebase.CanteenDocument
import com.example.data.firebase.OwnerDocument
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.viewmodel.CanteenAllocationUiState
import com.example.ui.viewmodel.OwnerAuthStep
import com.example.ui.viewmodel.OwnerLoginUiState
import com.example.ui.viewmodel.OwnerViewModel
import kotlinx.coroutines.delay

private val AmberBrand = Color(0xFFF5A623)
private val TextDark = Color(0xFF1F2937)
private val PillBackground = Color(0xFFF3F4F6)
private val BorderMuted = Color(0xFFE5E7EB)

/**
 * Modern Canteen Owner Login & Verification Screen.
 * Visual design matches Image 3 aesthetic (amber header, clean white card).
 *
 * Flow:
 * 1. Step 1: Owner Email & Password authentication.
 * 2. Step 2: Phone number OTP Re-verification.
 * 3. Step 3: Mini Popup Dialog for first-time owner canteen allocation.
 *    (Subsequent logins permanently skip this dialog).
 */
@Composable
fun OwnerLoginScreen(
  viewModel: OwnerViewModel,
  onBack: () -> Unit,
  onLoginSuccess: (OwnerDocument) -> Unit,
  onNavigateToForgotPassword: () -> Unit = {},
  initialEmail: String = "",
  onNavigateToOtp: ((String) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  var email by rememberSaveable { mutableStateOf(initialEmail) }
  var password by rememberSaveable { mutableStateOf("") }
  var passwordVisible by rememberSaveable { mutableStateOf(false) }

  // Email OTP state
  var registeredEmail by rememberSaveable { mutableStateOf(initialEmail) }
  var otpCode by rememberSaveable { mutableStateOf("") }
  var resendCountdown by remember { mutableIntStateOf(30) }
  val otpFocusRequester = remember { FocusRequester() }

  val loginState by viewModel.loginState.collectAsState()
  val currentStep by viewModel.currentStep.collectAsState()
  val allocationState by viewModel.allocationState.collectAsState()
  val keyboardController = LocalSoftwareKeyboardController.current

  // Show First-Time Mini Popup Canteen Selection
  var showAllocationMiniPopup by remember { mutableStateOf(false) }

  LaunchedEffect(initialEmail) {
    if (initialEmail.isNotBlank()) {
      if (email.isBlank()) email = initialEmail
      if (registeredEmail.isBlank()) registeredEmail = initialEmail
    }
  }

  LaunchedEffect(loginState) {
    if (loginState is OwnerLoginUiState.RequiresOtp) {
      val req = loginState as OwnerLoginUiState.RequiresOtp
      val targetEmail = when {
        req.email.isNotBlank() -> req.email
        registeredEmail.isNotBlank() -> registeredEmail
        email.isNotBlank() -> email
        else -> ""
      }
      if (targetEmail.isNotBlank()) {
        registeredEmail = targetEmail
      }
      // Ensure UI immediately transitions to the OTP screen
      viewModel.goToEmailOtp(targetEmail)
      onNavigateToOtp?.invoke(targetEmail)
    }
    if (loginState is OwnerLoginUiState.Success) {
      val success = loginState as OwnerLoginUiState.Success
      if (success.needsFirstTimeAllocation) {
        showAllocationMiniPopup = true
      } else {
        onLoginSuccess(success.owner)
      }
    }
  }

  // Resend countdown timer for OTP & auto-focus
  LaunchedEffect(currentStep) {
    if (currentStep == OwnerAuthStep.EMAIL_OTP || currentStep == OwnerAuthStep.PHONE_OTP) {
      delay(300)
      try {
        otpFocusRequester.requestFocus()
      } catch (e: Exception) {}
      resendCountdown = 30
      while (resendCountdown > 0) {
        delay(1000)
        resendCountdown--
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(AmberBrand)
      .statusBarsPadding()
      .navigationBarsPadding()
      .imePadding()
      .testTag("owner_login_screen")
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
    ) {
      // ── Top Header with Back Navigation & Staff Portal Badge ──────────
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f))
            .clickable(role = Role.Button, onClick = {
              if (currentStep == OwnerAuthStep.EMAIL_OTP || currentStep == OwnerAuthStep.PHONE_OTP) {
                viewModel.backToCredentials()
              } else {
                onBack()
              }
            })
            .testTag("owner_login_back_button"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = TextDark,
            modifier = Modifier.size(20.dp),
          )
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
          Text(
            text = "STAFF PORTAL",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = TextDark,
          )
        }
      }

      // Title & Subtitle
      val isOtpStep = (currentStep == OwnerAuthStep.EMAIL_OTP || currentStep == OwnerAuthStep.PHONE_OTP)
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 10.dp)
      ) {
        Text(
          text = if (isOtpStep) "Email Verification" else "Owner Sign In",
          fontSize = 32.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
          modifier = Modifier.testTag("owner_title_text"),
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = if (isOtpStep)
            "Verify your registered email for campus kitchen security."
          else
            "Sign in with your dedicated canteen owner email to manage live kitchen queues.",
          fontSize = 13.5.sp,
          lineHeight = 19.sp,
          color = TextDark.copy(alpha = 0.85f),
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // ── White Curved Sheet Container (Image 3 Right layout) ───────────────
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
          .background(PureWhite)
          .padding(horizontal = 24.dp, vertical = 26.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          // ── STEP 1: EMAIL & PASSWORD ─────────────────────────────────────
          if (currentStep == OwnerAuthStep.CREDENTIALS) {
            Column(modifier = Modifier.fillMaxWidth()) {
              Text(
                text = "OWNER EMAIL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
              )
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(52.dp)
                  .clip(RoundedCornerShape(26.dp))
                  .background(PillBackground),
                contentAlignment = Alignment.CenterStart,
              ) {
                TextField(
                  value = email,
                  onValueChange = { email = it },
                  placeholder = { Text("enter your email", color = Color(0xFF9CA3AF), fontSize = 14.sp) },
                  leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
                  },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                  colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextDark,
                    unfocusedTextColor = TextDark,
                  ),
                  modifier = Modifier.fillMaxWidth().testTag("owner_email_input"),
                )
              }

              Spacer(modifier = Modifier.height(18.dp))

              Text(
                text = "PASSWORD",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
              )
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(52.dp)
                  .clip(RoundedCornerShape(26.dp))
                  .background(PillBackground),
                contentAlignment = Alignment.CenterStart,
              ) {
                TextField(
                  value = password,
                  onValueChange = { password = it },
                  placeholder = { Text("enter your password", color = Color(0xFF9CA3AF), fontSize = 14.sp) },
                  leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
                  },
                  trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                      Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(20.dp),
                      )
                    }
                  },
                  visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                  keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                    viewModel.login(email, password)
                  }),
                  colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextDark,
                    unfocusedTextColor = TextDark,
                  ),
                  modifier = Modifier.fillMaxWidth().testTag("owner_password_input"),
                )
              }

              // Forgot Password Link
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 10.dp, end = 4.dp),
                contentAlignment = Alignment.CenterEnd,
              ) {
                Text(
                  text = "Forgot Password?",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF4B5563),
                  modifier = Modifier
                    .clickable { onNavigateToForgotPassword() }
                    .testTag("owner_forgot_password_button"),
                )
              }

              // Error feedback
              if (loginState is OwnerLoginUiState.Error) {
                val err = (loginState as OwnerLoginUiState.Error).message
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFFF7ED))
                    .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Box(
                    modifier = Modifier
                      .size(28.dp)
                      .clip(CircleShape)
                      .background(Color(0xFFFFEDD5)),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      Icons.Default.ErrorOutline,
                      contentDescription = null,
                      tint = Color(0xFFC2410C),
                      modifier = Modifier.size(17.dp),
                    )
                  }
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = err,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF9A3412),
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f).testTag("owner_error_banner"),
                  )
                }
              }

              Spacer(modifier = Modifier.height(26.dp))

              // Sign in button
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(54.dp)
                  .shadow(4.dp, RoundedCornerShape(27.dp))
                  .clip(RoundedCornerShape(27.dp))
                  .background(BlackPrimary)
                  .clickable(
                    role = Role.Button,
                    enabled = loginState !is OwnerLoginUiState.Loading,
                    onClick = {
                      keyboardController?.hide()
                      val trimmedEmail = email.trim()
                      val trimmedPass = password.trim()
                      if (trimmedEmail.isBlank() || trimmedPass.isBlank()) {
                        viewModel.setError("Please enter both owner email and password.")
                        return@clickable
                      }
                      if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
                        viewModel.setError("Please enter a valid email address.")
                        return@clickable
                      }
                      viewModel.login(trimmedEmail, trimmedPass)
                    }
                  )
                  .testTag("owner_sign_in_button")
                  .testTag("verify_credentials_button"),
                contentAlignment = Alignment.Center,
              ) {
                if (loginState is OwnerLoginUiState.Loading) {
                  CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else {
                  Text(
                    text = "Verify Credentials",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                  )
                }
              }
            }
          }

          // ── STEP 2: EMAIL OTP RE-VERIFICATION (VIA RESEND) ───────────────
          if (currentStep == OwnerAuthStep.EMAIL_OTP || currentStep == OwnerAuthStep.PHONE_OTP) {
            Column(modifier = Modifier.fillMaxWidth()) {
              Text(
                text = "REGISTERED EMAIL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
              )
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(52.dp)
                  .clip(RoundedCornerShape(26.dp))
                  .background(PillBackground),
                contentAlignment = Alignment.CenterStart,
              ) {
                TextField(
                  value = registeredEmail,
                  onValueChange = { registeredEmail = it },
                  placeholder = { Text("owner@campus.edu", color = Color(0xFF9CA3AF)) },
                  leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
                  },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                  colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextDark,
                    unfocusedTextColor = TextDark,
                  ),
                  modifier = Modifier.fillMaxWidth().testTag("owner_email_otp_display").testTag("owner_phone_input"),
                )
              }

              Spacer(modifier = Modifier.height(20.dp))

              Text(
                text = "ENTER 6-DIGIT OTP CODE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
              )

              // Interactive 6-digit OTP boxes container
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .clickable {
                    try {
                      otpFocusRequester.requestFocus()
                      keyboardController?.show()
                    } catch (e: Exception) {}
                  },
                contentAlignment = Alignment.Center,
              ) {
                // Visual 6-digit OTP boxes
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  val codePadded = otpCode.padEnd(6, ' ')
                  for (i in 0 until 6) {
                    val char = codePadded.getOrElse(i) { ' ' }
                    val isCurrent = (i == otpCode.length)
                    Box(
                      modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (char != ' ') Color(0xFFFFFBEB) else PillBackground)
                        .border(
                          width = if (isCurrent) 2.dp else 1.5.dp,
                          color = if (isCurrent || char != ' ') AmberBrand else BorderMuted,
                          shape = RoundedCornerShape(12.dp),
                        ),
                      contentAlignment = Alignment.Center,
                    ) {
                      Text(
                        text = if (char != ' ') char.toString() else "",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                      )
                    }
                  }
                }

                // Transparent BasicTextField covering the full boxes container
                BasicTextField(
                  value = otpCode,
                  onValueChange = { newText ->
                    val digits = newText.filter { it.isDigit() }
                    if (digits.length <= 6) {
                      otpCode = digits
                      if (digits.length == 6) {
                        keyboardController?.hide()
                        viewModel.verifyOtp(registeredEmail, digits)
                      }
                    }
                  },
                  keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                  ),
                  keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                    viewModel.verifyOtp(registeredEmail, otpCode)
                  }),
                  modifier = Modifier
                    .matchParentSize()
                    .alpha(0f)
                    .focusRequester(otpFocusRequester)
                    .testTag("otp_input_field"),
                )
              }

              Spacer(modifier = Modifier.height(14.dp))

              // Resend code timer
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = if (resendCountdown > 0) "Resend OTP in ${resendCountdown}s" else "Didn't receive code?",
                  fontSize = 12.5.sp,
                  color = Color(0xFF6B7280),
                )
                TextButton(
                  onClick = {
                    resendCountdown = 30
                    viewModel.resendEmailOtp(registeredEmail)
                  },
                  enabled = resendCountdown == 0,
                ) {
                  Text(
                    text = "Resend Code",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (resendCountdown == 0) AmberBrand else Color(0xFF9CA3AF),
                  )
                }
              }

              // Error feedback
              if (loginState is OwnerLoginUiState.Error) {
                val err = (loginState as OwnerLoginUiState.Error).message
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFFF7ED))
                    .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Box(
                    modifier = Modifier
                      .size(28.dp)
                      .clip(CircleShape)
                      .background(Color(0xFFFFEDD5)),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      Icons.Default.ErrorOutline,
                      contentDescription = null,
                      tint = Color(0xFFC2410C),
                      modifier = Modifier.size(17.dp),
                    )
                  }
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = err,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF9A3412),
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f).testTag("otp_error_text"),
                  )
                }
              }

              Spacer(modifier = Modifier.height(20.dp))

              // Verify button
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(54.dp)
                  .shadow(4.dp, RoundedCornerShape(27.dp))
                  .clip(RoundedCornerShape(27.dp))
                  .background(BlackPrimary)
                  .clickable(
                    role = Role.Button,
                    enabled = loginState !is OwnerLoginUiState.Loading,
                    onClick = {
                      keyboardController?.hide()
                      viewModel.verifyOtp(registeredEmail, otpCode)
                    }
                  )
                  .testTag("verify_otp_button"),
                contentAlignment = Alignment.Center,
              ) {
                if (loginState is OwnerLoginUiState.Loading) {
                  CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else {
                  Text(
                    text = "Verify OTP & Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                  )
                }
              }

              Spacer(modifier = Modifier.height(10.dp))

              TextButton(
                onClick = { viewModel.backToCredentials() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
              ) {
                Text(
                  text = "← Back to Email Sign In",
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFF4B5563),
                )
              }
            }
          }
        }
      }
    }
  }

  // ── STEP 3: FIRST-TIME OWNER CANTEEN ALLOCATION MINI POPUP DIALOG ────────
  if (showAllocationMiniPopup) {
    Dialog(
      onDismissRequest = { /* Prevent accidental dismiss without allocating */ },
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Surface(
        modifier = Modifier
          .fillMaxWidth(0.94f)
          .clip(RoundedCornerShape(28.dp))
          .background(PureWhite)
          .testTag("allocation_mini_popup_dialog"),
        shape = RoundedCornerShape(28.dp),
        color = PureWhite,
        shadowElevation = 12.dp,
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
        ) {
          // Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .background(Color(0xFFFEF3C7), CircleShape),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Storefront,
                  contentDescription = null,
                  tint = AmberBrand,
                  modifier = Modifier.size(22.dp),
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Select Your Canteen",
                  fontSize = 18.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextDark,
                  modifier = Modifier.testTag("popup_title"),
                )
                Text(
                  text = "First-Time Owner Setup",
                  fontSize = 12.sp,
                  color = Color(0xFF6B7280),
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          Text(
            text = "Choose the campus canteen for this account ($email). This choice is permanent and links to this email.",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = Color(0xFF4B5563),
          )

          Spacer(modifier = Modifier.height(16.dp))

          // Dynamic Canteen List from Firestore
          when (allocationState) {
            is CanteenAllocationUiState.Loading, CanteenAllocationUiState.Idle -> {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(180.dp),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator(color = AmberBrand, strokeWidth = 3.dp)
              }
            }
            is CanteenAllocationUiState.Ready -> {
              val ready = allocationState as CanteenAllocationUiState.Ready
              LazyColumn(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(240.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                items(ready.canteens) { canteen ->
                  val isSelected = ready.selectedCanteen?.id == canteen.id
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(16.dp))
                      .background(if (isSelected) Color(0xFFFFFBEB) else PillBackground)
                      .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) AmberBrand else BorderMuted,
                        shape = RoundedCornerShape(16.dp),
                      )
                      .clickable { viewModel.selectCanteen(canteen) }
                      .padding(14.dp)
                      .testTag("canteen_card_${canteen.id}"),
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = canteen.name,
                          fontSize = 14.5.sp,
                          fontWeight = FontWeight.Bold,
                          color = TextDark,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                          text = canteen.location.ifBlank { "${canteen.block} • ${canteen.floorInfo}" },
                          fontSize = 12.sp,
                          color = Color(0xFF6B7280),
                        )
                      }
                      RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.selectCanteen(canteen) },
                        colors = RadioButtonDefaults.colors(
                          selectedColor = AmberBrand,
                          unselectedColor = Color(0xFF9CA3AF),
                        ),
                      )
                    }
                  }
                }
              }
            }
            is CanteenAllocationUiState.Error -> {
              val err = (allocationState as CanteenAllocationUiState.Error).message
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 12.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(Color(0xFFFFF7ED))
                  .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(14.dp))
                  .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFEDD5)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFC2410C),
                    modifier = Modifier.size(17.dp),
                  )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = err,
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFF9A3412),
                  lineHeight = 17.sp,
                  modifier = Modifier.weight(1f),
                )
              }
            }
            is CanteenAllocationUiState.Allocating -> {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(180.dp),
                contentAlignment = Alignment.Center,
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  CircularProgressIndicator(color = AmberBrand, strokeWidth = 3.dp)
                  Spacer(modifier = Modifier.height(10.dp))
                  Text(text = "Binding Canteen Permanently...", fontSize = 13.sp, color = TextDark)
                }
              }
            }
            is CanteenAllocationUiState.Allocated -> {
              // Navigates
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Confirm & Bind Button
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .shadow(4.dp, RoundedCornerShape(26.dp))
              .clip(RoundedCornerShape(26.dp))
              .background(BlackPrimary)
              .clickable(
                role = Role.Button,
                enabled = allocationState is CanteenAllocationUiState.Ready &&
                  (allocationState as CanteenAllocationUiState.Ready).selectedCanteen != null,
                onClick = {
                  viewModel.confirmAllocation { updatedOwner ->
                    showAllocationMiniPopup = false
                    onLoginSuccess(updatedOwner)
                  }
                }
              )
              .testTag("confirm_canteen_allocation_button"),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Confirm & Bind Canteen",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = PureWhite,
            )
          }
        }
      }
    }
  }
}
