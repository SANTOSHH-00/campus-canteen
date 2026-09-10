package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Shared illustration design tokens ────────────────────────────────────────
private val OutlineColor  = Color(0xFF1C1C1E)
private val YellowAccent  = Color(0xFFE8B84B)
private val CardBg        = Color(0xFFFFFFFF)
private val IlluBg        = Color(0xFFF5F0E8)   // warm cream fill
private val TextDarkIllu  = Color(0xFF1C1C1E)
private val TextMutedIllu = Color(0xFF8A8078)
private val GreenAccent   = Color(0xFF2EC4B6)
private val BorderGrayIllu = Color(0xFFDDD8D0)
private val SkinLight     = Color(0xFFFDCBA8)
private val SkinMed       = Color(0xFFFDE68A)

/**
 * Slide 1 — Phone Order UI
 * A detailed smartphone mockup showing the canteen ordering app,
 * surrounded by floating food emoji circles.
 */
@Composable
fun PersonSlideOneImage(modifier: Modifier = Modifier) {
    Box(
        modifier         = modifier.size(300.dp).testTag("person_slide_one_image"),
        contentAlignment = Alignment.Center,
    ) {
        // ── White card frame ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(260.dp)
                .shadow(8.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(CardBg),
        ) {
            // Warm cream lower fill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .align(Alignment.BottomCenter)
                    .background(IlluBg),
            )

            // ── Phone mockup (Canvas) ──────────────────────────────────────
            Canvas(
                modifier = Modifier
                    .size(112.dp, 204.dp)
                    .align(Alignment.Center),
            ) {
                val w = size.width
                val h = size.height

                // Phone shell
                drawRoundRect(CardBg, cornerRadius = CornerRadius(18.dp.toPx()))
                drawRoundRect(
                    OutlineColor,
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(2.5.dp.toPx()),
                )

                // Notch
                drawRoundRect(
                    OutlineColor,
                    topLeft = Offset(w * 0.28f, h * 0.010f),
                    size    = Size(w * 0.44f, h * 0.021f),
                    cornerRadius = CornerRadius(5f),
                )

                // App header (dark bar)
                drawRoundRect(
                    OutlineColor,
                    topLeft = Offset(w * 0.05f, h * 0.048f),
                    size    = Size(w * 0.90f, h * 0.075f),
                    cornerRadius = CornerRadius(7f),
                )
                // Header accent dot
                drawCircle(YellowAccent, 3.5f, Offset(w * 0.14f, h * 0.086f))

                // ── Food card 1 (active / highlighted) ─────────────────────
                drawRoundRect(
                    YellowAccent.copy(alpha = 0.16f),
                    topLeft = Offset(w * 0.05f, h * 0.155f),
                    size    = Size(w * 0.90f, h * 0.165f),
                    cornerRadius = CornerRadius(9f),
                )
                drawRoundRect(
                    YellowAccent,
                    topLeft = Offset(w * 0.05f, h * 0.155f),
                    size    = Size(w * 0.90f, h * 0.165f),
                    cornerRadius = CornerRadius(9f),
                    style = Stroke(1.5f),
                )
                drawCircle(YellowAccent, 13f, Offset(w * 0.18f, h * 0.237f))
                // Title + price mocks
                drawRoundRect(
                    OutlineColor.copy(alpha = 0.80f),
                    topLeft = Offset(w * 0.32f, h * 0.175f),
                    size    = Size(w * 0.38f, h * 0.020f), cornerRadius = CornerRadius(3f),
                )
                drawRoundRect(
                    OutlineColor.copy(alpha = 0.35f),
                    topLeft = Offset(w * 0.32f, h * 0.204f),
                    size    = Size(w * 0.25f, h * 0.016f), cornerRadius = CornerRadius(3f),
                )
                drawRoundRect(
                    OutlineColor.copy(alpha = 0.75f),
                    topLeft = Offset(w * 0.74f, h * 0.175f),
                    size    = Size(w * 0.17f, h * 0.020f), cornerRadius = CornerRadius(3f),
                )

                // ── Food card 2 ─────────────────────────────────────────────
                drawRoundRect(
                    IlluBg,
                    topLeft = Offset(w * 0.05f, h * 0.348f),
                    size    = Size(w * 0.90f, h * 0.152f),
                    cornerRadius = CornerRadius(9f),
                )
                drawCircle(
                    Color(0xFFE0DCD5), 12f,
                    Offset(w * 0.18f, h * 0.424f),
                )
                drawRoundRect(
                    OutlineColor.copy(alpha = 0.60f),
                    topLeft = Offset(w * 0.32f, h * 0.367f),
                    size    = Size(w * 0.36f, h * 0.019f), cornerRadius = CornerRadius(3f),
                )
                drawRoundRect(
                    OutlineColor.copy(alpha = 0.25f),
                    topLeft = Offset(w * 0.32f, h * 0.395f),
                    size    = Size(w * 0.24f, h * 0.015f), cornerRadius = CornerRadius(3f),
                )

                // ── Food card 3 ─────────────────────────────────────────────
                drawRoundRect(
                    IlluBg,
                    topLeft = Offset(w * 0.05f, h * 0.524f),
                    size    = Size(w * 0.90f, h * 0.148f),
                    cornerRadius = CornerRadius(9f),
                )
                drawCircle(
                    Color(0xFFE0DCD5), 12f,
                    Offset(w * 0.18f, h * 0.598f),
                )
                drawRoundRect(
                    OutlineColor.copy(alpha = 0.50f),
                    topLeft = Offset(w * 0.32f, h * 0.543f),
                    size    = Size(w * 0.40f, h * 0.019f), cornerRadius = CornerRadius(3f),
                )
                drawRoundRect(
                    OutlineColor.copy(alpha = 0.20f),
                    topLeft = Offset(w * 0.32f, h * 0.571f),
                    size    = Size(w * 0.28f, h * 0.015f), cornerRadius = CornerRadius(3f),
                )

                // ── Golden "Order Now" button ───────────────────────────────
                drawRoundRect(
                    YellowAccent,
                    topLeft = Offset(w * 0.07f, h * 0.85f),
                    size    = Size(w * 0.86f, h * 0.11f),
                    cornerRadius = CornerRadius(22f),
                )
                drawRoundRect(
                    CardBg,
                    topLeft = Offset(w * 0.33f, h * 0.896f),
                    size    = Size(w * 0.34f, h * 0.018f),
                    cornerRadius = CornerRadius(5f),
                )
            }

            // ── Floating food emoji circles ────────────────────────────────
            // Top-left
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .align(Alignment.TopStart)
                    .offset(x = 18.dp, y = 44.dp)
                    .background(IlluBg, CircleShape)
                    .border(1.5.dp, BorderGrayIllu, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("☕", fontSize = 22.sp) }

            // Top-right
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (-18).dp, y = 44.dp)
                    .background(IlluBg, CircleShape)
                    .border(1.5.dp, BorderGrayIllu, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("🍛", fontSize = 22.sp) }

            // Bottom-left
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = 22.dp, y = (-60).dp)
                    .background(IlluBg, CircleShape)
                    .border(1.5.dp, BorderGrayIllu, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("🥙", fontSize = 20.sp) }

            // Bottom-right
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = (-22).dp, y = (-60).dp)
                    .background(IlluBg, CircleShape)
                    .border(1.5.dp, BorderGrayIllu, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("🍵", fontSize = 20.sp) }
        }

        // ── Floating badges (outside card) ─────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 4.dp, y = 22.dp)
                .shadow(4.dp, RoundedCornerShape(20.dp))
                .background(CardBg, RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(18.dp).clip(CircleShape).background(YellowAccent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Bolt, null, tint = CardBg, modifier = Modifier.size(11.dp))
                }
                Spacer(Modifier.width(5.dp))
                Text("2 min order", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkIllu)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 22.dp)
                .shadow(4.dp, RoundedCornerShape(16.dp))
                .background(CardBg, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Fastfood, null, tint = YellowAccent, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("Pre-Order", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkIllu)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-8).dp)
                .shadow(4.dp, RoundedCornerShape(20.dp))
                .background(OutlineColor, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(GreenAccent))
                Spacer(Modifier.width(6.dp))
                Text("3 items selected · ₹120", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = CardBg)
            }
        }
    }
}

