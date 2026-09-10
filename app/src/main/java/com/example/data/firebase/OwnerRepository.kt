package com.example.data.firebase

import android.util.Log
import com.example.data.api.MongoRepository
import com.example.data.api.toDocument
import com.example.data.session.SessionManager
import com.example.util.NetworkUtils
import kotlinx.coroutines.flow.Flow

sealed class OwnerAuthException(message: String) : Exception(message) {
  class InvalidCredentials(message: String = "Incorrect password. Please verify and try again.") : OwnerAuthException(message)
  class UserNotFound(message: String = "Access denied: No canteen owner account found for this email. Only authorized canteen owners can log in.") : OwnerAuthException(message)
  class OwnerDocumentMissing(message: String = "Account exists, but no canteen owner record was found in the database.") : OwnerAuthException(message)
  class NotAnOwner(message: String = "Access denied: This account does not have canteen owner privileges.") : OwnerAuthException(message)
  class CanteenNotAssigned(message: String = "Access Denied: Your account is registered, but no canteen has been assigned to you yet. Please contact the campus administrator to assign your canteen.") : OwnerAuthException(message)
  class ServiceUnavailable(message: String = "Authentication service is currently unavailable.") : OwnerAuthException(message)
  class General(message: String) : OwnerAuthException(message)
}

/**
 * Canteen Owner Repository powered by Node.js Express Backend & MongoDB Atlas.
 * Verified with Nodemailer Gmail SMTP 6-digit OTP.
 */
