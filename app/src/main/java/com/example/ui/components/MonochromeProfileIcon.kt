package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.PureWhite

/**
 * Minimal monochrome profile icon matching the visual language of the
 * canteen and filter icons (Icons.Default.Storefront, Icons.Default.Business).
 *
 * Rendered using a scalable vector silhouette with a circular head and
 * subtle shoulders, without any colors, gradients, or decorative clutter.
 */
@Composable
fun MonochromeProfileIcon(
  modifier: Modifier = Modifier,
  tint: Color = BlackPrimary,
  size: Dp = 24.dp,
  contentDescription: String? = "Profile",
) {
  Icon(
    painter = painterResource(id = R.drawable.ic_profile_monochrome),
    contentDescription = contentDescription,
    tint = tint,
    modifier = modifier.size(size),
  )
}

/**
 * Clean circular container hosting the monochrome profile icon.
 * Visually balanced, sharp at 24px, 32px, 48px, and larger sizes.
 */
@Composable
fun MonochromeProfileAvatar(
  modifier: Modifier = Modifier,
  size: Dp = 40.dp,
  iconSize: Dp = (size.value * 0.58f).dp,
  backgroundColor: Color = PureWhite,
  borderColor: Color = BorderGray.copy(alpha = 0.8f),
  iconTint: Color = BlackPrimary,
  onClick: (() -> Unit)? = null,
) {
  val baseModifier = modifier
    .size(size)
    .clip(CircleShape)
    .background(backgroundColor)
    .border(1.2.dp, borderColor, CircleShape)

  val finalModifier = if (onClick != null) {
    baseModifier.clickable(onClick = onClick)
  } else {
    baseModifier
  }

  Box(
    modifier = finalModifier,
    contentAlignment = Alignment.Center,
  ) {
    MonochromeProfileIcon(
      size = iconSize,
      tint = iconTint,
    )
  }
}