/**
 * Slide 2 — Token Ready
 * A large circular token "#042" in golden ring with a small counter
 * scene (chef + student) below, and a notification bell above.
 */
@Composable
fun PersonSlideTwoImage(modifier: Modifier = Modifier) {
    Box(
        modifier         = modifier.size(300.dp).testTag("person_slide_two_image"),
        contentAlignment = Alignment.Center,
    ) {
        // ── White card frame ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(260.dp)
                .shadow(8.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(CardBg),
        ) {
            // Cream lower fill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .align(Alignment.BottomCenter)
                    .background(IlluBg),
            )

            // ── Notification bell (top-center) ─────────────────────────────
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = 26.dp)
                    .background(YellowAccent.copy(alpha = 0.14f), CircleShape)
                    .border(1.5.dp, YellowAccent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.NotificationsActive,
                    null,
                    tint     = YellowAccent,
                    modifier = Modifier.size(20.dp),
                )
            }

            // ── Large token circle ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .align(Alignment.Center)
                    .offset(y = (-14).dp)
                    .background(YellowAccent.copy(alpha = 0.06f), CircleShape)
                    .border(3.dp, YellowAccent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "YOUR TOKEN",
                        fontSize      = 8.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = TextMutedIllu,
                        letterSpacing = 1.8.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "#042",
                        fontSize   = 36.sp,
                        fontWeight = FontWeight.Black,
                        color      = OutlineColor,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .background(GreenAccent.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "READY",
                            fontSize   = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color      = GreenAccent,
                        )
                    }
                }
            }

            // ── Counter desk + staff and student figures ───────────────────
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .align(Alignment.BottomCenter),
            ) {
                val w  = size.width
                val h  = size.height
                val sk = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

                // Counter desk line
                drawLine(
                    OutlineColor.copy(alpha = 0.7f),
                    Offset(w * 0.06f, h * 0.30f),
                    Offset(w * 0.94f, h * 0.30f),
                    strokeWidth = 2.5f, cap = StrokeCap.Round,
                )
                drawRoundRect(
                    color    = IlluBg,
                    topLeft  = Offset(w * 0.06f, h * 0.30f),
                    size     = Size(w * 0.88f, h * 0.20f),
                    cornerRadius = CornerRadius(4f),
                )

                // Staff (chef) figure — left side
                val chefX = w * 0.22f
                drawCircle(SkinLight, 14f, Offset(chefX, h * 0.13f))
                drawCircle(OutlineColor, 14f, Offset(chefX, h * 0.13f), style = Stroke(2f))
                // Chef hat
                val hat = Path().apply {
                    moveTo(chefX - 13f, h * 0.07f)
                    cubicTo(chefX - 15f, h * -0.01f, chefX + 15f, h * -0.01f, chefX + 13f, h * 0.07f)
                    close()
                }
                drawPath(hat, CardBg)
                drawPath(hat, OutlineColor, style = Stroke(1.8f))
                // Chef body
                val chefBody = Path().apply {
                    moveTo(chefX - 15f, h * 0.30f)
                    lineTo(chefX - 11f, h * 0.19f)
                    lineTo(chefX + 11f, h * 0.19f)
                    lineTo(chefX + 15f, h * 0.30f)
                }
                drawPath(chefBody, Color(0xFFF0EDE8))
                drawPath(chefBody, OutlineColor, style = sk)
                // Chef arm reaching forward
                drawLine(OutlineColor, Offset(chefX + 11f, h * 0.21f), Offset(chefX + 28f, h * 0.24f), 2.2f, StrokeCap.Round)

                // Student figure — right side, arms slightly raised (excited)
                val stuX = w * 0.78f
                drawCircle(SkinMed, 14f, Offset(stuX, h * 0.13f))
                drawCircle(OutlineColor, 14f, Offset(stuX, h * 0.13f), style = Stroke(2f))
                val stuHair = Path().apply {
                    moveTo(stuX - 13f, h * 0.07f)
                    cubicTo(stuX - 14f, h * 0.01f, stuX + 13f, h * 0.01f, stuX + 13f, h * 0.07f)
                    close()
                }
                drawPath(stuHair, OutlineColor)
                val stuBody = Path().apply {
                    moveTo(stuX - 15f, h * 0.30f)
                    lineTo(stuX - 11f, h * 0.19f)
                    lineTo(stuX + 11f, h * 0.19f)
                    lineTo(stuX + 15f, h * 0.30f)
                }
                drawPath(stuBody, Color(0xFFF0EDE8))
                drawPath(stuBody, OutlineColor, style = sk)
                // Arms raised slightly
                drawLine(OutlineColor, Offset(stuX - 11f, h * 0.21f), Offset(stuX - 22f, h * 0.12f), 2.2f, StrokeCap.Round)
                drawLine(OutlineColor, Offset(stuX + 11f, h * 0.21f), Offset(stuX + 22f, h * 0.12f), 2.2f, StrokeCap.Round)
            }
        }

        // ── Badges ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 4.dp, y = 22.dp)
                .shadow(4.dp, RoundedCornerShape(20.dp))
                .background(Color(0xFFDCFCE7), RoundedCornerShape(20.dp))
                .border(1.5.dp, GreenAccent, RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, null, tint = GreenAccent, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ready to Pickup!", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14532D))
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 22.dp)
                .shadow(4.dp, RoundedCornerShape(16.dp))
                .background(CardBg, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storefront, null, tint = OutlineColor, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("Counter 2", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkIllu)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-8).dp)
                .shadow(4.dp, RoundedCornerShape(20.dp))
                .background(CardBg, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restaurant, null, tint = YellowAccent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("North Campus Canteen", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkIllu)
            }
        }
    }
}

