package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.api.ProductSuggestionRepository
import com.example.data.api.SuggestionException
import com.example.ui.state.CanteenAppState
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import kotlinx.coroutines.launch

private val PinkAccent = Color(0xFFFA2E67)
private val PinkButtonBg = Color(0xFFFFB6C1)
private val InputBoxBg = Color(0xFFF5F3F7)

/**
 * Suggest Products Modal Sheet designed exactly per Reference Image 2:
 * - Title: "Suggest Products"
 * - Subtitle: "Didn't find what you are looking for? Please suggest the products"
 * - Large multiline text input
 * - 3-day rate limit (72-hour cooldown) banner with hours countdown
 * - "Send" button at bottom
 * - Strictly routes to the selected canteen's assigned owner
 */
@Composable
fun SuggestProductSheet(
  canteenId: String,
  canteenName: String,
  appState: CanteenAppState,
  onDismiss: () -> Unit,
  onSuggestionSent: () -> Unit = {},
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val repository = remember { ProductSuggestionRepository() }

  var suggestionText by remember { mutableStateOf("") }
  var isSubmitting by remember { mutableStateOf(false) }
  var isCheckingEligibility by remember { mutableStateOf(true) }
  var isEligible by remember { mutableStateOf(true) }
  var remainingHours by remember { mutableIntStateOf(0) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val studentId = appState.currentUser?.registrationNumber?.ifBlank { appState.currentUser?.email }.orEmpty()
  val studentEmail = appState.currentUser?.email.orEmpty()
  val studentReg = appState.currentUser?.registrationNumber.orEmpty()
  val studentName = appState.currentUser?.name?.ifBlank { appState.displayUserName } ?: "Student"

  // Check 3-day eligibility on open
  LaunchedEffect(canteenId) {
    isCheckingEligibility = true
    val result = repository.checkEligibility(
      canteenId = canteenId,
      studentId = studentId,
      studentEmail = studentEmail,
      studentReg = studentReg,
    )
    isCheckingEligibility = false
    result.onSuccess { res ->
      isEligible = res.eligible
      remainingHours = res.remainingHours
      if (!res.eligible) {
        val days = (res.remainingHours + 23) / 24
        errorMessage = "You already submitted a suggestion for $canteenName recently. You can submit another in ${if (days > 1) "$days days" else "${res.remainingHours}h"}."
      }
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color.Black.copy(alpha = 0.5f))
        .clickable { onDismiss() }
        .imePadding(),
      contentAlignment = Alignment.BottomCenter,
    ) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(enabled = false) {},
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = PureWhite,
        shadowElevation = 16.dp,
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
          // Drag handle
          Box(
            modifier = Modifier
              .align(Alignment.CenterHorizontally)
              .width(42.dp)
              .height(4.dp)
              .clip(RoundedCornerShape(2.dp))
              .background(BorderGray),
          )

          Spacer(modifier = Modifier.height(16.dp))

          // Top Header Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Suggest Products",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = BlackPrimary,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Didn't find what you are looking for? Please suggest the products",
                fontSize = 13.sp,
                color = TextMuted,
                lineHeight = 18.sp,
              )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
              Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
            }
          }

          Spacer(modifier = Modifier.height(18.dp))

          // Cooldown Warning Banner if active
          if (!isEligible && errorMessage != null) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFEF3C7))
                .border(1.dp, DrawerAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            ) {
              Row(verticalAlignment = Alignment.Top) {
                Icon(
                  imageVector = Icons.Default.Info,
                  contentDescription = null,
                  tint = DrawerAmber,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = errorMessage ?: "",
                  fontSize = 12.5.sp,
                  color = TextDark,
                  lineHeight = 17.sp,
                )
              }
            }
            Spacer(modifier = Modifier.height(14.dp))
          }

          // Multiline Text Box per Reference Image 2
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(140.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(InputBoxBg)
              .padding(16.dp),
          ) {
            if (suggestionText.isEmpty()) {
              Text(
                text = "Enter the name of the products you would like to see in $canteenName.",
                fontSize = 14.5.sp,
                color = Color(0xFF9E9E9E),
                lineHeight = 20.sp,
              )
            }

            BasicTextField(
              value = suggestionText,
              onValueChange = { if (it.length <= 500) suggestionText = it },
              enabled = isEligible && !isSubmitting,
              textStyle = TextStyle(
                fontSize = 14.5.sp,
                color = BlackPrimary,
                lineHeight = 20.sp,
              ),
              modifier = Modifier.fillMaxWidth(),
            )
          }

          Spacer(modifier = Modifier.height(6.dp))

          // Character Counter & Canteen Route Note
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Routed directly to $canteenName owner",
              fontSize = 11.sp,
              color = TextMuted,
            )
            Text(
              text = "${suggestionText.length}/500",
              fontSize = 11.sp,
              color = TextMuted,
            )
          }

          Spacer(modifier = Modifier.height(20.dp))

          // Send Button per Reference Image 2
          val isSendEnabled = isEligible && suggestionText.trim().length >= 2 && !isSubmitting
          val buttonBg = if (isSendEnabled) PinkButtonBg else PinkButtonBg.copy(alpha = 0.5f)

          Button(
            onClick = {
              if (suggestionText.trim().length < 2) return@Button
              isSubmitting = true
              errorMessage = null

              scope.launch {
                val result = repository.submitSuggestion(
                  canteenId = canteenId,
                  canteenName = canteenName,
                  suggestionText = suggestionText.trim(),
                  studentName = studentName,
                  studentId = studentId,
                  studentEmail = studentEmail,
                  studentReg = studentReg,
                )

                isSubmitting = false

                result.onSuccess { response ->
                  Toast.makeText(
                    context,
                    "Suggestion sent to $canteenName! Thank you.",
                    Toast.LENGTH_LONG
                  ).show()
                  onSuggestionSent()
                  onDismiss()
                }.onFailure { ex ->
                  if (ex is SuggestionException && ex.code == "RATE_LIMIT_COOLDOWN") {
                    isEligible = false
                    val hours = ex.remainingHours ?: 72
                    val days = (hours + 23) / 24
                    errorMessage = ex.message ?: "Cooldown active. You can suggest another in ${days} days."
                  } else {
                    errorMessage = ex.message ?: "Failed to submit. Please check your connection."
                  }
                }
              }
            },
            enabled = isSendEnabled,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = buttonBg,
              disabledContainerColor = PinkButtonBg.copy(alpha = 0.45f),
            ),
          ) {
            if (isSubmitting) {
              CircularProgressIndicator(
                color = PureWhite,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
              )
            } else {
              Text(
                text = "Send",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = PureWhite,
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
        }
      }
    }
  }
}
