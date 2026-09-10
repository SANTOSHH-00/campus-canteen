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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class PickupTimeSlot(
  val minutesFromNow: Int,
  val label: String,
)

/**
 * Clean, modern Pickup Time Selector with clear chronological order
 * and elegant typography (no awkward emojis or creepy text).
 */
@Composable
fun PickupTimePickerSheet(
  initialTimeStr: String = "12:45 PM",
  canteenName: String = "Campus Canteen",
  onTimeConfirmed: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

  // Chronological ordered slots
  val slots = remember {
    listOf(
      PickupTimeSlot(15, "15 mins"),
      PickupTimeSlot(30, "30 mins"),
      PickupTimeSlot(45, "45 mins"),
      PickupTimeSlot(60, "1 hour"),
    )
  }

  var selectedSlotMinutes by remember { mutableIntStateOf(30) }

  var targetCal by remember {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, 30)
    mutableStateOf(cal)
  }

  fun updateTime(minutesToAdd: Int) {
    val safeMinutes = minutesToAdd.coerceIn(5, 180)
    val newCal = Calendar.getInstance()
    newCal.add(Calendar.MINUTE, safeMinutes)
    targetCal = newCal
    selectedSlotMinutes = safeMinutes
  }

  val displayPickupTime = timeFormat.format(targetCal.time)

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(26.dp),
      color = PureWhite,
      shadowElevation = 18.dp,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(22.dp),
      ) {
        // ── Top Header Row ──────────────────────────────────────────────────
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
                .background(SoftGray),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = "Pickup Time",
                tint = BlackPrimary,
                modifier = Modifier.size(20.dp),
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Choose Pickup Time",
                fontSize = 17.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = BlackPrimary,
              )
              Text(
                text = "Ready at $canteenName counter",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted,
              )
            }
          }

          IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Hero Time Display Card ──────────────────────────────────────────
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF9F7F4))
            .border(1.dp, BorderGray.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(vertical = 16.dp, horizontal = 18.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "ESTIMATED READY TIME",
              fontSize = 10.5.sp,
              fontWeight = FontWeight.ExtraBold,
              color = TextMuted,
              letterSpacing = 0.8.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = displayPickupTime,
              fontSize = 32.sp,
              fontWeight = FontWeight.Black,
              color = BlackPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Ready in ~$selectedSlotMinutes minutes from now",
              fontSize = 12.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = DeliveryOrange,
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Chronological Quick Intervals ───────────────────────────────────
        Text(
          text = "SELECT AN INTERVAL",
          fontSize = 11.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
          letterSpacing = 0.5.sp,
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2x2 Grid of clean time slot cards
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          slots.chunked(2).forEach { rowSlots ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              rowSlots.forEach { slot ->
                val isSelected = selectedSlotMinutes == slot.minutesFromNow
                val slotCal = remember(slot.minutesFromNow) {
                  val c = Calendar.getInstance()
                  c.add(Calendar.MINUTE, slot.minutesFromNow)
                  timeFormat.format(c.time)
                }

                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) BlackPrimary else PureWhite)
                    .border(
                      width = if (isSelected) 1.5.dp else 1.dp,
                      color = if (isSelected) BlackPrimary else BorderGray.copy(alpha = 0.7f),
                      shape = RoundedCornerShape(14.dp),
                    )
                    .clickable { updateTime(slot.minutesFromNow) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column {
                      Text(
                        text = slot.label,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) PureWhite else TextDark,
                      )
                      Text(
                        text = slotCal,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) PureWhite.copy(alpha = 0.75f) else TextMuted,
                      )
                    }

                    if (isSelected) {
                      Box(
                        modifier = Modifier
                          .size(18.dp)
                          .clip(CircleShape)
                          .background(PureWhite),
                        contentAlignment = Alignment.Center,
                      ) {
                        Icon(
                          imageVector = Icons.Default.Check,
                          contentDescription = "Selected",
                          tint = BlackPrimary,
                          modifier = Modifier.size(12.dp),
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Fine-Tune Steppers ──────────────────────────────────────────────
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "ADJUST MINUTES",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextDark,
            letterSpacing = 0.5.sp,
          )

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // - 5m chip
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SoftGray)
                .clickable {
                  if (selectedSlotMinutes > 5) {
                    updateTime(selectedSlotMinutes - 5)
                  }
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "- 5 min",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
            }

            // + 5m chip
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SoftGray)
                .clickable {
                  updateTime(selectedSlotMinutes + 5)
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "+ 5 min",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
            }

            // + 15m chip
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SoftGray)
                .clickable {
                  updateTime(selectedSlotMinutes + 15)
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "+ 15 min",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Clean Assurance Note ───────────────────────────────────────────
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF3F4F6))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.AccessTime,
              contentDescription = null,
              tint = Color(0xFF6B7280),
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Your order will be freshly prepared for your chosen time.",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFF4B5563),
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Confirm Button ─────────────────────────────────────────────────
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BlackPrimary)
            .clickable {
              onTimeConfirmed(displayPickupTime)
              onDismiss()
            }
            .padding(vertical = 14.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "Confirm Pickup for $displayPickupTime →",
            fontSize = 14.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = PureWhite,
          )
        }
      }
    }
  }
}
