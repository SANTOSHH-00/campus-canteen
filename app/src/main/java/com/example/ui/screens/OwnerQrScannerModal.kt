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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.api.MongoRepository
import com.example.data.api.VerifyQrResponseDto
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

private const val TAG = "OwnerQrScanner"

sealed interface VerificationResultState {
  data object Idle : VerificationResultState
  data class Success(val response: VerifyQrResponseDto) : VerificationResultState
  data class Error(val code: String, val message: String) : VerificationResultState
}

@Composable
fun OwnerQrScannerModal(
  onDismiss: () -> Unit,
  onOrderVerified: ((VerifyQrResponseDto) -> Unit)? = null,
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

  var isVerifying by remember { mutableStateOf(false) }
  var lastScannedToken by remember { mutableStateOf<String?>(null) }
  var verificationResult by remember { mutableStateOf<VerificationResultState>(VerificationResultState.Idle) }

  var showManualInputDialog by remember { mutableStateOf(false) }
  var manualTokenText by remember { mutableStateOf("") }

  fun performVerification(rawToken: String) {
    if (isVerifying) return
    val clean = rawToken.trim()
    if (clean.isBlank()) return

    isVerifying = true
    lastScannedToken = clean

    coroutineScope.launch {
      val ownerToken = SessionManager.getOwnerToken().ifBlank { null }
      val result = MongoRepository.verifyQrCode(clean, ownerToken)
      isVerifying = false

      result.onSuccess { response ->
        verificationResult = VerificationResultState.Success(response)
        onOrderVerified?.invoke(response)
      }.onFailure { err ->
        val msg = err.message ?: "Verification failed."
        val code = when {
          msg.contains("WRONG_CANTEEN", ignoreCase = true) || msg.contains("Wrong canteen", ignoreCase = true) -> "WRONG_CANTEEN"
          msg.contains("ORDER_NOT_READY", ignoreCase = true) || msg.contains("not ready", ignoreCase = true) -> "ORDER_NOT_READY"
          msg.contains("ALREADY_REDEEMED", ignoreCase = true) || msg.contains("already been redeemed", ignoreCase = true) -> "ALREADY_REDEEMED"
          msg.contains("EXPIRED_QR", ignoreCase = true) || msg.contains("expired", ignoreCase = true) -> "EXPIRED_QR"
          msg.contains("ORDER_CANCELLED", ignoreCase = true) || msg.contains("cancelled", ignoreCase = true) -> "ORDER_CANCELLED"
          msg.contains("UNAUTHORIZED", ignoreCase = true) -> "UNAUTHORIZED"
          else -> "INVALID_QR"
        }
        verificationResult = VerificationResultState.Error(code, msg)
      }
    }
  }

  fun resetScannerForNext() {
    lastScannedToken = null
    verificationResult = VerificationResultState.Idle
    isVerifying = false
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = modifier.fillMaxSize(),
      color = Color.Black
    ) {
      Box(modifier = Modifier.fillMaxSize()) {

        if (hasCameraPermission) {
          // ── CameraX Preview View ──────────────────────────────────────────
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
              val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
              }
              val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
              val analysisExecutor = Executors.newSingleThreadExecutor()

              cameraProviderFuture.addListener({
                try {
                  val cameraProvider = cameraProviderFuture.get()
                  val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                  }

                  val barcodeScanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                      .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                      .build()
                  )

                  val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                  imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    processImageProxy(
                      imageProxy = imageProxy,
                      scanner = barcodeScanner,
                      isBusy = isVerifying || verificationResult !is VerificationResultState.Idle,
                      onTokenFound = { token ->
                        if (token != lastScannedToken) {
                          performVerification(token)
                        }
                      }
                    )
                  }

                  cameraProvider.unbindAll()
                  val cam = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                  )
                  cameraInstance = cam
                } catch (e: Exception) {
                  Log.e(TAG, "Camera binding failed", e)
                }
              }, ContextCompat.getMainExecutor(ctx))

              previewView
            }
          )

          // ── Viewfinder Reticle & Laser Scan Line Overlay ───────────────────
          ViewfinderOverlay(isScanning = !isVerifying && verificationResult is VerificationResultState.Idle)

        } else {
          // ── Camera Permission Required Fallback ───────────────────────────
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Box(
              modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFF1F2937)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = Color(0xFFF97316),
                modifier = Modifier.size(36.dp)
              )
            }
            Spacer(Modifier.height(18.dp))
            Text(
              text = "Camera Permission Required",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = PureWhite,
              textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
              text = "QuickBite requires camera access to scan customer order pickup QR codes quickly at the counter.",
              fontSize = 13.5.sp,
              color = Color(0xFF9CA3AF),
              textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
              onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text("Grant Camera Access", fontWeight = FontWeight.Bold)
            }
          }
        }

        // ── Top Action Bar (Close, Torch, Title, Manual Entry) ───────────────
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
              .size(42.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.55f))
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close Scanner",
              tint = PureWhite
            )
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "Scan Customer QR",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = PureWhite
            )
            Text(
              text = "Point camera at pickup QR code",
              fontSize = 12.sp,
              color = Color(0xFFD1D5DB)
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Flashlight Toggle
            if (hasCameraPermission && cameraInstance?.cameraInfo?.hasFlashUnit() == true) {
              IconButton(
                onClick = {
                  isTorchOn = !isTorchOn
                  cameraInstance?.cameraControl?.enableTorch(isTorchOn)
                },
                modifier = Modifier
                  .size(42.dp)
                  .clip(CircleShape)
                  .background(if (isTorchOn) Color(0xFFEA580C) else Color.Black.copy(alpha = 0.55f))
              ) {
                Icon(
                  imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                  contentDescription = "Toggle Torch",
                  tint = PureWhite
                )
              }
            }

            // Manual Token Input Button
            IconButton(
              onClick = { showManualInputDialog = true },
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
            ) {
              Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = "Manual Code Entry",
                tint = PureWhite
              )
            }
          }
        }

        // ── In-Flight Verification Loading Indicator ─────────────────────────
        if (isVerifying) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color.Black.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1F2937))
                .padding(horizontal = 28.dp, vertical = 22.dp)
            ) {
              CircularProgressIndicator(
                color = Color(0xFFEA580C),
                strokeWidth = 3.dp,
                modifier = Modifier.size(44.dp)
              )
              Spacer(Modifier.height(14.dp))
              Text(
                text = "Verifying Order...",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = PureWhite
              )
              Text(
                text = "Authorizing pickup with backend",
                fontSize = 12.sp,
                color = Color(0xFF9CA3AF)
              )
            }
          }
        }

        // ── Result Bottom Sheet / Modal (Success & Error States) ─────────────
        AnimatedVisibility(
          visible = verificationResult !is VerificationResultState.Idle,
          enter = slideInVertically { it } + fadeIn(),
          exit = slideOutVertically { it } + fadeOut(),
          modifier = Modifier.align(Alignment.BottomCenter)
        ) {
          when (val state = verificationResult) {
            is VerificationResultState.Success -> {
              ScanSuccessSheet(
                response = state.response,
                onScanNext = { resetScannerForNext() },
                onDismiss = onDismiss
              )
            }
            is VerificationResultState.Error -> {
              ScanErrorSheet(
                code = state.code,
                message = state.message,
                onTryAgain = { resetScannerForNext() },
                onDismiss = onDismiss
              )
            }
            VerificationResultState.Idle -> Unit
          }
        }
      }
    }
  }

  // ── Manual Token Input Fallback Dialog ─────────────────────────────────────
  if (showManualInputDialog) {
    Dialog(onDismissRequest = { showManualInputDialog = false }) {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = PureWhite,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(20.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Enter Verification Code",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark
            )
            IconButton(onClick = { showManualInputDialog = false }) {
              Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
            }
          }
          Spacer(Modifier.height(10.dp))
          Text(
            text = "If customer camera scanning is unavailable, enter the verification token or raw payload manually.",
            fontSize = 12.5.sp,
            color = TextMuted
          )
          Spacer(Modifier.height(14.dp))
          OutlinedTextField(
            value = manualTokenText,
            onValueChange = { manualTokenText = it },
            placeholder = { Text("Paste or type verification token", fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Color(0xFFEA580C),
              unfocusedBorderColor = Color(0xFFD1D5DB)
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
                val input = manualTokenText.trim()
                if (input.isNotBlank()) {
                  showManualInputDialog = false
                  performVerification(input)
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
              shape = RoundedCornerShape(10.dp)
            ) {
              Text("Verify Code", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

/**
 * Animated Viewfinder Overlay with corner reticles and vertical sweeping laser
 */
@Composable
private fun ViewfinderOverlay(isScanning: Boolean) {
  val infiniteTransition = rememberInfiniteTransition(label = "laser_transition")
  val laserProgress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "laser_position"
  )

  BoxWithConstraints(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    val boxWidth = (maxWidth * 0.78f).coerceAtMost(310.dp)
    val boxHeight = (boxWidth * 0.78f).coerceAtMost(250.dp) // Rectangular target window

    Box(
      modifier = Modifier
        .size(width = boxWidth, height = boxHeight)
        .border(2.dp, Color(0xFFEA580C).copy(alpha = 0.8f), RoundedCornerShape(16.dp))
        .background(Color.Transparent)
    ) {
      if (isScanning) {
        val sweepOffset = boxHeight * laserProgress
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(2.5.dp)
            .offset(y = sweepOffset)
            .background(
              Brush.horizontalGradient(
                listOf(
                  Color.Transparent,
                  Color(0xFFEA580C),
                  Color(0xFFF97316),
                  Color.Transparent
                )
              )
            )
        )
      }
    }
  }
}

/**
 * CameraX Image Analyzer delegating to ML Kit BarcodeScanning
 */
@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
  imageProxy: ImageProxy,
  scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
  isBusy: Boolean,
  onTokenFound: (String) -> Unit
) {
  val mediaImage = imageProxy.image
  if (mediaImage != null && !isBusy) {
    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(inputImage)
      .addOnSuccessListener { barcodes ->
        val rawValue = barcodes.firstOrNull()?.rawValue
        if (!rawValue.isNullOrBlank()) {
          onTokenFound(rawValue.trim())
        }
      }
      .addOnCompleteListener {
        imageProxy.close()
      }
  } else {
    imageProxy.close()
  }
}

/**
 * Success verification sheet displaying order details, student info, items and total
 */
@Composable
private fun ScanSuccessSheet(
  response: VerifyQrResponseDto,
  onScanNext: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val order = response.order
  val tokenDisplay = order?.tokenNumber?.let { if (it.startsWith("#")) it else "#Q$it" } ?: "#Order"

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .navigationBarsPadding(),
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    color = PureWhite,
    shadowElevation = 16.dp
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
      // Header: Green Check & Success Title
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0xFFDCFCE7)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF16A34A),
            modifier = Modifier.size(28.dp)
          )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Pickup Verified",
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF15803D)
            )
            Spacer(Modifier.width(8.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFDCFCE7))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = "PICKED UP",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF16A34A)
              )
            }
          }
          Text(
            text = "Token $tokenDisplay • ${order?.studentName?.ifBlank { "Customer" }}",
            fontSize = 13.sp,
            color = TextDark,
            fontWeight = FontWeight.SemiBold
          )
        }
      }

      Spacer(Modifier.height(14.dp))
      HorizontalDivider(color = Color(0xFFE5E7EB))
      Spacer(Modifier.height(12.dp))

      // Canteen & Counter info
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Storefront, contentDescription = null, tint = TextMuted, modifier = Modifier.size(15.dp))
          Spacer(Modifier.width(4.dp))
          Text(
            text = order?.pickupCanteenName?.ifBlank { order.canteenId } ?: "Campus Canteen",
            fontSize = 12.5.sp,
            color = TextMuted
          )
        }
        Text(
          text = order?.pickupCounter?.ifBlank { "Counter 1" } ?: "Counter 1",
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark
        )
      }

      Spacer(Modifier.height(12.dp))

      // Item Summary List
      val itemsList = order?.items ?: emptyList()
      Text(
        text = "Order Items (${itemsList.sumOf { it.quantity }})",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted
      )
      Spacer(Modifier.height(6.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFFF9FAFB))
          .padding(10.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          itemsList.take(4).forEach { item ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "${item.quantity} × ${item.name}",
                fontSize = 13.sp,
                color = TextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
              Text(
                text = "₹${item.price * item.quantity}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
              )
            }
          }
          if (itemsList.size > 4) {
            Text(
              text = "+ ${itemsList.size - 4} more item(s)",
              fontSize = 11.5.sp,
              color = TextMuted,
              fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
          }

          HorizontalDivider(color = Color(0xFFE5E7EB), modifier = Modifier.padding(vertical = 4.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Total Paid",
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark
            )
            Text(
              text = "₹${order?.totalAmount ?: 0}",
              fontSize = 16.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color(0xFF16A34A)
            )
          }
        }
      }

      Spacer(Modifier.height(18.dp))

      // Action Buttons: Scan Next & Done
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = onScanNext,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Scan Next", fontWeight = FontWeight.Bold)
        }

        Button(
          onClick = onDismiss,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6)),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f)
        ) {
          Text("Done", color = TextDark, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

/**
 * Error verification sheet with color-coded alerts and instructions
 */
@Composable
private fun ScanErrorSheet(
  code: String,
  message: String,
  onTryAgain: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val (bgColor, iconColor, title) = when (code) {
    "ORDER_NOT_READY" -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), "Order Not Ready")
    "WRONG_CANTEEN" -> Triple(Color(0xFFEFF6FF), Color(0xFF2563EB), "Wrong Canteen")
    "ALREADY_REDEEMED" -> Triple(Color(0xFFF3F4F6), Color(0xFF6B7280), "Already Redeemed")
    "EXPIRED_QR" -> Triple(Color(0xFFFFEDD5), Color(0xFFEA580C), "Expired QR Code")
    "ORDER_CANCELLED" -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "Order Cancelled")
    else -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "Invalid QR Code")
  }

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .navigationBarsPadding(),
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    color = PureWhite,
    shadowElevation = 16.dp
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(bgColor),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (code == "ORDER_NOT_READY") Icons.Default.Warning else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(26.dp)
          )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = iconColor
          )
          Spacer(Modifier.height(2.dp))
          Text(
            text = message,
            fontSize = 13.sp,
            color = TextDark,
            lineHeight = 18.sp
          )
        }
      }

      Spacer(Modifier.height(18.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = onTryAgain,
          colors = ButtonDefaults.buttonColors(containerColor = BlackPrimary),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Scan Again", fontWeight = FontWeight.Bold)
        }

        Button(
          onClick = onDismiss,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6)),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f)
        ) {
          Text("Close", color = TextDark, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

