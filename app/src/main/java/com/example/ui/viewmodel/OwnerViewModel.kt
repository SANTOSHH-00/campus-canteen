package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.CanteenDocument
import com.example.data.firebase.OwnerDocument
import com.example.data.firebase.OwnerRepository
import com.example.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import com.example.util.NetworkUtils

enum class OwnerAuthStep {
  CREDENTIALS,
  EMAIL_OTP,
  PHONE_OTP,
  COMPLETE
}

sealed interface OwnerLoginUiState {
  object Idle : OwnerLoginUiState
  object Loading : OwnerLoginUiState
  data class RequiresOtp(
    val owner: OwnerDocument,
    val phone: String = owner.phone,
    val email: String = owner.email,
    val otpCode: String = "",
    val isDelivered: Boolean = true,
    val deliveryNotice: String = "",
  ) : OwnerLoginUiState
  data class Success(val owner: OwnerDocument, val needsFirstTimeAllocation: Boolean) : OwnerLoginUiState
  data class Error(val message: String) : OwnerLoginUiState
}

sealed interface CanteenAllocationUiState {
  object Idle : CanteenAllocationUiState
  object Loading : CanteenAllocationUiState
  data class Ready(val canteens: List<CanteenDocument>, val selectedCanteen: CanteenDocument? = null) : CanteenAllocationUiState
  object Allocating : CanteenAllocationUiState
  data class Allocated(val updatedOwner: OwnerDocument) : CanteenAllocationUiState
  data class Error(val message: String) : CanteenAllocationUiState
}

