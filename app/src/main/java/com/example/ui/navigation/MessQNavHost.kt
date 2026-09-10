package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.compose.ui.platform.LocalContext
import com.example.data.session.SessionManager
import com.example.ui.screens.CampusCanteenSplashScreen
import com.example.ui.screens.ForgotPasswordScreen
import com.example.ui.screens.MainScreen
import com.example.ui.screens.ModernSignInScreen
import com.example.ui.screens.NoInternetScreen
import com.example.ui.screens.OwnerDashboardScreen
import com.example.ui.screens.OwnerLoginScreen
import com.example.ui.screens.ResetPasswordScreen
import com.example.ui.screens.WelcomeOnboardingScreen
import com.example.ui.state.CanteenAppState
import com.example.ui.state.rememberCanteenAppState
import com.example.ui.viewmodel.OwnerViewModel
import com.example.util.NetworkUtils

@Composable
fun MessQNavHost(
  navController: NavHostController = rememberNavController(),
  appState: CanteenAppState = rememberCanteenAppState(),
  ownerViewModel: OwnerViewModel = viewModel(),
  deepLinkDestination: String? = null,
  modifier: Modifier = Modifier,
) {
  val currentOwner by ownerViewModel.currentOwner.collectAsState()

  NavHost(
    navController = navController,
    startDestination = MessQDestinations.SPLASH,
    modifier = modifier,
  ) {
    // 1. Splash Screen
    composable(MessQDestinations.SPLASH) {
      val context = LocalContext.current
      CampusCanteenSplashScreen(
        onLoadingFinished = {
          val isOnline = NetworkUtils.isOnline(context)
          val nextDestination = if (!isOnline) {
            MessQDestinations.NO_INTERNET
          } else if (!deepLinkDestination.isNullOrBlank()) {
            deepLinkDestination
          } else if (com.example.ui.viewmodel.AuthViewModel.deepLinkToken.isNotBlank()) {
            MessQDestinations.RESET_PASSWORD
          } else {
            when {
              SessionManager.isStudentLoggedIn() -> MessQDestinations.STUDENT_MAIN
              SessionManager.isOwnerLoggedIn() -> MessQDestinations.OWNER_MAIN
              else -> MessQDestinations.WELCOME
            }
          }
          try {
            navController.navigate(nextDestination) {
              popUpTo(MessQDestinations.SPLASH) { inclusive = true }
              launchSingleTop = true
            }
          } catch (e: Exception) {
            android.util.Log.e("MessQNavHost", "Splash navigation error: ${e.message}", e)
          }
        }
      )
    }

    // 1b. Dedicated No Internet Screen (Matching user reference)
    composable(MessQDestinations.NO_INTERNET) {
      NoInternetScreen(
        onRetrySuccess = {
          val nextDestination = when {
            SessionManager.isStudentLoggedIn() -> MessQDestinations.STUDENT_MAIN
            SessionManager.isOwnerLoggedIn() -> MessQDestinations.OWNER_MAIN
            else -> MessQDestinations.WELCOME
          }
          try {
            navController.navigate(nextDestination) {
              popUpTo(MessQDestinations.NO_INTERNET) { inclusive = true }
              launchSingleTop = true
            }
          } catch (e: Exception) {
            android.util.Log.e("MessQNavHost", "Retry navigation error: ${e.message}", e)
          }
        }
      )
    }

    // 2. Welcome Screen (Image 3 Left Phone Screen)
    composable(MessQDestinations.WELCOME) {
      WelcomeOnboardingScreen(
        onNavigateToSignIn = {
          navController.navigate("${MessQDestinations.SIGN_IN}?register=false")
        },
        onNavigateToSignUp = {
          navController.navigate("${MessQDestinations.SIGN_IN}?register=true")
        },
        onExploreAsGuest = {
          appState.logoutUser()
          navController.navigate(MessQDestinations.STUDENT_MAIN) {
            popUpTo(MessQDestinations.WELCOME) { inclusive = false }
          }
        },
      )
    }

    // 3. Modern Sign In Screen (Image 3 Right Phone Screen with Google & Owner portal)
    composable("${MessQDestinations.SIGN_IN}?register={register}") { backStackEntry ->
      val isRegister = backStackEntry.arguments?.getString("register")?.toBoolean() ?: false
      ModernSignInScreen(
        onBack = {
          navController.popBackStack()
        },
        onLoginSuccess = { profile ->
          appState.loginUser(profile)
          navController.navigate(MessQDestinations.STUDENT_MAIN) {
            popUpTo(MessQDestinations.WELCOME) { inclusive = false }
          }
        },
        onNavigateToOwnerLogin = {
          navController.navigate(MessQDestinations.OWNER_LOGIN)
        },
        onNavigateToForgotPassword = {
          navController.navigate(MessQDestinations.FORGOT_PASSWORD)
        },
        isRegisterDefault = isRegister,
      )
    }

    // 4. Owner Login Screen (Dedicated Owner Portal with Email OTP)
    composable(MessQDestinations.OWNER_LOGIN) {
      OwnerLoginScreen(
        viewModel = ownerViewModel,
        onBack = {
          navController.popBackStack()
        },
        onLoginSuccess = { ownerDoc ->
          SessionManager.saveOwnerSession(ownerDoc)
          navController.navigate(MessQDestinations.OWNER_MAIN) {
            popUpTo(MessQDestinations.WELCOME) { inclusive = false }
          }
        },
        onNavigateToForgotPassword = {
          navController.navigate(MessQDestinations.FORGOT_PASSWORD)
        },
        onNavigateToOtp = { emailArg ->
          try {
            val encoded = java.net.URLEncoder.encode(emailArg, "UTF-8")
            navController.navigate("${MessQDestinations.OWNER_OTP}?email=$encoded")
          } catch (e: Exception) {
            navController.navigate("${MessQDestinations.OWNER_OTP}?email=$emailArg")
          }
        },
      )
    }

    // 4a. Owner OTP Verification Screen Route
    composable(
      route = "${MessQDestinations.OWNER_OTP}?email={email}",
      arguments = listOf(
        androidx.navigation.navArgument("email") {
          type = androidx.navigation.NavType.StringType
          defaultValue = ""
        }
      )
    ) { backStackEntry ->
      val rawEmail = backStackEntry.arguments?.getString("email").orEmpty()
      val decodedEmail = try {
        java.net.URLDecoder.decode(rawEmail, "UTF-8")
      } catch (e: Exception) {
        rawEmail
      }

      androidx.compose.runtime.LaunchedEffect(decodedEmail) {
        ownerViewModel.goToEmailOtp(decodedEmail)
      }

      OwnerLoginScreen(
        viewModel = ownerViewModel,
        initialEmail = decodedEmail,
        onBack = {
          ownerViewModel.backToCredentials()
          navController.popBackStack()
        },
        onLoginSuccess = { ownerDoc ->
          SessionManager.saveOwnerSession(ownerDoc)
          navController.navigate(MessQDestinations.OWNER_MAIN) {
            popUpTo(MessQDestinations.WELCOME) { inclusive = false }
          }
        },
        onNavigateToForgotPassword = {
          navController.navigate(MessQDestinations.FORGOT_PASSWORD)
        },
      )
    }

    composable(MessQDestinations.OWNER_OTP) {
      OwnerLoginScreen(
        viewModel = ownerViewModel,
        onBack = {
          ownerViewModel.backToCredentials()
          navController.popBackStack()
        },
        onLoginSuccess = { ownerDoc ->
          SessionManager.saveOwnerSession(ownerDoc)
          navController.navigate(MessQDestinations.OWNER_MAIN) {
            popUpTo(MessQDestinations.WELCOME) { inclusive = false }
          }
        },
        onNavigateToForgotPassword = {
          navController.navigate(MessQDestinations.FORGOT_PASSWORD)
        },
      )
    }

    // 4b. Forgot Password Screen
    composable(MessQDestinations.FORGOT_PASSWORD) {
      ForgotPasswordScreen(
        onBackToLogin = {
          navController.popBackStack()
        },
      )
    }

    // 4c. Reset Password Screen (Opened directly or via Recovery Deep Link)
    composable(
      route = MessQDestinations.RESET_PASSWORD,
      deepLinks = listOf(
        navDeepLink { uriPattern = "quickbite://reset-password.*" },
        navDeepLink { uriPattern = "messq://reset-password.*" },
        navDeepLink { uriPattern = "quickbite://auth-callback.*" },
        navDeepLink { uriPattern = "messq://auth-callback.*" },
        navDeepLink { uriPattern = "http://.*/api/auth/reset-password.*" },
        navDeepLink { uriPattern = "https://.*/api/auth/reset-password.*" },
      ),
    ) {
      ResetPasswordScreen(
        onNavigateToLogin = {
          navController.navigate("${MessQDestinations.SIGN_IN}?register=false") {
            popUpTo(MessQDestinations.WELCOME) { inclusive = false }
            launchSingleTop = true
          }
        },
        onNavigateToForgotPassword = {
          navController.navigate(MessQDestinations.FORGOT_PASSWORD) {
            popUpTo(MessQDestinations.RESET_PASSWORD) { inclusive = true }
          }
        },
      )
    }

    // 5. Student Main Screen (Full campus canteen ordering experience)
    composable(MessQDestinations.STUDENT_MAIN) {
      MainScreen(
        appState = appState,
        onLogout = {
          appState.logoutUser()
          navController.navigate(MessQDestinations.WELCOME) {
            popUpTo(MessQDestinations.STUDENT_MAIN) { inclusive = true }
          }
        },
      )
    }

    // 6. Owner Main Screen (Owner Dashboard destination)
    composable(MessQDestinations.OWNER_MAIN) {
      OwnerDashboardScreen(
        owner = currentOwner,
        appState = appState,
        onLogout = {
          ownerViewModel.logout()
          navController.navigate(MessQDestinations.WELCOME) {
            popUpTo(MessQDestinations.OWNER_MAIN) { inclusive = true }
          }
        },
        onNavigateToManageItems = {
          navController.navigate(MessQDestinations.OWNER_ITEMS)
        },
        onNavigateToInventory = {
          navController.navigate(MessQDestinations.OWNER_INVENTORY)
        },
        onNavigateToOrders = {
          navController.navigate(MessQDestinations.OWNER_ORDERS)
        },
      )
    }

    // 7. Owner Manage Items Screen
    composable(MessQDestinations.OWNER_ITEMS) {
      val canteenId = currentOwner?.canteenId ?: ""
      com.example.ui.screens.ManageItemsScreen(
        canteenId = canteenId,
        onBack = { navController.popBackStack() },
      )
    }

    // 7b. Owner Inventory Screen (Distinct from Menu Management)
    composable(MessQDestinations.OWNER_INVENTORY) {
      val canteenId = currentOwner?.canteenId ?: ""
      com.example.ui.screens.InventoryScreen(
        canteenId = canteenId,
        onBack = { navController.popBackStack() },
      )
    }

    // 8. Owner Live Orders Screen
    composable(MessQDestinations.OWNER_ORDERS) {
      val canteenId = currentOwner?.canteenId ?: ""
      com.example.ui.screens.OwnerOrdersScreen(
        canteenId = canteenId,
        onBack = { navController.popBackStack() },
        onNavigateToOrderDetails = { orderId ->
          navController.navigate("${MessQDestinations.OWNER_ORDER_DETAILS}/$orderId")
        },
      )
    }

    // 9. Owner Order Details Screen (Image 2)
    composable("${MessQDestinations.OWNER_ORDER_DETAILS}/{orderId}") { backStackEntry ->
      val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
      com.example.ui.screens.OwnerOrderDetailsScreen(
        orderId = orderId,
        onBack = { navController.popBackStack() },
      )
    }
  }
}
