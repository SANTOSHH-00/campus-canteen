// QuickBiteStateView: State holder maintaining active user session, cart, and canteen selections
package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted

enum class QuickBiteStateType {
  LOADING,
  NO_INTERNET,
  SERVER_UNAVAILABLE,
  TIMEOUT,
  SESSION_EXPIRED,
  EMPTY_DATA,
  FAILED,
}

/**
 * Cohesive, professional state view for all QuickBite screens.
 * Integrates directly into the screen's existing background (WarmCream / natural surface)
 * without arbitrary white boxes, aggressive borders, or banner-like containers.
 */
@Composable
fun QuickBiteStateView(
  type: QuickBiteStateType,
  title: String,
  message: String,
  modifier: Modifier = Modifier,
  isRetrying: Boolean = false,
  onRetry: (() -> Unit)? = null,
  customIcon: ImageVector? = null,
) {
  val iconVector = customIcon ?: when (type) {
    QuickBiteStateType.LOADING -> null
    QuickBiteStateType.NO_INTERNET -> Icons.Default.WifiOff
    QuickBiteStateType.SERVER_UNAVAILABLE -> Icons.Default.CloudOff
    QuickBiteStateType.TIMEOUT -> Icons.Default.Timer
    QuickBiteStateType.SESSION_EXPIRED -> Icons.Default.ErrorOutline
    QuickBiteStateType.EMPTY_DATA -> Icons.Default.RestaurantMenu
    QuickBiteStateType.FAILED -> Icons.Default.ErrorOutline
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    if (type == QuickBiteStateType.LOADING) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(CircleShape)
          .background(DrawerAmber.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(
          color = DrawerAmber,
          strokeWidth = 3.dp,
          modifier = Modifier.size(28.dp),
        )
      }
    } else if (iconVector != null) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(CircleShape)
          .background(Color(0xFFE8E3D8)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = iconVector,
          contentDescription = null,
          tint = TextDark,
          modifier = Modifier.size(26.dp),
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Text(
      text = title,
      fontSize = 17.sp,
      fontWeight = FontWeight.Bold,
      color = TextDark,
      textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = message,
      fontSize = 13.5.sp,
      fontWeight = FontWeight.Normal,
      color = TextMuted,
      textAlign = TextAlign.Center,
      lineHeight = 19.sp,
      modifier = Modifier.padding(horizontal = 12.dp),
    )

    if (onRetry != null) {
      Spacer(modifier = Modifier.height(18.dp))

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(12.dp))
          .background(DrawerAmber)
          .clickable(enabled = !isRetrying) { onRetry() }
          .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (isRetrying) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
              color = PureWhite,
              strokeWidth = 2.dp,
              modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Retrying...",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = PureWhite,
            )
          }
        } else {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = null,
              tint = PureWhite,
              modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Retry",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = PureWhite,
            )
          }
        }
      }
    }
  }
}

