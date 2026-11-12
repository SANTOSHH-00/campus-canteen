package com.example.data.api

import android.util.Log
import com.example.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ProductSuggestionRepository(
  private val apiService: QuickbiteApiService = ApiClient.apiService
) {
  private val TAG = "ProductSuggestionRepo"

  suspend fun submitSuggestion(
    canteenId: String,
    canteenName: String,
    suggestionText: String,
    studentName: String,
    studentId: String = "",
    studentEmail: String = "",
    studentReg: String = "",
  ): Result<CreateSuggestionResponseDto> = withContext(Dispatchers.IO) {
    try {
      val token = SessionManager.getStudentToken()
      val authHeader = if (token.isNotBlank()) "Bearer $token" else null
      val session = SessionManager.getUserSession()

      val effectiveStudentId = studentId.ifBlank {
        studentReg.ifBlank {
          session?.registrationNumber?.ifBlank { session.email } ?: "student"
        }
      }
      val effectiveReg = studentReg.ifBlank { session?.registrationNumber.orEmpty() }
      val effectiveEmail = studentEmail.ifBlank { session?.email.orEmpty() }
      val effectiveName = studentName.ifBlank { session?.name ?: "Student" }

      val request = CreateSuggestionRequestDto(
        canteenId = canteenId,
        canteenName = canteenName,
        suggestionText = suggestionText.trim(),
        studentName = effectiveName,
        studentId = effectiveStudentId,
        studentEmail = effectiveEmail,
        studentRegistration = effectiveReg,
      )

      val response = apiService.submitProductSuggestion(
        body = request,
        authHeader = authHeader,
        studentIdHeader = effectiveStudentId,
        studentRegHeader = effectiveReg,
        studentEmailHeader = effectiveEmail,
        studentNameHeader = effectiveName,
      )

      if (response.isSuccessful && response.body() != null) {
        Result.success(response.body()!!)
      } else {
        val errorBodyStr = response.errorBody()?.string().orEmpty()
        var errorMsg = "Unable to submit product suggestion (${response.code()})"
        var code: String? = null
        var remainingHours: Int? = null

        try {
          if (errorBodyStr.isNotBlank()) {
            val json = JSONObject(errorBodyStr)
            errorMsg = json.optString("error", json.optString("message", errorMsg))
            code = json.optString("code", null)
            if (json.has("remainingHours")) {
              remainingHours = json.optInt("remainingHours")
            }
          }
        } catch (_: Exception) {}

        Result.failure(SuggestionException(errorMsg, code = code, remainingHours = remainingHours))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error submitting suggestion", e)
      Result.failure(e)
    }
  }

  suspend fun checkEligibility(
    canteenId: String,
    studentId: String = "",
    studentEmail: String = "",
    studentReg: String = "",
  ): Result<SuggestionEligibilityResponseDto> = withContext(Dispatchers.IO) {
    try {
      val token = SessionManager.getStudentToken()
      val authHeader = if (token.isNotBlank()) "Bearer $token" else null
      val session = SessionManager.getUserSession()

      val effectiveStudentId = studentId.ifBlank {
        studentReg.ifBlank {
          session?.registrationNumber?.ifBlank { session.email } ?: "student"
        }
      }
      val effectiveReg = studentReg.ifBlank { session?.registrationNumber.orEmpty() }
      val effectiveEmail = studentEmail.ifBlank { session?.email.orEmpty() }

      val response = apiService.checkSuggestionEligibility(
        canteenId = canteenId,
        authHeader = authHeader,
        studentIdHeader = effectiveStudentId,
        studentRegHeader = effectiveReg,
        studentEmailHeader = effectiveEmail,
      )

      if (response.isSuccessful && response.body() != null) {
        Result.success(response.body()!!)
      } else {
        Result.failure(Exception("Failed to check suggestion eligibility: ${response.code()}"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error checking eligibility", e)
      Result.failure(e)
    }
  }

  suspend fun getOwnerSuggestions(): Result<List<ProductSuggestionDto>> = withContext(Dispatchers.IO) {
    try {
      val ownerToken = SessionManager.getOwnerToken()
      val authHeader = if (ownerToken.isNotBlank()) "Bearer $ownerToken" else null

      val response = apiService.getOwnerSuggestions(
        authHeader = authHeader,
        ownerToken = ownerToken.ifBlank { null },
      )

      if (response.isSuccessful && response.body() != null) {
        Result.success(response.body()!!.suggestions)
      } else {
        Result.failure(Exception("Failed to fetch suggestions (${response.code()})"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error fetching owner suggestions", e)
      Result.failure(e)
    }
  }

  suspend fun updateSuggestionStatus(
    suggestionId: String,
    status: String,
    ownerNote: String? = null,
  ): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
      val ownerToken = SessionManager.getOwnerToken()
      val authHeader = if (ownerToken.isNotBlank()) "Bearer $ownerToken" else null

      val response = apiService.updateSuggestionStatus(
        suggestionId = suggestionId,
        body = UpdateSuggestionStatusRequestDto(status = status, ownerNote = ownerNote),
        authHeader = authHeader,
        ownerToken = ownerToken.ifBlank { null },
      )

      if (response.isSuccessful) {
        Result.success(true)
      } else {
        Result.failure(Exception("Failed to update status (${response.code()})"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error updating suggestion status", e)
      Result.failure(e)
    }
  }
}

class SuggestionException(
  message: String,
  val code: String? = null,
  val remainingHours: Int? = null,
) : Exception(message)
