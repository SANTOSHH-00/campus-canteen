// Empty Cart Illustration: Vector graphics rendering empty cart state with call-to-action
package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.MintFresh
import com.example.ui.theme.PureWhite

/**
 * Modern, elegant Empty Cart Illustration for QuickBite.
 * Features a minimalist culinary basket with soft radial warmth,
 * subtle campus food sparkle accents, and clean geometric linework.
 * 
 * Free of creepy cartoon faces, robots, or oversized characters.
 * Integrates seamlessly with the WarmCream app background.
 */
@Composable
fun ModernEmptyCartIllustration(
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.size(175.dp),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .size(165.dp)
        .clip(CircleShape)
        .background(
          Brush.radialGradient(
            colors = listOf(
              Color(0xFFF7ECE1).copy(alpha = 0.85f),
              Color(0xFFFAF3EC).copy(alpha = 0.55f),
              Color(0xFFFFFDF9).copy(alpha = 0.15f),
              Color.Transparent,
            ),
          )
        )
    )

    Canvas(modifier = Modifier.size(145.dp)) {
      val centerOffset = Offset(size.width / 2f, size.height / 2f)
      val radius = (size.width / 2f) - 4.dp.toPx()
      drawCircle(
        color = Color(0xFFE5D5C5).copy(alpha = 0.75f),
        radius = radius,
        center = centerOffset,
        style = Stroke(
          width = 1.4.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()), 0f)
        )
      )
    }

    Canvas(modifier = Modifier.size(130.dp)) {
      val w = size.width
      val h = size.height

      val mintSparkle = MintFresh
      val amberSparkle = DrawerAmber

      drawCircle(mintSparkle, radius = 2.4.dp.toPx(), center = Offset(w * 0.18f, h * 0.28f))
      drawCircle(amberSparkle, radius = 2.8.dp.toPx(), center = Offset(w * 0.82f, h * 0.24f))
      drawCircle(amberSparkle.copy(alpha = 0.7f), radius = 2.0.dp.toPx(), center = Offset(w * 0.85f, h * 0.62f))
      drawCircle(mintSparkle.copy(alpha = 0.7f), radius = 2.2.dp.toPx(), center = Offset(w * 0.15f, h * 0.65f))

      drawOval(
        color = Color(0xFFE2D3C4).copy(alpha = 0.6f),
        topLeft = Offset(w * 0.22f, h * 0.82f),
        size = Size(w * 0.56f, 10.dp.toPx())
      )

      val handlePath = Path().apply {
        moveTo(w * 0.30f, h * 0.46f)
        cubicTo(w * 0.30f, h * 0.14f, w * 0.70f, h * 0.14f, w * 0.70f, h * 0.46f)
      }
      drawPath(
        path = handlePath,
        color = DrawerAmber,
        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
      )

      val gripPath = Path().apply {
        moveTo(w * 0.43f, h * 0.19f)
        cubicTo(w * 0.47f, h * 0.17f, w * 0.53f, h * 0.17f, w * 0.57f, h * 0.19f)
      }
      drawPath(
        path = gripPath,
        color = BlackPrimary,
        style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
      )

      val basketBody = Path().apply {
        moveTo(w * 0.22f, h * 0.46f)
        lineTo(w * 0.78f, h * 0.46f)
        lineTo(w * 0.71f, h * 0.78f)
        quadraticTo(w * 0.50f, h * 0.82f, w * 0.29f, h * 0.78f)
        close()
      }

      drawPath(
        path = basketBody,
        brush = Brush.verticalGradient(
          colors = listOf(
            PureWhite,
            Color(0xFFFAF3EC),
            Color(0xFFF3E7DB),
          ),
          startY = h * 0.46f,
          endY = h * 0.82f
        )
      )

      drawPath(
        path = basketBody,
        color = BlackPrimary,
        style = Stroke(width = 2.8.dp.toPx(), join = StrokeJoin.Round)
      )

      drawRoundRect(
        color = BlackPrimary,
        topLeft = Offset(w * 0.19f, h * 0.43f),
        size = Size(w * 0.62f, 7.dp.toPx()),
        cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
      )

      val wireColor = Color(0xFFD6C2B0)
      drawLine(
        color = wireColor,
        start = Offset(w * 0.37f, h * 0.49f),
        end = Offset(w * 0.39f, h * 0.76f),
        strokeWidth = 1.6.dp.toPx(),
        cap = StrokeCap.Round
      )
      drawLine(
        color = wireColor,
        start = Offset(w * 0.50f, h * 0.49f),
        end = Offset(w * 0.50f, h * 0.78f),
        strokeWidth = 1.6.dp.toPx(),
        cap = StrokeCap.Round
      )
      drawLine(
        color = wireColor,
        start = Offset(w * 0.63f, h * 0.49f),
        end = Offset(w * 0.61f, h * 0.76f),
        strokeWidth = 1.6.dp.toPx(),
        cap = StrokeCap.Round
      )

      drawLine(
        color = wireColor,
        start = Offset(w * 0.26f, h * 0.63f),
        end = Offset(w * 0.74f, h * 0.63f),
        strokeWidth = 1.6.dp.toPx(),
        cap = StrokeCap.Round
      )

      val badgeCenter = Offset(w * 0.50f, h * 0.63f)
      drawCircle(
        color = PureWhite,
        radius = 11.dp.toPx(),
        center = badgeCenter
      )
      drawCircle(
        color = DrawerAmber,
        radius = 11.dp.toPx(),
        center = badgeCenter,
        style = Stroke(width = 1.8.dp.toPx())
      )

      val plusLen = 4.5.dp.toPx()
      drawLine(
        color = DrawerAmber,
        start = Offset(badgeCenter.x - plusLen, badgeCenter.y),
        end = Offset(badgeCenter.x + plusLen, badgeCenter.y),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
      )
      drawLine(
        color = DrawerAmber,
        start = Offset(badgeCenter.x, badgeCenter.y - plusLen),
        end = Offset(badgeCenter.x, badgeCenter.y + plusLen),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
      )

      val steamWisp = Path().apply {
        moveTo(w * 0.48f, h * 0.38f)
        cubicTo(w * 0.46f, h * 0.33f, w * 0.52f, h * 0.30f, w * 0.50f, h * 0.25f)
      }
      drawPath(
        path = steamWisp,
        color = DrawerAmber.copy(alpha = 0.5f),
        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
      )
    }
  }
}
