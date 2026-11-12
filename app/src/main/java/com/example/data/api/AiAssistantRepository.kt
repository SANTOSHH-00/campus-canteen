package com.example.data.api

import android.util.Log
import com.example.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class MessageRole {
  USER,
  ASSISTANT,
}

data class ChatMessage(
  val id: String = UUID.randomUUID().toString(),
  val role: MessageRole,
  val content: String,
  val timestamp: Long = System.currentTimeMillis(),
  val recommendedItems: List<AiRecommendedItemDto> = emptyList(),
  val trackedOrder: AiTrackedOrderDto? = null,
  val selectableOrders: List<AiTrackedOrderDto> = emptyList(),
  val isError: Boolean = false,
  val userFeedback: String? = null, // "positive", "negative", or null
  val feedbackReason: String? = null,
  val messageId: String? = null, // Unique ID from backend for feedback linkage
)

sealed class AiStreamEvent {
  data class Meta(
    val messageId: String?,
    val trackedOrder: AiTrackedOrderDto?,
    val activeOrders: List<AiTrackedOrderDto>,
    val recommendedItems: List<AiRecommendedItemDto>,
  ) : AiStreamEvent()

  data class Chunk(val text: String) : AiStreamEvent()

  data class Done(
    val messageId: String?,
    val fullReply: String,
    val suggestions: List<String>,
  ) : AiStreamEvent()

  data class Error(val message: String, val statusCode: Int? = null) : AiStreamEvent()
}

