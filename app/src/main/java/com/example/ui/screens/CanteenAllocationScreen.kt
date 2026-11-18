// CanteenAllocationScreen: Jetpack Compose screen for CanteenAllocation user interface
package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firebase.CanteenDocument
import com.example.data.firebase.OwnerDocument
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.OpenGreen
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import com.example.ui.viewmodel.CanteenAllocationUiState
import com.example.ui.viewmodel.OwnerViewModel

@Composable
fun CanteenAllocationScreen(
  viewModel: OwnerViewModel,
  onAllocationSuccess: (OwnerDocument) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val allocationState by viewModel.allocationState.collectAsState()

  LaunchedEffect(Unit) {
    viewModel.loadCanteensForAllocation()
  }

  LaunchedEffect(allocationState) {
    if (allocationState is CanteenAllocationUiState.Allocated) {
      onAllocationSuccess((allocationState as CanteenAllocationUiState.Allocated).updatedOwner)
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream)
      .statusBarsPadding()
      .navigationBarsPadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp),
    ) {
      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(PureWhite)
            .clickable(role = Role.Button, onClick = onBack)
            .testTag("allocation_back_button"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = BlackPrimary,
            modifier = Modifier.size(18.dp),
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
          Text(
            text = "Select Your Canteen",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextDark,
            modifier = Modifier.testTag("allocation_title"),
          )
          Text(
            text = "Assign your owner account to your campus outlet",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted,
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      when (val state = allocationState) {
        is CanteenAllocationUiState.Loading, CanteenAllocationUiState.Idle -> {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator(
                color = BlackPrimary,
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
              )
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                text = "Loading canteens from Firestore...",
                fontSize = 14.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium,
              )
            }
          }
        }

        is CanteenAllocationUiState.Error -> {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.padding(24.dp),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(Color(0xFFFFF7ED))
                  .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(14.dp))
                  .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  Icons.Default.ErrorOutline,
                  contentDescription = null,
                  tint = Color(0xFFC2410C),
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = state.message,
                  fontSize = 13.5.sp,
                  color = Color(0xFF9A3412),
                  textAlign = TextAlign.Start,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.weight(1f),
                )
              }
              Spacer(modifier = Modifier.height(16.dp))
              IconButton(
                onClick = { viewModel.loadCanteensForAllocation() },
                modifier = Modifier
                  .clip(CircleShape)
                  .background(BlackPrimary),
              ) {
                Icon(
                  imageVector = Icons.Default.Refresh,
                  contentDescription = "Retry",
                  tint = PureWhite,
                )
              }
            }
          }
        }

        is CanteenAllocationUiState.Ready,
        is CanteenAllocationUiState.Allocating -> {
          val readyState = state as? CanteenAllocationUiState.Ready
          val canteens = readyState?.canteens ?: emptyList()
          val selectedCanteen = readyState?.selectedCanteen
          val isAllocating = state is CanteenAllocationUiState.Allocating

          if (canteens.isEmpty()) {
            Box(
              modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "No canteens found in database.\nPlease check Firestore 'canteens' collection.",
                fontSize = 14.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
              )
            }
          } else {
            LazyColumn(
              modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              item {
                Text(
                  text = "AVAILABLE CAMPUS CANTEENS (${canteens.size})",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.ExtraBold,
                  letterSpacing = 1.sp,
                  color = TextMuted,
                  modifier = Modifier.padding(bottom = 2.dp),
                )
              }

              items(canteens, key = { it.id }) { canteen ->
                val isSelected = selectedCanteen?.id == canteen.id
                CanteenAllocationCard(
                  canteen = canteen,
                  isSelected = isSelected,
                  onClick = {
                    if (!isAllocating) {
                      viewModel.selectCanteen(canteen)
                    }
                  },
                )
              }

              item {
                Spacer(modifier = Modifier.height(16.dp))
              }
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
            ) {
              val isButtonEnabled = selectedCanteen != null && !isAllocating

              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(54.dp)
                  .shadow(if (isButtonEnabled) 4.dp else 0.dp, RoundedCornerShape(28.dp))
                  .clip(RoundedCornerShape(28.dp))
                  .background(if (isButtonEnabled) BlackPrimary else BorderGray)
                  .clickable(
                    role = Role.Button,
                    enabled = isButtonEnabled,
                    onClick = { viewModel.confirmAllocation() },
                  )
                  .testTag("confirm_allocation_button"),
                contentAlignment = Alignment.Center,
              ) {
                if (isAllocating) {
                  CircularProgressIndicator(
                    color = PureWhite,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                  )
                } else {
                  Text(
                    text = if (selectedCanteen != null) "Confirm Allocation · ${selectedCanteen.name}" else "Select a Canteen Above",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isButtonEnabled) PureWhite else TextMuted,
                    maxLines = 1,
                  )
                }
              }
            }
          }
        }

        is CanteenAllocationUiState.Allocated -> {
          // Navigating away handled by LaunchedEffect
        }
      }
    }
  }
}

@Composable
private fun CanteenAllocationCard(
  canteen: CanteenDocument,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  val borderColor = if (isSelected) com.example.ui.theme.DrawerAmber else BorderGray.copy(alpha = 0.7f)
  val cardBg = if (isSelected) Color(0xFFFFFBEB) else PureWhite

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .shadow(
        elevation = if (isSelected) 5.dp else 1.5.dp,
        shape = RoundedCornerShape(20.dp),
        spotColor = if (isSelected) com.example.ui.theme.DrawerAmber.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.06f),
      )
      .clip(RoundedCornerShape(20.dp))
      .background(cardBg)
      .border(if (isSelected) 1.8.dp else 1.dp, borderColor, RoundedCornerShape(20.dp))
      .clickable(role = Role.RadioButton, onClick = onClick)
      .padding(15.dp)
      .testTag("canteen_card_${canteen.id}")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) com.example.ui.theme.DrawerAmber else Color(0xFFF3F4F6)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.Storefront,
            contentDescription = null,
            tint = if (isSelected) PureWhite else BlackPrimary,
            modifier = Modifier.size(26.dp),
          )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = canteen.name,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark,
            )
          }

          Spacer(modifier = Modifier.height(3.dp))

          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = null,
              tint = TextMuted,
              modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            val locText = if (canteen.location.isNotBlank()) {
              canteen.location
            } else {
              "${canteen.block} · ${canteen.floorInfo}".trim(' ', '·').trim()
            }
            Text(
              text = locText,
              fontSize = 12.5.sp,
              color = TextMuted,
              fontWeight = FontWeight.Medium,
            )
          }

          Spacer(modifier = Modifier.height(5.dp))

          // Open/Closed Status Chip
          val statusBg = if (canteen.isOpen) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
          val statusTextClr = if (canteen.isOpen) Color(0xFF065F46) else Color(0xFF991B1B)
          val dotClr = if (canteen.isOpen) Color(0xFF10B981) else Color(0xFFEF4444)

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(statusBg)
              .padding(horizontal = 8.dp, vertical = 3.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(dotClr),
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = if (canteen.isOpen) "Open for Orders" else "Closed",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = statusTextClr,
              )
            }
          }
        }
      }

      // Radio Selection Indicator
      Box(
        modifier = Modifier
          .size(24.dp)
          .clip(CircleShape)
          .border(
            width = 2.dp,
            color = if (isSelected) com.example.ui.theme.DrawerAmber else BorderGray,
            shape = CircleShape
          )
          .background(if (isSelected) com.example.ui.theme.DrawerAmber else PureWhite),
        contentAlignment = Alignment.Center,
      ) {
        if (isSelected) {
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
