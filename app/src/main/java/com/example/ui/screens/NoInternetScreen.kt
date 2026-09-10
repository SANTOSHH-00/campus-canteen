package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.PureWhite
import com.example.util.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val OrangeBrand = Color(0xFFFF5722)
private val TextDark = Color(0xFF0F172A)
private val TextMuted = Color(0xFF64748B)
private val BorderLight = Color(0xFFE2E8F0)
private val ButtonSecondaryBg = Color(0xFFF8FAFC)

/**
 * Dedicated Full-Screen "No Internet Connection" UI.
 * Matches the reference design with cute mascot, "Oops! No Internet Connection",
 * "Try Again" primary action button, and "Check Network Settings" action button.
 */
@Composable
fun NoInternetScreen(
  onRetrySuccess: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var isChecking by remember { mutableStateOf(false) }
  var retryFailedNotice by remember { mutableStateOf<String?>(null) }

  fun checkConnectionAndRetry() {
    if (isChecking) return
    isChecking = true
    retryFailedNotice = null

    coroutineScope.launch {
      // Small pause for tactile UI feedback
      delay(400)
      if (NetworkUtils.isOnline(context)) {
        isChecking = false
        onRetrySuccess()
      } else {
        isChecking = false
        retryFailedNotice = "Still offline. Please check your Wi-Fi or mobile data."
        Toast.makeText(context, "No active internet connection found", Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun openNetworkSettings() {
    try {
      val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      try {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
      } catch (ignored: Exception) {
        Toast.makeText(context, "Please enable Wi-Fi or Mobile Data in your device settings", Toast.LENGTH_LONG).show()
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(PureWhite)
      .statusBarsPadding()
      .navigationBarsPadding()
      .padding(horizontal = 28.dp)
      .testTag("no_internet_screen"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      // Mascot Illustration seamlessly integrated with background
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(260.dp),
        contentAlignment = Alignment.Center,
      ) {
        // Soft subtle warm ambient radial circle behind mascot for gentle depth
        Box(
          modifier = Modifier
            .size(240.dp)
            .background(
              brush = Brush.radialGradient(
                colors = listOf(
                  Color(0xFFFFF4EC),
                  Color(0xFFFFF9F5),
                  Color.Transparent,
                )
              ),
              shape = CircleShape,
            )
        )
        Image(
          painter = painterResource(id = R.drawable.no_internet_mascot),
          contentDescription = "No Internet Connection Mascot",
          modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
          contentScale = ContentScale.Fit,
        )
      }

      Spacer(modifier = Modifier.height(28.dp))

      // Heading: "Oops! No Internet Connection"
      Text(
        text = "Oops! No Internet Connection",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = TextDark,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("no_internet_title"),
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Subtitle: "It looks like you're offline. Please check your network connection and try again."
      Text(
        text = "It looks like you're offline. Please check your network connection and try again.",
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        color = TextMuted,
        lineHeight = 22.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 12.dp),
      )

      if (retryFailedNotice != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = retryFailedNotice.orEmpty(),
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          color = OrangeBrand,
          textAlign = TextAlign.Center,
        )
      }

      Spacer(modifier = Modifier.height(36.dp))

      // Button 1: "Try Again" (Orange pill)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .shadow(6.dp, RoundedCornerShape(27.dp), ambientColor = OrangeBrand.copy(alpha = 0.35f), spotColor = OrangeBrand)
          .clip(RoundedCornerShape(27.dp))
          .background(OrangeBrand)
          .clickable(
            role = Role.Button,
            enabled = !isChecking,
            onClick = { checkConnectionAndRetry() }
          )
          .testTag("no_internet_try_again_button"),
        contentAlignment = Alignment.Center,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (isChecking) {
            CircularProgressIndicator(
              color = PureWhite,
              modifier = Modifier.size(20.dp),
              strokeWidth = 2.5.dp,
            )
          } else {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Retry",
              tint = PureWhite,
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = "Try Again",
              fontSize = 16.sp,
              fontWeight = FontWeight.SemiBold,
              color = PureWhite,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Button 2: "Check Network Settings" (Subtle outline pill)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .clip(RoundedCornerShape(26.dp))
          .background(ButtonSecondaryBg)
          .border(1.dp, BorderLight, RoundedCornerShape(26.dp))
          .clickable(
            role = Role.Button,
            onClick = { openNetworkSettings() }
          )
          .testTag("no_internet_check_settings_button"),
        contentAlignment = Alignment.Center,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Default.WifiOff,
            contentDescription = "Network Settings",
            tint = TextDark,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = "Check Network Settings",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextDark,
          )
        }
      }
    }
  }
}