class AiAssistantRepository(
  private val apiService: QuickbiteApiService = ApiClient.apiService,
) {
  private val TAG = "AiAssistantRepo"

  /**
   * Streams chat response over Server-Sent Events (SSE).
   * Yields Meta (cards), Chunks (tokens in real-time), and Done events.
   */
  fun streamChatMessage(
    message: String,
    canteenId: String? = null,
    conversationHistory: List<ChatMessage> = emptyList(),
  ): Flow<AiStreamEvent> = flow {
    val cleanPrompt = message.trim()
    val token = SessionManager.getStudentToken()
    val authHeader = if (token.isNotBlank()) "Bearer $token" else null
    val studentProfile = SessionManager.getStudentSession()
    val userDoc = SessionManager.getUserSession()
    val reg = studentProfile?.registrationNumber?.ifBlank { null } ?: userDoc?.registrationNumber?.ifBlank { null }
    val email = studentProfile?.email?.ifBlank { null } ?: userDoc?.email?.ifBlank { null }
    val name = studentProfile?.name?.ifBlank { null } ?: userDoc?.name?.ifBlank { null }
    val effectiveStudentId = reg ?: email ?: userDoc?.uid ?: "student_guest"

    val historyDtos = conversationHistory.takeLast(4).map {
      AiChatMessageDto(
        role = if (it.role == MessageRole.USER) "user" else "model",
        content = it.content,
      )
    }

    val request = AiChatRequestDto(
      message = cleanPrompt,
      canteenId = canteenId,
      conversationHistory = historyDtos,
      studentId = effectiveStudentId,
    )

    val response = apiService.streamAiMessage(
      body = request,
      authHeader = authHeader,
      studentIdHeader = effectiveStudentId,
      studentRegHeader = reg,
      studentEmailHeader = email,
      studentNameHeader = name,
    )
    if (!response.isSuccessful || response.body() == null) {
      val errBody = response.errorBody()?.string() ?: ""
      emit(AiStreamEvent.Error(errBody.ifBlank { "HTTP ${response.code()}" }, response.code()))
      return@flow
    }

    val body = response.body()!!
    val reader = BufferedReader(InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))

    val moshi = ApiClient.moshi
    val itemAdapter = moshi.adapter(AiRecommendedItemDto::class.java)
    val orderAdapter = moshi.adapter(AiTrackedOrderDto::class.java)

    try {
      var line = reader.readLine()
      while (line != null) {
        val trimmed = line.trim()
        if (trimmed.startsWith("data:")) {
          val jsonStr = trimmed.substring(5).trim()
          if (jsonStr.isNotBlank() && jsonStr != "[DONE]") {
            try {
              val json = JSONObject(jsonStr)
              when (json.optString("type")) {
                "meta" -> {
                  val messageId = if (json.has("messageId") && !json.isNull("messageId")) json.getString("messageId") else null

                  val trackedOrderDto = if (json.has("trackedOrder") && !json.isNull("trackedOrder")) {
                    orderAdapter.fromJson(json.getJSONObject("trackedOrder").toString())
                  } else null

                  val itemsList = mutableListOf<AiRecommendedItemDto>()
                  if (json.has("recommendedItems") && !json.isNull("recommendedItems")) {
                    val arr = json.getJSONArray("recommendedItems")
                    for (i in 0 until arr.length()) {
                      arr.optJSONObject(i)?.let {
                        itemAdapter.fromJson(it.toString())?.let { item -> itemsList.add(item) }
                      }
                    }
                  }

                  val ordersList = mutableListOf<AiTrackedOrderDto>()
                  if (json.has("activeOrders") && !json.isNull("activeOrders")) {
                    val arr = json.getJSONArray("activeOrders")
                    for (i in 0 until arr.length()) {
                      arr.optJSONObject(i)?.let {
                        orderAdapter.fromJson(it.toString())?.let { o -> ordersList.add(o) }
                      }
                    }
                  }

                  emit(AiStreamEvent.Meta(messageId, trackedOrderDto, ordersList, itemsList))
                }
                "chunk" -> {
                  val text = json.optString("text")
                  if (text.isNotEmpty()) {
                    emit(AiStreamEvent.Chunk(text))
                  }
                }
                "done" -> {
                  val messageId = if (json.has("messageId") && !json.isNull("messageId")) json.getString("messageId") else null
                  val fullReply = json.optString("fullReply", "")
                  val suggestions = mutableListOf<String>()
                  if (json.has("suggestions") && !json.isNull("suggestions")) {
                    val arr = json.getJSONArray("suggestions")
                    for (i in 0 until arr.length()) {
                      suggestions.add(arr.getString(i))
                    }
                  }
                  emit(AiStreamEvent.Done(messageId, fullReply, suggestions))
                }
                "error" -> {
                  emit(AiStreamEvent.Error(json.optString("error", "Streaming error.")))
                }
              }
            } catch (je: Exception) {
              Log.w(TAG, "Error parsing SSE chunk: ${je.message}")
            }
          }
        }
        line = reader.readLine()
      }
    } finally {
      try {
        reader.close()
      } catch (ignored: Exception) {}
    }
  }.flowOn(Dispatchers.IO)

  suspend fun sendChatMessage(
    message: String,
    canteenId: String? = null,
    conversationHistory: List<ChatMessage> = emptyList(),
  ): Result<AiChatResponseDto> {
    val cleanPrompt = message.trim()

    return try {
      val token = SessionManager.getStudentToken()
      val authHeader = if (token.isNotBlank()) "Bearer $token" else null
      val studentProfile = SessionManager.getStudentSession()
      val userDoc = SessionManager.getUserSession()
      val reg = studentProfile?.registrationNumber?.ifBlank { null } ?: userDoc?.registrationNumber?.ifBlank { null }
      val email = studentProfile?.email?.ifBlank { null } ?: userDoc?.email?.ifBlank { null }
      val name = studentProfile?.name?.ifBlank { null } ?: userDoc?.name?.ifBlank { null }
      val effectiveStudentId = reg ?: email ?: userDoc?.uid ?: "student_guest"

      val historyDtos = conversationHistory.takeLast(4).map {
        AiChatMessageDto(
          role = if (it.role == MessageRole.USER) "user" else "model",
          content = it.content,
        )
      }

      val request = AiChatRequestDto(
        message = cleanPrompt,
        canteenId = canteenId,
        conversationHistory = historyDtos,
        studentId = effectiveStudentId,
      )

      val response = apiService.sendAiMessage(
        body = request,
        authHeader = authHeader,
        studentIdHeader = effectiveStudentId,
        studentRegHeader = reg,
        studentEmailHeader = email,
        studentNameHeader = name,
      )

      if (response.isSuccessful && response.body() != null) {
        Result.success(response.body()!!)
      } else {
        val errMessage = response.errorBody()?.string() ?: "HTTP ${response.code()}"
        Log.w(TAG, "Backend returned error: $errMessage")
        Result.failure(IOException(errMessage))
      }
    } catch (e: Exception) {
      Log.w(TAG, "Backend unreachable: ${e.message}")
      Result.failure(e)
    }
  }

  suspend fun submitFeedback(
    messageId: String,
    userQuestion: String? = null,
    assistantResponse: String? = null,
    feedbackType: String,
    reason: String? = null,
  ): Result<Boolean> {
    return try {
      val token = SessionManager.getStudentToken()
      val authHeader = if (token.isNotBlank()) "Bearer $token" else null
      val req = AiFeedbackRequestDto(
        messageId = messageId,
        userQuestion = userQuestion,
        assistantResponse = assistantResponse,
        feedbackType = feedbackType,
        reason = reason,
      )
      val response = apiService.submitAiFeedback(req, authHeader)
      if (response.isSuccessful && response.body()?.success == true) {
        Result.success(true)
      } else {
        Result.failure(IOException("Server error: ${response.code()}"))
      }
    } catch (e: Exception) {
      Log.w(TAG, "submitFeedback failed: ${e.message}")
      Result.failure(e)
    }
  }

  suspend fun getSuggestions(): Result<List<String>> {
    return try {
      val response = apiService.getAiSuggestions()
      if (response.isSuccessful && response.body() != null) {
        Result.success(response.body()!!.suggestions)
      } else {
        Result.success(defaultSuggestions)
      }
    } catch (e: Exception) {
      Log.w(TAG, "getSuggestions exception: ${e.message}")
      Result.success(defaultSuggestions)
    }
  }

  companion object {
    val defaultSuggestions = listOf(
      "Recommend something tasty",
      "What's popular today?",
      "Find food under ₹100",
      "Track my latest order",
      "Check my order status",
      "Explore available canteens",
    )
  }
}
