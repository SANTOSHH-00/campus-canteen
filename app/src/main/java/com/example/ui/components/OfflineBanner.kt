package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.NetworkMonitor
import kotlinx.coroutines.delay

@Composable
fun OfflineBanner(
  modifier: Modifier = Modifier,
) {
  val isOnline by NetworkMonitor.isOnline.collectAsState()
  var wasOffline by remember { mutableStateOf(false) }
  var showBackOnline by remember { mutableStateOf(false) }

  LaunchedEffect(isOnline) {
    if (!isOnline) {
      wasOffline = true
      showBackOnline = false
    } else if (wasOffline) {
      showBackOnline = true
      delay(2500)
      showBackOnline = false
      wasOffline = false
    }
  }

  val isVisible = !isOnline || showBackOnline

  AnimatedVisibility(
    visible = isVisible,
    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
    modifier = modifier.padding(top = 10.dp),
  ) {
    val bgColor = if (!isOnline) Color(0xFF1E293B) else Color(0xFF065F46)
    val accentColor = if (!isOnline) Color(0xFFF59E0B) else Color(0xFF34D399)
    val text = if (!isOnline) "Offline • Showing cached data" else "Back online ✓"

    Box(
      modifier = Modifier
        .shadow(8.dp, RoundedCornerShape(24.dp))
        .clip(RoundedCornerShape(24.dp))
        .background(bgColor.copy(alpha = 0.96f))
        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
        .padding(horizontal = 16.dp, vertical = 7.dp),
      contentAlignment = Alignment.Center,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (!isOnline) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(accentColor)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Icon(
            imageVector = Icons.Default.WifiOff,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(15.dp),
          )
        } else {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(15.dp),
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = text,
          fontSize = 12.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = Color.White,
          letterSpacing = 0.2.sp,
        )
      }
    }
  }
}
