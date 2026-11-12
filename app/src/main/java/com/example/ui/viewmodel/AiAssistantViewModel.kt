package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.AiAssistantRepository
import com.example.data.api.AiStreamEvent
import com.example.data.api.ChatMessage
import com.example.data.api.MessageRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

class AiAssistantViewModel(
  private val repository: AiAssistantRepository = AiAssistantRepository(),
) : ViewModel() {

  private val TAG = "AiAssistantViewModel"

  private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
  val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _suggestions = MutableStateFlow<List<String>>(AiAssistantRepository.defaultSuggestions)
  val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

  private val _inputText = MutableStateFlow("")
  val inputText: StateFlow<String> = _inputText.asStateFlow()

  private var lastUserMessage: String? = null

  init {
    loadSuggestions()
  }

  fun onInputTextChanged(text: String) {
    _inputText.value = text
  }

  fun loadSuggestions() {
    viewModelScope.launch {
      try {
        val res = repository.getSuggestions()
        if (res.isSuccess) {
          val list = res.getOrNull()
          if (!list.isNullOrEmpty()) {
            _suggestions.value = list
          }
        }
      } catch (e: Throwable) {
        // Fall back quietly to default chips
      }
    }
  }

  fun sendMessage(promptText: String? = null, canteenId: String? = null) {
    val textToSend = (promptText ?: _inputText.value).trim()
    if (textToSend.isEmpty() || _isLoading.value) return

    _inputText.value = ""
    _errorMessage.value = null
    lastUserMessage = textToSend

    // 1. Capture clean PRIOR conversation history (excluding the new prompt and error bubbles)
    val priorHistory = _messages.value.filter { !it.isError && it.content.isNotBlank() }

    val userMessage = ChatMessage(
      role = MessageRole.USER,
      content = textToSend,
    )

    // 2. Append user message to UI
    _messages.value = _messages.value + userMessage
    _isLoading.value = true

    val assistantMsgId = UUID.randomUUID().toString()

    viewModelScope.launch {
      var hasAppendedAssistantBubble = false
      val currentReply = StringBuilder()

      try {
        repository.streamChatMessage(
          message = textToSend,
          canteenId = canteenId,
          conversationHistory = priorHistory,
        ).catch { streamErr ->
          Log.w(TAG, "Stream flow error: ${streamErr.message}")
          if (!hasAppendedAssistantBubble) {
            fallbackSyncCall(textToSend, canteenId, priorHistory, assistantMsgId)
          } else {
            _isLoading.value = false
          }
        }.collect { event ->
          when (event) {
            is AiStreamEvent.Meta -> {
              if (!hasAppendedAssistantBubble) {
                hasAppendedAssistantBubble = true
                val newMsg = ChatMessage(
                  id = assistantMsgId,
                  role = MessageRole.ASSISTANT,
                  content = "",
                  messageId = event.messageId,
                  recommendedItems = event.recommendedItems,
                  trackedOrder = event.trackedOrder,
                  selectableOrders = if (event.trackedOrder == null && event.activeOrders.size > 1) event.activeOrders else emptyList(),
                )
                _messages.value = _messages.value + newMsg
              } else {
                updateAssistantMessage(assistantMsgId) { msg ->
                  msg.copy(
                    messageId = event.messageId ?: msg.messageId,
                    recommendedItems = event.recommendedItems,
                    trackedOrder = event.trackedOrder,
                    selectableOrders = if (event.trackedOrder == null && event.activeOrders.size > 1) event.activeOrders else emptyList(),
                  )
                }
              }
            }
            is AiStreamEvent.Chunk -> {
              currentReply.append(event.text)
              if (!hasAppendedAssistantBubble) {
                hasAppendedAssistantBubble = true
                val newMsg = ChatMessage(
                  id = assistantMsgId,
                  role = MessageRole.ASSISTANT,
                  content = currentReply.toString(),
                )
                _messages.value = _messages.value + newMsg
              } else {
                updateAssistantMessage(assistantMsgId) { msg ->
                  msg.copy(content = currentReply.toString())
                }
              }
            }
            is AiStreamEvent.Done -> {
              _isLoading.value = false
              val finalContent = if (currentReply.isNotEmpty()) currentReply.toString() else event.fullReply
              if (!hasAppendedAssistantBubble) {
                hasAppendedAssistantBubble = true
                val newMsg = ChatMessage(
                  id = assistantMsgId,
                  role = MessageRole.ASSISTANT,
                  content = finalContent.ifBlank { "Here is the information from QuickBite." },
                  messageId = event.messageId,
                )
                _messages.value = _messages.value + newMsg
              } else {
                updateAssistantMessage(assistantMsgId) { msg ->
                  msg.copy(
                    content = finalContent.ifBlank { msg.content.ifBlank { "Here is the information from QuickBite." } },
                    messageId = event.messageId ?: msg.messageId,
                  )
                }
              }
              if (event.suggestions.isNotEmpty()) {
                _suggestions.value = event.suggestions
              }
            }
            is AiStreamEvent.Error -> {
              if (!hasAppendedAssistantBubble) {
                fallbackSyncCall(textToSend, canteenId, priorHistory, assistantMsgId)
              } else {
                _isLoading.value = false
              }
            }
          }
        }
      } catch (e: Exception) {
        if (!hasAppendedAssistantBubble) {
          fallbackSyncCall(textToSend, canteenId, priorHistory, assistantMsgId)
        } else {
          _isLoading.value = false
        }
      }
    }
  }

  private suspend fun fallbackSyncCall(
    prompt: String,
    canteenId: String?,
    priorHistory: List<ChatMessage>,
    assistantMsgId: String,
  ) {
    try {
      val result = repository.sendChatMessage(
        message = prompt,
        canteenId = canteenId,
        conversationHistory = priorHistory,
      )
      _isLoading.value = false

      if (result.isSuccess) {
        val response = result.getOrThrow()
        val replyText = response.reply ?: "I couldn't process that right now. Please try again."
        val newMsg = ChatMessage(
          id = assistantMsgId,
          role = MessageRole.ASSISTANT,
          content = replyText,
          recommendedItems = response.recommendedItems,
          trackedOrder = response.trackedOrder,
          selectableOrders = if (response.trackedOrder == null && response.activeOrders.size > 1) response.activeOrders else emptyList(),
          messageId = response.messageId,
        )
        _messages.value = _messages.value + newMsg

        if (response.suggestions.isNotEmpty()) {
          _suggestions.value = response.suggestions
        }
      } else {
        val ex = result.exceptionOrNull()
        val friendlyError = getFriendlyErrorMessage(ex)
        _errorMessage.value = friendlyError
        val errorMsg = ChatMessage(
          id = assistantMsgId,
          role = MessageRole.ASSISTANT,
          content = friendlyError,
          isError = true,
        )
        _messages.value = _messages.value + errorMsg
      }
    } catch (t: Throwable) {
      _isLoading.value = false
      val friendlyError = getFriendlyErrorMessage(t)
      _errorMessage.value = friendlyError
      val errorMsg = ChatMessage(
        id = assistantMsgId,
        role = MessageRole.ASSISTANT,
        content = friendlyError,
        isError = true,
      )
      _messages.value = _messages.value + errorMsg
    }
  }

  private fun updateAssistantMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
    val list = _messages.value.toMutableList()
    val index = list.indexOfFirst { it.id == id }
    if (index != -1) {
      list[index] = transform(list[index])
      _messages.value = list
    }
  }

  private fun getFriendlyErrorMessage(throwable: Throwable?): String {
    return when (throwable) {
      is UnknownHostException -> "You appear to be offline. Please check your internet connection."
      is SocketTimeoutException -> "The connection timed out. Please tap Retry."
      else -> {
        val msg = throwable?.message?.lowercase() ?: ""
        when {
          msg.contains("404") || msg.contains("not found") ->
            "QuickBite server is updating or waking up on Render. Please wait a moment and tap Retry."
          msg.contains("429") || msg.contains("too many") ->
            "High campus demand on MunchMate right now. Please wait a moment and tap Retry."
          msg.contains("failed to connect") || msg.contains("timeout") ->
            "Could not connect to QuickBite server. Please check your internet or tap Retry."
          else ->
            "Sorry, I ran into an issue connecting to the QuickBite server. Please check your internet or tap Retry."
        }
      }
    }
  }

  fun onFeedbackSelected(
    targetMessage: ChatMessage,
    feedbackType: String,
    reason: String? = null,
  ) {
    val currentMessages = _messages.value
    val index = currentMessages.indexOfFirst { it.id == targetMessage.id }
    if (index == -1) return

    val updatedMessage = targetMessage.copy(
      userFeedback = feedbackType,
      feedbackReason = reason,
    )

    val updatedList = currentMessages.toMutableList()
    updatedList[index] = updatedMessage
    _messages.value = updatedList

    val userQuestion = currentMessages.take(index).lastOrNull { it.role == MessageRole.USER }?.content ?: ""
    val backendMessageId = targetMessage.messageId ?: targetMessage.id

    viewModelScope.launch {
      repository.submitFeedback(
        messageId = backendMessageId,
        userQuestion = userQuestion,
        assistantResponse = targetMessage.content,
        feedbackType = feedbackType,
        reason = reason,
      )
    }
  }

  fun retryLastMessage(canteenId: String? = null) {
    lastUserMessage?.let { prompt ->
      val currentList = _messages.value.toMutableList()
      // Remove trailing error message
      if (currentList.isNotEmpty() && currentList.last().isError) {
        currentList.removeAt(currentList.lastIndex)
      }
      // Also remove preceding user message so it isn't duplicated
      if (currentList.isNotEmpty() && currentList.last().role == MessageRole.USER && currentList.last().content == prompt) {
        currentList.removeAt(currentList.lastIndex)
      }
      _messages.value = currentList
      sendMessage(promptText = prompt, canteenId = canteenId)
    }
  }

  fun clearConversation() {
    _messages.value = emptyList()
  }
}