class OwnerViewModel(
  private val repository: OwnerRepository = OwnerRepository(),
) : ViewModel() {

  private val _loginState = MutableStateFlow<OwnerLoginUiState>(OwnerLoginUiState.Idle)
  val loginState: StateFlow<OwnerLoginUiState> = _loginState.asStateFlow()

  private val _currentStep = MutableStateFlow(OwnerAuthStep.CREDENTIALS)
  val currentStep: StateFlow<OwnerAuthStep> = _currentStep.asStateFlow()

  private val _allocationState = MutableStateFlow<CanteenAllocationUiState>(CanteenAllocationUiState.Idle)
  val allocationState: StateFlow<CanteenAllocationUiState> = _allocationState.asStateFlow()

  private val _currentOwner = MutableStateFlow<OwnerDocument?>(null)
  val currentOwner: StateFlow<OwnerDocument?> = _currentOwner.asStateFlow()

  private var pendingOwner: OwnerDocument? = null

  init {
    checkPersistedOwner()
  }

  private fun checkPersistedOwner() {
    val savedOwner = SessionManager.getOwnerSession()
    if (savedOwner != null) {
      if (savedOwner.canteenAssigned && savedOwner.canteenId.isNotBlank()) {
        _currentOwner.value = savedOwner
        OwnerRepository.activeSessionOwner = savedOwner
        _currentStep.value = OwnerAuthStep.COMPLETE
        _loginState.value = OwnerLoginUiState.Success(savedOwner, needsFirstTimeAllocation = false)
      } else {
        SessionManager.clearOwnerSession()
        OwnerRepository.activeSessionOwner = null
      }
      return
    }

    val firebaseUser = repository.currentUser
    if (firebaseUser != null) {
      viewModelScope.launch {
        val result = repository.getOwnerProfile(firebaseUser.uid)
        if (result.isSuccess) {
          val owner = result.getOrNull()
          if (owner != null && owner.canteenAssigned && owner.canteenId.isNotBlank()) {
            _currentOwner.value = owner
            SessionManager.saveOwnerSession(owner)
            OwnerRepository.activeSessionOwner = owner
          }
        }
      }
    }
  }

  /**
   * Step 1: Login with owner email and password.
   * Only authorized owners with canteenAssigned == true proceed to OTP.
   * If canteen_assigned == false, NO OTP is sent and access is denied with a clear message.
   */
  fun login(email: String, pass: String) {
    if (_loginState.value is OwnerLoginUiState.Loading) {
      return
    }

    val trimmedEmail = email.trim()
    val trimmedPass = pass.trim()
    if (trimmedEmail.isBlank() || trimmedPass.isBlank()) {
      _loginState.value = OwnerLoginUiState.Error("Please enter both owner email and password.")
      return
    }

    if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
      _loginState.value = OwnerLoginUiState.Error("Please enter a valid email address.")
      return
    }

    viewModelScope.launch {
      _loginState.value = OwnerLoginUiState.Loading
      val result = repository.loginOwner(trimmedEmail, trimmedPass)
      if (result.isSuccess) {
        val owner = result.getOrNull()!!

        // Strict validation: canteen_assigned must be true
        if (!owner.canteenAssigned || owner.canteenId.isBlank()) {
          _currentStep.value = OwnerAuthStep.CREDENTIALS
          _loginState.value = OwnerLoginUiState.Error(
            "Access Denied: Your account is registered, but no canteen has been assigned to you yet. Please contact the campus administrator to assign your canteen."
          )
          return@launch
        }

        pendingOwner = owner
        _currentOwner.value = owner
        val ownerEmail = trimmedEmail

        // Backend POST /api/owner/login already generates and sends the 6-digit OTP via Gmail SMTP!
        // Do not call repository.sendEmailOtp again here as it can trigger cooldown or failure.
        val receivedOtp = repository.lastReceivedOtp.orEmpty()
        _currentStep.value = OwnerAuthStep.EMAIL_OTP
        _loginState.value = OwnerLoginUiState.RequiresOtp(
          owner = owner,
          phone = owner.phone,
          email = ownerEmail,
          otpCode = receivedOtp,
          isDelivered = repository.lastOtpDelivered,
          deliveryNotice = if (receivedOtp.isNotBlank()) "Verification OTP: $receivedOtp" else repository.lastOtpDeliveryNotice,
        )
      } else {
        _currentStep.value = OwnerAuthStep.CREDENTIALS
        val ex = result.exceptionOrNull()
        val rawMsg = ex?.message ?: "Login failed. Please verify owner credentials."
        val friendlyMsg = when {
          rawMsg.contains("CanteenNotAssigned", ignoreCase = true) ||
          rawMsg.contains("no canteen has been assigned", ignoreCase = true) ||
          rawMsg.contains("not assigned", ignoreCase = true) ->
            "Access Denied: Your account is registered, but no canteen has been assigned to you yet. Please contact the campus administrator to assign your canteen."
          rawMsg.contains("invalid_credentials", ignoreCase = true) ||
          rawMsg.contains("Invalid login credentials", ignoreCase = true) ||
          rawMsg.contains("Incorrect password", ignoreCase = true) ->
            "Incorrect password. Please verify and try again."
          rawMsg.contains("No account found", ignoreCase = true) ||
          rawMsg.contains("No canteen owner account", ignoreCase = true) ||
          rawMsg.contains("UserNotFound", ignoreCase = true) ->
            "Access denied: No account found with this email. Only authorized canteen owners can log in."
          rawMsg.contains("privileges", ignoreCase = true) || rawMsg.contains("NotAnOwner", ignoreCase = true) ->
            "Access denied: This account does not have canteen owner privileges."
          else -> NetworkUtils.toFriendlyErrorMessage(ex, "Login failed. Please verify owner credentials.")
        }
        _loginState.value = OwnerLoginUiState.Error(friendlyMsg)
      }
    }
  }

  /**
   * Step 2: Request / Resend Email OTP via Gmail SMTP to the typed email.
   */
  fun sendOtp(target: String) {
    viewModelScope.launch {
      val emailToSend = target.ifBlank { pendingOwner?.email.orEmpty() }.trim()
      val ownerName = pendingOwner?.name ?: "Canteen Owner"
      if (emailToSend.isNotBlank()) {
        val otpResult = repository.sendEmailOtp(emailToSend, ownerName)
        val generatedOtp = otpResult.getOrNull().orEmpty()
        val owner = pendingOwner ?: _currentOwner.value
        if (owner != null) {
          _loginState.value = OwnerLoginUiState.RequiresOtp(
            owner = owner,
            phone = owner.phone,
            email = emailToSend,
            otpCode = generatedOtp,
            isDelivered = repository.lastOtpDelivered,
            deliveryNotice = repository.lastOtpDeliveryNotice,
          )
        }
      }
    }
  }

  fun resendEmailOtp(email: String) {
    viewModelScope.launch {
      val emailToSend = email.ifBlank { pendingOwner?.email.orEmpty() }.trim()
      val ownerName = pendingOwner?.name ?: "Canteen Owner"
      if (emailToSend.isNotBlank()) {
        val otpResult = repository.sendEmailOtp(emailToSend, ownerName)
        val generatedOtp = otpResult.getOrNull().orEmpty()
        val owner = pendingOwner ?: _currentOwner.value
        if (otpResult.isFailure || !repository.lastOtpDelivered) {
          val ex = otpResult.exceptionOrNull()
          val errorMsg = NetworkUtils.toFriendlyErrorMessage(ex, repository.lastOtpDeliveryNotice.ifBlank { "Failed to deliver OTP. Please try again." })
          _loginState.value = OwnerLoginUiState.Error(errorMsg)
        } else if (owner != null) {
          _loginState.value = OwnerLoginUiState.RequiresOtp(
            owner = owner,
            phone = owner.phone,
            email = emailToSend,
            otpCode = generatedOtp,
            isDelivered = repository.lastOtpDelivered,
            deliveryNotice = repository.lastOtpDeliveryNotice,
          )
        }
      }
    }
  }

  /**
   * Step 2: Verify 6-digit OTP code entered by the owner.
   * If verified, logs in to that specified canteen directly (allocation is skipped).
   */
  fun verifyOtp(target: String, otpCode: String) {
    if (otpCode.trim().length < 6) {
      _loginState.value = OwnerLoginUiState.Error("Please enter a valid 6-digit OTP code.")
      return
    }

    viewModelScope.launch {
      _loginState.value = OwnerLoginUiState.Loading
      val result = repository.verifyEmailOtp(target.trim(), otpCode.trim())
      if (result.isSuccess) {
        val owner = pendingOwner ?: _currentOwner.value
        if (owner != null && owner.canteenAssigned && owner.canteenId.isNotBlank()) {
          _currentStep.value = OwnerAuthStep.COMPLETE
          // Directly log into their specified assigned canteen
          _loginState.value = OwnerLoginUiState.Success(owner, needsFirstTimeAllocation = false)
          SessionManager.saveOwnerSession(owner)
          OwnerRepository.activeSessionOwner = owner
        } else if (owner != null && !owner.canteenAssigned) {
          _loginState.value = OwnerLoginUiState.Error(
            "Access Denied: No canteen has been assigned to your account yet. Please contact administration."
          )
        } else {
          _loginState.value = OwnerLoginUiState.Error("Owner session expired. Please log in again.")
        }
      } else {
        val ex = result.exceptionOrNull()
        val errorMsg = NetworkUtils.toFriendlyErrorMessage(ex, "Invalid OTP code. Please check your email and try again.")
        _loginState.value = OwnerLoginUiState.Error(errorMsg)
      }
    }
  }

  fun goToEmailOtp(email: String = "") {
    _currentStep.value = OwnerAuthStep.EMAIL_OTP
    val current = _loginState.value
    if (current is OwnerLoginUiState.RequiresOtp && email.isNotBlank() && current.email.isBlank()) {
      _loginState.value = current.copy(email = email)
    }
  }

  fun setError(message: String) {
    _loginState.value = OwnerLoginUiState.Error(message)
  }

  fun backToCredentials() {
    _currentStep.value = OwnerAuthStep.CREDENTIALS
    _loginState.value = OwnerLoginUiState.Idle
  }

  fun resetLoginState() {
    _loginState.value = OwnerLoginUiState.Idle
    _currentStep.value = OwnerAuthStep.CREDENTIALS
  }

  fun loadCanteensForAllocation() {
    viewModelScope.launch {
      _allocationState.value = CanteenAllocationUiState.Loading
      repository.observeCanteens()
        .catch { e ->
          _allocationState.value = CanteenAllocationUiState.Error(e.message ?: "Failed to load canteens from Firestore.")
        }
        .collect { canteens ->
          val current = _allocationState.value
          val selected = if (current is CanteenAllocationUiState.Ready) current.selectedCanteen else null
          _allocationState.value = CanteenAllocationUiState.Ready(canteens = canteens, selectedCanteen = selected)
        }
    }
  }

  fun selectCanteen(canteen: CanteenDocument) {
    val current = _allocationState.value
    if (current is CanteenAllocationUiState.Ready) {
      _allocationState.value = current.copy(selectedCanteen = canteen)
    }
  }

  fun confirmAllocation(onSuccess: (OwnerDocument) -> Unit = {}) {
    val current = _allocationState.value
    if (current !is CanteenAllocationUiState.Ready || current.selectedCanteen == null) {
      return
    }

    val selectedCanteen = current.selectedCanteen
    val owner = _currentOwner.value ?: pendingOwner
    if (owner == null) {
      _allocationState.value = CanteenAllocationUiState.Error("No active owner session found. Please log in again.")
      return
    }

    viewModelScope.launch {
      _allocationState.value = CanteenAllocationUiState.Allocating
      val result = repository.allocateCanteen(
        uid = owner.uid,
        canteenId = selectedCanteen.id,
        block = selectedCanteen.block,
      )

      if (result.isSuccess) {
        val updated = result.getOrNull()!!
        _currentOwner.value = updated
        _allocationState.value = CanteenAllocationUiState.Allocated(updated)
        SessionManager.saveOwnerSession(updated)
        OwnerRepository.activeSessionOwner = updated
        onSuccess(updated)
      } else {
        val errorMsg = result.exceptionOrNull()?.message ?: "Failed to allocate canteen. Please try again."
        _allocationState.value = CanteenAllocationUiState.Error(errorMsg)
      }
    }
  }

  fun setOwner(owner: OwnerDocument?) {
    _currentOwner.value = owner
  }

  fun logout() {
    repository.signOut()
    SessionManager.clearOwnerSession()
    _currentOwner.value = null
    pendingOwner = null
    _currentStep.value = OwnerAuthStep.CREDENTIALS
    _loginState.value = OwnerLoginUiState.Idle
    _allocationState.value = CanteenAllocationUiState.Idle
  }
}
