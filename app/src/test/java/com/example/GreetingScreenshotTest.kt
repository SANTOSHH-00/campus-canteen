package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.screens.CampusCanteenSplashScreen
import com.example.ui.screens.SplashOrderProgress
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent { MyApplicationTheme { CampusCanteenSplashScreen(onLoadingFinished = {}) } }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }

  @Test
  fun splash_step2_reference_match() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1C1E))
            .padding(vertical = 40.dp, horizontal = 16.dp),
          contentAlignment = Alignment.Center,
        ) {
          SplashOrderProgress(currentStep = 1, stepProgress = 0.70f)
        }
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/splash_step2_reference.png")
  }

  @Test
  fun splash_all_steps() {
    composeTestRule.setContent {
      MyApplicationTheme {
        androidx.compose.foundation.layout.Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1C1E))
            .padding(24.dp),
          verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          // Step 1: Taking order
          SplashOrderProgress(currentStep = 0, stepProgress = 0.65f)
          // Step 2: Preparing food
          SplashOrderProgress(currentStep = 1, stepProgress = 0.70f)
          // Step 3: Getting order ready
          SplashOrderProgress(currentStep = 2, stepProgress = 0.70f)
          // Step 4: Almost there / Completed
          SplashOrderProgress(currentStep = 3, stepProgress = 0.85f)
        }
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/splash_all_steps.png")
  }
}
