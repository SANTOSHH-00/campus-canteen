// SplashScreen: Jetpack Compose screen for Splash user interface
package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

private val SplashBg     = Color(0xFF1C1C1E)   // near-black — premium dark
private val SplashAccent = Color(0xFFE8B84B)   // golden-yellow (app accent)
private val SplashCream  = Color(0xFFF5F0E8)   // warm cream text
private val SplashMuted  = Color(0xFFB0A88C)   // muted warm subtitle
private val SplashGreen  = Color(0xFF2EC4B6)   // success accent

/**
 * Campus Canteen splash screen.
 *
 * Matches the original layout:
 * spinning Q-arc logo • speed lines • pulsing inner icon •
 * food emoji background • dotted progress steps • bottom wave.
 *
 * Increased timing slightly (~2.2s total) so the progress steps
 * animate smoothly and comfortably without opening too fast.
 */
@Composable
fun CampusCanteenSplashScreen(onLoadingFinished: () -> Unit) {

    var currentStep by remember { mutableIntStateOf(0) }
    val stepProgress = remember { Animatable(0f) }

    val messages = listOf(
        "Taking your order...",
        "Preparing something delicious...",
        "Getting your order ready...",
        "Almost there...",
    )

    // Animated 4-step sequence with smooth looping and lifecycle safety
    LaunchedEffect(Unit) {
        var hasFinishedFirstCycle = false
        while (isActive) {
            for (step in 0..3) {
                currentStep = step
                stepProgress.snapTo(0f)
                stepProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 920,
                        easing = LinearEasing,
                    ),
                )
            }
            if (!hasFinishedFirstCycle) {
                hasFinishedFirstCycle = true
                delay(300)
                onLoadingFinished()
            }
            // Smooth brief pause before repeating the sequence if splash remains visible
            delay(400)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splashAnim")

    // Spinning arc rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label         = "rotation",
    )

    // Inner circle pulse
    val pulse by infiniteTransition.animateFloat(
        initialValue  = 0.9f,
        targetValue   = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBg),
    ) {
        SplashFoodBackground()

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter),
        ) {
            val path = Path().apply {
                moveTo(0f, size.height * 0.55f)
                cubicTo(
                    size.width * 0.28f, size.height * 0.05f,
                    size.width * 0.72f, size.height * 0.95f,
                    size.width, size.height * 0.48f,
                )
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path, SplashAccent.copy(alpha = 0.07f))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {

            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier.size(155.dp),
            ) {

                // Layer 1: Spinning golden arc (280° sweep — leaves gap at bottom-right)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(rotation),
                ) {
                    drawArc(
                        color      = SplashAccent,
                        startAngle = 40f,
                        sweepAngle = 278f,
                        useCenter  = false,
                        style      = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round),
                    )
                }

                // Layer 2: Static speed lines (left of inner circle — the Q tail)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width  / 2f
                    val cy = size.height / 2f
                    val col = SplashAccent.copy(alpha = 0.80f)
                    val sw  = 4.5.dp.toPx()

                    // Three horizontal speed dashes, staggered in length
                    drawLine(col, Offset(cx - 88f, cy - 11f), Offset(cx - 60f, cy - 11f), sw, StrokeCap.Round)
                    drawLine(col, Offset(cx - 92f, cy),       Offset(cx - 58f, cy),       sw, StrokeCap.Round)
                    drawLine(col, Offset(cx - 85f, cy + 11f), Offset(cx - 62f, cy + 11f), sw, StrokeCap.Round)
                }

                // Layer 3: Inner pulsing cream circle with golden restaurant icon
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(pulse)
                        .background(SplashCream, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Default.Restaurant,
                        contentDescription = "QuickBite",
                        tint               = SplashAccent,
                        modifier           = Modifier.size(52.dp),
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            // App name
            Text(
                text       = "QuickBite",
                color      = SplashCream,
                fontSize   = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign  = TextAlign.Center,
            )

            Spacer(Modifier.height(7.dp))

            // Tagline
            Text(
                text      = "Good Food. Short Breaks.",
                color     = SplashMuted,
                fontSize  = 16.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(56.dp))

            SplashOrderProgress(
                currentStep = currentStep,
                stepProgress = stepProgress.value,
            )

            Spacer(Modifier.height(30.dp))

            // Loading message
            Text(
                text       = messages[currentStep],
                color      = SplashCream,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign  = TextAlign.Center,
            )

            Spacer(Modifier.height(18.dp))

            // Bouncing dots
            SplashLoadingDots()
        }
    }
}


