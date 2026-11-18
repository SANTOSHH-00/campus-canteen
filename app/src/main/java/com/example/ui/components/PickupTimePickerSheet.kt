// PickupTimePickerSheet: Reusable Jetpack Compose UI component
package com.example.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.DrawerAmberLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class SimplePickupSlot(
  val minutes: Int,
  val label: String,
)

/**
 * Clean, minimal, student-friendly pickup arrival time selector.
 * Simple layout, readable typography, no robotic or creepy text.
 */
@Composable
fun PickupTimePickerSheet(
  initialTimeStr: String = "12:45 PM",
  canteenName: String = "Campus Canteen",
  canteenTimings: String = "7:00 AM – 10:00 PM",
  onTimeConfirmed: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

  val quickSlots = remember {
    listOf(
      SimplePickupSlot(15, "15m"),
      SimplePickupSlot(30, "30m"),
      SimplePickupSlot(45, "45m"),
      SimplePickupSlot(60, "1h"),
      SimplePickupSlot(120, "2h"),
      SimplePickupSlot(180, "3h"),
    )
  }

  var selectedSlotMinutes by remember { mutableIntStateOf(30) }
  var validationError by remember { mutableStateOf<String?>(null) }

  var targetCal by remember {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, 30)
    mutableStateOf(cal)
  }

  fun validateTime(cal: Calendar): String? {
    val nowCal = Calendar.getInstance()
    nowCal.add(Calendar.MINUTE, -2) // 2 min grace
    if (cal.before(nowCal)) {
      return "Pickup time cannot be in the past"
    }

    val closing = extractClosingTime(canteenTimings)
    val parsed = try {
      SimpleDateFormat("h:mm a", Locale.getDefault()).parse(closing)
    } catch (_: Exception) { null }

    if (parsed != null) {
      val closeCal = Calendar.getInstance().apply {
        val temp = Calendar.getInstance().apply { time = parsed }
        set(Calendar.HOUR_OF_DAY, temp.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, temp.get(Calendar.MINUTE))
        set(Calendar.SECOND, 0)
      }
      if (cal.after(closeCal)) {
        return "Canteen closes at $closing"
      }
    }
    return null
  }

  fun updateMinutes(minutesToAdd: Int) {
    val safeMinutes = minutesToAdd.coerceIn(5, 360)
    val newCal = Calendar.getInstance()
    newCal.add(Calendar.MINUTE, safeMinutes)
    targetCal = newCal
    selectedSlotMinutes = safeMinutes
    validationError = validateTime(newCal)
  }

  val displayPickupTime = timeFormat.format(targetCal.time)

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = PureWhite,
      shadowElevation = 16.dp,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 6.dp),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(DrawerAmberLight),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.AccessTime,
                contentDescription = null,
                tint = DrawerAmber,
                modifier = Modifier.size(18.dp),
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Pickup Time",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
              Text(
                text = "Collect at $canteenName",
                fontSize = 12.sp,
                color = TextMuted,
              )
            }
          }

          IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(20.dp))
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WarmCream)
            .border(1.dp, DrawerAmber.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(vertical = 18.dp, horizontal = 16.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = displayPickupTime,
              fontSize = 32.sp,
              fontWeight = FontWeight.ExtraBold,
              color = TextDark,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Ready in ~$selectedSlotMinutes mins from now",
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium,
              color = DrawerAmber,
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
          text = "Quick Select",
          fontSize = 12.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = TextDark,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          quickSlots.forEach { slot ->
            val isSelected = selectedSlotMinutes == slot.minutes
            Box(
              modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) DrawerAmber else SoftGray.copy(alpha = 0.5f))
                .border(
                  width = 1.dp,
                  color = if (isSelected) DrawerAmber else BorderGray.copy(alpha = 0.5f),
                  shape = RoundedCornerShape(12.dp),
                )
                .clickable { updateMinutes(slot.minutes) },
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = slot.label,
                fontSize = 13.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) PureWhite else TextDark,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SoftGray.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Adjust timing",
            fontSize = 12.5.sp,
            color = TextDark,
            fontWeight = FontWeight.Medium,
          )

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            // [- 5m]
            Box(
              modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selectedSlotMinutes > 5) PureWhite else Color.Transparent)
                .border(1.dp, BorderGray.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .clickable(enabled = selectedSlotMinutes > 5) {
                  updateMinutes(selectedSlotMinutes - 5)
                },
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Minus 5 mins",
                tint = if (selectedSlotMinutes > 5) TextDark else BorderGray,
                modifier = Modifier.size(16.dp),
              )
            }

            Text(
              text = "$selectedSlotMinutes m",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark,
              modifier = Modifier.width(42.dp),
              textAlign = TextAlign.Center,
            )

            // [+ 5m]
            Box(
              modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PureWhite)
                .border(1.dp, BorderGray.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .clickable {
                  updateMinutes(selectedSlotMinutes + 5)
                },
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Plus 5 mins",
                tint = TextDark,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Exact Clock Time Picker Button (Useful for class schedules, e.g. "12:30 PM")
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PureWhite)
            .border(1.dp, DrawerAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable {
              val hour = targetCal.get(Calendar.HOUR_OF_DAY)
              val min = targetCal.get(Calendar.MINUTE)
              android.app.TimePickerDialog(
                context,
                { _, selectedHour, selectedMinute ->
                  val newCal = Calendar.getInstance()
                  newCal.set(Calendar.HOUR_OF_DAY, selectedHour)
                  newCal.set(Calendar.MINUTE, selectedMinute)
                  newCal.set(Calendar.SECOND, 0)
                  val diffMinutes = ((newCal.timeInMillis - Calendar.getInstance().timeInMillis) / 60000).toInt()
                  targetCal = newCal
                  selectedSlotMinutes = diffMinutes.coerceAtLeast(0)
                  validationError = validateTime(newCal)
                },
                hour,
                min,
                false,
              ).show()
            }
            .padding(vertical = 9.dp),
          contentAlignment = Alignment.Center,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Outlined.AccessTime,
              contentDescription = null,
              tint = DrawerAmber,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Pick Custom Clock Time (e.g. after class)",
              fontSize = 12.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = DrawerAmber,
            )
          }
        }

        if (validationError != null) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = validationError!!,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFDC2626),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val isConfirmEnabled = validationError == null

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(if (isConfirmEnabled) DrawerAmber else BorderGray)
            .clickable(enabled = isConfirmEnabled) {
              onTimeConfirmed(displayPickupTime)
              onDismiss()
            },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "Confirm · $displayPickupTime",
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = PureWhite,
          )
        }
      }
    }
  }
}

private fun extractClosingTime(timings: String): String {
  val parts = timings.split("–", "-", "—")
  return if (parts.size >= 2) parts[1].trim() else "10:00 PM"
}
