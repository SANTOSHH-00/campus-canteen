package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.data.firebase.AuthRepository
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted

import com.example.ui.state.UserProfile

typealias DemoUser = UserProfile

private val PillBackground = Color(0xFFF3F4F6)
private val LabelColor = Color(0xFF6B7280)
private val PlaceholderColor = Color(0xFF9CA3AF)

@Composable
fun LoginContent(
  modifier: Modifier = Modifier,
  onDismiss: () -> Unit = {},
  onLoginSuccess: (UserProfile) -> Unit = {},
  isSignUpDefault: Boolean = false,
) {
  var isSignUpMode by remember { mutableStateOf(isSignUpDefault) }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var studentName by remember { mutableStateOf("") }
  var registrationNumber by remember { mutableStateOf("") }
  var currentCourse by remember { mutableStateOf("") }
  var phoneNumber by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var successMessage by remember { mutableStateOf<String?>(null) }
  var showForgotPasswordDialog by remember { mutableStateOf(false) }

  val coroutineScope = rememberCoroutineScope()
  val authRepository = remember { AuthRepository() }
  val keyboardController = LocalSoftwareKeyboardController.current

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        .background(PureWhite)
        .padding(horizontal = 24.dp)
        .navigationBarsPadding()
        .imePadding()
        .testTag("login_sheet_content")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(top = 12.dp, bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Top Handle pill (like Screenshot 5)
      Box(
        modifier =
          Modifier
            .width(42.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(BorderGray)
            .testTag("sheet_drag_handle")
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Header Row with Back Button and Title
      Box(modifier = Modifier.fillMaxWidth()) {
        // Circular back button on the left (Screenshot 5)
        Box(
          modifier =
            Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(SoftGray)
              .clickable(role = Role.Button, onClick = onDismiss)
              .align(Alignment.CenterStart)
              .testTag("login_back_button"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back to Intro",
            tint = BlackPrimary,
            modifier = Modifier.size(20.dp),
          )
        }

        // Title (Centered)
        Text(
          text = if (isSignUpMode) "Create Account" else "Welcome back",
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
          modifier = Modifier.align(Alignment.Center).testTag("login_title_text"),
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Sign Up extra fields: Student Name, Reg Number, Current Course, Phone Number
      AnimatedVisibility(visible = isSignUpMode) {
        Column(modifier = Modifier.fillMaxWidth()) {
          // 1. Student Full Name
          Text(
            text = "FULL NAME",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = LabelColor,
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
              value = studentName,
              onValueChange = { studentName = it; errorMessage = null },
              placeholder = { Text("enter your name", color = PlaceholderColor, fontSize = 14.sp) },
              leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = null, tint = PlaceholderColor, modifier = Modifier.size(20.dp))
              },
              singleLine = true,
              colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = TextDark,
                unfocusedTextColor = TextDark,
              ),
              modifier = Modifier.fillMaxWidth().testTag("name_input_field"),
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          // 2. Student Registration Number (8 digits)
          Text(
            text = "REGISTRATION NUMBER",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = LabelColor,
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
              value = registrationNumber,
              onValueChange = { input ->
                if (input.length <= 8 && input.all { it.isDigit() }) {
                  registrationNumber = input
                  errorMessage = null
                }
              },
              placeholder = { Text("enter your registration number", color = PlaceholderColor, fontSize = 14.sp) },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
              leadingIcon = {
                Icon(Icons.Default.School, contentDescription = null, tint = PlaceholderColor, modifier = Modifier.size(20.dp))
              },
              singleLine = true,
              colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = TextDark,
                unfocusedTextColor = TextDark,
              ),
              modifier = Modifier.fillMaxWidth().testTag("reg_number_input_field"),
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          // 3. Current Course
          Text(
            text = "CURRENT COURSE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = LabelColor,
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
              value = currentCourse,
              onValueChange = { currentCourse = it; errorMessage = null },
              placeholder = { Text("e.g. B.Tech CSE, MBA, B.Sc", color = PlaceholderColor, fontSize = 14.sp) },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
              leadingIcon = {
                Icon(Icons.Default.School, contentDescription = null, tint = PlaceholderColor, modifier = Modifier.size(20.dp))
              },
              singleLine = true,
              colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = TextDark,
                unfocusedTextColor = TextDark,
              ),
              modifier = Modifier.fillMaxWidth().testTag("course_input_field"),
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          // 4. Phone Number (10 digits)
          Text(
            text = "PHONE NUMBER",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = LabelColor,
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
              value = phoneNumber,
              onValueChange = { input ->
                if (input.length <= 10 && input.all { it.isDigit() }) {
                  phoneNumber = input
                  errorMessage = null
                }
              },
              placeholder = { Text("enter 10-digit phone number", color = PlaceholderColor, fontSize = 14.sp) },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
              leadingIcon = {
                Icon(Icons.Default.Phone, contentDescription = null, tint = PlaceholderColor, modifier = Modifier.size(20.dp))
              },
              singleLine = true,
              colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = TextDark,
                unfocusedTextColor = TextDark,
              ),
              modifier = Modifier.fillMaxWidth().testTag("phone_input_field"),
            )
          }

          Spacer(modifier = Modifier.height(14.dp))
        }
      }

      // EMAIL SECTION (or Reg No in Login mode)
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = if (isSignUpMode) "EMAIL" else "EMAIL OR REGISTRATION NUMBER",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp,
          color = LabelColor,
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
            onValueChange = {
              email = it
              errorMessage = null
            },
            placeholder = {
              Text(
                if (isSignUpMode) "enter your email" else "enter your email or registration number",
                color = PlaceholderColor,
                fontSize = 14.sp,
              )
            },
            singleLine = true,
            keyboardOptions =
              KeyboardOptions(
                keyboardType = if (isSignUpMode) KeyboardType.Email else KeyboardType.Text,
                imeAction = ImeAction.Next,
              ),
            leadingIcon = {
              Icon(Icons.Default.Email, contentDescription = null, tint = PlaceholderColor, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
              if (email.isNotEmpty()) {
                IconButton(onClick = { email = "" }) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = PlaceholderColor,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            },
            colors =
              TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = TextDark,
                unfocusedTextColor = TextDark,
              ),
            modifier = Modifier.fillMaxWidth().testTag("email_input_field"),
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // PASSWORD SECTION
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "PASSWORD",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp,
          color = LabelColor,
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
            onValueChange = {
              password = it
              errorMessage = null
            },
            placeholder = { Text("enter your password", color = PlaceholderColor, fontSize = 14.sp) },
            singleLine = true,
            visualTransformation =
              if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions =
              KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
              ),
            keyboardActions =
              KeyboardActions(
                onDone = {
                  keyboardController?.hide()
                }
              ),
            leadingIcon = {
              Icon(Icons.Default.Lock, contentDescription = null, tint = PlaceholderColor, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
              IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                  imageVector =
                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  contentDescription = if (passwordVisible) "Hide password" else "Show password",
                  tint = PlaceholderColor,
                  modifier = Modifier.size(20.dp),
                )
              }
            },
            colors =
              TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = TextDark,
                unfocusedTextColor = TextDark,
              ),
            modifier = Modifier.fillMaxWidth().testTag("password_input_field"),
          )
        }
      }

      // Forgot Password link (in sign in mode)
      if (!isSignUpMode) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
          Text(
            text = "Forgot password?",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            modifier =
              Modifier
                .align(Alignment.CenterEnd)
                .clickable { showForgotPasswordDialog = true }
                .testTag("forgot_password_link"),
          )
        }
      }

      // Error message feedback
      if (errorMessage != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFF7ED))
            .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = Color(0xFFC2410C),
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = errorMessage ?: "",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF9A3412),
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f).testTag("login_error_message"),
          )
        }
      }

      // Success feedback banner
      if (successMessage != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(Color(0xFFECFDF5))
              .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = successMessage ?: "",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF065F46),
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Primary Login Button
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(4.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(BlackPrimary)
            .clickable(
              role = Role.Button,
              enabled = !isLoading,
              onClick = {
                keyboardController?.hide()
                val trimmedInput = email.trim()
                if (trimmedInput.isEmpty()) {
                  errorMessage = if (isSignUpMode) "Please enter your email" else "Please enter your email or registration number"
                  return@clickable
                }
                if (isSignUpMode) {
                  if (studentName.trim().isEmpty()) {
                    errorMessage = "Please enter your name"
                    return@clickable
                  }
                  val trimmedReg = registrationNumber.trim()
                  if (trimmedReg.length != 8 || !trimmedReg.all { it.isDigit() }) {
                    errorMessage = "Registration number must be 8 digits (e.g. 12345678)"
                    return@clickable
                  }
                  if (!trimmedInput.contains("@")) {
                    errorMessage = "Please enter a valid email address"
                    return@clickable
                  }
                }
                if (password.length < 4) {
                  errorMessage = "Password must be at least 4 characters"
                  return@clickable
                }

                isLoading = true
                errorMessage = null

                val finalName = if (studentName.isNotBlank()) {
                  studentName.trim()
                } else {
                  trimmedInput.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
                }

                val finalRegNo = if (registrationNumber.isNotBlank()) {
                  registrationNumber.trim().uppercase()
                } else if (!trimmedInput.contains("@")) {
                  trimmedInput.uppercase()
                } else {
                  "12345678"
                }

                val finalCourse = if (currentCourse.isNotBlank()) {
                  currentCourse.trim()
                } else {
                  "CSE – 3rd Year"
                }

                val finalPhone = phoneNumber.trim()

                val finalEmail = trimmedInput

                val profile =
                  UserProfile(
                    name = finalName,
                    registrationNumber = finalRegNo,
                    department = finalCourse,
                    email = finalEmail,
                    phone = finalPhone,
                    course = finalCourse,
                  )

                coroutineScope.launch {
                  try {
                    if (isSignUpMode) {
                      val result = authRepository.signUpStudent(
                        name = finalName,
                        email = finalEmail,
                        password = password,
                        registrationNumber = finalRegNo,
                        department = finalCourse,
                        phone = finalPhone,
                        course = finalCourse,
                      )
                      if (result.isSuccess) {
                        val finalProf = result.getOrNull()!!.toUserProfile()
                        successMessage = "Account created! Welcome $finalName"
                        isLoading = false
                        onLoginSuccess(finalProf)
                      } else {
                        isLoading = false
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to create account. Please try again."
                      }
                    } else {
                      val result = authRepository.signIn(finalEmail, password)
                      if (result.isSuccess) {
                        val finalProf = result.getOrNull()!!.toUserProfile()
                        successMessage = "Logged in successfully! Welcome ${finalProf.name}"
                        isLoading = false
                        onLoginSuccess(finalProf)
                      } else {
                        isLoading = false
                        errorMessage = result.exceptionOrNull()?.message ?: "Sign in failed. Please check credentials or register."
                      }
                    }
                  } catch (e: Exception) {
                    isLoading = false
                    errorMessage = e.localizedMessage ?: "Authentication error. Please try again."
                  }
                }
              },
            )
            .testTag("login_submit_button"),
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
            text = if (isSignUpMode) "Create Account" else "Log in",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = PureWhite,
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Bottom toggle: "Don't have an account? Sign up" or "New here? Create an account"
      val bottomAnnotatedText =
        buildAnnotatedString {
          if (isSignUpMode) {
            append("Already have an account? ")
            withStyle(
              style =
                SpanStyle(
                  fontWeight = FontWeight.Bold,
                  color = BlackPrimary,
                )
            ) {
              append("Log in")
            }
          } else {
            append("New here? ")
            withStyle(
              style =
                SpanStyle(
                  fontWeight = FontWeight.Bold,
                  color = BlackPrimary,
                )
            ) {
              append("Create an account")
            }
          }
        }

      Text(
        text = bottomAnnotatedText,
        fontSize = 13.sp,
        color = TextDark,
        modifier =
          Modifier
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
            ) {
              isSignUpMode = !isSignUpMode
              errorMessage = null
              successMessage = null
            }
            .testTag("toggle_auth_mode_text"),
      )
    }
  }

  // Forgot password dialog
  if (showForgotPasswordDialog) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { showForgotPasswordDialog = false },
      title = { Text("Reset Password") },
      text = {
        Text(
          "A password reset link will be sent to your registered college email ($email)."
        )
      },
      confirmButton = {
        androidx.compose.material3.TextButton(
          onClick = {
            val targetEmail = email.trim()
            if (targetEmail.isNotEmpty()) {
              coroutineScope.launch {
                try {
                  authRepository.sendPasswordReset(targetEmail)
                } catch (e: Exception) {}
              }
            }
            showForgotPasswordDialog = false
          }
        ) {
          Text("Send Reset Link", color = BlackPrimary, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        androidx.compose.material3.TextButton(
          onClick = { showForgotPasswordDialog = false }
        ) {
          Text("Cancel", color = TextMuted)
        }
      },
      containerColor = PureWhite,
      shape = RoundedCornerShape(20.dp),
    )
  }
}
