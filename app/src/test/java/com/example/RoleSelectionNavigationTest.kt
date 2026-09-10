package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ui.screens.ModernSignInScreen
import com.example.ui.screens.OwnerLoginScreen
import com.example.ui.screens.WelcomeOnboardingScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoleSelectionNavigationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun testWelcomeScreen_signInAndSignUpButtons() {
    var signInClicked = false
    var signUpClicked = false
    var guestClicked = false

    composeTestRule.setContent {
      MyApplicationTheme {
        WelcomeOnboardingScreen(
          onNavigateToSignIn = { signInClicked = true },
          onNavigateToSignUp = { signUpClicked = true },
          onExploreAsGuest = { guestClicked = true },
        )
      }
    }

    composeTestRule.onNodeWithTag("welcome_heading").assertExists()
    composeTestRule.onNodeWithTag("welcome_sign_in_button").assertExists()
    composeTestRule.onNodeWithTag("welcome_sign_up_button").assertExists()
    composeTestRule.onNodeWithTag("welcome_guest_link").assertExists()

    composeTestRule.onNodeWithTag("welcome_sign_in_button").performClick()
    assertTrue("Sign In button should be triggered", signInClicked)

    composeTestRule.onNodeWithTag("welcome_sign_up_button").performClick()
    assertTrue("Sign Up button should be triggered", signUpClicked)

    composeTestRule.onNodeWithTag("welcome_guest_link").performClick()
    assertTrue("Guest link should be triggered", guestClicked)
  }

  @Test
  fun testModernSignInScreen_isDisplayedWithGoogleAndOwner() {
    var ownerNavigated = false

    composeTestRule.setContent {
      MyApplicationTheme {
        ModernSignInScreen(
          onBack = {},
          onLoginSuccess = {},
          onNavigateToOwnerLogin = { ownerNavigated = true },
          isRegisterDefault = false,
        )
      }
    }

    composeTestRule.onNodeWithTag("sign_in_title").assertExists()
    composeTestRule.onNodeWithTag("input_email_username").assertExists()
    composeTestRule.onNodeWithTag("input_password").assertExists()
    composeTestRule.onNodeWithTag("sign_in_submit_button").assertExists()
    composeTestRule.onNodeWithTag("continue_with_google_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("continue_as_owner_button").assertExists()

    composeTestRule.onNodeWithTag("continue_as_owner_button").performScrollTo().performClick()
    assertTrue("Continue as Owner should navigate to owner login", ownerNavigated)
  }

  @Test
  fun testOwnerLoginScreen_isDisplayed() {
    val fakeRepo = FakeOwnerRepository()
    val viewModel = com.example.ui.viewmodel.OwnerViewModel(fakeRepo)

    composeTestRule.setContent {
      MyApplicationTheme {
        OwnerLoginScreen(
          viewModel = viewModel,
          onBack = {},
          onLoginSuccess = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("owner_title_text").assertExists()
    composeTestRule.onNodeWithTag("owner_email_input").assertExists()
    composeTestRule.onNodeWithTag("owner_password_input").assertExists()
    composeTestRule.onNodeWithTag("owner_sign_in_button").assertExists()
  }
}