/**
 * Redesigned four-step animated icon sequence matching the connected circular
 * progress design:
 * [ Icon 1 ]  · · · ·  [ Icon 2 ]  · · · ·  [ Icon 3 ]  · · · ·  [ Icon 4 ]
 *
 * Features:
 * - Four horizontally connected circular icon containers with thin, clean outlines.
 * - Active circular progress ring wrapping around the active icon.
 * - Subtle scale-up for the active circle.
 * - Seamless progressive illumination of connecting dots between steps.
 * - Muted subtle styling for inactive and completed icons.
 * - Pure QuickBite golden-yellow & warm cream theme harmony.
 */
@Composable
internal fun SplashOrderProgress(
    currentStep: Int,
    stepProgress: Float,
    modifier: Modifier = Modifier,
) {
    val stepIcons = listOf(
        Icons.Default.Fastfood,
        Icons.Default.Restaurant,
        Icons.Default.ShoppingBag,
        Icons.Default.Check,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        stepIcons.forEachIndexed { index, icon ->
            val isActive = (index == currentStep)
            val isCompleted = (index < currentStep)

            // Smooth scale transition for the active circle
            val targetScale = if (isActive) 1.12f else 1.0f
            val circleScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                label = "scale_$index",
            )

            // Step Circle Container
            StepCircleItem(
                stepIndex = index,
                icon = icon,
                isActive = isActive,
                isCompleted = isCompleted,
                stepProgress = if (isActive) stepProgress else if (isCompleted) 1f else 0f,
                scale = circleScale,
            )

            // Connecting Dotted Line
            if (index < stepIcons.lastIndex) {
                StepDottedConnector(
                    connectorIndex = index,
                    currentStep = currentStep,
                    stepProgress = stepProgress,
                )
            }
        }
    }
}


@Composable
private fun StepCircleItem(
    stepIndex: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    isCompleted: Boolean,
    stepProgress: Float,
    scale: Float,
) {
    val circleSize = 50.dp

    // Smooth transition for active ring alpha when step transitions to completed
    val ringAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "ringAlpha_$stepIndex",
    )

    // Base subtle border color
    val baseBorderColor = when {
        isActive -> SplashAccent.copy(alpha = 0.45f)
        isCompleted -> SplashAccent.copy(alpha = 0.40f)
        else -> SplashCream.copy(alpha = 0.22f)
    }

    // Icon tint
    val iconTint = when {
        isActive -> SplashCream
        isCompleted -> SplashAccent.copy(alpha = 0.85f)
        else -> SplashCream.copy(alpha = 0.38f)
    }

    // Subtle micro-motion for the active icon without distortion
    val iconOffsetY = if (isActive && stepIndex == 0) {
        (-2.5f * sin(stepProgress * PI.toFloat())).dp
    } else 0.dp

    val iconRotation = if (isActive && stepIndex == 1) {
        3.5f * sin(stepProgress * 2f * PI.toFloat())
    } else 0f

    val iconScaleExtra = when {
        isActive && stepIndex == 2 -> 1f + 0.08f * sin(stepProgress * PI.toFloat())
        isActive && stepIndex == 3 -> {
            if (stepProgress < 0.35f) {
                0.85f + (stepProgress / 0.35f) * 0.25f
            } else {
                1.10f - ((stepProgress - 0.35f) / 0.65f) * 0.10f
            }
        }
        else -> 1f
    }

    Box(
        modifier = Modifier
            .size(circleSize)
            .scale(scale),
        contentAlignment = Alignment.Center,
    ) {
        // Subtle ambient glow / soft background fill when active
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (isActive) SplashAccent.copy(alpha = 0.12f) else Color.Transparent,
                    shape = CircleShape,
                ),
        )

        // Custom Canvas for thin base outline and animated circular progress ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ringStrokeWidth = 3.2.dp.toPx()
            val baseStrokeWidth = 1.5.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)

            // Keep base circle and arc aligned on the exact same radius
            val radius = (size.minDimension - ringStrokeWidth) / 2f

            // 1. Base subtle circular outline
            drawCircle(
                color = baseBorderColor,
                radius = radius,
                center = center,
                style = Stroke(width = baseStrokeWidth),
            )

            // 2. Active circular progress ring (animates around the icon)
            if (ringAlpha > 0.01f) {
                // Starts at 180° (left, where dotted connector enters) and sweeps clockwise
                val startAngle = 180f
                val sweepAngle = (stepProgress * 360f).coerceIn(0f, 360f)

                if (sweepAngle > 0.5f) {
                    val arcTopLeft = Offset(
                        center.x - radius,
                        center.y - radius,
                    )
                    val arcSize = Size(radius * 2f, radius * 2f)

                    // Draw golden-yellow progress arc with rounded cap
                    drawArc(
                        color = SplashAccent.copy(alpha = ringAlpha),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = ringStrokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
        }

        // Existing icon artwork, perfectly centered with micro-motion
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .size(24.dp)
                .offset(y = iconOffsetY)
                .rotate(iconRotation)
                .scale(iconScaleExtra),
        )
    }
}


