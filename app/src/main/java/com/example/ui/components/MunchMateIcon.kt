// MunchMateIcon: Reusable Jetpack Compose UI component
package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Recreated native vector Chef Bot icon for MunchMate AI Assistant.
 * Faithfully extracted from reference design:
 * - Scalable vector artwork featuring the iconic puffy chef toque, expressive cartoon googly eyes, and circular border.
 * - Crisp, multi-colored vector asset rendering sharp at any scale (from small chat avatars to hero headers).
 */
@Composable
fun MunchMateIcon(
  modifier: Modifier = Modifier,
  tint: Color? = null,
  contentDescription: String? = "MunchMate",
) {
  Image(
    painter = painterResource(id = R.drawable.ic_munchmate_vector),
    contentDescription = contentDescription,
    modifier = modifier,
  )
}
