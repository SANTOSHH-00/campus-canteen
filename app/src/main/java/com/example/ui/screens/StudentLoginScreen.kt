package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.state.UserProfile
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream

@Composable
fun StudentLoginScreen(
  onBack: () -> Unit,
  onLoginSuccess: (UserProfile) -> Unit,
  onExploreGuest: () -> Unit,
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
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      // Reuses existing complete student login UI without duplicating any logic or styling
      LoginContent(
        onDismiss = onBack,
        onLoginSuccess = onLoginSuccess,
      )

      // Guest exploration option
      TextButton(
        onClick = onExploreGuest,
        modifier = Modifier
          .padding(bottom = 24.dp)
          .testTag("explore_as_guest_button"),
      ) {
        Text(
          text = "Explore Canteens as Guest →",
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Bold,
          color = BlackPrimary,
        )
      }
    }
  }
}
