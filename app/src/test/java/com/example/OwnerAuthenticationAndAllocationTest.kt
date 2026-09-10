package com.example

import com.example.data.firebase.CanteenDocument
import com.example.data.firebase.OwnerAuthException
import com.example.data.firebase.OwnerDocument
import com.example.data.firebase.OwnerRepository
import com.example.ui.viewmodel.CanteenAllocationUiState
import com.example.ui.viewmodel.OwnerLoginUiState
import com.example.ui.viewmodel.OwnerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeOwnerRepository : OwnerRepository(auth = null, firestore = null) {
  var loginResult: Result<OwnerDocument> = Result.failure(Exception("Not set"))
  var canteensFlow: Flow<List<CanteenDocument>> = flowOf(emptyList())
  var allocateResult: Result<OwnerDocument> = Result.failure(Exception("Not set"))
  var loggedOut: Boolean = false
  override var lastSentOtpEmail: String? = null

  override suspend fun loginOwner(email: String, pass: String): Result<OwnerDocument> {
    if (loginResult.isSuccess) {
      lastSentOtpEmail = email
    }
    return loginResult
  }

  override fun observeCanteens(): Flow<List<CanteenDocument>> {
    return canteensFlow
  }

  override suspend fun allocateCanteen(uid: String, canteenId: String, block: String): Result<OwnerDocument> {
    return allocateResult
  }

  override suspend fun sendEmailOtp(email: String, ownerName: String): Result<String> {
    lastSentOtpEmail = email
    return Result.success("123456")
  }

  override suspend fun verifyEmailOtp(email: String, otpCode: String): Result<Boolean> {
    if (otpCode == "123456") return Result.success(true)
    return Result.failure(IllegalArgumentException("Invalid verification code."))
  }

  override fun signOut() {
    loggedOut = true
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class OwnerAuthenticationAndAllocationTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var fakeRepository: FakeOwnerRepository
  private lateinit var viewModel: OwnerViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    fakeRepository = FakeOwnerRepository()
    viewModel = OwnerViewModel(repository = fakeRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun testLogin_ownerWithCanteenAssignedFalse_deniesAccessAndSendsNoOtp() = runTest {
    fakeRepository.loginResult = Result.failure(OwnerAuthException.CanteenNotAssigned())

    viewModel.login("canteen.owner@campus.edu", "validpass123")
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.loginState.value
    assertTrue("State should be Error", state is OwnerLoginUiState.Error)
    val error = state as OwnerLoginUiState.Error
    assertTrue("Error should mention not assigned / contact administrator", error.message.contains("assigned", ignoreCase = true))
    assertEquals("Should stay on credentials step", com.example.ui.viewmodel.OwnerAuthStep.CREDENTIALS, viewModel.currentStep.value)
    assertNull("No OTP should be sent when canteen is not assigned", fakeRepository.lastSentOtpEmail)
  }

  @Test
  fun testLogin_validOwner_alreadyAllocated_skipsAllocation() = runTest {
    val allocatedOwner = OwnerDocument(
      uid = "owner_456",
      name = "Suresh Patel",
      email = "suresh.owner@campus.edu",
      role = "owner",
      canteenAssigned = true,
      canteenId = "canteen_26_27",
      block = "Block 26-27",
    )

    fakeRepository.loginResult = Result.success(allocatedOwner)

    val typedEmail = "suresh.owner@campus.edu"
    viewModel.login(typedEmail, "validpass123")
    testDispatcher.scheduler.advanceUntilIdle()

    val otpState = viewModel.loginState.value
    assertTrue("State should be RequiresOtp", otpState is OwnerLoginUiState.RequiresOtp)
    assertEquals("OTP should be sent to the email typed by user", typedEmail, fakeRepository.lastSentOtpEmail)

    // Verify OTP
    viewModel.verifyOtp(typedEmail, "123456")
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.loginState.value
    assertTrue("State should be Success", state is OwnerLoginUiState.Success)
    val success = state as OwnerLoginUiState.Success
    assertEquals("canteen_26_27", success.owner.canteenId)
    assertFalse("Should NOT need allocation when already assigned", success.needsFirstTimeAllocation)
  }

  @Test
  fun testVerifyOtp_invalidOtp_returnsError() = runTest {
    val owner = OwnerDocument(
      uid = "owner_123",
      email = "owner@campus.edu",
      role = "owner",
    )
    fakeRepository.loginResult = Result.success(owner)

    viewModel.login("owner@campus.edu", "validpass")
    testDispatcher.scheduler.advanceUntilIdle()

    // Pass invalid OTP
    viewModel.verifyOtp("+91 98765 43210", "000000")
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.loginState.value
    assertTrue("State should be Error", state is OwnerLoginUiState.Error)
  }

  @Test
  fun testLogin_invalidPassword_returnsError() = runTest {
    fakeRepository.loginResult = Result.failure(OwnerAuthException.InvalidCredentials())

    viewModel.login("owner@campus.edu", "wrongpass")
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.loginState.value
    assertTrue("State should be Error", state is OwnerLoginUiState.Error)
    val error = state as OwnerLoginUiState.Error
    assertTrue("Error should mention incorrect password", error.message.contains("password", ignoreCase = true))
  }

  @Test
  fun testLogin_unknownAccount_returnsError() = runTest {
    fakeRepository.loginResult = Result.failure(OwnerAuthException.UserNotFound())

    viewModel.login("unknown@campus.edu", "somepass")
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.loginState.value
    assertTrue("State should be Error", state is OwnerLoginUiState.Error)
    val error = state as OwnerLoginUiState.Error
    assertTrue("Error should mention no account found", error.message.contains("No account found", ignoreCase = true))
  }

  @Test
  fun testLogin_accountWithoutOwnerDocument_returnsError() = runTest {
    fakeRepository.loginResult = Result.failure(OwnerAuthException.OwnerDocumentMissing())

    viewModel.login("user@campus.edu", "somepass")
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.loginState.value
    assertTrue("State should be Error", state is OwnerLoginUiState.Error)
    val error = state as OwnerLoginUiState.Error
    assertTrue("Error should mention missing owner record", error.message.contains("owner record", ignoreCase = true))
  }

  @Test
  fun testLogin_nonOwnerAccount_returnsError() = runTest {
    fakeRepository.loginResult = Result.failure(OwnerAuthException.NotAnOwner())

    viewModel.login("student@campus.edu", "somepass")
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.loginState.value
    assertTrue("State should be Error", state is OwnerLoginUiState.Error)
    val error = state as OwnerLoginUiState.Error
    assertTrue("Error should mention access denied / owner privileges", error.message.contains("privileges", ignoreCase = true))
  }

  @Test
  fun testCanteenAllocation_loadAndConfirmAllocation() = runTest {
    val unallocatedOwner = OwnerDocument(
      uid = "owner_789",
      name = "Ramesh Kumar",
      email = "ramesh@campus.edu",
      role = "owner",
      canteenAssigned = false,
    )

    fakeRepository.loginResult = Result.success(unallocatedOwner)
    viewModel.setOwner(unallocatedOwner)

    val mockCanteens = listOf(
      CanteenDocument(id = "canteen_1", name = "Rooftop 26-27 Hub", block = "26-27", location = "5th Floor", isOpen = true),
      CanteenDocument(id = "canteen_2", name = "Central Food Court", block = "32-33", location = "Ground Floor", isOpen = true),
    )

    fakeRepository.canteensFlow = flowOf(mockCanteens)

    viewModel.loadCanteensForAllocation()
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.allocationState.value
    assertTrue("State should be Ready", state is CanteenAllocationUiState.Ready)
    val ready = state as CanteenAllocationUiState.Ready
    assertEquals(2, ready.canteens.size)

    // Select canteen_1
    val selected = ready.canteens[0]
    viewModel.selectCanteen(selected)

    val selectedState = viewModel.allocationState.value as CanteenAllocationUiState.Ready
    assertEquals("canteen_1", selectedState.selectedCanteen?.id)

    // Confirm allocation
    val allocatedOwner = unallocatedOwner.copy(
      canteenAssigned = true,
      canteenId = "canteen_1",
      block = "26-27",
    )
    fakeRepository.allocateResult = Result.success(allocatedOwner)

    viewModel.confirmAllocation()
    testDispatcher.scheduler.advanceUntilIdle()

    val finalState = viewModel.allocationState.value
    assertTrue("State should be Allocated", finalState is CanteenAllocationUiState.Allocated)
    val allocated = finalState as CanteenAllocationUiState.Allocated
    assertEquals("canteen_1", allocated.updatedOwner.canteenId)
    assertTrue("Canteen assigned must be true", allocated.updatedOwner.canteenAssigned)
  }

  @Test
  fun testLogin_emailOtpVerificationFlow() = runTest {
    val owner = OwnerDocument(
      uid = "owner_resend",
      name = "Santosh",
      email = "santosh@gmail.com",
      role = "owner",
      canteenAssigned = true,
      canteenId = "canteen_library",
    )
    fakeRepository.loginResult = Result.success(owner)

    viewModel.login("santosh@gmail.com", "validpass")
    testDispatcher.scheduler.advanceUntilIdle()

    val reqState = viewModel.loginState.value
    assertTrue("Should be RequiresOtp", reqState is OwnerLoginUiState.RequiresOtp)
    assertEquals("santosh@gmail.com", (reqState as OwnerLoginUiState.RequiresOtp).email)
    assertEquals(com.example.ui.viewmodel.OwnerAuthStep.EMAIL_OTP, viewModel.currentStep.value)

    viewModel.verifyOtp("santosh@gmail.com", "123456")
    testDispatcher.scheduler.advanceUntilIdle()

    val successState = viewModel.loginState.value
    assertTrue("Should be Success", successState is OwnerLoginUiState.Success)
  }

  @Test
  fun testLogout_clearsOwnerSession() = runTest {
    viewModel.logout()
    assertTrue(fakeRepository.loggedOut)
    assertNull(viewModel.currentOwner.value)
    assertEquals(OwnerLoginUiState.Idle, viewModel.loginState.value)
  }
}
