package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.DeliveryOrangeLight
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.MintFresh
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import java.util.Calendar
import java.util.Locale

enum class TimeSelectionTab {
  HOURS,
  MINUTES,
}

/**
 * Structured, Decent UI for Canteen Break Timings Selection:
 * - Order & Flow:
 *   1. Clean Header with subtitle & close button
 *   2. Campus Quick Slots (Lunch, Tea, Snack, Dinner)
 *   3. Structured Digital Time Card with AM/PM toggle & -/+ buttons
 *   4. Clean Step-by-Step Number Grid (Hours 1-12 & Minutes 00-55)
 *   5. Duration Selector (15m, 20m, 30m, 45m, 60m)
 *   6. Dynamic Live Window & Countdown Preview
 *   7. Action Buttons
 */
@Composable
fun MinimalBreakClockDialog(
  initialStart: String = "12:30 PM",
  initialEnd: String = "1:00 PM",
  onDismiss: () -> Unit,
  onConfirm: (start: String, end: String, minutesLeft: Int) -> Unit,
) {
  var selectedHour by remember { mutableIntStateOf(12) }
  var selectedMinute by remember { mutableIntStateOf(30) }
  var isAm by remember { mutableStateOf(false) }
  var selectedDurationMinutes by remember { mutableIntStateOf(30) }
  var activeTab by remember { mutableStateOf(TimeSelectionTab.HOURS) }
  var isVisible by remember { mutableStateOf(false) }

  // Parse initial time
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
    isVisible = true
  }

  // Format start time string
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

  // Calculate dynamic time remaining
  val now = Calendar.getInstance()
  val currentTotalMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
  val diffMinutes = totalStartMinutes - currentTotalMinutes
  val minutesLeft = when {
    diffMinutes in 1..240 -> diffMinutes
    diffMinutes in -selectedDurationMinutes..0 -> (selectedDurationMinutes + diffMinutes).coerceAtLeast(1)
    else -> selectedDurationMinutes
  }

  val statusMessage = when {
    diffMinutes in -selectedDurationMinutes..0 -> "🟢 Break in progress • $minutesLeft min remaining"
    diffMinutes in 1..240 -> "⏳ Break starts in $diffMinutes mins"
    else -> "⏱️ Scheduled break • $selectedDurationMinutes mins"
  }

  Dialog(onDismissRequest = onDismiss) {
    AnimatedVisibility(
      visible = isVisible,
      enter = scaleIn(initialScale = 0.94f, animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
      exit = scaleOut(targetScale = 0.94f, animationSpec = tween(180)) + fadeOut(animationSpec = tween(180)),
    ) {
      Surface(
        shape = RoundedCornerShape(26.dp),
        color = PureWhite,
        shadowElevation = 22.dp,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 2.dp),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        ) {
          // ── 1. Top Header ─────────────────────────────────────────────────
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(DeliveryOrangeLight),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.AccessTime,
                  contentDescription = null,
                  tint = DeliveryOrange,
                  modifier = Modifier.size(22.dp),
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Canteen Break Timings",
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 17.5.sp,
                  color = BlackPrimary,
                )
                Text(
                  text = "Schedule your meal break window",
                  fontSize = 12.sp,
                  color = TextMuted,
                )
              }
            }

            IconButton(
              onClick = onDismiss,
              modifier = Modifier.size(32.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = TextMuted,
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // ── 2. Campus Quick Slots (Order & Structure) ─────────────────────
          Text(
            text = "CAMPUS BREAK PRESETS",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextMuted,
            letterSpacing = 0.6.sp,
          )
          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            PresetSlotChip(
              icon = "🍱",
              title = "Lunch",
              timeText = "12:30 PM",
              isSelected = (selectedHour == 12 && selectedMinute == 30 && !isAm),
              onClick = {
                selectedHour = 12
                selectedMinute = 30
                isAm = false
                selectedDurationMinutes = 30
              },
              modifier = Modifier.weight(1f),
            )
            PresetSlotChip(
              icon = "☕",
              title = "Tea Break",
              timeText = "3:30 PM",
              isSelected = (selectedHour == 3 && selectedMinute == 30 && !isAm),
              onClick = {
                selectedHour = 3
                selectedMinute = 30
                isAm = false
                selectedDurationMinutes = 20
              },
              modifier = Modifier.weight(1f),
            )
            PresetSlotChip(
              icon = "🥪",
              title = "Snack",
              timeText = "5:00 PM",
              isSelected = (selectedHour == 5 && selectedMinute == 0 && !isAm),
              onClick = {
                selectedHour = 5
                selectedMinute = 0
                isAm = false
                selectedDurationMinutes = 20
              },
              modifier = Modifier.weight(1f),
            )
          }

          Spacer(modifier = Modifier.height(18.dp))

          // ── 3. Structured Digital Time Card + AM/PM + Nudge Buttons ───────
          Text(
            text = "CUSTOM START TIME",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextMuted,
            letterSpacing = 0.6.sp,
          )
          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(SoftGray)
              .border(1.dp, BorderGray.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
              .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            // Quick minus 5 min button
            IconButton(
              onClick = {
                val newTotal = (totalStartMinutes - 5 + 24 * 60) % (24 * 60)
                val h24 = newTotal / 60
                val m = newTotal % 60
                isAm = h24 < 12
                selectedHour = when {
                  h24 == 0 -> 12
                  h24 > 12 -> h24 - 12
                  else -> h24
                }
                selectedMinute = m
              },
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(PureWhite),
            ) {
              Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Minus 5m",
                tint = BlackPrimary,
                modifier = Modifier.size(16.dp),
              )
            }

            // Digital Time Readout (Hour : Minute)
            Row(verticalAlignment = Alignment.CenterVertically) {
              // Hour Display Box
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(if (activeTab == TimeSelectionTab.HOURS) DeliveryOrange else PureWhite)
                  .border(
                    width = if (activeTab == TimeSelectionTab.HOURS) 0.dp else 1.dp,
                    color = BorderGray.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                  )
                  .clickable { activeTab = TimeSelectionTab.HOURS }
                  .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = String.format(Locale.getDefault(), "%02d", selectedHour),
                  fontSize = 24.sp,
                  fontWeight = FontWeight.Black,
                  color = if (activeTab == TimeSelectionTab.HOURS) PureWhite else BlackPrimary,
                )
              }

              Text(
                text = ":",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = BlackPrimary,
                modifier = Modifier.padding(horizontal = 8.dp),
              )

              // Minute Display Box
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(if (activeTab == TimeSelectionTab.MINUTES) DeliveryOrange else PureWhite)
                  .border(
                    width = if (activeTab == TimeSelectionTab.MINUTES) 0.dp else 1.dp,
                    color = BorderGray.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                  )
                  .clickable { activeTab = TimeSelectionTab.MINUTES }
                  .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = String.format(Locale.getDefault(), "%02d", selectedMinute),
                  fontSize = 24.sp,
                  fontWeight = FontWeight.Black,
                  color = if (activeTab == TimeSelectionTab.MINUTES) PureWhite else BlackPrimary,
                )
              }
            }

            // Quick plus 5 min button
            IconButton(
              onClick = {
                val newTotal = (totalStartMinutes + 5) % (24 * 60)
                val h24 = newTotal / 60
                val m = newTotal % 60
                isAm = h24 < 12
                selectedHour = when {
                  h24 == 0 -> 12
                  h24 > 12 -> h24 - 12
                  else -> h24
                }
                selectedMinute = m
              },
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(PureWhite),
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Plus 5m",
                tint = BlackPrimary,
                modifier = Modifier.size(16.dp),
              )
            }

            // AM / PM Segmented Control
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(PureWhite)
                .border(1.dp, BorderGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
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
                  fontWeight = FontWeight.ExtraBold,
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
                  fontWeight = FontWeight.ExtraBold,
                  color = if (!isAm) PureWhite else TextMuted,
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // ── 4. Structured Number Grid Selector (Decent & Organized UI) ────
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(PureWhite)
              .border(1.dp, BorderGray.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
              .padding(14.dp),
          ) {
            // Mode Header with Step Indicator
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = if (activeTab == TimeSelectionTab.HOURS) "Pick Hour" else "Pick Minutes",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Black,
                  color = BlackPrimary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (activeTab == TimeSelectionTab.HOURS) "(1 to 12)" else "(5-min steps)",
                  fontSize = 11.5.sp,
                  color = TextMuted,
                )
              }

              // Tab Switcher Pill
              Row(
                modifier = Modifier
                  .clip(RoundedCornerShape(10.dp))
                  .background(SoftGray)
                  .padding(2.dp),
              ) {
                Text(
                  text = "Hours",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (activeTab == TimeSelectionTab.HOURS) PureWhite else TextMuted,
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (activeTab == TimeSelectionTab.HOURS) DeliveryOrange else Color.Transparent)
                    .clickable { activeTab = TimeSelectionTab.HOURS }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Text(
                  text = "Minutes",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (activeTab == TimeSelectionTab.MINUTES) PureWhite else TextMuted,
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (activeTab == TimeSelectionTab.MINUTES) DeliveryOrange else Color.Transparent)
                    .clickable { activeTab = TimeSelectionTab.MINUTES }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (activeTab == TimeSelectionTab.HOURS) {
              // 2-row grid of hours 1 to 12
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  (1..6).forEach { hr ->
                    NumberChip(
                      label = hr.toString(),
                      isSelected = (selectedHour == hr),
                      onClick = {
                        selectedHour = hr
                        activeTab = TimeSelectionTab.MINUTES // Smooth advance to minutes
                      },
                      modifier = Modifier.size(42.dp),
                    )
                  }
                }
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  (7..12).forEach { hr ->
                    NumberChip(
                      label = hr.toString(),
                      isSelected = (selectedHour == hr),
                      onClick = {
                        selectedHour = hr
                        activeTab = TimeSelectionTab.MINUTES // Smooth advance to minutes
                      },
                      modifier = Modifier.size(42.dp),
                    )
                  }
                }
              }
            } else {
              // 2-row grid of minutes in 5-min intervals
              val minuteOptions = listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55)
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  minuteOptions.take(6).forEach { min ->
                    NumberChip(
                      label = String.format(Locale.getDefault(), "%02d", min),
                      isSelected = (selectedMinute == min),
                      onClick = { selectedMinute = min },
                      modifier = Modifier.size(42.dp),
                    )
                  }
                }
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  minuteOptions.drop(6).forEach { min ->
                    NumberChip(
                      label = String.format(Locale.getDefault(), "%02d", min),
                      isSelected = (selectedMinute == min),
                      onClick = { selectedMinute = min },
                      modifier = Modifier.size(42.dp),
                    )
                  }
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // ── 5. Break Duration Selector ────────────────────────────────────
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "BREAK DURATION",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextMuted,
                letterSpacing = 0.6.sp,
              )
              Text(
                text = "$selectedDurationMinutes minutes total",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = DeliveryOrange,
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              listOf(15, 20, 30, 45, 60).forEach { dur ->
                val isSelected = (selectedDurationMinutes == dur)
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) DeliveryOrange else SoftGray)
                    .clickable { selectedDurationMinutes = dur }
                    .padding(vertical = 9.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = "${dur}m",
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (isSelected) PureWhite else TextDark,
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // ── 6. Live Result Summary Card ───────────────────────────────────
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(DeliveryOrangeLight)
              .border(1.dp, DeliveryOrange.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
              .padding(horizontal = 14.dp, vertical = 12.dp),
          ) {
            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Text(
                    text = "SCHEDULED BREAK WINDOW",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DeliveryOrange,
                    letterSpacing = 0.5.sp,
                  )
                  Spacer(modifier = Modifier.height(3.dp))
                  Text(
                    text = "$startFormatted – $endFormatted",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
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
                    text = "~$minutesLeft min left",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DeliveryOrange,
                  )
                }
              }

              Spacer(modifier = Modifier.height(6.dp))
              HorizontalDivider(thickness = 0.8.dp, color = DeliveryOrange.copy(alpha = 0.2f))
              Spacer(modifier = Modifier.height(6.dp))

              Text(
                text = statusMessage,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = BlackPrimary.copy(alpha = 0.85f),
              )
            }
          }

          Spacer(modifier = Modifier.height(20.dp))

          // ── 7. Action Buttons ─────────────────────────────────────────────
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TextButton(
              onClick = onDismiss,
              modifier = Modifier.weight(1f),
            ) {
              Text(
                text = "Cancel",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
              )
            }

            Button(
              onClick = {
                onConfirm(startFormatted, endFormatted, minutesLeft)
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = DeliveryOrange,
                contentColor = PureWhite,
              ),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1.4f),
            ) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Set Schedule",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PresetSlotChip(
  icon: String,
  title: String,
  timeText: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(if (isSelected) DeliveryOrangeLight else SoftGray)
      .border(
        width = if (isSelected) 1.5.dp else 1.dp,
        color = if (isSelected) DeliveryOrange else BorderGray.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
      )
      .clickable { onClick() }
      .padding(vertical = 10.dp, horizontal = 6.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(text = icon, fontSize = 18.sp)
      Spacer(modifier = Modifier.height(3.dp))
      Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = if (isSelected) DeliveryOrange else BlackPrimary,
      )
      Text(
        text = timeText,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextMuted,
      )
    }
  }
}

@Composable
private fun NumberChip(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .clip(CircleShape)
      .background(if (isSelected) DeliveryOrange else SoftGray)
      .clickable { onClick() },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      fontSize = 13.sp,
      fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
      color = if (isSelected) PureWhite else BlackPrimary,
    )
  }
}