/**
 * Slide 3 — Food Tray (Overhead)
 * A warm top-down view of a lunch tray: plate with rice + curry,
 * salad bowl, tea cup, fork and spoon. A "20 min saved" badge inside.
 */
@Composable
fun PersonSlideThreeImage(modifier: Modifier = Modifier) {
    Box(
        modifier         = modifier.size(300.dp).testTag("person_slide_three_image"),
        contentAlignment = Alignment.Center,
    ) {
        // ── White card frame ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(260.dp)
                .shadow(8.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(CardBg),
        ) {
            // Cream lower fill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .align(Alignment.BottomCenter)
                    .background(IlluBg),
            )

            // ── Food tray overhead (Canvas) ────────────────────────────────
            Canvas(
                modifier = Modifier
                    .size(224.dp)
                    .align(Alignment.Center)
                    .offset(y = (-6).dp),
            ) {
                val w = size.width
                val h = size.height

                // Tray background (warm oval)
                drawRoundRect(
                    color    = Color(0xFFEDE8DF),
                    topLeft  = Offset(w * 0.03f, h * 0.12f),
                    size     = Size(w * 0.94f, h * 0.76f),
                    cornerRadius = CornerRadius(22.dp.toPx()),
                )
                drawRoundRect(
                    color    = OutlineColor.copy(alpha = 0.18f),
                    topLeft  = Offset(w * 0.03f, h * 0.12f),
                    size     = Size(w * 0.94f, h * 0.76f),
                    cornerRadius = CornerRadius(22.dp.toPx()),
                    style    = Stroke(1.8f),
                )

                // ── Main round plate (left-center) ──────────────────────────
                val plateCx = w * 0.40f
                val plateCy = h * 0.51f
                val plateR  = w * 0.235f

                drawCircle(CardBg, plateR, Offset(plateCx, plateCy))

                // Rice section — left half (cream dots = rice grains)
                drawArc(
                    color     = Color(0xFFF5F0E8),
                    startAngle = 90f, sweepAngle = 180f, useCenter = true,
                    topLeft   = Offset(plateCx - plateR, plateCy - plateR),
                    size      = Size(plateR * 2, plateR * 2),
                )
                // Rice grain dots
                val dotSpacing = 8.dp.toPx()
                for (row in -4..4) {
                    for (col in -5..-1) {
                        val dx = col * dotSpacing
                        val dy = row * dotSpacing
                        if (dx * dx + dy * dy < (plateR - 6f) * (plateR - 6f)) {
                            drawCircle(Color(0xFFDDD8CE), 2.5f, Offset(plateCx + dx, plateCy + dy))
                        }
                    }
                }

                // Curry section — right half (golden)
                drawArc(
                    color     = YellowAccent.copy(alpha = 0.82f),
                    startAngle = 270f, sweepAngle = 180f, useCenter = true,
                    topLeft   = Offset(plateCx - plateR, plateCy - plateR),
                    size      = Size(plateR * 2, plateR * 2),
                )

                // Plate outline + divider
                drawCircle(OutlineColor, plateR, Offset(plateCx, plateCy), style = Stroke(2f))
                drawLine(
                    OutlineColor.copy(alpha = 0.3f),
                    Offset(plateCx, plateCy - plateR),
                    Offset(plateCx, plateCy + plateR),
                    strokeWidth = 1.5f,
                )

                // ── Salad bowl (top-right of tray) ─────────────────────────
                val saladCx = w * 0.75f
                val saladCy = h * 0.29f
                val saladR  = w * 0.115f
                drawCircle(Color(0xFFDFF7EC), saladR, Offset(saladCx, saladCy))
                drawCircle(OutlineColor, saladR, Offset(saladCx, saladCy), style = Stroke(1.5f))
                drawCircle(Color(0xFF5BB87A), 5f,   Offset(saladCx - 8f,  saladCy))
                drawCircle(Color(0xFF5BB87A), 4f,   Offset(saladCx + 5f,  saladCy - 7f))
                drawCircle(Color(0xFF5BB87A), 3.5f, Offset(saladCx + 3f,  saladCy + 8f))
                drawCircle(Color(0xFF5BB87A), 4.5f, Offset(saladCx - 4f,  saladCy - 9f))
                drawCircle(Color(0xFFFCC96B), 3f,   Offset(saladCx + 7f,  saladCy + 4f))

                // ── Tea/coffee cup (bottom-right of tray) ──────────────────
                val cupX = w * 0.75f
                val cupY = h * 0.625f
                drawRoundRect(CardBg,        Offset(cupX - 14f, cupY - 17f), Size(28f, 34f), CornerRadius(5f))
                drawRoundRect(OutlineColor,  Offset(cupX - 14f, cupY - 17f), Size(28f, 34f), CornerRadius(5f), style = Stroke(2f))
                // Hot drink surface
                drawRoundRect(
                    Color(0xFF8B5A2B).copy(alpha = 0.55f),
                    Offset(cupX - 12f, cupY - 15f),
                    Size(24f, 7f),
                    CornerRadius(3f),
                )
                // Cup handle
                drawArc(
                    OutlineColor, -60f, 120f, false,
                    Offset(cupX + 8f, cupY - 10f), Size(16f, 20f),
                    style = Stroke(2f),
                )
                // Steam
                drawLine(Color(0xFFB0A090), Offset(cupX - 3f, cupY - 24f), Offset(cupX - 3f, cupY - 19f), 2f, StrokeCap.Round)
                drawLine(Color(0xFFB0A090), Offset(cupX + 4f, cupY - 27f), Offset(cupX + 4f, cupY - 22f), 2f, StrokeCap.Round)

                // ── Utensils (bottom-left of tray) ─────────────────────────
                val uY = h * 0.70f
                // Fork
                drawLine(OutlineColor, Offset(w * 0.11f, uY), Offset(w * 0.11f, uY + h * 0.14f), 3f, StrokeCap.Round)
                // Fork tines
                for (t in -1..1) {
                    drawLine(OutlineColor, Offset(w * 0.11f + t * 3.5f, uY), Offset(w * 0.11f + t * 3.5f, uY - h * 0.03f), 2f, StrokeCap.Round)
                }
                // Spoon handle
                drawLine(OutlineColor, Offset(w * 0.19f, uY), Offset(w * 0.19f, uY + h * 0.14f), 3f, StrokeCap.Round)
                // Spoon bowl
                drawOval(CardBg,       Offset(w * 0.155f, uY - h * 0.044f), Size(w * 0.07f, h * 0.036f))
                drawOval(OutlineColor, Offset(w * 0.155f, uY - h * 0.044f), Size(w * 0.07f, h * 0.036f), style = Stroke(1.8f))
            }

            // ── "20 min saved" badge inside card ──────────────────────────
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = (-16).dp, y = (-14).dp)
                    .background(YellowAccent.copy(alpha = 0.12f), CircleShape)
                    .border(2.dp, YellowAccent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("20m", fontSize = 12.sp, fontWeight = FontWeight.Black, color = OutlineColor)
                    Text("saved", fontSize = 7.sp, color = TextMutedIllu)
                }
            }
        }

        // ── Badges ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 22.dp)
                .shadow(4.dp, RoundedCornerShape(16.dp))
                .background(CardBg, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, null, tint = YellowAccent, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("20 mins", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDarkIllu)
                }
                Text("saved in break", fontSize = 9.sp, color = TextMutedIllu)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-8).dp)
                .shadow(4.dp, RoundedCornerShape(20.dp))
                .background(CardBg, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(0.75f),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(30.dp).clip(CircleShape).background(Color(0xFFFEF3C7)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.LocalCafe, null, tint = YellowAccent, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Skip Long Queues", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkIllu)
                        Text("More time for friends", fontSize = 9.sp, color = TextMutedIllu)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(GreenAccent)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text("Done", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CardBg)
                }
            }
        }
    }
}