@Composable
private fun StepDottedConnector(
    connectorIndex: Int,
    currentStep: Int,
    stepProgress: Float,
) {
    val connectorWidth = 28.dp
    val connectorHeight = 16.dp
    val dotCount = 5

    Canvas(
        modifier = Modifier
            .width(connectorWidth)
            .height(connectorHeight),
    ) {
        val centerY = size.height / 2f
        val baseInactiveColor = SplashCream.copy(alpha = 0.20f)
        val activeCompletedColor = SplashAccent.copy(alpha = 0.90f)

        for (d in 0 until dotCount) {
            val cx = (d + 0.5f) / dotCount.toFloat() * size.width

            val (dotColor, dotRadius) = when {
                // Prior steps: connector is fully completed
                connectorIndex < currentStep -> {
                    activeCompletedColor to 2.2.dp.toPx()
                }
                // Current active step: progressive illumination of dots from left to right
                connectorIndex == currentStep -> {
                    val dotStart = 0.30f + (d.toFloat() / dotCount.toFloat()) * 0.45f
                    val dotEnd = dotStart + 0.16f
                    val fraction = ((stepProgress - dotStart) / (dotEnd - dotStart)).coerceIn(0f, 1f)

                    val color = lerp(baseInactiveColor, activeCompletedColor, fraction)
                    val r = 1.9.dp.toPx() + (2.5.dp.toPx() - 1.9.dp.toPx()) * fraction
                    color to r
                }
                // Upcoming steps: connector is inactive
                else -> {
                    baseInactiveColor to 1.9.dp.toPx()
                }
            }

            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(cx, centerY),
            )
        }
    }
}


@Composable
private fun SplashLoadingDots() {

    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val scale by infiniteTransition.animateFloat(
                initialValue  = 0.5f,
                targetValue   = 1f,
                animationSpec = infiniteRepeatable(
                    tween(durationMillis = 480, delayMillis = index * 160),
                    RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .background(SplashAccent.copy(alpha = 0.85f), CircleShape),
            )
        }
    }
}


@Composable
private fun SplashFoodBackground() {
    val a = 0.07f // opacity
    Box(modifier = Modifier.fillMaxSize()) {
        Text("🍔", fontSize = 46.sp, color = Color.White.copy(alpha = a),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 55.dp))
        Text("🍜", fontSize = 50.sp, color = Color.White.copy(alpha = a),
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 32.dp, top = 48.dp))
        Text("🥗", fontSize = 44.sp, color = Color.White.copy(alpha = a),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp))
        Text("🍱", fontSize = 50.sp, color = Color.White.copy(alpha = a),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 22.dp))
        Text("☕", fontSize = 46.sp, color = Color.White.copy(alpha = a),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 70.dp))
        Text("🍕", fontSize = 46.sp, color = Color.White.copy(alpha = a),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 70.dp))
        Text("🥪", fontSize = 44.sp, color = Color.White.copy(alpha = 0.055f),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp))
        Text("🍛", fontSize = 52.sp, color = Color.White.copy(alpha = 0.055f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun SplashOrderProgressPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SplashBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        SplashOrderProgress(currentStep = 1, stepProgress = 0.65f)
    }
}
