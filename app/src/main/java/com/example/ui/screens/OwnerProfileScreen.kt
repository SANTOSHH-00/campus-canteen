package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.firebase.OwnerDocument
import com.example.data.session.SessionManager
import com.example.util.CanteenImageHelper
import com.example.data.firebase.FirestoreRepository
import com.example.ui.state.CanteenAppState
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.OwnerDashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Private Palette ───────────────────────────────────────────────────────────
private val PrimaryOrange = Color(0xFFFF6433)
private val LightOrangeBg = Color(0xFFFFF1EB)
private val DarkText = Color(0xFF111827)
private val GrayText = Color(0xFF6B7280)
private val CardBg = Color(0xFFFFFFFF)
private val ScreenBg = Color(0xFFF8F9FA)
private val GreenOpen = Color(0xFF10B981)
private val RedClosed = Color(0xFFEF4444)

/**
 * Minimal, clean profile screen for Canteen Owner:
 * - Canteen Profile Picture (upload preset or custom URL, reflecting immediately to students)
 * - Canteen Name, Owner Name, Email, Phone, Location
 * - Canteen Operations: Open/Closed switch toggle
 * - Operating Timings: Quick selector connecting directly to Canteen Selection sheet
 * - Quick shortcuts & Logout
 */
@Composable
fun OwnerProfileScreen(
  owner: OwnerDocument?,
  onBack: () -> Unit,
  onLogout: () -> Unit,
  onNavigateToManageItems: () -> Unit = {},
  onNavigateToOrders: () -> Unit = {},
  appState: CanteenAppState? = null,
  dashboardViewModel: OwnerDashboardViewModel = viewModel(),
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val uiState by dashboardViewModel.uiState.collectAsState()

  // Canteen ID & Current info
  val activeOwner = owner ?: (uiState as? DashboardUiState.Ready)?.owner
  val canteenDoc = (uiState as? DashboardUiState.Ready)?.canteen
  val canteenId = activeOwner?.canteenId?.ifBlank { canteenDoc?.id ?: "canteen_33" } ?: "canteen_33"

  // Local state initialized with saved values
  var currentImgUrl by remember(canteenId, canteenDoc?.imageUrl) {
    val dbUrl = canteenDoc?.imageUrl?.ifBlank { null }
    val sessionUrl = SessionManager.getCanteenImage(canteenId)?.ifBlank { null }
    val localFile = CanteenImageHelper.getLocalProfileFile(context, canteenId)
    val initial = dbUrl ?: sessionUrl ?: localFile?.absolutePath ?: ""
    mutableStateOf(initial)
  }
  var currentTimings by remember(canteenId) {
    mutableStateOf(SessionManager.getCanteenTimings(canteenId) ?: canteenDoc?.timings ?: "7:00 AM – 10:00 PM")
  }
  var isOpen by remember(canteenId) {
    mutableStateOf(SessionManager.isCanteenOpen(canteenId, canteenDoc?.isOpen ?: true))
  }
  var closeReason by remember(canteenId) {
    mutableStateOf(SessionManager.getCanteenCloseReason(canteenId) ?: canteenDoc?.closeReason ?: "")
  }

  val coroutineScope = rememberCoroutineScope()
  var isUploadingPhoto by remember { mutableStateOf(false) }

  // Album photo picker (Selecting pic from album only - no presets)
  val albumLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent(),
    onResult = { uri: Uri? ->
      if (uri != null) {
        isUploadingPhoto = true
        coroutineScope.launch(Dispatchers.IO) {
          try {
            // 1. Immediately drop previous image from Coil memory cache
            withContext(Dispatchers.Main) {
              try {
                Coil.imageLoader(context).memoryCache?.clear()
              } catch (e: Exception) {}
            }

            // 2. Save new persistent local file with timestamp (deletes previous local images)
            val persistentFile = CanteenImageHelper.saveGalleryImageLocally(context, canteenId, uri)
            val persistentPath = persistentFile.absolutePath

            withContext(Dispatchers.Main) {
              currentImgUrl = persistentPath
              SessionManager.saveCanteenProfile(canteenId, persistentPath, currentTimings, isOpen, closeReason)
              appState?.updateCanteenDetails(canteenId, isOpen, currentTimings, persistentPath, closeReason)
            }

            // 3. Update canteen profile in MongoDB
            FirestoreRepository().updateCanteenProfile(canteenId, persistentPath, currentTimings, isOpen, closeReason)

            withContext(Dispatchers.Main) {
              isUploadingPhoto = false
              currentImgUrl = persistentPath
              SessionManager.saveCanteenProfile(canteenId, persistentPath, currentTimings, isOpen, closeReason)
              appState?.updateCanteenDetails(canteenId, isOpen, currentTimings, persistentPath, closeReason)
              dashboardViewModel.updateCanteenProfileImage(persistentPath)
              Toast.makeText(context, "Canteen profile photo updated successfully!", Toast.LENGTH_SHORT).show()
            }
          } catch (e: Exception) {
            android.util.Log.e("OwnerProfile", "Failed to save profile photo: ${e.message}", e)
            withContext(Dispatchers.Main) {
              isUploadingPhoto = false
              Toast.makeText(context, "Failed to update canteen photo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          }
        }
      }
    }
  )

  // Dialog states
  var showTimingsPicker by remember { mutableStateOf(false) }
  var showCloseReasonDialog by remember { mutableStateOf(false) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(ScreenBg)
      .statusBarsPadding()
      .navigationBarsPadding(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
      // ── Top Bar ────────────────────────────────────────────────────────────
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE5E7EB), CircleShape),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = DarkText,
            modifier = Modifier.size(20.dp),
          )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
          Text(
            text = "Canteen Profile",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = DarkText,
          )
          Text(
            text = "Manage canteen visibility & timings",
            fontSize = 12.5.sp,
            color = GrayText,
          )
        }
      }

      // ── Card 1: Canteen Photo & Identity ───────────────────────────────────
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          // Profile Photo with Camera / Edit badge (Circle Container)
          val resolvedModel = CanteenImageHelper.resolveCanteenImageSource(context, canteenId, currentImgUrl)

          key(currentImgUrl) {
            Box(
              contentAlignment = Alignment.BottomEnd,
              modifier = Modifier
                .size(100.dp)
                .clickable { albumLauncher.launch("image/*") },
            ) {
              Box(
                modifier = Modifier
                  .size(96.dp)
                  .align(Alignment.Center)
                  .clip(CircleShape)
                  .background(
                    brush = Brush.linearGradient(
                      colors = listOf(Color(0xFF2C2D30), Color(0xFF1A1B1E))
                    )
                  )
                  .border(2.5.dp, PrimaryOrange.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
              ) {
                if (resolvedModel != null) {
                  SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                      .data(resolvedModel)
                      .crossfade(false)
                      .memoryCachePolicy(CachePolicy.DISABLED)
                      .build(),
                    contentDescription = "Canteen Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                      .fillMaxSize()
                      .clip(CircleShape),
                    error = {
                      OwnerMonogram(activeOwner?.name)
                    },
                  )
                } else {
                  OwnerMonogram(activeOwner?.name)
                }
                if (isUploadingPhoto) {
                  Box(
                    modifier = Modifier
                      .fillMaxSize()
                      .clip(CircleShape)
                      .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                  ) {
                    CircularProgressIndicator(
                      color = PrimaryOrange,
                      strokeWidth = 2.5.dp,
                      modifier = Modifier.size(24.dp),
                    )
                  }
                }
              }

              // Edit Camera Icon Overlay - Opens Album
              Box(
                modifier = Modifier
                  .size(30.dp)
                  .clip(CircleShape)
                  .background(PrimaryOrange)
                  .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.CameraAlt,
                  contentDescription = "Change Canteen Photo",
                  tint = Color.White,
                  modifier = Modifier.size(15.dp),
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Canteen Name
          val canteenName = (canteenDoc?.name ?: activeOwner?.canteenId?.replace("_", " ")?.replaceFirstChar { it.uppercase() })
            ?: "Campus Canteen"
          Text(
            text = canteenName,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = DarkText,
          )

          Text(
            text = "Owner: ${activeOwner?.name ?: "Canteen Owner"}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = GrayText,
          )

          Spacer(modifier = Modifier.height(12.dp))

          // "Select From Album" Action Pill
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(LightOrangeBg)
              .clickable { albumLauncher.launch("image/*") }
              .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = PrimaryOrange,
                modifier = Modifier.size(14.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isUploadingPhoto) "Uploading..." else "Choose Photo from Album",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryOrange,
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))
          HorizontalDivider(color = Color(0xFFF1F2F4))
          Spacer(modifier = Modifier.height(12.dp))

          // Details List
          OwnerInfoRow(
            icon = Icons.Default.LocationOn,
            label = "Canteen Location",
            value = canteenDoc?.location?.ifBlank { "Block ${activeOwner?.block ?: "26-27"}" } ?: "Main Campus Block",
          )
          OwnerInfoRow(
            icon = Icons.Default.Email,
            label = "Owner Email",
            value = activeOwner?.email?.ifBlank { "Not provided" } ?: "owner@canteen.edu",
          )
          OwnerInfoRow(
            icon = Icons.Default.Phone,
            label = "Contact Phone",
            value = activeOwner?.phone?.ifBlank { "+91 98765 43210" } ?: "+91 98765 43210",
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // ── Card 2: Operations, Open/Closed & Shop Timings ───────────────────────
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        ) {
          Text(
            text = "Canteen Operations & Timings",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText,
          )
          Text(
            text = "Changes reflect live on the student canteen selector",
            fontSize = 12.sp,
            color = GrayText,
          )

          Spacer(modifier = Modifier.height(16.dp))

          // 1. Open / Closed Switch
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(if (isOpen) Color(0xFFECFDF5) else Color(0xFFFEF2F2)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Storefront,
                  contentDescription = null,
                  tint = if (isOpen) GreenOpen else RedClosed,
                  modifier = Modifier.size(18.dp),
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Canteen Status",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = DarkText,
                )
                Text(
                  text = if (isOpen) "Currently Open for Orders" else "Closed (${closeReason.ifBlank { "Temporarily" }})",
                  fontSize = 12.sp,
                  color = if (isOpen) GreenOpen else RedClosed,
                )
              }
            }

            Switch(
              checked = isOpen,
              onCheckedChange = { checked ->
                if (!checked) {
                  showCloseReasonDialog = true
                } else {
                  isOpen = true
                  closeReason = ""
                  dashboardViewModel.setCanteenOpenStatus(true, "")
                  appState?.updateCanteenDetails(canteenId, true, currentTimings, currentImgUrl, "")
                  Toast.makeText(context, "Canteen is now marked Open!", Toast.LENGTH_SHORT).show()
                }
              },
              colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = GreenOpen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = RedClosed,
              ),
            )
          }

          Spacer(modifier = Modifier.height(14.dp))
          HorizontalDivider(color = Color(0xFFF1F2F4))
          Spacer(modifier = Modifier.height(14.dp))

          // 2. Operating Timings Selector
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { showTimingsPicker = true }
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(LightOrangeBg),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Schedule,
                  contentDescription = null,
                  tint = PrimaryOrange,
                  modifier = Modifier.size(18.dp),
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Operating Timings",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = DarkText,
                )
                Text(
                  text = currentTimings,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = PrimaryOrange,
                )
              }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "Change",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryOrange,
              )
              Spacer(modifier = Modifier.width(4.dp))
              Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Timings",
                tint = PrimaryOrange,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // ── Card 3: Quick Navigation ───────────────────────────────────────────
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        ) {
          ActionNavRow(
            icon = Icons.Default.RestaurantMenu,
            label = "Menu Items Management",
            subtitle = "Update availability, prices & add new dishes",
            onClick = onNavigateToManageItems,
          )
          HorizontalDivider(color = Color(0xFFF6F7F9), modifier = Modifier.padding(horizontal = 16.dp))
          ActionNavRow(
            icon = Icons.Default.ShoppingBag,
            label = "Kitchen Orders & History",
            subtitle = "View incoming live orders & tokens",
            onClick = onNavigateToOrders,
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // ── Card 4: Logout ─────────────────────────────────────────────────────
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onLogout() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = "Logout",
            tint = RedClosed,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Log Out of Owner Account",
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = RedClosed,
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }

  // ── Operating Timings Dialog (Simple custom timings feature) ────────────────
  if (showTimingsPicker) {
    var customTimingsInput by remember { mutableStateOf(currentTimings) }

    AlertDialog(
      onDismissRequest = { showTimingsPicker = false },
      title = {
        Text("Operating timings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkText)
      },
      text = {
        Column {
          Text(
            text = "Enter your custom daily operating timings. This reflects live on the student canteen selector.",
            fontSize = 13.sp,
            color = GrayText,
          )
          Spacer(modifier = Modifier.height(14.dp))
          OutlinedTextField(
            value = customTimingsInput,
            onValueChange = { customTimingsInput = it },
            label = { Text("Operating timings") },
            placeholder = { Text("e.g. 7:00 AM – 10:00 PM") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
          )
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            val finalTimings = customTimingsInput.trim().ifBlank { "7:00 AM – 10:00 PM" }
            currentTimings = finalTimings
            SessionManager.saveCanteenProfile(canteenId, currentImgUrl, finalTimings, isOpen, closeReason)
            appState?.updateCanteenDetails(canteenId, isOpen, finalTimings, currentImgUrl, closeReason)
            dashboardViewModel.updateCanteenTimings(finalTimings)
            coroutineScope.launch(Dispatchers.IO) {
              try {
                FirestoreRepository().updateCanteenProfile(canteenId, timings = finalTimings)
              } catch (e: Exception) {
                android.util.Log.w("OwnerProfile", "Failed to update timings in MongoDB: ${e.message}")
              }
            }
            showTimingsPicker = false
            Toast.makeText(context, "Operating timings saved: $finalTimings", Toast.LENGTH_SHORT).show()
          }
        ) {
          Text("Save Operating Timings", color = PrimaryOrange, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showTimingsPicker = false }) {
          Text("Cancel", color = GrayText)
        }
      },
    )
  }


  // ── DIALOG 3: Close Canteen Reason Dialog ──────────────────────────────────
  if (showCloseReasonDialog) {
    val reasons = listOf("Opens 8:00 AM", "Break", "Kitchen maintenance", "Stock unavailable", "End of day")
    var selectedReason by remember { mutableStateOf(reasons.first()) }

    AlertDialog(
      onDismissRequest = { showCloseReasonDialog = false },
      title = {
        Text("Close Canteen?", fontSize = 17.sp, fontWeight = FontWeight.Bold)
      },
      text = {
        Column {
          Text(
            text = "Select a reason or re-open time. This will be shown on the student canteen selector card.",
            fontSize = 12.5.sp,
            color = GrayText,
          )
          Spacer(modifier = Modifier.height(12.dp))
          reasons.forEach { r ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedReason = r }
                .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(
                selected = (selectedReason == r),
                onClick = { selectedReason = r },
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(text = r, fontSize = 13.5.sp, color = DarkText)
            }
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            isOpen = false
            closeReason = selectedReason
            dashboardViewModel.setCanteenOpenStatus(false, selectedReason)
            appState?.updateCanteenDetails(canteenId, false, currentTimings, currentImgUrl, selectedReason)
            showCloseReasonDialog = false
            Toast.makeText(context, "Canteen is now marked Closed ($selectedReason)", Toast.LENGTH_SHORT).show()
          }
        ) {
          Text("Mark Closed", color = RedClosed, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showCloseReasonDialog = false }) {
          Text("Cancel", color = GrayText)
        }
      },
    )
  }
}

