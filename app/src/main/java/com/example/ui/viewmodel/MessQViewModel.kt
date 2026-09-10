package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CampusBlockCanteen
import com.example.data.NotificationItem
import com.example.data.OrderRecord
import com.example.data.SampleFoodData
import com.example.data.firebase.AuthRepository
import com.example.data.firebase.FirestoreRepository
import com.example.data.firebase.OrderDocument
import com.example.data.firebase.StorageRepository
import com.example.ui.state.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MessQViewModel(
  val authRepository: AuthRepository = AuthRepository(),
  val firestoreRepository: FirestoreRepository = FirestoreRepository(),
  val storageRepository: StorageRepository = StorageRepository(),
) : ViewModel() {

  private val _currentUser = MutableStateFlow<UserProfile?>(null)
  val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

  private val _canteens = MutableStateFlow<List<CampusBlockCanteen>>(emptyList())
  val canteens: StateFlow<List<CampusBlockCanteen>> = _canteens.asStateFlow()

  private val _orders = MutableStateFlow<List<OrderRecord>>(emptyList())
  val orders: StateFlow<List<OrderRecord>> = _orders.asStateFlow()

  private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
  val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  init {
    listenToAuthState()
    listenToCanteens()
  }

  private fun listenToAuthState() {
    viewModelScope.launch {
      authRepository.observeAuthState().collect { firebaseUser ->
        if (firebaseUser != null) {
          val profileResult = authRepository.getUserProfile(firebaseUser.uid)
          if (profileResult.isSuccess) {
            _currentUser.value = profileResult.getOrNull()?.toUserProfile()
          } else {
            _currentUser.value = UserProfile(
              name = firebaseUser.displayName ?: "Alex Rivera",
              registrationNumber = "12345678",
              department = "Computer Science & Engineering",
              email = firebaseUser.email ?: "alex.student@campus.edu",
            )
          }
          listenToUserData(firebaseUser.uid)
        } else {
          // Keep guest or existing session state
        }
      }
    }
  }

  private fun listenToCanteens() {
    viewModelScope.launch {
      firestoreRepository.observeCanteens().collect { canteenDocs ->
        if (canteenDocs.isNotEmpty()) {
          val mapped = canteenDocs.map { it.toCampusBlockCanteen() }
          _canteens.value = mapped
        }
      }
    }
  }

  private fun listenToUserData(uid: String) {
    viewModelScope.launch {
      firestoreRepository.observeStudentOrders(uid).collect { orderDocs ->
        if (orderDocs.isNotEmpty()) {
          _orders.value = orderDocs.map { it.toOrderRecord() }
        }
      }
    }
    viewModelScope.launch {
      firestoreRepository.observeUserNotifications(uid).collect { notifDocs ->
        if (notifDocs.isNotEmpty()) {
          _notifications.value = notifDocs.map { it.toNotificationItem() }
        }
      }
    }
  }

  private fun seedInitialDataIfNecessary() {
    viewModelScope.launch {
      firestoreRepository.seedDefaultDataIfEmpty()
    }
  }

  fun signIn(email: String, pass: String, onSuccess: (UserProfile) -> Unit, onError: (String) -> Unit) {
    viewModelScope.launch {
      _isLoading.value = true
      val result = authRepository.signIn(email, pass)
      _isLoading.value = false
      if (result.isSuccess) {
        val profile = result.getOrNull()?.toUserProfile() ?: UserProfile(
          name = email.substringBefore("@"),
          registrationNumber = "12345678",
          department = "Computer Science & Engineering",
          email = email,
        )
        _currentUser.value = profile
        onSuccess(profile)
      } else {
        onError(result.exceptionOrNull()?.localizedMessage ?: "Sign in failed")
      }
    }
  }

  fun signUp(
    name: String,
    email: String,
    pass: String,
    regNo: String,
    dept: String,
    onSuccess: (UserProfile) -> Unit,
    onError: (String) -> Unit,
  ) {
    viewModelScope.launch {
      _isLoading.value = true
      val result = authRepository.signUpStudent(name, email, pass, regNo, dept)
      _isLoading.value = false
      if (result.isSuccess) {
        val profile = result.getOrNull()!!.toUserProfile()
        _currentUser.value = profile
        onSuccess(profile)
      } else {
        onError(result.exceptionOrNull()?.localizedMessage ?: "Registration failed")
      }
    }
  }

  fun signOut() {
    authRepository.signOut()
    _currentUser.value = null
  }

  fun placeOrder(orderRecord: OrderRecord, studentId: String = "student_guest", canteenId: String = "canteen_33") {
    viewModelScope.launch {
      val doc = OrderDocument.fromOrderRecord(
        record = orderRecord,
        studentId = studentId,
        studentName = _currentUser.value?.name ?: "Alex Rivera",
        canteenId = canteenId,
      )
      firestoreRepository.createOrder(doc)
    }
  }
}
