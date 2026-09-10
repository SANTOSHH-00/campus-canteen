package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BlackPrimary

/**
 * 3-half design icon matching Image 3:
 * Three horizontal rounded bars of varying lengths stacked vertically.
 */
@Composable
fun StaggeredMenuIcon(
  modifier: Modifier = Modifier,
  tint: Color = BlackPrimary,
  onClick: (() -> Unit)? = null,
) {
  val clickModifier = if (onClick != null) {
    Modifier
      .clip(CircleShape)
      .clickable(onClick = onClick)
  } else {
    Modifier
  }


  Box(
    modifier = modifier
      .then(clickModifier)
      .padding(8.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(4.dp),
      horizontalAlignment = Alignment.Start,
      modifier = Modifier.size(width = 24.dp, height = 18.dp),
    ) {
      // Bar 1 (Medium - top)
      Box(
        modifier = Modifier
          .width(15.dp)
          .height(3.dp)
          .clip(CircleShape)
          .background(tint),
      )
      // Bar 2 (Long - middle)
      Box(
        modifier = Modifier
          .width(22.dp)
          .height(3.dp)
          .clip(CircleShape)
          .background(tint),
      )
      // Bar 3 (Short - bottom)
      Box(
        modifier = Modifier
          .width(11.dp)
          .height(3.dp)
          .clip(CircleShape)
          .background(tint),
      )
    }
  }
}
