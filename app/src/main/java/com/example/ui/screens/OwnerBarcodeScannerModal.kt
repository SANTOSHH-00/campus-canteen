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
import com.example.data.api.MongoRepository
import com.example.data.api.VerifyBarcodeResponseDto
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
          msg.contains("ORDER_NOT_READY", ignoreCase = true) || msg.contains("not ready", ignoreCase = true) -> "ORDER_NOT_READY"
          msg.contains("ALREADY_COLLECTED", ignoreCase = true) || msg.contains("already been collected", ignoreCase = true) -> "ALREADY_COLLECTED"
          msg.contains("EXPIRED_BARCODE", ignoreCase = true) || msg.contains("expired", ignoreCase = true) -> "EXPIRED_BARCODE"
          msg.contains("ORDER_CANCELLED", ignoreCase = true) || msg.contains("cancelled", ignoreCase = true) -> "ORDER_CANCELLED"
          msg.contains("connection", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) || msg.contains("connect", ignoreCase = true) -> "NETWORK_ERROR"
          else -> "INVALID_BARCODE"
        }
        val userFriendlyMessage = when (code) {
          "INVALID_BARCODE" -> "This barcode is invalid. Please scan a valid Quick Bite order barcode."
          "ALREADY_COLLECTED" -> "This order has already been collected."
          "WRONG_OWNER" -> "This order does not belong to your canteen."
          "ORDER_NOT_READY" -> "This order is not ready for collection yet."
          "EXPIRED_BARCODE" -> "This barcode has expired. Please contact the canteen owner."
          "NETWORK_ERROR" -> "Unable to verify the order. Please check your internet connection and try again."
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
        // ── Camera Preview (active only when in Scanning state) ──────
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

        // ── Top Navigation & Controls Bar ───────────────────────────
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

        // ── Camera Overlay & Viewfinder Reticle ──────────────────────
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

        // ── Loading Indicator while verifying ───────────────────────
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

        // ── Step 1: Green Success Screen: ORDER VERIFIED SUCCESSFULLY ──
        if (workflowState is BarcodeScanWorkflowState.VerifiedSuccess) {
          val verified = (workflowState as BarcodeScanWorkflowState.VerifiedSuccess).response
          val order = verified.order
          val currentToken = (workflowState as BarcodeScanWorkflowState.VerifiedSuccess).token

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
                // Green Header Badge
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFDCFCE7))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(28.dp)
                  )
                  Spacer(Modifier.width(10.dp))
                  Column {
                    Text(
                      text = "ORDER VERIFIED SUCCESSFULLY",
                      fontSize = 14.sp,
                      fontWeight = FontWeight.ExtraBold,
                      color = Color(0xFF15803D),
                      letterSpacing = 0.5.sp
                    )
                    Text(
                      text = "Eligible for collection at Counter 1",
                      fontSize = 11.5.sp,
                      color = Color(0xFF16A34A)
                    )
                  }
                }

                Spacer(Modifier.height(16.dp))

                // Order Meta Details
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column {
                    Text("Order ID", fontSize = 11.sp, color = TextMuted)
                    Text(
                      text = "#${order?.orderId ?: "---"}",
                      fontSize = 14.sp,
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

                Spacer(Modifier.height(10.dp))

                // Student Name
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
                  }
                  Column(horizontalAlignment = Alignment.End) {
                    Text("Current Status", fontSize = 11.sp, color = TextMuted)
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFDCFCE7))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                      Text(
                        text = order?.status ?: "READY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF16A34A)
                      )
                    }
                  }
                }

                Spacer(Modifier.height(10.dp))

                // Order Creation Time
                Text("Order Placed At", fontSize = 11.sp, color = TextMuted)
                Text(
                  text = order?.orderPlacedAtLocal ?: order?.orderDate ?: "Today",
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.Medium,
                  color = TextDark
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(Modifier.height(8.dp))

                // Ordered Items List
                Text("Ordered Items", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Spacer(Modifier.height(6.dp))

                val itemsList = order?.items ?: emptyList()
                LazyColumn(modifier = Modifier.height(100.dp)) {
                  items(itemsList) { item ->
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text(
                        text = "${item.quantity}x ${item.name}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                      )
                      Text(
                        text = "₹${item.price * item.quantity}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                      )
                    }
                  }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(Modifier.height(10.dp))

                // Total Amount
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Total Amount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
                  Text(
                    text = "₹${order?.totalAmount ?: 0}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFEA580C)
                  )
                }

                Spacer(Modifier.height(18.dp))

                // ── Prominent Action Button: CONFIRM ORDER PICKUP ──
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
                    text = "CONFIRM ORDER PICKUP",
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
                  Text("Cancel / Scan Another", color = TextMuted, fontSize = 13.sp)
                }
              }
            }
          }
        }

        // ── Loading Pickup Confirmation ─────────────────────────────
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

        // ── Step 2: Successfully Collected Screen ───────────────────
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

        // ── Error State Screen ──────────────────────────────────────
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
                    "ORDER_NOT_READY" -> "Order Not Ready"
                    "ALREADY_COLLECTED" -> "Already Collected"
                    "EXPIRED_BARCODE" -> "Barcode Expired"
                    "NETWORK_ERROR" -> "Network Error"
                    else -> "Verification Failed"
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
                  Text("Restart Scanning", fontWeight = FontWeight.Bold)
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
      }
    }
  }

  // ── Manual Input Dialog Fallback ──────────────────────────────────
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

