package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.example.data.firebase.FirebaseConfig
import com.example.data.session.SessionManager
import com.example.ui.navigation.MessQDestinations
import com.example.ui.navigation.MessQNavHost
import com.example.ui.screens.MainScreen
import com.example.ui.state.rememberCanteenAppState
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val TAG = "MainActivity"
  private var pendingDeepLinkRoute by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    SessionManager.initialize(applicationContext)
    FirebaseConfig.initialize(applicationContext)

    processPasswordResetDeepLink(intent)

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
      }
    }

    // Connect to Quick Bite real-time WebSockets
    com.example.data.api.WebSocketManager.connect()

    // Sync FCM Token to Backend if available
    FirebaseConfig.messaging?.token?.addOnCompleteListener { task ->
      if (task.isSuccessful && !task.result.isNullOrBlank()) {
        val fcmToken: String = task.result ?: return@addOnCompleteListener
        Log.i(TAG, "Current FCM Token retrieved: $fcmToken")
        val currentUid = SessionManager.getUserSession()?.uid
        if (!currentUid.isNullOrBlank()) {
          kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
              com.example.data.api.ApiClient.apiService.updateUserFcmToken(
                currentUid,
                com.example.data.api.FcmTokenDto(fcmToken = fcmToken)
              )
            } catch (e: Exception) {
              Log.w(TAG, "FCM token sync skipped: ${e.message}")
            }
          }
        }
      }
    }

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        AppEntry(
          deepLinkDestination = pendingDeepLinkRoute,
          onDeepLinkHandled = { pendingDeepLinkRoute = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    processPasswordResetDeepLink(intent)
  }

  private fun processPasswordResetDeepLink(intent: Intent?) {
    if (intent == null) return
    val data = intent.data ?: return
    Log.i(TAG, "Incoming intent deep link: $data")

    val uriStr = data.toString().lowercase()
    val isRecovery = uriStr.contains("reset-password") ||
        uriStr.contains("auth-callback") ||
        uriStr.contains("type=recovery") ||
        uriStr.contains("recovery")

    if (isRecovery) {
      val token = data.getQueryParameter("token") ?: ""
      var email = data.getQueryParameter("email") ?: ""
      if (email.isBlank()) {
        email = SessionManager.getPendingResetEmail().orEmpty()
      }
      if (token.isNotBlank()) {
        AuthViewModel.setDeepLinkInfo(token, email)
      }
      pendingDeepLinkRoute = MessQDestinations.RESET_PASSWORD
    }
  }
}

/**
 * Top-level composable that hosts Navigation Compose:
 * Splash -> Role Selection (MESSQ) -> Student Login / Owner Login -> Respective Dashboards
 */
@Composable
fun AppEntry(
  deepLinkDestination: String? = null,
  onDeepLinkHandled: () -> Unit = {},
) {
  val appState = rememberCanteenAppState()
  val navController = rememberNavController()

  LaunchedEffect(deepLinkDestination) {
    if (!deepLinkDestination.isNullOrBlank()) {
      try {
        navController.navigate(deepLinkDestination) {
          popUpTo(MessQDestinations.SPLASH) { inclusive = true }
          launchSingleTop = true
        }
        onDeepLinkHandled()
      } catch (e: Exception) {
        Log.e("AppEntry", "Deep link navigation error: ${e.message}")
      }
    }
  }

  MessQNavHost(
    navController = navController,
    appState = appState,
    deepLinkDestination = deepLinkDestination,
  )
}

@Preview(showBackground = true)
@Composable
fun AppEntryPreview() {
  MyApplicationTheme {
    MainScreen()
  }
}
