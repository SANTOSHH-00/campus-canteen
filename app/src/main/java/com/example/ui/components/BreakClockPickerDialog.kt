package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.CardSurface
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.DeliveryOrangeLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import java.util.Calendar
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class ClockPickerMode {
  HOURS,
  MINUTES,
}

@Composable
fun BreakClockPickerDialog(
  initialStart: String = "12:30 PM",
  initialEnd: String = "1:00 PM",
  onDismiss: () -> Unit,
  onConfirm: (start: String, end: String, minutesLeft: Int) -> Unit,
) {
  // Parse initial values
  var selectedHour by remember { mutableIntStateOf(12) }
  var selectedMinute by remember { mutableIntStateOf(30) }
  var isAm by remember { mutableStateOf(false) } // PM by default for canteen lunch
  var selectedDurationMinutes by remember { mutableIntStateOf(30) }
  var pickerMode by remember { mutableStateOf(ClockPickerMode.HOURS) }

  // Quick initial parse
  LaunchedEffect(initialStart) {
    try {
      val parts = initialStart.trim().split(" ")
      val timeParts = parts[0].split(":")
      selectedHour = timeParts[0].toInt().let { if (it == 0) 12 else it }
      selectedMinute = timeParts[1].toInt()
      isAm = parts.getOrNull(1)?.equals("AM", ignoreCase = true) == true
    } catch (e: Exception) {
      selectedHour = 12
      selectedMinute = 30
      isAm = false
    }
  }

  // Calculate formatted start time, end time, and minutes left
  val startFormatted = String.format(Locale.getDefault(), "%d:%02d %s", selectedHour, selectedMinute, if (isAm) "AM" else "PM")

  // Calculate end time
  val totalStartMinutes = (if (selectedHour % 12 == 0) (if (isAm) 0 else 12) else selectedHour + (if (isAm) 0 else 12)) * 60 + selectedMinute
  val totalEndMinutes = (totalStartMinutes + selectedDurationMinutes) % (24 * 60)
  val endHour24 = totalEndMinutes / 60
  val endMin = totalEndMinutes % 60
  val endIsAm = endHour24 < 12
  val endHour12 = when {
    endHour24 == 0 -> 12
    endHour24 > 12 -> endHour24 - 12
    else -> endHour24
  }
  val endFormatted = String.format(Locale.getDefault(), "%d:%02d %s", endHour12, endMin, if (endIsAm) "AM" else "PM")

  // Calculate realistic countdown
  val now = Calendar.getInstance()
  val currentTotalMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
  val diffMinutes = totalStartMinutes - currentTotalMinutes
  val minutesCountdown = when {
    diffMinutes in 1..240 -> diffMinutes
    diffMinutes in -selectedDurationMinutes..0 -> (selectedDurationMinutes + diffMinutes).coerceAtLeast(1)
    else -> selectedDurationMinutes - 2 // default representative display
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(26.dp),
      color = PureWhite,
      shadowElevation = 16.dp,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // ── Top Bar: Title & Close ──────────────────────────────────────────
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(DeliveryOrangeLight),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                tint = DeliveryOrange,
                modifier = Modifier.size(20.dp),
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Set Break Schedule",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                color = BlackPrimary,
              )
              Text(
                text = "Interactive Clock Selector",
                fontSize = 11.sp,
                color = TextMuted,
              )
            }
          }

          IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Digital Time Display + AM/PM Toggle ─────────────────────────────
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SoftGray)
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Hour & Minute Digits
          Row(verticalAlignment = Alignment.CenterVertically) {
            // Hour Button
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (pickerMode == ClockPickerMode.HOURS) DeliveryOrange else PureWhite)
                .clickable { pickerMode = ClockPickerMode.HOURS }
                .padding(horizontal = 14.dp, vertical = 8.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = String.format(Locale.getDefault(), "%02d", selectedHour),
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (pickerMode == ClockPickerMode.HOURS) PureWhite else BlackPrimary,
              )
            }

            Text(
              text = ":",
              fontSize = 22.sp,
              fontWeight = FontWeight.Bold,
              color = BlackPrimary,
              modifier = Modifier.padding(horizontal = 6.dp),
            )

            // Minute Button
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (pickerMode == ClockPickerMode.MINUTES) DeliveryOrange else PureWhite)
                .clickable { pickerMode = ClockPickerMode.MINUTES }
                .padding(horizontal = 14.dp, vertical = 8.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = String.format(Locale.getDefault(), "%02d", selectedMinute),
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (pickerMode == ClockPickerMode.MINUTES) PureWhite else BlackPrimary,
              )
            }
          }

          // AM / PM Segmented Selector
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(PureWhite)
              .border(1.dp, BorderGray.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
              .padding(3.dp),
          ) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(if (isAm) DeliveryOrange else Color.Transparent)
                .clickable { isAm = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "AM",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAm) PureWhite else TextMuted,
              )
            }
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(if (!isAm) DeliveryOrange else Color.Transparent)
                .clickable { isAm = false }
                .padding(horizontal = 10.dp, vertical = 6.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "PM",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (!isAm) PureWhite else TextMuted,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Mode label
        Text(
          text = if (pickerMode == ClockPickerMode.HOURS) "Tap to select Break Start Hour" else "Tap to select Break Start Minute",
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          color = TextMuted,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Interactive Clock Layout Dial ───────────────────────────────────
        InteractiveClockDial(
          mode = pickerMode,
          selectedValue = if (pickerMode == ClockPickerMode.HOURS) selectedHour else selectedMinute,
          onValueSelected = { value ->
            if (pickerMode == ClockPickerMode.HOURS) {
              selectedHour = value
              pickerMode = ClockPickerMode.MINUTES // automatically transition to minute selection
            } else {
              selectedMinute = value
            }
          },
          modifier = Modifier.size(210.dp),
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ── Break Duration Options ──────────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Break Duration",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = BlackPrimary,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            listOf(15, 20, 30, 45, 60).forEach { dur ->
              val isSelected = (selectedDurationMinutes == dur)
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(12.dp))
                  .background(if (isSelected) DeliveryOrange else SoftGray)
                  .clickable { selectedDurationMinutes = dur }
                  .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "${dur}m",
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) PureWhite else BlackPrimary,
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Quick Campus Shortcuts ──────────────────────────────────────────
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          QuickShortcutChip(
            icon = "⚡",
            label = "Starts in 15m",
            onClick = {
              val cal = Calendar.getInstance()
              cal.add(Calendar.MINUTE, 15)
              val h = cal.get(Calendar.HOUR)
              selectedHour = if (h == 0) 12 else h
              selectedMinute = (cal.get(Calendar.MINUTE) / 5) * 5
              isAm = cal.get(Calendar.AM_PM) == Calendar.AM
            },
            modifier = Modifier.weight(1f),
          )
          QuickShortcutChip(
            icon = "🍱",
            label = "Lunch (12:30 PM)",
            onClick = {
              selectedHour = 12
              selectedMinute = 30
              isAm = false
              selectedDurationMinutes = 30
            },
            modifier = Modifier.weight(1f),
          )
          QuickShortcutChip(
            icon = "☕",
            label = "Tea (3:30 PM)",
            onClick = {
              selectedHour = 3
              selectedMinute = 30
              isAm = false
              selectedDurationMinutes = 20
            },
            modifier = Modifier.weight(1f),
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Result Summary Card ─────────────────────────────────────────────
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DeliveryOrangeLight)
            .border(1.dp, DeliveryOrange.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = "SELECTED BREAK",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = DeliveryOrange,
                letterSpacing = 0.5.sp,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "$startFormatted – $endFormatted",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = BlackPrimary,
              )
            }

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(PureWhite)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
              Text(
                text = "~$minutesCountdown min left",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = DeliveryOrange,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Bottom Action Buttons ───────────────────────────────────────────
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onDismiss) {
            Text("Cancel", color = TextMuted, fontWeight = FontWeight.SemiBold)
          }
          Spacer(modifier = Modifier.width(10.dp))
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(14.dp))
              .background(DeliveryOrange)
              .clickable {
                onConfirm(startFormatted, endFormatted, minutesCountdown)
              }
              .padding(horizontal = 22.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Check, contentDescription = null, tint = PureWhite, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Save Break", fontWeight = FontWeight.Bold, color = PureWhite, fontSize = 13.sp)
            }
          }
        }
      }
    }
  }
}

