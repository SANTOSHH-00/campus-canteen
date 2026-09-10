package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.PureWhite

/**
 * Aesthetic Gourmet Order Ready Cloche Illustration:
 * Replaces the crude cartoon takeout bag with a modern, luxury culinary cloche
 * with steam wisps, concentric warm halos, and a gleaming gold-amber ready badge.
 */
@Composable
fun TakeoutBagIllustration(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.size(190.dp),
    contentAlignment = Alignment.Center,
  ) {
    // Soft glowing radial halo backdrop
    Box(
      modifier = Modifier
        .size(175.dp)
        .clip(CircleShape)
        .background(
          Brush.radialGradient(
            colors = listOf(
              Color(0xFFFBF4EB),
              Color(0xFFF5EBE0),
              Color(0xFFFDFBF7).copy(alpha = 0.5f),
              Color.Transparent,
            ),
          )
        )
    )

    // Concentric delicate ring
    Box(
      modifier = Modifier
        .size(150.dp)
        .clip(CircleShape)
        .border(1.2.dp, Color(0xFFE8DACB).copy(alpha = 0.8f), CircleShape)
    )

    Canvas(modifier = Modifier.size(140.dp)) {
      val w = size.width
      val h = size.height

      // Subtle golden culinary sparkle stars
      val goldStar = Color(0xFFDDA647)
      drawCircle(goldStar, radius = 2.5.dp.toPx(), center = Offset(w * 0.18f, h * 0.32f))
      drawCircle(goldStar, radius = 2.dp.toPx(), center = Offset(w * 0.84f, h * 0.28f))
      drawCircle(goldStar, radius = 2.5.dp.toPx(), center = Offset(w * 0.86f, h * 0.65f))
      drawCircle(goldStar, radius = 2.dp.toPx(), center = Offset(w * 0.15f, h * 0.68f))

      // Steam wisps rising above cloche
      val steamPath1 = Path().apply {
        moveTo(w * 0.42f, h * 0.28f)
        cubicTo(w * 0.40f, h * 0.20f, w * 0.45f, h * 0.15f, w * 0.43f, h * 0.08f)
      }
      drawPath(
        path = steamPath1,
        color = Color(0xFFBA7A18).copy(alpha = 0.45f),
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
      )

      val steamPath2 = Path().apply {
        moveTo(w * 0.52f, h * 0.26f)
        cubicTo(w * 0.55f, h * 0.18f, w * 0.50f, h * 0.14f, w * 0.53f, h * 0.07f)
      }
      drawPath(
        path = steamPath2,
        color = Color(0xFFBA7A18).copy(alpha = 0.55f),
        style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round),
      )

      val steamPath3 = Path().apply {
        moveTo(w * 0.60f, h * 0.30f)
        cubicTo(w * 0.63f, h * 0.22f, w * 0.59f, h * 0.16f, w * 0.62f, h * 0.10f)
      }
      drawPath(
        path = steamPath3,
        color = Color(0xFFBA7A18).copy(alpha = 0.40f),
        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
      )

      // Base Food Platter / Tray (Dark Graphite with Amber Accent)
      val trayY = h * 0.76f
      // Tray shadow
      drawOval(
        color = Color(0xFF1E1D1B).copy(alpha = 0.12f),
        topLeft = Offset(w * 0.16f, trayY + 4.dp.toPx()),
        size = Size(w * 0.68f, 14.dp.toPx()),
      )

      // Silver-Graphite Platter Base
      drawOval(
        brush = Brush.horizontalGradient(
          colors = listOf(Color(0xFF2B2825), Color(0xFF1A1917), Color(0xFF2B2825)),
        ),
        topLeft = Offset(w * 0.18f, trayY - 4.dp.toPx()),
        size = Size(w * 0.64f, 16.dp.toPx()),
      )

      // Golden tray trim rim
      drawLine(
        brush = Brush.horizontalGradient(
          colors = listOf(Color(0xFF9E6814), Color(0xFFE5B55E), Color(0xFF9E6814)),
        ),
        start = Offset(w * 0.22f, trayY + 3.dp.toPx()),
        end = Offset(w * 0.78f, trayY + 3.dp.toPx()),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
      )

      // Cloche Dome Body (Rich Dark Graphite with Smooth Gradient)
      val domePath = Path().apply {
        moveTo(w * 0.24f, trayY)
        cubicTo(
          w * 0.24f, h * 0.36f,
          w * 0.76f, h * 0.36f,
          w * 0.76f, trayY,
        )
        close()
      }
      drawPath(
        path = domePath,
        brush = Brush.verticalGradient(
          colors = listOf(Color(0xFF2E2C29), Color(0xFF1A1918)),
          startY = h * 0.36f,
          endY = trayY,
        ),
      )

      // Subtle light reflection sheen on the dome curve
      val reflectionPath = Path().apply {
        moveTo(w * 0.32f, trayY - 4.dp.toPx())
        cubicTo(
          w * 0.32f, h * 0.44f,
          w * 0.60f, h * 0.42f,
          w * 0.64f, h * 0.44f,
        )
      }
      drawPath(
        path = reflectionPath,
        color = PureWhite.copy(alpha = 0.22f),
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
      )

      // Cloche Golden Top Knob / Handle
      val knobCenter = Offset(w * 0.50f, h * 0.36f)
      // Small neck
      drawLine(
        color = Color(0xFFBA7A18),
        start = Offset(knobCenter.x, knobCenter.y),
        end = Offset(knobCenter.x, knobCenter.y - 7.dp.toPx()),
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round,
      )
      // Golden sphere
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(Color(0xFFFDE68A), Color(0xFFDDA647), Color(0xFF92570D)),
          center = Offset(knobCenter.x - 2.dp.toPx(), knobCenter.y - 10.dp.toPx()),
          radius = 8.dp.toPx(),
        ),
        radius = 7.dp.toPx(),
        center = Offset(knobCenter.x, knobCenter.y - 8.dp.toPx()),
      )
    }

    // Floating Gourmet "ORDER READY" Badge in center foreground
    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 6.dp)
        .shadow(6.dp, RoundedCornerShape(14.dp))
        .clip(RoundedCornerShape(14.dp))
        .background(BlackPrimary)
        .border(1.dp, Color(0xFF33312E), RoundedCornerShape(14.dp))
        .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(DrawerAmber),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = PureWhite,
            modifier = Modifier.size(12.dp),
          )
        }
        Spacer(modifier = Modifier.width(7.dp))
        Text(
          text = "READY FOR PICKUP",
          fontSize = 10.5.sp,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 0.8.sp,
          color = PureWhite,
        )
      }
    }
  }
}

