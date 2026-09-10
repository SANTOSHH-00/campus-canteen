package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object NetworkUtils {

  /**
   * Checks if the device has an active and capable internet connection.
   */
  fun isOnline(context: Context): Boolean {
    return try {
      val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
      val activeNetwork = connectivityManager.activeNetwork ?: return false
      val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
      capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Converts any raw exception or network error into an accurate, user-friendly message.
   * Differentiates between actual offline state, unreachable backend server, timeouts,
   * authentication failures (401), account not found (404), duplicate registration (409),
   * and server errors (500).
   */
  fun toFriendlyErrorMessage(
    throwable: Throwable?,
    defaultFallback: String = "Something went wrong. Please try again.",
  ): String {
    if (throwable == null) return defaultFallback

    val msg = throwable.message.orEmpty().trim()
    val localized = throwable.localizedMessage.orEmpty().trim()
    val combined = "$msg $localized ${throwable.javaClass.simpleName}"

    return when {
      // 1. Connection Refused / Server Unreachable (Port 5000 offline or wrong host IP)
      throwable is ConnectException ||
      combined.contains("Connection refused", ignoreCase = true) ||
      combined.contains("Failed to connect", ignoreCase = true) ||
      combined.contains("ECONNREFUSED", ignoreCase = true) -> {
        "Unable to connect to Quick Bite server. Please ensure the backend is running on port 5000."
      }

      // 2. Connection or Read Timeout
      throwable is SocketTimeoutException ||
      combined.contains("SocketTimeoutException", ignoreCase = true) ||
      combined.contains("timeout", ignoreCase = true) ||
      combined.contains("timed out", ignoreCase = true) -> {
        "Connection timed out. The server took too long to respond. Please try again."
      }

      // 3. DNS / Offline (Real lack of internet connection)
      throwable is UnknownHostException ||
      combined.contains("Unable to resolve host", ignoreCase = true) ||
      combined.contains("No address associated with hostname", ignoreCase = true) ||
      combined.contains("Network is unreachable", ignoreCase = true) ||
      combined.contains("ENETUNREACH", ignoreCase = true) -> {
        "No internet connection. Please check your network and try again."
      }

      // 4. Duplicate Account Registration (HTTP 409)
      combined.contains("409") ||
      combined.contains("already registered", ignoreCase = true) ||
      combined.contains("already exists", ignoreCase = true) -> {
        "User already registered. Please log in instead."
      }

      // 5. Authentication Failure / Invalid Credentials (HTTP 401)
      combined.contains("401") ||
      combined.contains("Incorrect password", ignoreCase = true) ||
      combined.contains("invalid_credentials", ignoreCase = true) ||
      combined.contains("Invalid login credentials", ignoreCase = true) ||
      combined.contains("Invalid email or password", ignoreCase = true) -> {
        "Invalid email or password. Please verify and try again."
      }

      // 6. Account Not Found (HTTP 404)
      combined.contains("404") ||
      combined.contains("No student account found", ignoreCase = true) ||
      combined.contains("No canteen owner account", ignoreCase = true) ||
      combined.contains("UserNotFound", ignoreCase = true) ||
      combined.contains("Account not found", ignoreCase = true) ||
      combined.contains("No account found", ignoreCase = true) -> {
        "Account not found. Please check your details or register a new account."
      }

      // 7. Access Denied / Unassigned Canteen (HTTP 403)
      combined.contains("403") ||
      combined.contains("Access Denied", ignoreCase = true) ||
      combined.contains("not assigned", ignoreCase = true) ||
      combined.contains("CanteenNotAssigned", ignoreCase = true) -> {
        "Access Denied: No canteen has been assigned to your account yet. Please contact the administrator."
      }

      // 8. Server Error (HTTP 500)
      combined.contains("500") ||
      combined.contains("Internal Server Error", ignoreCase = true) ||
      combined.contains("Server error", ignoreCase = true) -> {
        "Server error. Please try again."
      }

      // 9. Bad Request / Validation Failure (HTTP 400)
      combined.contains("400") -> {
        if (msg.isNotBlank() && !msg.contains("http://", ignoreCase = true) && !msg.contains("https://", ignoreCase = true)) {
          msg.removePrefix("Registration failed (400): ").removePrefix("Login failed (400): ").trim()
        } else {
          "Invalid request details. Please check your input."
        }
      }

      // 10. Stripping raw URLs and technical internals
      msg.contains("http://", ignoreCase = true) ||
      msg.contains("https://", ignoreCase = true) -> {
        defaultFallback
      }

      msg.isNotBlank() -> msg
      else -> defaultFallback
    }
  }
}
