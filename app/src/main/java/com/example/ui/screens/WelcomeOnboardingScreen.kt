package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.PureWhite

// Vibrant warm amber matching Image 3
private val AmberBrand = Color(0xFFF5A623)
private val TextDark = Color(0xFF1F2937)

/**
 * Onboarding / Welcome screen matching Image 3 (Left phone screen).
 * Top clean section with MessQ logo branding.
 * Bottom warm amber card with "Welcome", description, and "Sign In" / "Sign Up" pills.
 */
@Composable
fun WelcomeOnboardingScreen(
  onNavigateToSignIn: () -> Unit,
  onNavigateToSignUp: () -> Unit,
  onExploreAsGuest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(PureWhite)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("welcome_onboarding_screen")
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // ── Top Half: Logo Branding (like Image 3) ──────────────────────────
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1.05f),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          // MessQ Distinctive Food/Arc Icon
          Box(
            modifier = Modifier.size(110.dp),
            contentAlignment = Alignment.Center,
          ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              drawArc(
                color = AmberBrand,
                startAngle = 35f,
                sweepAngle = 290f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                  width = 7.dp.toPx(),
                  cap = StrokeCap.Round,
                )
              )
            }

            Box(
              modifier = Modifier
                .size(72.dp)
                .background(Color(0xFFFFFBEB), CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Restaurant,
                contentDescription = "QuickBite Logo",
                tint = AmberBrand,
                modifier = Modifier.size(38.dp),
              )
            }
          }

          Spacer(modifier = Modifier.height(20.dp))

          // App Name
          Text(
            text = "QuickBite",
            fontSize = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            color = BlackPrimary,
          )

          Spacer(modifier = Modifier.height(6.dp))

          // Tagline / Subtitle
          Text(
            text = "SMART CAMPUS DINING",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.5.sp,
            color = Color(0xFF9CA3AF),
          )
        }
      }

      // ── Bottom Half: Amber Card with Welcome & Action Buttons (Image 3 Left) ──
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(0.95f)
          .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
          .background(AmberBrand)
          .padding(horizontal = 28.dp, vertical = 28.dp)
      ) {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.SpaceBetween,
        ) {
          Column {
            Text(
              text = "Welcome",
              fontSize = 32.sp,
              fontWeight = FontWeight.ExtraBold,
              color = TextDark,
              modifier = Modifier.testTag("welcome_heading"),
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = "Pre-order hot meals from your campus canteens, skip the 20-min lunch queue, and pick up your order with a live digital counter token.",
              fontSize = 14.sp,
              lineHeight = 21.sp,
              fontWeight = FontWeight.Normal,
              color = TextDark.copy(alpha = 0.88f),
            )
          }

          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            // Two Action Buttons (Sign In / Sign Up pills like Image 3)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              // Sign In (Black pill)
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(54.dp)
                  .shadow(4.dp, RoundedCornerShape(28.dp))
                  .clip(RoundedCornerShape(28.dp))
                  .background(BlackPrimary)
                  .clickable(
                    role = Role.Button,
                    onClick = onNavigateToSignIn,
                  )
                  .testTag("welcome_sign_in_button"),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "Sign In",
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = PureWhite,
                )
              }

              // Sign Up (White pill)
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(54.dp)
                  .shadow(2.dp, RoundedCornerShape(28.dp))
                  .clip(RoundedCornerShape(28.dp))
                  .background(PureWhite)
                  .clickable(
                    role = Role.Button,
                    onClick = onNavigateToSignUp,
                  )
                  .testTag("welcome_sign_up_button"),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "Sign Up",
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = BlackPrimary,
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Guest exploration link
            TextButton(
              onClick = onExploreAsGuest,
              modifier = Modifier.testTag("welcome_guest_link"),
            ) {
              Text(
                text = "Explore Canteens as Guest →",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
            }
          }
        }
      }
    }
  }
}