/**
 * Aesthetic Gourmet Order Tracking Live Radar Illustration:
 * Replaces the cartoon thumbs-up illustration with an elegant live kitchen tracker
 * featuring milestone radar rings, a chef's stopwatch/prep timer, and gold beacon nodes.
 */
@Composable
fun CustomerThumbsUpIllustration(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.size(190.dp),
    contentAlignment = Alignment.Center,
  ) {
    // Ambient warm halo backdrop
    Box(
      modifier = Modifier
        .size(175.dp)
        .clip(CircleShape)
        .background(
          Brush.radialGradient(
            colors = listOf(
              Color(0xFFFBF5EE),
              Color(0xFFF3ECE0),
              Color(0xFFFAF6F0).copy(alpha = 0.4f),
              Color.Transparent,
            ),
          )
        )
    )

    // Outer milestone orbit ring
    Box(
      modifier = Modifier
        .size(145.dp)
        .clip(CircleShape)
        .border(1.2.dp, Color(0xFFE8DACB).copy(alpha = 0.75f), CircleShape)
    )

    // Inner orbit ring
    Box(
      modifier = Modifier
        .size(105.dp)
        .clip(CircleShape)
        .border(1.dp, Color(0xFFDCC8B3).copy(alpha = 0.5f), CircleShape)
    )

    Canvas(modifier = Modifier.size(140.dp)) {
      val w = size.width
      val h = size.height

      // Golden Sparkle Stars around orbit
      val starColor = Color(0xFFDDA647)
      drawCircle(starColor, radius = 2.5.dp.toPx(), center = Offset(w * 0.18f, h * 0.28f))
      drawCircle(starColor, radius = 2.dp.toPx(), center = Offset(w * 0.82f, h * 0.25f))
      drawCircle(starColor, radius = 2.5.dp.toPx(), center = Offset(w * 0.86f, h * 0.72f))
      drawCircle(starColor, radius = 2.dp.toPx(), center = Offset(w * 0.14f, h * 0.70f))

      // Radar sweep arc (subtle amber wash)
      val sweepCenter = Offset(w * 0.5f, h * 0.5f)
      drawArc(
        brush = Brush.sweepGradient(
          colors = listOf(Color.Transparent, Color(0xFFBA7A18).copy(alpha = 0.18f), Color(0xFFBA7A18).copy(alpha = 0.35f)),
          center = sweepCenter,
        ),
        startAngle = 200f,
        sweepAngle = 100f,
        useCenter = true,
        topLeft = Offset(w * 0.15f, h * 0.15f),
        size = Size(w * 0.70f, h * 0.70f),
      )

      // Orbit milestone nodes (Ordered, Cooking, Ready, Pickup)
      val node1 = Offset(w * 0.50f, h * 0.14f) // Top: Order
      val node2 = Offset(w * 0.86f, h * 0.50f) // Right: Prep
      val node3 = Offset(w * 0.50f, h * 0.86f) // Bottom: Ready
      val node4 = Offset(w * 0.14f, h * 0.50f) // Left: Counter

      // Node 1 (Completed: Golden Amber)
      drawCircle(Color(0xFFBA7A18), radius = 5.dp.toPx(), center = node1)
      drawCircle(PureWhite, radius = 2.2.dp.toPx(), center = node1)

      // Node 2 (Active: Radiant Amber Glow)
      drawCircle(Color(0xFFBA7A18).copy(alpha = 0.28f), radius = 8.dp.toPx(), center = node2)
      drawCircle(Color(0xFFBA7A18), radius = 5.5.dp.toPx(), center = node2)
      drawCircle(PureWhite, radius = 2.5.dp.toPx(), center = node2)

      // Node 3 & 4 (Upcoming milestones)
      drawCircle(Color(0xFFDCC8B3), radius = 4.dp.toPx(), center = node3)
      drawCircle(Color(0xFFDCC8B3), radius = 4.dp.toPx(), center = node4)

      // Center Chef's Stopwatch / Precision Prep Timer
      val timerCenter = Offset(w * 0.5f, h * 0.5f)
      val timerRadius = 26.dp.toPx()

      // Outer bezel in dark graphite
      drawCircle(
        color = Color(0xFF1F1E1D),
        radius = timerRadius,
        center = timerCenter,
      )

      // Golden Bezel Rim
      drawCircle(
        color = Color(0xFFDDA647),
        radius = timerRadius,
        center = timerCenter,
        style = Stroke(width = 2.dp.toPx()),
      )

      // Watch Crown at top
      drawRoundRect(
        color = Color(0xFFDDA647),
        topLeft = Offset(timerCenter.x - 3.dp.toPx(), timerCenter.y - timerRadius - 5.dp.toPx()),
        size = Size(6.dp.toPx(), 6.dp.toPx()),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
      )

      // Subtle dial tick marks at 12, 3, 6, 9
      drawLine(
        color = PureWhite.copy(alpha = 0.5f),
        start = Offset(timerCenter.x, timerCenter.y - timerRadius + 4.dp.toPx()),
        end = Offset(timerCenter.x, timerCenter.y - timerRadius + 8.dp.toPx()),
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round,
      )
      drawLine(
        color = PureWhite.copy(alpha = 0.5f),
        start = Offset(timerCenter.x + timerRadius - 8.dp.toPx(), timerCenter.y),
        end = Offset(timerCenter.x + timerRadius - 4.dp.toPx(), timerCenter.y),
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round,
      )
      drawLine(
        color = PureWhite.copy(alpha = 0.5f),
        start = Offset(timerCenter.x, timerCenter.y + timerRadius - 8.dp.toPx()),
        end = Offset(timerCenter.x, timerCenter.y + timerRadius - 4.dp.toPx()),
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round,
      )
      drawLine(
        color = PureWhite.copy(alpha = 0.5f),
        start = Offset(timerCenter.x - timerRadius + 4.dp.toPx(), timerCenter.y),
        end = Offset(timerCenter.x - timerRadius + 8.dp.toPx(), timerCenter.y),
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round,
      )

      // Clock Hands (Pointing forward toward Ready)
      // Hour hand
      drawLine(
        color = PureWhite,
        start = timerCenter,
        end = Offset(timerCenter.x + 8.dp.toPx(), timerCenter.y - 7.dp.toPx()),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round,
      )
      // Minute hand (Golden amber)
      drawLine(
        color = Color(0xFFDDA647),
        start = timerCenter,
        end = Offset(timerCenter.x + 14.dp.toPx(), timerCenter.y + 6.dp.toPx()),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
      )
      // Center pin
      drawCircle(Color(0xFFDDA647), radius = 2.5.dp.toPx(), center = timerCenter)
    }

    // Floating "LIVE TRACKING" Capsule Badge in center foreground
    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 6.dp)
        .shadow(6.dp, RoundedCornerShape(14.dp))
        .clip(RoundedCornerShape(14.dp))
        .background(BlackPrimary)
        .border(1.dp, Color(0xFF33312E), RoundedCornerShape(14.dp))
        .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(DrawerAmber),
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
          text = "LIVE ORDER STATUS",
          fontSize = 10.5.sp,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 0.8.sp,
          color = PureWhite,
        )
      }
    }
  }
}
