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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ShoppingBag
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
import com.example.ui.theme.DeliveryOrangeLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class QuickPickupSlot(
  val minutesFromNow: Int,
  val title: String,
  val subtitle: String,
  val emoji: String,
)

/**
 * Purpose-built, minimal Pickup Time Selector for hot food counter collection.
 * Replaces the break schedule analog clock with a streamlined food arrival scheduler.
 */
@Composable
fun PickupTimePickerSheet(
  initialTimeStr: String = "12:45 PM",
  canteenName: String = "Govinda's Kitchen",
  onTimeConfirmed: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val nowCal = remember { Calendar.getInstance() }
  val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

  // Quick arrival slots
  val quickSlots = remember {
    listOf(
      QuickPickupSlot(15, "In 15 mins", "Fresh Batch ready", "⚡"),
      QuickPickupSlot(30, "In 30 mins", "Next Class dismissal", "🔔"),
      QuickPickupSlot(45, "In 45 mins", "Lunch break start", "🍱"),
      QuickPickupSlot(60, "In 60 mins", "Post-lab session", "⏰"),
    )
  }

  var selectedSlotMinutes by remember { mutableIntStateOf(30) }

  // Custom Time Adjustments
  var targetCal by remember {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, 30)
    mutableStateOf(cal)
  }

  fun updateTime(minutesToAdd: Int) {
    val newCal = Calendar.getInstance()
    newCal.add(Calendar.MINUTE, minutesToAdd)
    targetCal = newCal
    selectedSlotMinutes = minutesToAdd
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
                .background(DeliveryOrangeLight),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.ShoppingBag,
                contentDescription = null,
                tint = DeliveryOrange,
                modifier = Modifier.size(20.dp),
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Schedule Pickup",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = BlackPrimary,
              )
              Text(
                text = "Coordinated hot batch pickup",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
              )
            }
          }

          IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Quick Departure / Arrival Slots ─────────────────────────────────
        Text(
          text = "WHEN CAN YOU REACH THE COUNTER?",
          fontSize = 11.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
          letterSpacing = 0.5.sp,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          quickSlots.forEach { slot ->
            val isSelected = selectedSlotMinutes == slot.minutesFromNow
            val slotTimeCal = remember(slot.minutesFromNow) {
              val c = Calendar.getInstance()
              c.add(Calendar.MINUTE, slot.minutesFromNow)
              timeFormat.format(c.time)
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) DeliveryOrangeLight else SoftGray)
                .border(
                  width = if (isSelected) 2.dp else 1.dp,
                  color = if (isSelected) DeliveryOrange else BorderGray.copy(alpha = 0.5f),
                  shape = RoundedCornerShape(14.dp),
                )
                .clickable {
                  updateTime(slot.minutesFromNow)
                }
                .padding(horizontal = 14.dp, vertical = 11.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = slot.emoji, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = "${slot.title} (~$slotTimeCal)",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                  )
                  Text(
                    text = slot.subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted,
                  )
                }
              }

              if (isSelected) {
                Box(
                  modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(DeliveryOrange),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = PureWhite,
                    modifier = Modifier.size(14.dp),
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Minimal Quick-Nudge Steppers (+10m, +15m) ───────────────────────
        Text(
          text = "OR NUDGE EXACT MINUTES",
          fontSize = 11.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
          letterSpacing = 0.5.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(10, 15, 20, 30).forEach { mins ->
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(PureWhite)
                .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                .clickable {
                  updateTime(selectedSlotMinutes + mins)
                }
                .padding(vertical = 8.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "+$mins min",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Hot Kitchen Guarantee Banner ───────────────────────────────────
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFF6EB))
            .border(1.dp, Color(0xFFFDE6D2), RoundedCornerShape(14.dp))
            .padding(12.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.LocalFireDepartment,
              contentDescription = null,
              tint = Color(0xFFEA580C),
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = "Guaranteed Hot & Fresh at $displayPickupTime",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDark,
              )
              Text(
                text = "Prepared at $canteenName counter right before your arrival",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
              )
            }
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
