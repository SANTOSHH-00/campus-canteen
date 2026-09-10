package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ── Private palette (matches app theme, no external import needed) ─────────────
private val SplashBg     = Color(0xFF1C1C1E)   // near-black — premium dark
private val SplashAccent = Color(0xFFE8B84B)   // golden-yellow (app accent)
private val SplashCream  = Color(0xFFF5F0E8)   // warm cream text
private val SplashMuted  = Color(0xFFB0A88C)   // muted warm subtitle
private val SplashGreen  = Color(0xFF2EC4B6)   // success accent

/**
 * Campus Canteen splash screen.
 *
 * Matches the MessQ-style layout from the reference image:
 * spinning Q-arc logo • speed lines • pulsing inner icon •
 * food emoji background • dotted progress steps • bottom wave.
 *
 * Colors adapted to the app's warm-cream / golden-yellow theme.
 */
@Composable
fun CampusCanteenSplashScreen(onLoadingFinished: () -> Unit) {

    var currentStep by remember { mutableIntStateOf(0) }

    val messages = listOf(
        "Taking your order...",
        "Preparing something delicious...",
        "Getting your order ready...",
        "Almost there...",
    )

    LaunchedEffect(Unit) {
        repeat(4) { step ->
            currentStep = step
            delay(900)
        }
        onLoadingFinished()
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
        // ── Subtle food emoji background ──────────────────────────────────────
        SplashFoodBackground()

        // ── Bottom wave (gentle S-curve golden shape) ─────────────────────────
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

        // ── Main content column ───────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {

            // ── Logo: Q-style spinning arc + static speed lines + pulsing icon ──
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

            Spacer(Modifier.height(72.dp))

            // ── Order progress steps with dotted connectors ───────────────────
            SplashOrderProgress(currentStep = currentStep)

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

// ── 4-step progress row with Canvas-drawn dotted connectors ──────────────────

@Composable
private fun SplashOrderProgress(currentStep: Int) {

    val stepIcons = listOf(
        Icons.Default.Fastfood,
        Icons.Default.Restaurant,
        Icons.Default.ShoppingBag,
        Icons.Default.Check,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        stepIcons.forEachIndexed { index, icon ->

            // Step circle
            val active = index <= currentStep
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(
                        color = if (active) SplashAccent else Color.Transparent,
                        shape = CircleShape,
                    )
                    .border(
                        width = if (active) 0.dp else 1.5.dp,
                        color = SplashAccent.copy(alpha = 0.35f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = if (active) SplashBg else SplashCream.copy(alpha = 0.45f),
                    modifier           = Modifier.size(26.dp),
                )
            }

            // Dotted connector (Canvas circles)
            if (index < stepIcons.lastIndex) {
                Canvas(modifier = Modifier.width(22.dp).height(4.dp)) {
                    val dotR   = 2.dp.toPx()
                    val gap    = 7.dp.toPx()
                    val color  = if (index < currentStep) SplashAccent else SplashAccent.copy(alpha = 0.25f)
                    var x = dotR
                    while (x < size.width - dotR) {
                        drawCircle(color, dotR, Offset(x, size.height / 2f))
                        x += gap
                    }
                }
            }
        }
    }
}

// ── Three staggered bouncing dots ─────────────────────────────────────────────

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

// ── Subtle food emoji pattern tiled across the dark background ────────────────

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
