package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.CampusBlockCanteen
import com.example.data.SampleFoodData
import com.example.util.CanteenImageHelper

// ── Private Theme Palette (Matches Reference Design) ──────────────────────────
private val OrangeBrand = Color(0xFFFF6433)
private val OrangeBorder = Color(0xFFFF7A45)
private val OrangeBgSoft = Color(0xFFFFF1EB)
private val SelectedCardBg = Color(0xFFFFFBF9)
private val BorderMuted = Color(0xFFF0F1F3)
private val DarkTitle = Color(0xFF111827)
private val SubtitleGray = Color(0xFF6B7280)
private val StatusGreen = Color(0xFF10B981)
private val StatusRed = Color(0xFFEF4444)

/**
 * Campus Canteen Selection Sheet designed exactly per user's reference mockup:
 * - Top drag handle
 * - "Select Canteen" header with circular orange location pin
 * - Rounded cards with orange border + circular orange checkmark for selected canteen
 * - Canteen profile photo (connected to owner uploaded profile picture or initials avatar)
 * - Canteen name, location, and real-time open/closed + timings status
 * - No walk distance text (per user request)
 * - "Use Current Location" feature at bottom
 */
@Composable
fun BlockCanteenSelectorSheet(
  currentCanteen: CampusBlockCanteen,
  currentBlock: Int? = 26,
  allCanteens: List<CampusBlockCanteen> = SampleFoodData.campusCanteens,
  onCanteenSelected: (CampusBlockCanteen, Int?) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.52f))
        .clickable { onDismiss() },
      contentAlignment = Alignment.BottomCenter,
    ) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .fillMaxHeight(0.88f)
          .clickable(enabled = false) {}, // Prevent dismiss when tapping inside sheet
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color.White,
        shadowElevation = 16.dp,
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        ) {
          // ── Drag Handle ──────────────────────────────────────────────────────
          Spacer(modifier = Modifier.height(10.dp))
          Box(
            modifier = Modifier
              .align(Alignment.CenterHorizontally)
              .width(42.dp)
              .height(4.5.dp)
              .clip(RoundedCornerShape(3.dp))
              .background(Color(0xFFE5E7EB)),
          )

          Spacer(modifier = Modifier.height(16.dp))

          // ── Header: Orange Location Pin + Title & Subtitle ────────────────────
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(OrangeBgSoft),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Select Canteen",
                tint = OrangeBrand,
                modifier = Modifier.size(23.dp),
              )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
              Text(
                text = "Select Canteen",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DarkTitle,
              )
              Text(
                text = "Choose a canteen near your class/lab",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Normal,
                color = SubtitleGray,
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // ── Canteen List ─────────────────────────────────────────────────────
          Column(
            modifier = Modifier
              .weight(1f)
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(11.dp),
          ) {
            allCanteens.forEach { canteen ->
              val isSelected = (canteen.id == currentCanteen.id)

              CanteenCardItem(
                canteen = canteen,
                isSelected = isSelected,
                onClick = {
                  onCanteenSelected(canteen, canteen.primaryBlocks.firstOrNull())
                  onDismiss()
                },
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Bottom Action: "Use Current Location" ───────────────────────────
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                  Toast.makeText(context, "Location features will be enabled in a future update", Toast.LENGTH_SHORT).show()
                }
                .padding(vertical = 12.dp, horizontal = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(OrangeBgSoft),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.NearMe,
                  contentDescription = "Current Location",
                  tint = OrangeBrand,
                  modifier = Modifier.size(20.dp),
                )
              }

              Spacer(modifier = Modifier.width(12.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Use Current Location",
                  fontSize = 14.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = DarkTitle,
                )
                Text(
                  text = "Find canteens near you",
                  fontSize = 12.sp,
                  color = SubtitleGray,
                )
              }

              Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(20.dp),
              )
            }

            Spacer(modifier = Modifier.height(16.dp))
          }
        }
      }
    }
  }
}

/**
 * Individual Canteen Item Card matching reference design:
 * - Square image / monogram avatar
 * - Bold name, location
 * - Real-time open/closed + timings status
 * - Selected orange outline border + checkmark badge
 * - No walk distance text
 */
@Composable
private fun CanteenCardItem(
  canteen: CampusBlockCanteen,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .border(
        width = if (isSelected) 1.6.dp else 1.dp,
        color = if (isSelected) OrangeBorder else BorderMuted,
        shape = RoundedCornerShape(16.dp),
      )
      .background(if (isSelected) SelectedCardBg else Color.White)
      .clickable { onClick() }
      .padding(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // ── Left: Canteen Profile Image / Initials Monogram Avatar (Circle) ───
      val context = LocalContext.current
      val imageSource = CanteenImageHelper.resolveCanteenImageSource(context, canteen.id, canteen.imageUrl)

      Box(
        modifier = Modifier
          .size(64.dp)
          .clip(CircleShape)
          .background(
            brush = Brush.linearGradient(
              colors = listOf(Color(0xFF2C2D30), Color(0xFF1C1D20))
            )
          )
          .border(
            width = if (isSelected) 2.dp else 1.2.dp,
            color = if (isSelected) OrangeBorder else Color(0xFFE5E7EB),
            shape = CircleShape,
          ),
        contentAlignment = Alignment.Center,
      ) {
        if (imageSource != null) {
          SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
              .data(imageSource)
              .crossfade(false)
              .build(),
            contentDescription = canteen.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxSize()
              .clip(CircleShape),
            error = {
              CanteenMonogram(canteen.name)
            },
          )
        } else {
          CanteenMonogram(canteen.name)
        }
      }

      Spacer(modifier = Modifier.width(14.dp))

      // ── Middle: Canteen Name, Location, Timings ──────────────────────────
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = canteen.name,
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = DarkTitle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
          text = canteen.betweenBlocks,
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Normal,
          color = SubtitleGray,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(5.dp))

        // Open/Closed Status with dot
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(6.5.dp)
              .clip(CircleShape)
              .background(if (canteen.isOpen) StatusGreen else StatusRed),
          )

          Spacer(modifier = Modifier.width(5.dp))

          val statusText = if (canteen.isOpen) {
            "Open · ${canteen.timings}"
          } else {
            val reason = canteen.closeReason?.ifBlank { null } ?: "Opens 8:00 AM"
            if (reason.startsWith("Opens", ignoreCase = true)) {
              "Closed · $reason"
            } else {
              "Closed · $reason"
            }
          }

          Text(
            text = statusText,
            fontSize = 11.8.sp,
            fontWeight = FontWeight.Medium,
            color = if (canteen.isOpen) Color(0xFF047857) else Color(0xFFB91C1C),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // ── Right: Circular Orange Checkmark for Selected ────────────────────
      if (isSelected) {
        Spacer(modifier = Modifier.width(10.dp))
        Box(
          modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(OrangeBorder),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Selected",
            tint = Color.White,
            modifier = Modifier.size(15.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun CanteenMonogram(name: String) {
  val initials = name
    .split(" ")
    .filter { it.isNotBlank() }
    .take(2)
    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
    .joinToString("")
    .ifBlank { "CB" }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = Icons.Default.Restaurant,
      contentDescription = null,
      tint = OrangeBorder.copy(alpha = 0.85f),
      modifier = Modifier.size(18.dp),
    )
    Spacer(modifier = Modifier.height(1.5.dp))
    Text(
      text = initials,
      fontSize = 12.sp,
      fontWeight = FontWeight.ExtraBold,
      color = Color(0xFFF5F0E8),
      letterSpacing = 0.8.sp,
    )
  }
}

