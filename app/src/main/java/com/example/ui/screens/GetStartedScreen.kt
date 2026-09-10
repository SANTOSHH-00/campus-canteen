package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PersonSlideOneImage
import com.example.ui.components.PersonSlideThreeImage
import com.example.ui.components.PersonSlideTwoImage
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.DeliveryOrangeLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import kotlinx.coroutines.launch

import com.example.ui.state.UserProfile

data class OnboardingPageData(
  val title: String,
  val subtitle: String,
)

@Composable
fun GetStartedScreen(
  onLoginSuccess: (UserProfile) -> Unit = {},
  onExploreGuest: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val pages =
    listOf(
      OnboardingPageData(
        title = "Pre-Order Meals\nBefore Your Break",
        subtitle = "Browse your campus canteens, customize your food, and place your order in seconds.",
      ),
      OnboardingPageData(
        title = "Skip the Rush &\nCounter Queues",
        subtitle = "Get notified with a live digital token the moment your hot meal is ready at the counter.",
      ),
      OnboardingPageData(
        title = "Enjoy Fresh Food\nWithout Waiting",
        subtitle = "Save 20+ minutes every lunch break to relax and spend quality time with friends.",
      ),
    )

  val pagerState = rememberPagerState(pageCount = { pages.size })
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  var showLoginSheet by remember { mutableStateOf(false) }
  var isSignUpFromLink by remember { mutableStateOf(false) }
  var loggedInUser by remember { mutableStateOf<UserProfile?>(null) }

  val isLastPage = pagerState.currentPage == pages.size - 1

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(WarmCream)
        .testTag("get_started_screen")
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Top Navigation / Brand Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "QuickBite",
          fontWeight = FontWeight.ExtraBold,
          fontSize = 18.sp,
          color = BlackPrimary,
        )
        Text(
          text = "Explore Canteen →",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = DeliveryOrange,
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DeliveryOrangeLight)
            .clickable { onExploreGuest() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        )
      }

      // 2. Carousel of Onboarding Screens
      HorizontalPager(
        state = pagerState,
        modifier =
          Modifier
            .weight(1f)
            .fillMaxWidth()
            .testTag("onboarding_pager"),
      ) { pageIndex ->
        Column(
          modifier =
            Modifier
              .fillMaxSize()
              .padding(horizontal = 24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          // Visual Person Illustration Component (Clean realistic image style, no chaotic AI animations)
          Box(
            modifier =
              Modifier
                .size(300.dp)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
          ) {
            when (pageIndex) {
              0 -> PersonSlideOneImage()
              1 -> PersonSlideTwoImage()
              2 -> PersonSlideThreeImage()
            }
          }

          Spacer(modifier = Modifier.height(24.dp))

          // Bold Title
          Text(
            text = pages[pageIndex].title,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextDark,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp,
            modifier = Modifier.padding(horizontal = 16.dp).testTag("slide_title_${pageIndex}"),
          )

          Spacer(modifier = Modifier.height(12.dp))

          // Subtitle text (High contrast, semi-bold for crystal-clear readability)
          Text(
            text = pages[pageIndex].subtitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 24.dp).testTag("slide_subtitle_${pageIndex}"),
          )
        }
      }

      // 3. Page Indicator Dots (3 dots with smooth pill transition)
      Row(
        modifier = Modifier.padding(vertical = 16.dp).testTag("pager_indicator"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        repeat(pages.size) { index ->
          val isSelected = pagerState.currentPage == index
          val dotWidth by
            animateDpAsState(
              targetValue = if (isSelected) 24.dp else 6.dp,
              animationSpec = tween(300),
              label = "dot_width_$index",
            )
          val dotColor = if (isSelected) BlackPrimary else Color(0xFFD6CFC5)

          Box(
            modifier =
              Modifier
                .height(6.dp)
                .width(dotWidth)
                .clip(RoundedCornerShape(3.dp))
                .background(dotColor)
                .clickable {
                  coroutineScope.launch { pagerState.animateScrollToPage(index) }
                }
          )
        }
      }

      // 4. Primary Action Button: "Next ->" on early screens, "Login ->" ONLY on the last screen
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(56.dp)
            .shadow(4.dp, RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
            .background(BlackPrimary)
            .clickable(
              role = Role.Button,
              onClick = {
                if (!isLastPage) {
                  // Advance smoothly to the next screen
                  coroutineScope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                  }
                } else {
                  // On the last screen: open the login bottom sheet
                  isSignUpFromLink = false
                  showLoginSheet = true
                }
              },
            )
            .testTag(if (isLastPage) "main_login_button" else "main_next_button"),
        contentAlignment = Alignment.Center,
      ) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Spacer(modifier = Modifier.weight(1f))

          Text(
            text = if (isLastPage) "Login" else "Next",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = PureWhite,
            modifier = Modifier.padding(start = 28.dp),
          )

          Spacer(modifier = Modifier.weight(1f))

          // Circular white arrow container
          Box(
            modifier =
              Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(PureWhite),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = if (isLastPage) "Open Login" else "Next Screen",
              tint = BlackPrimary,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // 5. Bottom Navigation Control:
      // On early screens: "Skip to login" or next indicator.
      // On last screen: "Don't have an account? Sign up"
      if (!isLastPage) {
        Text(
          text = "Skip",
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          color = TextMuted,
          modifier =
            Modifier
              .padding(bottom = 12.dp)
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
              ) {
                // Skip directly to the final screen
                coroutineScope.launch {
                  pagerState.animateScrollToPage(pages.size - 1)
                }
              }
              .testTag("skip_to_last_text"),
        )
      } else {
        val signUpText =
          buildAnnotatedString {
            append("Don't have an account? ")
            withStyle(
              style =
                SpanStyle(
                  fontWeight = FontWeight.Bold,
                  color = BlackPrimary,
                )
            ) {
              append("Sign up")
            }
          }

        Text(
          text = signUpText,
          fontSize = 13.sp,
          color = TextDark,
          modifier =
            Modifier
              .padding(bottom = 12.dp)
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
              ) {
                isSignUpFromLink = true
                showLoginSheet = true
              }
              .testTag("signup_prompt_text"),
        )
      }
    }

    // Logged In Status Banner (if student logged in via demo features)
    if (loggedInUser != null) {
      Box(
        modifier =
          Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(PureWhite)
            .padding(14.dp)
            .testTag("logged_in_card")
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
          ) {
            Box(
              modifier =
                Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFDCFCE7)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = AccentGreen,
                modifier = Modifier.size(22.dp),
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Logged in as ${loggedInUser?.name}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
              Text(
                text = "${loggedInUser?.email} · Pre-orders ready",
                fontSize = 11.sp,
                color = TextMuted,
              )
            }
          }

          // Sign Out / Switch button
          Box(
            modifier =
              Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SoftGray)
                .clickable {
                  loggedInUser = null
                  coroutineScope.launch {
                    snackbarHostState.showSnackbar("Logged out successfully.")
                  }
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Logout,
                contentDescription = "Log Out",
                tint = TextDark,
                modifier = Modifier.size(14.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Log Out",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark,
              )
            }
          }
        }
      }
    }

    // 6. Dimmed Scrim Overlay + Login Screen Bottom Sheet (Only when triggered)
    AnimatedVisibility(
      visible = showLoginSheet,
      enter = fadeIn(animationSpec = tween(200)),
      exit = fadeOut(animationSpec = tween(200)),
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .background(Color(0x73000000))
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = { showLoginSheet = false },
            )
            .testTag("login_sheet_scrim"),
        contentAlignment = Alignment.BottomCenter,
      ) {
        AnimatedVisibility(
          visible = showLoginSheet,
          enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(280)),
          exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(240)),
        ) {
          LoginContent(
            onDismiss = { showLoginSheet = false },
            onLoginSuccess = { user ->
              loggedInUser = user
              showLoginSheet = false
              onLoginSuccess(user)
            },
            isSignUpDefault = isSignUpFromLink,
          )
        }
      }
    }

    // Snackbar Host
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
    )

  }
}

@Composable
private fun OutlinedBadge(name: String, desc: String) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(SoftGray)
        .padding(horizontal = 12.dp, vertical = 8.dp)
  ) {
    Text(name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextDark)
    Text(desc, fontSize = 10.sp, color = TextMuted)
  }
}
