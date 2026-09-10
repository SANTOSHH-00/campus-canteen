package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.data.firebase.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PasswordResetState {
  object Idle : PasswordResetState
  object Loading : PasswordResetState
  data class EmailSent(val message: String) : PasswordResetState
  data class PasswordUpdated(val message: String) : PasswordResetState
  data class Error(val message: String) : PasswordResetState
}

class AuthViewModel(
  private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

  companion object {
    var deepLinkToken: String = ""
    var deepLinkEmail: String = ""

    fun setDeepLinkInfo(token: String, email: String) {
      deepLinkToken = token.trim()
      deepLinkEmail = email.trim().lowercase()
    }

    fun clearDeepLinkInfo() {
      deepLinkToken = ""
      deepLinkEmail = ""
    }
  }

  private val _loading = MutableStateFlow(false)
  val loading: StateFlow<Boolean> = _loading.asStateFlow()

  private val _successMessage = MutableStateFlow<String?>(null)
  val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _resetState = MutableStateFlow<PasswordResetState>(PasswordResetState.Idle)
  val resetState: StateFlow<PasswordResetState> = _resetState.asStateFlow()

  private val _hasRecoverySession = MutableStateFlow(true)
  val hasRecoverySession: StateFlow<Boolean> = _hasRecoverySession.asStateFlow()

  fun hasActiveRecoverySession(): Boolean {
    return deepLinkToken.isNotBlank() || authRepository.isUserLoggedIn
  }

  suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
    val trimmed = email.trim()
    if (trimmed.isEmpty()) {
      val msg = "Please enter your registered email address."
      _errorMessage.value = msg
      _resetState.value = PasswordResetState.Error(msg)
      return Result.failure(IllegalArgumentException(msg))
    }
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
      val msg = "Please enter a valid email address."
      _errorMessage.value = msg
      _resetState.value = PasswordResetState.Error(msg)
      return Result.failure(IllegalArgumentException(msg))
    }

    _loading.value = true
    _errorMessage.value = null
    _successMessage.value = null
    _resetState.value = PasswordResetState.Loading

    return try {
      val result = authRepository.sendPasswordResetEmail(trimmed)
      _loading.value = false
      if (result.isSuccess) {
        val successText = "Password reset link sent! Please check your email inbox."
        _successMessage.value = successText
        _resetState.value = PasswordResetState.EmailSent(successText)
        Result.success(Unit)
      } else {
        val ex = result.exceptionOrNull()
        val friendlyMsg = mapAuthError(ex?.message ?: "", ex)
        _errorMessage.value = friendlyMsg
        _resetState.value = PasswordResetState.Error(friendlyMsg)
        Result.failure(ex ?: Exception(friendlyMsg))
      }
    } catch (e: Exception) {
      _loading.value = false
      val friendlyMsg = mapAuthError(e.message ?: "", e)
      _errorMessage.value = friendlyMsg
      _resetState.value = PasswordResetState.Error(friendlyMsg)
      Result.failure(e)
    }
  }

  suspend fun updatePassword(newPassword: String, confirmPassword: String): Result<Unit> {
    if (newPassword.isEmpty()) {
      val msg = "New password cannot be empty."
      _errorMessage.value = msg
      _resetState.value = PasswordResetState.Error(msg)
      return Result.failure(IllegalArgumentException(msg))
    }
    if (newPassword.length < 6) {
      val msg = "Password must be at least 6 characters long."
      _errorMessage.value = msg
      _resetState.value = PasswordResetState.Error(msg)
      return Result.failure(IllegalArgumentException(msg))
    }
    if (confirmPassword.isEmpty()) {
      val msg = "Please confirm your new password."
      _errorMessage.value = msg
      _resetState.value = PasswordResetState.Error(msg)
      return Result.failure(IllegalArgumentException(msg))
    }
    if (newPassword != confirmPassword) {
      val msg = "Passwords do not match."
      _errorMessage.value = msg
      _resetState.value = PasswordResetState.Error(msg)
      return Result.failure(IllegalArgumentException(msg))
    }

    _loading.value = true
    _errorMessage.value = null
    _successMessage.value = null
    _resetState.value = PasswordResetState.Loading

    return try {
      val effectiveEmail = deepLinkEmail.ifBlank {
        com.example.data.session.SessionManager.getPendingResetEmail().orEmpty()
      }
      val result = authRepository.updatePasswordWithToken(
        token = deepLinkToken,
        email = effectiveEmail,
        newPassword = newPassword,
      )
      _loading.value = false
      if (result.isSuccess) {
        val successText = "Password updated successfully."
        _successMessage.value = successText
        _resetState.value = PasswordResetState.PasswordUpdated(successText)
        clearDeepLinkInfo()
        Result.success(Unit)
      } else {
        val ex = result.exceptionOrNull()
        val friendlyMsg = mapAuthError(ex?.message ?: "", ex)
        _errorMessage.value = friendlyMsg
        _resetState.value = PasswordResetState.Error(friendlyMsg)
        Result.failure(ex ?: Exception(friendlyMsg))
      }
    } catch (e: Exception) {
      _loading.value = false
      val friendlyMsg = mapAuthError(e.message ?: "", e)
      _errorMessage.value = friendlyMsg
      _resetState.value = PasswordResetState.Error(friendlyMsg)
      Result.failure(e)
    }
  }

  fun clearState() {
    _loading.value = false
    _errorMessage.value = null
    _successMessage.value = null
    _resetState.value = PasswordResetState.Idle
  }

  fun clearErrorMessage() {
    _errorMessage.value = null
    if (_resetState.value is PasswordResetState.Error) {
      _resetState.value = PasswordResetState.Idle
    }
  }

  private fun mapAuthError(error: String, throwable: Throwable? = null): String {
    if (throwable != null) {
      return com.example.util.NetworkUtils.toFriendlyErrorMessage(throwable, error)
    }
    val lower = error.lowercase()
    return when {
      lower.contains("no account found") || lower.contains("no student account") || lower.contains("user not found") ->
        "No account found with this email. Please check the email or register a new account."
      lower.contains("rate limit") || lower.contains("too many requests") || lower.contains("wait") ->
        "Too many reset attempts. Please wait a few minutes before trying again."
      lower.contains("connect") || lower.contains("refused") || lower.contains("failed to connect") ->
        "Unable to connect to Quick Bite server. Please ensure the backend is running."
      lower.contains("timeout") || lower.contains("timed out") ->
        "Connection timed out. The server took too long to respond. Please try again."
      lower.contains("network") || lower.contains("unable to resolve host") || lower.contains("offline") ->
        "No internet connection. Please check your network and try again."
      lower.contains("expired") || lower.contains("token") || lower.contains("invalid") ->
        "Your password reset link is invalid or has expired. Please request a new link."
      lower.contains("weak") || lower.contains("short") || lower.contains("characters") ->
        "Password is too weak. Please use at least 6 characters."
      else -> error.ifBlank { "An unexpected error occurred. Please try again." }
    }
  }
}
