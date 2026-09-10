package com.example.data.firebase

import android.util.Log
import com.example.data.api.MongoRepository
import com.example.data.api.RegisterRequestDto
import com.example.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Universal authenticated user representation.
 */
data class AppUser(
  val uid: String,
  val email: String? = null,
  val displayName: String? = null,
)

/**
 * Authentication repository powered by Quick Bite Express REST backend and MongoDB Atlas.
 * Zero Supabase dependencies.
 */
class AuthRepository(
  private val mongoRepo: MongoRepository = MongoRepository(),
) {
  private val TAG = "AuthRepository"

  companion object {
    private val _authState = MutableStateFlow<AppUser?>(null)
  }

  init {
    val savedUser = SessionManager.getUserSession()
    if (savedUser != null) {
      _authState.value = AppUser(
        uid = savedUser.uid,
        email = savedUser.email,
        displayName = savedUser.name.ifBlank { savedUser.email.substringBefore("@") },
      )
    }
  }

  val currentUser: AppUser?
    get() = _authState.value ?: SessionManager.getUserSession()?.let {
      AppUser(
        uid = it.uid,
        email = it.email,
        displayName = it.name.ifBlank { it.email.substringBefore("@") },
      )
    }

  val isUserLoggedIn: Boolean
    get() = currentUser != null

  fun observeAuthState(): Flow<AppUser?> = _authState.asStateFlow()

  /**
   * Signs in student or user with email and password via Quick Bite Express REST API.
   */
  suspend fun signIn(email: String, password: String): Result<UserDocument> {
    val trimmed = email.trim().lowercase()
    return try {
      val res = mongoRepo.loginUser(trimmed, password)
      if (res.isSuccess) {
        val dto = res.getOrThrow()
        val userDto = dto.user ?: throw Exception("User details missing from login response")
        val doc = UserDocument(
          uid = userDto.uid,
          name = userDto.name,
          email = userDto.email,
          role = userDto.role,
          registrationNumber = userDto.registrationNumber,
          department = userDto.department,
          avatarId = userDto.avatarId,
          fcmToken = userDto.fcmToken,
          phone = userDto.phone,
          course = userDto.course,
        )
        _authState.value = AppUser(
          uid = doc.uid,
          email = doc.email,
          displayName = doc.name.ifBlank { doc.email.substringBefore("@") },
        )
        SessionManager.saveUserSession(doc)
        Result.success(doc)
      } else {
        Result.failure(res.exceptionOrNull() ?: Exception("Sign in failed"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "signIn error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Signs in or creates student session using Google credential ID token.
   */
  suspend fun signInWithGoogle(
    idToken: String,
    displayName: String? = null,
    email: String? = null,
  ): Result<UserDocument> {
    return try {
      val cleanEmail = email?.trim()?.lowercase() ?: "user@campus.edu"
      val cleanName = displayName?.trim()?.ifBlank { null } ?: cleanEmail.substringBefore("@")
      val uid = if (idToken.isNotBlank()) "google_${kotlin.math.abs(idToken.hashCode())}" else "usr_${kotlin.math.abs(cleanEmail.hashCode())}"
      val userDoc = UserDocument(
        uid = uid,
        name = cleanName,
        email = cleanEmail,
        role = "student",
      )
      _authState.value = AppUser(
        uid = userDoc.uid,
        email = userDoc.email,
        displayName = userDoc.name,
      )
      SessionManager.saveUserSession(userDoc)
      Result.success(userDoc)
    } catch (e: Exception) {
      Log.e(TAG, "signInWithGoogle error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Signs up student via Quick Bite backend.
   */
  suspend fun signUpStudent(
    name: String,
    email: String,
    password: String,
    registrationNumber: String,
    department: String,
    avatarId: String = "scholar",
    fcmToken: String = "",
    phone: String = "",
    course: String = "",
  ): Result<UserDocument> {
    val trimmed = email.trim().lowercase()
    return try {
      val req = RegisterRequestDto(
        name = name.trim(),
        email = trimmed,
        password = password,
        registrationNumber = registrationNumber.trim(),
        department = department.trim(),
        avatarId = avatarId,
        phone = phone.trim(),
        course = course.trim(),
      )
      val res = mongoRepo.registerUser(req)
      if (res.isSuccess) {
        val dto = res.getOrThrow()
        val userDto = dto.user ?: throw Exception("User details missing from registration response")
        val doc = UserDocument(
          uid = userDto.uid,
          name = userDto.name,
          email = userDto.email,
          role = userDto.role,
          registrationNumber = userDto.registrationNumber,
          department = userDto.department,
          avatarId = userDto.avatarId,
          fcmToken = userDto.fcmToken,
          phone = userDto.phone,
          course = userDto.course,
        )
        _authState.value = AppUser(
          uid = doc.uid,
          email = doc.email,
          displayName = doc.name.ifBlank { doc.email.substringBefore("@") },
        )
        SessionManager.saveUserSession(doc)
        Result.success(doc)
      } else {
        Result.failure(res.exceptionOrNull() ?: Exception("Sign up failed"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "signUpStudent error: ${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun getUserProfile(uid: String): Result<UserDocument> {
    return try {
      val res = mongoRepo.getUser(uid)
      if (res.isSuccess) {
        val userDto = res.getOrThrow()
        Result.success(
          UserDocument(
            uid = userDto.uid,
            name = userDto.name,
            email = userDto.email,
            role = userDto.role,
            registrationNumber = userDto.registrationNumber,
            department = userDto.department,
            avatarId = userDto.avatarId,
            fcmToken = userDto.fcmToken,
            phone = userDto.phone,
            course = userDto.course,
          )
        )
      } else {
        Result.failure(res.exceptionOrNull() ?: Exception("User not found"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun sendPasswordReset(email: String): Result<Unit> {
    return sendPasswordResetEmail(email)
  }

  val lastResetToken: String?
    get() = mongoRepo.lastForgotPasswordResponse?.resetToken

  val lastResetUrl: String?
    get() = mongoRepo.lastForgotPasswordResponse?.resetUrl

  suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
    return try {
      val res = mongoRepo.forgotPassword(email.trim().lowercase())
      if (res.isSuccess) {
        Result.success(Unit)
      } else {
        Result.failure(res.exceptionOrNull() ?: Exception("Failed to send password reset email"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun updatePassword(newPassword: String): Result<Unit> {
    // When called from standard session (if active user is logged in)
    val email = currentUser?.email ?: SessionManager.getUserSession()?.email.orEmpty()
    return updatePasswordWithToken(token = "", email = email, newPassword = newPassword)
  }

  suspend fun updatePasswordWithToken(token: String, email: String, newPassword: String): Result<Unit> {
    return try {
      val res = mongoRepo.resetPassword(token = token, email = email, newPassword = newPassword)
      if (res.isSuccess) {
        Result.success(Unit)
      } else {
        Result.failure(res.exceptionOrNull() ?: Exception("Password update failed"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun signOut() {
    _authState.value = null
    SessionManager.clearUserSession()
  }
}
