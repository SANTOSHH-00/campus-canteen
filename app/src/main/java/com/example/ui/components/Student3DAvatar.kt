package com.example.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted

data class StudentAvatarItem(
  val id: String,
  val name: String,
  val persona: String,
  val drawableRes: Int,
  val ringColor: Color,
)

object Student3DAvatars {
  val list = listOf(
    StudentAvatarItem("alex", "Alex Kumar", "CSE Student • Techie", R.drawable.avatar_alex, Color(0xFFF97316)),
    StudentAvatarItem("priya", "Priya Sharma", "Design & Scholar", R.drawable.avatar_priya, Color(0xFF10B981)),
    StudentAvatarItem("rohit", "Rohit Verma", "Campus Life • Energy", R.drawable.avatar_rohit, Color(0xFF3B82F6)),
    StudentAvatarItem("sneha", "Sneha Patel", "Creator • Connoisseur", R.drawable.avatar_sneha, Color(0xFFA855F7)),
  )

  fun getById(id: String): StudentAvatarItem {
    return list.firstOrNull { it.id.equals(id, ignoreCase = true) }
      ?: list.firstOrNull { id.contains(it.id, ignoreCase = true) }
      ?: list[0]
  }
}

@Composable
fun Student3DAvatar(
  avatarId: String,
  size: Dp = 80.dp,
  showCameraBadge: Boolean = true,
  onClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val avatar = Student3DAvatars.getById(avatarId)

  Box(
    modifier = modifier
      .size(size)
      .clickable(enabled = onClick != null) { onClick?.invoke() },
    contentAlignment = Alignment.Center,
  ) {
    // 3D Avatar Image
    Image(
      painter = painterResource(id = avatar.drawableRes),
      contentDescription = avatar.name,
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .size(size)
        .clip(CircleShape)
        .border(2.5.dp, Color(0xFFFFD8BF), CircleShape)
    )

    // Camera badge on bottom right (matching Image 1)
    if (showCameraBadge) {
      Box(
        modifier = Modifier
          .size(size * 0.32f)
          .align(Alignment.BottomEnd)
          .clip(CircleShape)
          .background(Color(0xFF1B5E20)) // Dark emerald green matching Image 1
          .border(1.5.dp, PureWhite, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.CameraAlt,
          contentDescription = "Edit Avatar",
          tint = PureWhite,
          modifier = Modifier.size(size * 0.17f),
        )
      }
    }
  }
}

@Composable
fun StudentAvatarPickerDialog(
  currentAvatarId: String,
  onDismiss: () -> Unit,
  onSelectAvatar: (String) -> Unit,
) {
  var selectedId by remember { mutableStateOf(currentAvatarId) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Choose Profile Avatar",
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        color = BlackPrimary,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Select your 3D campus avatar persona:",
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Medium,
          color = TextMuted,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Student3DAvatars.list.forEach { item ->
          val isSelected = item.id.equals(selectedId, ignoreCase = true)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(if (isSelected) Color(0xFFFFF4ED) else SoftGray)
              .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) DeliveryOrange else BorderGray.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
              )
              .clickable {
                selectedId = item.id
                onSelectAvatar(item.id)
                onDismiss()
              }
              .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Image(
                painter = painterResource(id = item.drawableRes),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .size(46.dp)
                  .clip(CircleShape)
                  .border(2.dp, item.ringColor, CircleShape)
              )

              Spacer(modifier = Modifier.padding(start = 12.dp))

              Column {
                Text(
                  text = item.name,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextDark,
                )
                Text(
                  text = item.persona,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Medium,
                  color = TextMuted,
                )
              }
            }

            if (isSelected) {
              Box(
                modifier = Modifier
                  .size(24.dp)
                  .clip(CircleShape)
                  .background(DeliveryOrange),
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
      TextButton(onClick = onDismiss) {
        Text("Done", color = DeliveryOrange, fontWeight = FontWeight.Bold)
      }
    },
    containerColor = PureWhite,
    shape = RoundedCornerShape(22.dp),
  )
}
