package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Recreated native vector fork-and-knife icon for MunchMate AI Assistant.
 * Built from native vector artwork with clean scalable geometry, sharp rendering at any size,
 * and customizable theme tinting.
 */
@Composable
fun MunchMateIcon(
  modifier: Modifier = Modifier,
  tint: Color? = Color(0xFF2E5A44),
  contentDescription: String? = "MunchMate",
) {
  if (tint != null) {
    Icon(
      painter = painterResource(id = R.drawable.ic_munchmate_vector),
      contentDescription = contentDescription,
      tint = tint,
      modifier = modifier,
    )
  } else {
    Image(
      painter = painterResource(id = R.drawable.ic_munchmate_vector),
      contentDescription = contentDescription,
      modifier = modifier,
    )
  }
}