// ── Helper Subcomponents ──────────────────────────────────────────────────────

@Composable
private fun OwnerInfoRow(
  icon: ImageVector,
  label: String,
  value: String,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(32.dp)
        .clip(CircleShape)
        .background(Color(0xFFF3F4F6)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = GrayText,
        modifier = Modifier.size(16.dp),
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(text = label, fontSize = 11.5.sp, color = GrayText)
      Text(
        text = value,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = DarkText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun ActionNavRow(
  icon: ImageVector,
  label: String,
  subtitle: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(38.dp)
        .clip(CircleShape)
        .background(Color(0xFFF3F4F6)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = DarkText,
        modifier = Modifier.size(19.dp),
      )
    }
    Spacer(modifier = Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
      Text(text = subtitle, fontSize = 11.5.sp, color = GrayText)
    }
    Icon(
      imageVector = Icons.Default.ChevronRight,
      contentDescription = null,
      tint = Color(0xFF9CA3AF),
      modifier = Modifier.size(20.dp),
    )
  }
}

@Composable
private fun OwnerMonogram(name: String?) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Icon(
      imageVector = Icons.Default.Restaurant,
      contentDescription = null,
      tint = PrimaryOrange,
      modifier = Modifier.size(26.dp),
    )
    Text(
      text = (name?.take(2)?.uppercase() ?: "CP"),
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      color = Color.White,
    )
  }
}