open class OwnerRepository(
  private val firestoreRepo: FirestoreRepository = FirestoreRepository(),
  private val mongoRepo: MongoRepository = MongoRepository(),
  auth: Any? = null,
  firestore: Any? = null,
) {
  private val TAG = "OwnerRepository"

  companion object {
    @Volatile
    var activeSessionOwner: OwnerDocument? = null
  }

  open var lastOtpDelivered: Boolean = true
  open var lastOtpDeliveryNotice: String = ""
  open var lastSentOtpEmail: String? = null

  open val currentUser: AppUser?
    get() = (activeSessionOwner ?: SessionManager.getOwnerSession())?.let {
      AppUser(
        uid = it.uid,
        email = it.email,
        displayName = it.name.ifBlank { it.email.substringBefore("@") },
      )
    }

  open val isUserLoggedIn: Boolean
    get() = currentUser != null

  /**
   * Step 1: Owner Login with email and password.
   * Calls POST /api/owner/login on the backend, which verifies credentials and sends a 6-digit OTP.
   */
  open suspend fun loginOwner(email: String, pass: String): Result<OwnerDocument> {
    val trimmedEmail = email.trim().lowercase()
    val trimmedPass = pass.trim()

    if (trimmedPass.isBlank()) {
      return Result.failure(OwnerAuthException.InvalidCredentials("Please enter your password."))
    }

    return try {
      // 1. Call Backend API to authenticate and trigger 6-digit OTP via Gmail SMTP
      val loginResult = mongoRepo.ownerLogin(trimmedEmail, trimmedPass)
      if (loginResult.isFailure) {
        val rawMsg = loginResult.exceptionOrNull()?.message.orEmpty()
        val mappedException = when {
          rawMsg.contains("CanteenNotAssigned", ignoreCase = true) ||
          rawMsg.contains("no canteen has been assigned", ignoreCase = true) ||
          rawMsg.contains("not assigned", ignoreCase = true) ->
            OwnerAuthException.CanteenNotAssigned()
          rawMsg.contains("invalid_credentials", ignoreCase = true) ||
          rawMsg.contains("Incorrect password", ignoreCase = true) ->
            OwnerAuthException.InvalidCredentials()
          rawMsg.contains("No account found", ignoreCase = true) ||
          rawMsg.contains("No canteen owner account", ignoreCase = true) ||
          rawMsg.contains("UserNotFound", ignoreCase = true) ->
            OwnerAuthException.UserNotFound()
          rawMsg.contains("privileges", ignoreCase = true) || rawMsg.contains("NotAnOwner", ignoreCase = true) ->
            OwnerAuthException.NotAnOwner()
          else -> OwnerAuthException.General(rawMsg)
        }
        return Result.failure(mappedException)
      }

      val loginResponse = loginResult.getOrNull()
      lastSentOtpEmail = trimmedEmail
      lastOtpDelivered = true
      lastOtpDeliveryNotice = loginResponse?.message ?: "OTP code sent to $trimmedEmail"

      // 2. Fetch owner profile from MongoDB or loginResponse
      val ownerDto = loginResponse?.owner ?: mongoRepo.getOwner(trimmedEmail).getOrNull()
      if (ownerDto == null) {
        return Result.failure(OwnerAuthException.UserNotFound("Owner profile could not be found."))
      }

      val doc = OwnerDocument(
        uid = ownerDto.uid,
        name = ownerDto.name.ifBlank { trimmedEmail.substringBefore("@") },
        email = trimmedEmail,
        role = ownerDto.role,
        canteenAssigned = ownerDto.canteenAssigned,
        canteenId = ownerDto.canteenId,
        block = ownerDto.block,
        phone = ownerDto.phone,
        fcmToken = ownerDto.fcmToken,
      )

      Result.success(doc)
    } catch (e: Exception) {
      Log.e(TAG, "Owner login failed: ${e.message}", e)
      if (e is OwnerAuthException) {
        Result.failure(e)
      } else {
        val friendlyMsg = NetworkUtils.toFriendlyErrorMessage(e, "Owner authentication failed. Please try again.")
        Result.failure(OwnerAuthException.General(friendlyMsg))
      }
    }
  }

  open suspend fun getOwnerProfile(uid: String): Result<OwnerDocument> {
    return try {
      val res = mongoRepo.getOwner(uid)
      val ownerDto = res.getOrNull()
      if (ownerDto != null) {
        val doc = OwnerDocument(
          uid = ownerDto.uid,
          name = ownerDto.name,
          email = ownerDto.email,
          role = ownerDto.role,
          canteenAssigned = ownerDto.canteenAssigned,
          canteenId = ownerDto.canteenId,
          block = ownerDto.block,
          phone = ownerDto.phone,
          fcmToken = ownerDto.fcmToken,
        )
        activeSessionOwner = doc
        Result.success(doc)
      } else if (activeSessionOwner != null && (activeSessionOwner?.uid == uid || activeSessionOwner?.email.equals(uid, ignoreCase = true))) {
        Result.success(activeSessionOwner!!)
      } else {
        Result.failure(OwnerAuthException.OwnerDocumentMissing())
      }
    } catch (e: Exception) {
      if (activeSessionOwner != null && (activeSessionOwner?.uid == uid || activeSessionOwner?.email.equals(uid, ignoreCase = true))) {
        Result.success(activeSessionOwner!!)
      } else {
        Result.failure(e)
      }
    }
  }

  /**
   * Resend 6-digit OTP code to the owner's email via Gmail SMTP
   */
  open suspend fun sendEmailOtp(email: String, ownerName: String = "Canteen Owner"): Result<String> {
    val trimmedEmail = email.trim()
    Log.i(TAG, "Requesting OTP resend for owner ($trimmedEmail)")

    return try {
      val res = mongoRepo.ownerResendOtp(trimmedEmail)
      if (res.isSuccess) {
        lastSentOtpEmail = trimmedEmail
        lastOtpDelivered = true
        lastOtpDeliveryNotice = res.getOrNull() ?: "OTP code sent to $trimmedEmail"
        Result.success(lastOtpDeliveryNotice)
      } else {
        lastOtpDelivered = false
        val errMsg = res.exceptionOrNull()?.message ?: "Failed to send OTP email."
        lastOtpDeliveryNotice = errMsg
        Result.failure(Exception(errMsg))
      }
    } catch (e: Exception) {
      lastOtpDelivered = false
      lastOtpDeliveryNotice = e.message ?: "Failed to resend OTP."
      Result.failure(e)
    }
  }

  /**
   * Step 2: Verify 6-digit OTP entered by the owner.
   * Calls POST /api/owner/verify-otp on the backend.
   */
  open suspend fun verifyEmailOtp(email: String, otpCode: String): Result<Boolean> {
    val trimmedCode = otpCode.trim()
    val trimmedEmail = email.trim()

    return try {
      val res = mongoRepo.ownerVerifyOtp(trimmedEmail, trimmedCode)
      if (res.isSuccess) {
        val authSuccess = res.getOrThrow()
        val ownerDto = authSuccess.owner
        if (ownerDto != null) {
          val doc = OwnerDocument(
            uid = ownerDto.uid,
            name = ownerDto.name,
            email = ownerDto.email,
            role = ownerDto.role,
            canteenAssigned = ownerDto.canteenAssigned,
            canteenId = ownerDto.canteenId,
            block = ownerDto.block,
            phone = ownerDto.phone,
            fcmToken = ownerDto.fcmToken,
          )
          activeSessionOwner = doc
          SessionManager.saveOwnerSession(doc)
        }
        Log.i(TAG, "OTP verified successfully for $trimmedEmail")
        Result.success(true)
      } else {
        val errMsg = res.exceptionOrNull()?.message ?: "Invalid verification code."
        Result.failure(IllegalArgumentException(errMsg))
      }
    } catch (e: Exception) {
      Log.e(TAG, "OTP verification exception: ${e.message}", e)
      Result.failure(e)
    }
  }

  open suspend fun sendPhoneOtp(phoneNumber: String): Result<String> {
    return sendEmailOtp(phoneNumber)
  }

  open suspend fun verifyPhoneOtp(phoneNumber: String, otpCode: String): Result<Boolean> {
    return verifyEmailOtp(phoneNumber, otpCode)
  }

  open fun observeCanteens(): Flow<List<CanteenDocument>> {
    return firestoreRepo.observeCanteens()
  }

  open suspend fun allocateCanteen(
    uid: String,
    canteenId: String,
    block: String,
  ): Result<OwnerDocument> {
    return try {
      mongoRepo.assignCanteen(uid, true, canteenId, block)
      val updated = getOwnerProfile(uid).getOrNull()
      if (updated != null) {
        activeSessionOwner = updated
        Result.success(updated)
      } else {
        val fallback = activeSessionOwner?.copy(
          canteenAssigned = true,
          canteenId = canteenId,
          block = block,
        ) ?: OwnerDocument(
          uid = uid,
          canteenAssigned = true,
          canteenId = canteenId,
          block = block,
        )
        activeSessionOwner = fallback
        Result.success(fallback)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to allocate canteen in MongoDB: ${e.message}", e)
      Result.failure(e)
    }
  }

  open fun signOut() {
    activeSessionOwner = null
    SessionManager.clearOwnerSession()
  }
}
