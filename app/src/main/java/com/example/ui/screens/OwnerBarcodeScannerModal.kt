// OwnerBarcodeScannerModal: Jetpack Compose screen for OwnerBarcodeScannerModal user interface
package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.api.ConfirmPickupResponseDto
import com.example.data.api.MongoOrderDto
import com.example.data.api.MongoRepository
import com.example.data.api.VerifyBarcodeResponseDto
import com.example.data.api.WebSocketManager
import com.example.data.session.SessionManager
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val TAG = "OwnerBarcodeScanner"

sealed interface BarcodeScanWorkflowState {
  data object Scanning : BarcodeScanWorkflowState
  data class Verifying(val token: String) : BarcodeScanWorkflowState
  data class VerifiedSuccess(val response: VerifyBarcodeResponseDto, val token: String) : BarcodeScanWorkflowState
  data class ConfirmingPickup(val token: String) : BarcodeScanWorkflowState
  data class SuccessfullyCollected(val response: ConfirmPickupResponseDto) : BarcodeScanWorkflowState
  data class Error(val code: String, val message: String) : BarcodeScanWorkflowState
}

@Composable
fun OwnerBarcodeScannerModal(
  onDismiss: () -> Unit,
  onOrderVerified: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val coroutineScope = rememberCoroutineScope()

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    hasCameraPermission = isGranted
  }

  LaunchedEffect(Unit) {
    if (!hasCameraPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  var isTorchOn by remember { mutableStateOf(false) }
  var cameraInstance by remember { mutableStateOf<Camera?>(null) }

  var workflowState by remember { mutableStateOf<BarcodeScanWorkflowState>(BarcodeScanWorkflowState.Scanning) }

  var showManualInputDialog by remember { mutableStateOf(false) }
  var manualTokenText by remember { mutableStateOf("") }

  fun performVerification(rawToken: String) {
    val clean = rawToken.trim()
    if (clean.isBlank()) return

    workflowState = BarcodeScanWorkflowState.Verifying(clean)

    coroutineScope.launch {
      val ownerToken = SessionManager.getOwnerToken().ifBlank { null }
      val result = MongoRepository.verifyBarcode(clean, ownerToken)

      result.onSuccess { response ->
        if (response.success && response.order != null) {
          workflowState = BarcodeScanWorkflowState.VerifiedSuccess(response, clean)
        } else {
          val msg = response.error ?: response.message ?: "This barcode is invalid. Please scan a valid Quick Bite order barcode."
          workflowState = BarcodeScanWorkflowState.Error(response.code ?: "INVALID_BARCODE", msg)
        }
      }.onFailure { err ->
        val msg = err.message ?: "Unable to verify the order. Please check your internet connection and try again."
        val code = when {
          msg.contains("WRONG_OWNER", ignoreCase = true) || msg.contains("not belong to your canteen", ignoreCase = true) -> "WRONG_OWNER"
          msg.contains("EXPIRED_BARCODE", ignoreCase = true) || msg.contains("expired", ignoreCase = true) -> "EXPIRED_BARCODE"
          msg.contains("ALREADY_COLLECTED", ignoreCase = true) || msg.contains("already been collected", ignoreCase = true) -> "ALREADY_COLLECTED"
          msg.contains("UNAUTHORIZED", ignoreCase = true) || msg.contains("sign in", ignoreCase = true) || msg.contains("401", ignoreCase = true) -> "UNAUTHORIZED"
          msg.contains("REJECTED", ignoreCase = true) -> "ORDER_REJECTED"
          msg.contains("CANCELLED", ignoreCase = true) -> "ORDER_CANCELLED"
          msg.contains("connection", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) || msg.contains("connect", ignoreCase = true) || msg.contains("Unable to resolve host", ignoreCase = true) -> "NETWORK_ERROR"
          msg.contains("No order found", ignoreCase = true) || msg.contains("invalid", ignoreCase = true) || msg.contains("404", ignoreCase = true) -> "INVALID_BARCODE"
          else -> "INVALID_BARCODE"
        }
        val userFriendlyMessage = when (code) {
          "WRONG_OWNER" -> if (msg.contains("This order belongs to", ignoreCase = true)) msg else "This order does not belong to your canteen."
          "EXPIRED_BARCODE" -> "This barcode has expired. Please contact support."
          "ALREADY_COLLECTED" -> "This order has already been collected."
          "UNAUTHORIZED" -> "Session expired. Please sign in again as canteen owner."
          "ORDER_REJECTED" -> "This order was rejected and cannot be collected."
          "ORDER_CANCELLED" -> "This order was cancelled."
          "NETWORK_ERROR" -> "Network error: Unable to verify order. Please check your internet connection and try again."
          "INVALID_BARCODE" -> "No order found matching this barcode. Please scan a valid QuickBite order barcode."
          else -> msg
        }
        workflowState = BarcodeScanWorkflowState.Error(code, userFriendlyMessage)
      }
    }
  }

  fun performPickupConfirmation(token: String) {
    workflowState = BarcodeScanWorkflowState.ConfirmingPickup(token)

    coroutineScope.launch {
      val ownerToken = SessionManager.getOwnerToken().ifBlank { null }
      val result = MongoRepository.confirmPickup(token, ownerToken)

      result.onSuccess { response ->
        if (response.success) {
          workflowState = BarcodeScanWorkflowState.SuccessfullyCollected(response)
          onOrderVerified?.invoke()
        } else {
          val msg = response.error ?: response.message ?: "Pickup confirmation failed."
          workflowState = BarcodeScanWorkflowState.Error(response.code ?: "CONFIRMATION_FAILED", msg)
        }
      }.onFailure { err ->
        val msg = err.message ?: "Unable to confirm order pickup."
        workflowState = BarcodeScanWorkflowState.Error("CONFIRMATION_FAILED", msg)
      }
    }
  }

  fun resetScannerForNext() {
    workflowState = BarcodeScanWorkflowState.Scanning
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = modifier.fillMaxSize(),
      color = Color(0xFF0F172A) // Dark theater slate
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
              val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
              }
              val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
              val analysisExecutor = Executors.newSingleThreadExecutor()

              cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                  it.surfaceProvider = previewView.surfaceProvider
                }

                // Configured for Code 128 barcodes (and QR Code compatibility)
                val barcodeScanner = BarcodeScanning.getClient(
                  BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                      Barcode.FORMAT_CODE_128,
                      Barcode.FORMAT_QR_CODE
                    )
                    .build()
                )

                val imageAnalysis = ImageAnalysis.Builder()
                  .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                  .build()

                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                  processImageProxy(imageProxy, barcodeScanner) { scannedRawValue ->
                    if (workflowState is BarcodeScanWorkflowState.Scanning) {
                      performVerification(scannedRawValue)
                    }
                  }
                }

                try {
                  cameraProvider.unbindAll()
                  val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                  )
                  cameraInstance = camera
                } catch (e: Exception) {
                  Log.e(TAG, "Use case binding failed", e)
                }
              }, ContextCompat.getMainExecutor(ctx))

              previewView
            }
          )
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(Color(0x99000000))
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close Scanner",
              tint = PureWhite
            )
          }

          Text(
            text = "SCAN & VERIFY ORDER",
            color = PureWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
          )

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Manual Key Input
            IconButton(
              onClick = { showManualInputDialog = true },
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
            ) {
              Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = "Manual Code Entry",
                tint = PureWhite
              )
            }

            // Torch Toggle
            IconButton(
              onClick = {
                cameraInstance?.let { cam ->
                  if (cam.cameraInfo.hasFlashUnit()) {
                    isTorchOn = !isTorchOn
                    cam.cameraControl.enableTorch(isTorchOn)
                  }
                }
              },
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isTorchOn) Color(0xFFF59E0B) else Color(0x99000000))
            ) {
              Icon(
                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Toggle Torch",
                tint = PureWhite
              )
            }
          }
        }

        if (workflowState is BarcodeScanWorkflowState.Scanning) {
          if (!hasCameraPermission) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
              contentAlignment = Alignment.Center
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
              ) {
                Icon(
                  imageVector = Icons.Default.CameraAlt,
                  contentDescription = null,
                  tint = Color(0xFFF59E0B),
                  modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                  text = "Camera Permission Required",
                  color = PureWhite,
                  fontSize = 18.sp,
                  fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                  text = "Camera permission is required to scan the barcode.",
                  color = Color(0xFFCBD5E1),
                  fontSize = 13.sp,
                  textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                  onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Text("Grant Permission", fontWeight = FontWeight.Bold)
                }
              }
            }
          } else {
            // Scanner Viewfinder with Animated Red Reticle
            BoxWithConstraints(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              val reticleWidth = maxWidth * 0.85f
              val reticleHeight = 200.dp

              val infiniteTransition = rememberInfiniteTransition(label = "laser")
              val laserOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                  animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                  repeatMode = RepeatMode.Reverse
                ),
                label = "laserOffset"
              )

              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
              ) {
                Box(
                  modifier = Modifier
                    .width(reticleWidth)
                    .height(reticleHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Color(0xFFEA580C), RoundedCornerShape(16.dp))
                    .background(Color(0x1AFFFFFF))
                ) {
                  // Moving Red Laser Scan Bar
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(3.dp)
                      .offset(y = reticleHeight * laserOffset)
                      .background(
                        Brush.horizontalGradient(
                          listOf(Color.Transparent, Color(0xFFEF4444), Color(0xFFFF0000), Color(0xFFEF4444), Color.Transparent)
                        )
                      )
                  )
                }

                Spacer(Modifier.height(18.dp))

                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                  Text(
                    text = "Align Code 128 barcode inside the window",
                    color = PureWhite,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                  )
                }
              }
            }
          }
        }

        if (workflowState is BarcodeScanWorkflowState.Verifying) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color(0xCC000000)),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator(
                color = Color(0xFFEA580C),
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
              )
              Spacer(Modifier.height(16.dp))
              Text(
                text = "Verifying Barcode...",
                color = PureWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Checking canteen authority & order state",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
              )
            }
          }
        }

        if (workflowState is BarcodeScanWorkflowState.VerifiedSuccess) {
          val verified = (workflowState as BarcodeScanWorkflowState.VerifiedSuccess).response
          val order = verified.order
          val currentToken = (workflowState as BarcodeScanWorkflowState.VerifiedSuccess).token
          val activeOrderId = order?.orderId ?: ""

          // Real-time synchronization while scanner modal is open
          DisposableEffect(activeOrderId) {
            if (activeOrderId.isNotBlank()) {
              WebSocketManager.subscribe("order:$activeOrderId")
            }
            onDispose {
              if (activeOrderId.isNotBlank()) {
                WebSocketManager.unsubscribe("order:$activeOrderId")
              }
            }
          }

          LaunchedEffect(activeOrderId) {
            if (activeOrderId.isNotBlank()) {
              WebSocketManager.observeOrder(activeOrderId).collect { updatedDto ->
                if (workflowState is BarcodeScanWorkflowState.VerifiedSuccess) {
                  val current = workflowState as BarcodeScanWorkflowState.VerifiedSuccess
                  val updatedCat = when (updatedDto.status.uppercase().trim()) {
                    "READY" -> "READY"
                    "PREPARING" -> "PREPARING"
                    "COMPLETED", "PICKED_UP", "COLLECTED" -> "COLLECTED"
                    "CANCELLED" -> "CANCELLED"
                    "CONFIRMED", "ACCEPTED" -> "ACCEPTED"
                    "NEW", "WAITING", "PLACED", "ORDER_PLACED" -> "ORDER_PLACED"
                    else -> updatedDto.status
                  }
                  val updatedMsg = when (updatedCat) {
                    "ORDER_PLACED" -> "Your order has been received. Waiting for owner acceptance."
                    "ACCEPTED" -> "Your order has been accepted. Preparation will begin shortly."
                    "PREPARING" -> "Your order is currently being prepared. Please wait until it is ready for pickup."
                    "READY" -> "Your order is ready! Please verify the customer before handing over the order."
                    "COLLECTED" -> "This order has already been collected."
                    "CANCELLED" -> "This order has been cancelled and cannot be collected."
                    else -> "Your order is currently being prepared. Please wait until it is ready for pickup."
                  }
                  workflowState = BarcodeScanWorkflowState.VerifiedSuccess(
                    response = current.response.copy(
                      order = updatedDto,
                      code = updatedCat,
                      currentStatus = updatedDto.status,
                      statusMessage = updatedMsg,
                      message = updatedMsg,
                      eligibleForPickup = (updatedCat == "READY")
                    ),
                    token = current.token
                  )
                }
              }
            }
          }

          val rawStatus = order?.status ?: verified.currentStatus ?: verified.code ?: "PREPARING"
          val statusCategory = when (rawStatus.uppercase().trim()) {
            "READY" -> "READY"
            "PREPARING" -> "PREPARING"
            "COMPLETED", "PICKED_UP", "COLLECTED" -> "COLLECTED"
            "CANCELLED" -> "CANCELLED"
            "CONFIRMED", "ACCEPTED" -> "ACCEPTED"
            "NEW", "WAITING", "PLACED", "ORDER_PLACED" -> "ORDER_PLACED"
            else -> verified.code?.uppercase()?.trim() ?: "PREPARING"
          }

          val statusDisplayMessage = verified.statusMessage?.ifBlank { null }
            ?: verified.message?.ifBlank { null }
            ?: when (statusCategory) {
              "ORDER_PLACED" -> "Your order has been received. Waiting for owner acceptance."
              "ACCEPTED" -> "Your order has been accepted. Preparation will begin shortly."
              "PREPARING" -> "Your order is currently being prepared. Please wait until it is ready for pickup."
              "READY" -> "Your order is ready! Please verify the customer before handing over the order."
              "COLLECTED" -> "This order has already been collected."
              "CANCELLED" -> "This order has been cancelled and cannot be collected."
              else -> "Your order is currently being prepared. Please wait until it is ready for pickup."
            }

          val isReadyForPickup = (statusCategory == "READY")
          val isPreparing = (statusCategory == "PREPARING")

          val badgeLabel = when (statusCategory) {
            "READY" -> "Ready for Pickup"
            "PREPARING" -> "Order is being prepared"
            "COLLECTED" -> "Already Collected"
            "CANCELLED" -> "Order Cancelled"
            "ACCEPTED" -> "Order Accepted"
            "ORDER_PLACED" -> "Order Placed"
            else -> rawStatus
          }

          val badgeBgColor = when (statusCategory) {
            "READY" -> Color(0xFFDCFCE7)
            "PREPARING" -> Color(0xFFFEF3C7)
            "COLLECTED" -> Color(0xFFF1F5F9)
            "CANCELLED" -> Color(0xFFFEE2E2)
            "ACCEPTED" -> Color(0xFFE0E7FF)
            "ORDER_PLACED" -> Color(0xFFE0F2FE)
            else -> Color(0xFFFEF3C7)
          }

          val badgeTextColor = when (statusCategory) {
            "READY" -> Color(0xFF15803D)
            "PREPARING" -> Color(0xFFB45309)
            "COLLECTED" -> Color(0xFF475569)
            "CANCELLED" -> Color(0xFFB91C1C)
            "ACCEPTED" -> Color(0xFF4338CA)
            "ORDER_PLACED" -> Color(0xFF0369A1)
            else -> Color(0xFFB45309)
          }

          val badgeIcon = when (statusCategory) {
            "READY" -> Icons.Default.CheckCircle
            "PREPARING" -> Icons.Default.Warning
            "COLLECTED" -> Icons.Default.Check
            "CANCELLED" -> Icons.Default.ErrorOutline
            else -> Icons.Default.CheckCircle
          }

          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color(0xE60F172A))
              .statusBarsPadding()
              .navigationBarsPadding()
              .padding(16.dp),
            contentAlignment = Alignment.Center
          ) {
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
              color = PureWhite,
              shadowElevation = 8.dp
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(20.dp)
              ) {
                // Status Visual Indicator Header
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeBgColor)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = badgeIcon,
                    contentDescription = null,
                    tint = badgeTextColor,
                    modifier = Modifier.size(26.dp)
                  )
                  Spacer(Modifier.width(10.dp))
                  Column {
                    Text(
                      text = badgeLabel.uppercase(),
                      fontSize = 13.5.sp,
                      fontWeight = FontWeight.ExtraBold,
                      color = badgeTextColor,
                      letterSpacing = 0.5.sp
                    )
                    Text(
                      text = when (statusCategory) {
                        "READY" -> "Eligible for customer handover"
                        "PREPARING" -> "Preparation in kitchen"
                        "COLLECTED" -> "Order already handed over"
                        "CANCELLED" -> "Order cannot be collected"
                        else -> "Current status: $rawStatus"
                      },
                      fontSize = 11.sp,
                      color = badgeTextColor.copy(alpha = 0.85f)
                    )
                  }
                }

                Spacer(Modifier.height(10.dp))

                // Exact Status Message Banner
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeBgColor.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                  Text(
                    text = statusDisplayMessage,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeTextColor,
                    lineHeight = 17.sp
                  )
                }

                if (isPreparing) {
                  Spacer(Modifier.height(8.dp))
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .background(Color(0xFFFFFBEB))
                      .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(8.dp))
                      .padding(horizontal = 10.dp, vertical = 6.dp)
                  ) {
                    Text(
                      text = "Order is still being prepared. Pickup confirmation is disabled.",
                      fontSize = 11.5.sp,
                      color = Color(0xFF92400E),
                      fontWeight = FontWeight.Medium
                    )
                  }
                }

                if (isReadyForPickup) {
                  Spacer(Modifier.height(8.dp))
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .background(Color(0xFFF0FDF4))
                      .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(8.dp))
                      .padding(horizontal = 10.dp, vertical = 6.dp)
                  ) {
                    Text(
                      text = "Verify that the correct student is collecting this order.",
                      fontSize = 12.sp,
                      color = Color(0xFF166534),
                      fontWeight = FontWeight.SemiBold
                    )
                  }
                }

                Spacer(Modifier.height(14.dp))

                // Order Meta Details (Order ID, Token, Student Name, Roll Number, Order Time)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column {
                    Text("Order ID", fontSize = 11.sp, color = TextMuted)
                    Text(
                      text = "#${order?.orderId ?: "---"}",
                      fontSize = 13.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextDark
                    )
                  }
                  Column(horizontalAlignment = Alignment.End) {
                    Text("Token", fontSize = 11.sp, color = TextMuted)
                    Text(
                      text = "#Q${order?.tokenNumber ?: "---"}",
                      fontSize = 16.sp,
                      fontWeight = FontWeight.ExtraBold,
                      color = Color(0xFFEA580C)
                    )
                  }
                }

                Spacer(Modifier.height(8.dp))

                // Student Name & Roll Number
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column {
                    Text("Student Name", fontSize = 11.sp, color = TextMuted)
                    Text(
                      text = order?.studentName?.ifBlank { "Campus Student" } ?: "Campus Student",
                      fontSize = 13.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = TextDark
                    )
                    val rollNumber = order?.studentRollNumber?.ifBlank { null }
                    if (!rollNumber.isNullOrBlank()) {
                      Text(
                        text = "Roll No: $rollNumber",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0369A1)
                      )
                    }
                  }
                  Column(horizontalAlignment = Alignment.End) {
                    Text("Status", fontSize = 11.sp, color = TextMuted)
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBgColor)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                      Text(
                        text = order?.status ?: rawStatus,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeTextColor
                      )
                    }
                  }
                }

                Spacer(Modifier.height(8.dp))

                // Order Creation Time
                Text("Order Placed At", fontSize = 11.sp, color = TextMuted)
                Text(
                  text = order?.orderPlacedAtLocal ?: order?.orderDate ?: "Today",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium,
                  color = TextDark
                )

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(Modifier.height(8.dp))

                // Ordered Food Items List
                Text("Food Items", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Spacer(Modifier.height(4.dp))

                val itemsList = order?.items ?: emptyList()
                LazyColumn(modifier = Modifier.height(85.dp)) {
                  items(itemsList) { item ->
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.5.dp),
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text(
                        text = "${item.quantity}x ${item.name}",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                      )
                      Text(
                        text = "₹${item.price * item.quantity}",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                      )
                    }
                  }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(Modifier.height(8.dp))

                // Total Amount
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Total Amount", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextDark)
                  Text(
                    text = "₹${order?.totalAmount ?: 0}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFEA580C)
                  )
                }

                Spacer(Modifier.height(16.dp))

                if (isReadyForPickup) {
                  Button(
                    onClick = { performPickupConfirmation(currentToken) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(50.dp)
                  ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = PureWhite)
                    Spacer(Modifier.width(8.dp))
                    Text(
                      text = "CONFIRM HANDOVER",
                      fontSize = 14.sp,
                      fontWeight = FontWeight.ExtraBold,
                      color = PureWhite,
                      letterSpacing = 0.5.sp
                    )
                  }

                  Spacer(Modifier.height(8.dp))

                  TextButton(
                    onClick = { resetScannerForNext() },
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Text("Cancel", color = TextMuted, fontSize = 13.sp)
                  }
                } else {
                  // Non-ready states: Pickup disabled, provide Scan Again & Close Scanner
                  Button(
                    onClick = { resetScannerForNext() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(48.dp)
                  ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = PureWhite)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan Again", fontWeight = FontWeight.Bold, color = PureWhite)
                  }

                  Spacer(Modifier.height(6.dp))

                  TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Text("Close Scanner", color = TextMuted, fontSize = 13.sp)
                  }
                }
              }
            }
          }
        }

        if (workflowState is BarcodeScanWorkflowState.ConfirmingPickup) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color(0xCC000000)),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator(color = Color(0xFF16A34A), strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
              Spacer(Modifier.height(14.dp))
              Text("Confirming Order Pickup...", color = PureWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
              Text("Updating order status atomically to Collected", color = Color(0xFFCBD5E1), fontSize = 12.sp)
            }
          }
        }

        if (workflowState is BarcodeScanWorkflowState.SuccessfullyCollected) {
          val res = (workflowState as BarcodeScanWorkflowState.SuccessfullyCollected).response
          val order = res.order

          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color(0xE60F172A))
              .statusBarsPadding()
              .navigationBarsPadding()
              .padding(16.dp),
            contentAlignment = Alignment.Center
          ) {
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
              color = PureWhite,
              shadowElevation = 8.dp
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Box(
                  modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDCFCE7)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(42.dp)
                  )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                  text = "Successfully Collected",
                  fontSize = 20.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = Color(0xFF15803D)
                )

                Spacer(Modifier.height(4.dp))

                Text(
                  text = "Order #${order?.orderId ?: ""} marked as Collected",
                  fontSize = 13.sp,
                  color = TextMuted
                )

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(Modifier.height(14.dp))

                Text(
                  text = "Student: ${order?.studentName?.ifBlank { "Campus Student" } ?: "Campus Student"}",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextDark
                )
                Text(
                  text = "Token: #Q${order?.tokenNumber ?: ""}",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFEA580C)
                )

                Spacer(Modifier.height(20.dp))

                Button(
                  onClick = { resetScannerForNext() },
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                ) {
                  Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = PureWhite)
                  Spacer(Modifier.width(8.dp))
                  Text("Scan Next Order", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(6.dp))

                TextButton(
                  onClick = onDismiss,
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text("Close", color = TextMuted)
                }
              }
            }
          }
        }

        if (workflowState is BarcodeScanWorkflowState.Error) {
          val errorState = workflowState as BarcodeScanWorkflowState.Error

          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color(0xE60F172A))
              .statusBarsPadding()
              .navigationBarsPadding()
              .padding(20.dp),
            contentAlignment = Alignment.Center
          ) {
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
              color = PureWhite,
              shadowElevation = 8.dp
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Box(
                  modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEE2E2)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(36.dp)
                  )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                  text = when (errorState.code) {
                    "WRONG_OWNER" -> "Wrong Canteen"
                    "EXPIRED_BARCODE" -> "Barcode Expired"
                    "ALREADY_COLLECTED" -> "Already Collected"
                    "UNAUTHORIZED" -> "Authentication Required"
                    "ORDER_REJECTED" -> "Order Rejected"
                    "ORDER_CANCELLED" -> "Order Cancelled"
                    "NETWORK_ERROR" -> "Network Error"
                    else -> "Invalid Barcode"
                  },
                  fontSize = 18.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFDC2626)
                )

                Spacer(Modifier.height(8.dp))

                Text(
                  text = errorState.message,
                  fontSize = 13.5.sp,
                  color = TextDark,
                  textAlign = TextAlign.Center,
                  lineHeight = 18.sp
                )

                Spacer(Modifier.height(20.dp))

                Button(
                  onClick = { resetScannerForNext() },
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                ) {
                  Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = PureWhite)
                  Spacer(Modifier.width(8.dp))
                  Text("Scan Again", fontWeight = FontWeight.Bold, color = PureWhite)
                }

                Spacer(Modifier.height(6.dp))

                TextButton(
                  onClick = onDismiss,
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text("Close Scanner", color = TextMuted)
                }
              }
            }
          }
        }
      }
    }
  }

  if (showManualInputDialog) {
    Dialog(onDismissRequest = { showManualInputDialog = false }) {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = PureWhite,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(20.dp)) {
          Text(
            text = "Enter Verification Code",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = TextDark
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text = "Enter the alphanumeric verification token shown below the student's barcode (e.g. QB-8F3A2B1C9D4E5F6A)",
            fontSize = 12.sp,
            color = TextMuted
          )
          Spacer(Modifier.height(14.dp))

          OutlinedTextField(
            value = manualTokenText,
            onValueChange = { manualTokenText = it },
            placeholder = { Text("e.g. QB-...", fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Color(0xFFEA580C),
              unfocusedBorderColor = Color(0xFFE2E8F0)
            )
          )

          Spacer(Modifier.height(18.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
          ) {
            TextButton(onClick = { showManualInputDialog = false }) {
              Text("Cancel", color = TextMuted)
            }
            Spacer(Modifier.width(8.dp))
            Button(
              onClick = {
                val clean = manualTokenText.trim()
                if (clean.isNotBlank()) {
                  showManualInputDialog = false
                  performVerification(clean)
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text("Verify", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
  imageProxy: ImageProxy,
  barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
  onBarcodeDetected: (String) -> Unit,
) {
  val mediaImage = imageProxy.image
  if (mediaImage != null) {
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    barcodeScanner.process(image)
      .addOnSuccessListener { barcodes ->
        for (barcode in barcodes) {
          val rawValue = barcode.rawValue
          if (!rawValue.isNullOrBlank()) {
            onBarcodeDetected(rawValue)
            break
          }
        }
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Barcode analysis failed", e)
      }
      .addOnCompleteListener {
        imageProxy.close()
      }
  } else {
    imageProxy.close()
  }
}

