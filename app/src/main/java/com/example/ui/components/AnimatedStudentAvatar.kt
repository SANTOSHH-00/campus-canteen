package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted

data class StudentAvatarData(
  val id: String,
  val name: String,
  val subtitle: String,
  val bgColor: Color,
  val accentColor: Color,
  val emoji: String,
)

object StudentAvatars {
  val list = listOf(
    StudentAvatarData("scholar", "The Scholar", "Academics & Research", Color(0xFF1E3A8A), Color(0xFFFBBF24), "🎓"),
    StudentAvatarData("coder", "The Techie", "Coding & Coffee", Color(0xFF0F766E), Color(0xFF2DD4BF), "🧑‍💻"),
    StudentAvatarData("creator", "The Creative", "Design & Arts", Color(0xFF7E22CE), Color(0xFFF472B6), "🎨"),
    StudentAvatarData("athlete", "The Athlete", "Sports & Energy", Color(0xFF15803D), Color(0xFF4ADE80), "⚡"),
    StudentAvatarData("foodie", "The Foodie", "Canteen Connoisseur", Color(0xFFC2410C), Color(0xFFFB923C), "🍔"),
  )

  fun getById(id: String): StudentAvatarData {
    return list.firstOrNull { it.id == id } ?: list[0]
  }
}

/**
 * Animated Student Face Avatar:
 * Replaces blank image with charming, lively animated student personas.
 * Features gentle breathing bob and interactive avatar picker modal.
 */
@Composable
fun AnimatedStudentAvatar(
  avatarId: String,
  size: Dp = 64.dp,
  isInteractive: Boolean = true,
  onAvatarChanged: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val avatar = StudentAvatars.getById(avatarId)
  var showPicker by remember { mutableStateOf(false) }

  // Infinite micro-animation for lifelike feel
  val infiniteTransition = rememberInfiniteTransition(label = "avatarAnimation")
  val bounceScale by infiniteTransition.animateFloat(
    initialValue = 0.97f,
    targetValue = 1.03f,
    animationSpec = infiniteRepeatable(
      animation = tween(1400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "bounceScale",
  )

  Box(
    modifier = modifier
      .size(size)
      .scale(bounceScale)
      .clip(CircleShape)
      .background(avatar.bgColor)
      .border(2.5.dp, avatar.accentColor, CircleShape)
      .clickable(enabled = isInteractive) { showPicker = true },
    contentAlignment = Alignment.Center,
  ) {
    // Custom Vector Canvas Drawing Face Base + Emoji Accent
    Canvas(modifier = Modifier.size(size)) {
      val centerOffset = Offset(this.size.width / 2, this.size.height / 2)
      val radius = this.size.width / 2

      // Subtle ambient glowing ring
      drawCircle(
        color = avatar.accentColor.copy(alpha = 0.25f),
        radius = radius - 3.dp.toPx(),
        center = centerOffset,
        style = Stroke(width = 2.dp.toPx()),
      )
    }

    // Persona Big Emoji & Badge
    Text(
      text = avatar.emoji,
      fontSize = (size.value * 0.46f).sp,
    )
  }

  // Avatar Picker Dialog when student taps profile picture
  if (showPicker && isInteractive) {
    AlertDialog(
      onDismissRequest = { showPicker = false },
      title = {
        Text(
          text = "Choose Your Student Avatar",
          fontWeight = FontWeight.ExtraBold,
          fontSize = 18.sp,
          color = BlackPrimary,
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Pick an animated avatar that represents your campus personality:",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted,
          )

          Spacer(modifier = Modifier.height(6.dp))

          StudentAvatars.list.forEach { item ->
            val isSelected = item.id == avatarId
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) item.bgColor.copy(alpha = 0.12f) else SoftGray)
                .border(
                  width = if (isSelected) 2.dp else 1.dp,
                  color = if (isSelected) item.accentColor else BorderGray.copy(alpha = 0.6f),
                  shape = RoundedCornerShape(14.dp),
                )
                .clickable {
                  onAvatarChanged(item.id)
                  showPicker = false
                }
                .padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(item.bgColor)
                    .border(2.dp, item.accentColor, CircleShape),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(text = item.emoji, fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.padding(start = 12.dp))

                Column {
                  Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDark,
                  )
                  Text(
                    text = item.subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted,
                  )
                }
              }

              if (isSelected) {
                Box(
                  modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(item.accentColor),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = PureWhite,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showPicker = false }) {
          Text("Done", color = DeliveryOrange, fontWeight = FontWeight.Bold)
        }
      },
      containerColor = PureWhite,
      shape = RoundedCornerShape(22.dp),
    )
  }
}