/**
 * Custom interactive Clock Dial Canvas + Number Layout
 */
@Composable
private fun InteractiveClockDial(
  mode: ClockPickerMode,
  selectedValue: Int,
  onValueSelected: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val values = if (mode == ClockPickerMode.HOURS) {
    listOf(12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
  } else {
    listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55)
  }

  Box(
    modifier = modifier
      .clip(CircleShape)
      .background(SoftGray)
      .border(1.5.dp, BorderGray.copy(alpha = 0.5f), CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    // Draw Center Pivot & Clock Hand
    val angleDegrees = if (mode == ClockPickerMode.HOURS) {
      (selectedValue % 12) * 30.0 - 90.0
    } else {
      (selectedValue % 60) * 6.0 - 90.0
    }
    val angleRad = Math.toRadians(angleDegrees)

    Canvas(modifier = Modifier.matchParentSize()) {
      val center = Offset(size.width / 2, size.height / 2)
      val radius = size.width / 2 - 32.dp.toPx()

      val handEnd = Offset(
        x = center.x + (radius * cos(angleRad)).toFloat(),
        y = center.y + (radius * sin(angleRad)).toFloat(),
      )

      // Clock Hand Line
      drawLine(
        color = DeliveryOrange,
        start = center,
        end = handEnd,
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round,
      )

      // Center pivot point
      drawCircle(
        color = DeliveryOrange,
        radius = 5.dp.toPx(),
        center = center,
      )

      // Selected value background disc
      drawCircle(
        color = DeliveryOrange,
        radius = 16.dp.toPx(),
        center = handEnd,
      )
    }

    // Interactive Tap Detector & Number Layout on the clock face
    Box(
      modifier = Modifier
        .matchParentSize()
        .pointerInput(mode) {
          detectTapGestures { offset ->
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val dx = offset.x - centerX
            val dy = offset.y - centerY
            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 90.0
            if (angle < 0) angle += 360.0

            if (mode == ClockPickerMode.HOURS) {
              val rawHour = Math.round(angle / 30.0).toInt()
              val hour = when (rawHour) {
                0 -> 12
                13 -> 1
                else -> rawHour
              }
              onValueSelected(hour)
            } else {
              val rawMin = (Math.round(angle / 6.0).toInt() / 5) * 5
              val minute = when {
                rawMin >= 60 -> 0
                else -> rawMin
              }
              onValueSelected(minute)
            }
          }
        }
    ) {
      // Direct text overlays for crystal-clear readability
      values.forEach { value ->
        val itemAngleDeg = if (mode == ClockPickerMode.HOURS) {
          (value % 12) * 30.0 - 90.0
        } else {
          (value % 60) * 6.0 - 90.0
        }
        val itemAngleRad = Math.toRadians(itemAngleDeg)
        val isSelected = (value == selectedValue)

        val dialRadius = 78.0
        val x = (dialRadius * cos(itemAngleRad)).toFloat()
        val y = (dialRadius * sin(itemAngleRad)).toFloat()

        Box(
          modifier = Modifier
            .size(30.dp)
            .align(Alignment.Center)
            .offset(x = x.dp, y = y.dp)
            .clip(CircleShape)
            .clickable { onValueSelected(value) },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = if (mode == ClockPickerMode.HOURS) value.toString() else String.format(Locale.getDefault(), "%02d", value),
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isSelected) PureWhite else TextDark,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}

@Composable
private fun QuickShortcutChip(
  icon: String,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(SoftGray)
      .clickable { onClick() }
      .padding(vertical = 8.dp, horizontal = 4.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(icon, fontSize = 15.sp)
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = TextDark,
        textAlign = TextAlign.Center,
        maxLines = 1,
      )
    }
  }
}


