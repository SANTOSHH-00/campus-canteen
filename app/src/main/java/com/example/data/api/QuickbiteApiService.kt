// QuickbiteApiService: Background service handling order lifecycle notifications and background synchronization
package com.example.data.api

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface QuickbiteApiService {

  @GET("health")
  suspend fun getHealth(): Response<Map<String, Any>>

  @POST("auth/login")
  suspend fun loginUser(@Body body: LoginRequestDto): Response<LoginResponseDto>

  @POST("auth/register")
  suspend fun registerUser(@Body body: RegisterRequestDto): Response<LoginResponseDto>

  @POST("auth/forgot-password")
  suspend fun forgotPassword(@Body body: ForgotPasswordRequestDto): Response<SimpleMessageResponseDto>

  @POST("auth/reset-password")
  suspend fun resetPassword(@Body body: ResetPasswordRequestDto): Response<SimpleMessageResponseDto>

  @POST("owner/login")
  suspend fun ownerLogin(@Body body: OwnerLoginRequestDto): Response<OwnerLoginResponseDto>

  @POST("owner/verify-otp")
  suspend fun ownerVerifyOtp(@Body body: OwnerVerifyOtpRequestDto): Response<OwnerAuthSuccessResponseDto>

  @POST("owner/resend-otp")
  suspend fun ownerResendOtp(@Body body: OwnerResendOtpRequestDto): Response<SimpleMessageResponseDto>

  @POST("owner/orders/verify-qr")
  suspend fun verifyQr(
    @Body body: VerifyQrRequestDto,
    @retrofit2.http.Header("Authorization") authHeader: String? = null,
  ): Response<VerifyQrResponseDto>

  @POST("owner/orders/verify-barcode")
  suspend fun verifyBarcode(
    @Body body: VerifyBarcodeRequestDto,
    @retrofit2.http.Header("Authorization") authHeader: String? = null,
  ): Response<VerifyBarcodeResponseDto>

  @POST("owner/orders/confirm-pickup")
  suspend fun confirmPickup(
    @Body body: ConfirmPickupRequestDto,
    @retrofit2.http.Header("Authorization") authHeader: String? = null,
  ): Response<ConfirmPickupResponseDto>

  @GET("canteens")
  suspend fun getCanteens(): Response<List<MongoCanteenDto>>

  @GET("canteens/{id}")
  suspend fun getCanteen(@Path("id") id: String): Response<MongoCanteenDto>

  @POST("canteens")
  suspend fun saveCanteen(@Body canteen: MongoCanteenDto): Response<MongoCanteenDto>

  @PATCH("canteens/{id}/status")
  suspend fun updateCanteenStatus(
    @Path("id") id: String,
    @Body body: StatusUpdateDto,
  ): Response<MongoCanteenDto>

  @PATCH("canteens/{id}/profile")
  suspend fun updateCanteenProfile(
    @Path("id") id: String,
    @Body body: ProfileUpdateDto,
  ): Response<MongoCanteenDto>

  @GET("items/canteen/{canteenId}")
  suspend fun getCanteenItems(@Path("canteenId") canteenId: String): Response<List<MongoItemDto>>

  @GET("items/{id}")
  suspend fun getItem(@Path("id") id: String): Response<MongoItemDto>

  @POST("items")
  suspend fun saveItem(@Body item: MongoItemDto): Response<MongoItemDto>

  @PATCH("items/{id}/stock")
  suspend fun updateItemStock(
    @Path("id") id: String,
    @Body body: StockUpdateDto,
  ): Response<MongoItemDto>

  @PATCH("items/{id}/availability")
  suspend fun updateItemAvailability(
    @Path("id") id: String,
    @Body body: AvailabilityUpdateDto,
  ): Response<Map<String, Any>>

  @DELETE("items/{id}")
  suspend fun deleteItem(@Path("id") id: String): Response<Map<String, Any>>

  @Multipart
  @POST("items/upload-image")
  suspend fun uploadItemImage(
    @Part image: MultipartBody.Part,
  ): Response<ImageUploadResponseDto>

  @POST("items/generate-description")
  suspend fun generateItemDescription(
    @Body body: GenerateDescriptionRequestDto,
  ): Response<GenerateDescriptionResponseDto>

  @GET("items/{id}/reviews")
  suspend fun getItemReviews(
    @Path("id") id: String,
  ): Response<ItemReviewsResponseDto>

  @POST("items/{id}/reviews")
  suspend fun submitItemReview(
    @Path("id") id: String,
    @Body body: CreateReviewRequestDto,
  ): Response<CreateReviewResponseDto>

  @GET("orders/student/{studentId}")
  suspend fun getStudentOrders(
    @Path("studentId") studentId: String,
    @retrofit2.http.Query("canteenId") canteenId: String? = null,
    @retrofit2.http.Query("dateOption") dateOption: String? = null,
    @retrofit2.http.Query("startDate") startDate: String? = null,
    @retrofit2.http.Query("endDate") endDate: String? = null,
  ): Response<List<MongoOrderDto>>

  @GET("orders/canteen/{canteenId}")
  suspend fun getCanteenOrders(@Path("canteenId") canteenId: String): Response<List<MongoOrderDto>>

  @GET("orders/{id}")
  suspend fun getOrder(@Path("id") id: String): Response<MongoOrderDto>

  @POST("orders")
  suspend fun createOrder(
    @Body order: MongoOrderDto,
    @retrofit2.http.Header("Idempotency-Key") idempotencyKey: String? = null,
  ): Response<MongoOrderDto>

  @GET("orders/queue/canteen/{canteenId}")
  suspend fun getCanteenQueue(@Path("canteenId") canteenId: String): Response<CanteenQueueDto>

  @POST("orders/queue/cart")
  suspend fun getCartQueue(@Body body: CartQueueRequestDto): Response<CartQueueResponseDto>

  @GET("orders/{id}/queue")
  suspend fun getOrderQueuePosition(@Path("id") id: String): Response<OrderQueuePositionDto>

  @GET("orders/{id}/qr")
  suspend fun getOrderQr(@Path("id") id: String): Response<OrderQrResponseDto>

  @GET("orders/{id}/barcode")
  suspend fun getOrderBarcode(@Path("id") id: String): Response<OrderBarcodeResponseDto>

  @PATCH("orders/{id}/status")
  suspend fun updateOrderStatus(
    @Path("id") id: String,
    @Body body: StatusUpdateDto,
  ): Response<MongoOrderDto>

  @POST("orders/{id}/accept")
  suspend fun acceptOrder(
    @Path("id") id: String,
    @Body body: Map<String, String> = emptyMap(),
  ): Response<MongoOrderDto>

  @POST("orders/{id}/reject")
  suspend fun rejectOrder(
    @Path("id") id: String,
    @Body body: Map<String, String>,
  ): Response<MongoOrderDto>

  @GET("users/{uid}")
  suspend fun getUser(@Path("uid") uid: String): Response<MongoUserDto>

  @POST("users")
  suspend fun saveUser(@Body user: MongoUserDto): Response<MongoUserDto>

  @PATCH("users/{uid}/fcm")
  suspend fun updateUserFcmToken(
    @Path("uid") uid: String,
    @Body body: FcmTokenDto,
  ): Response<MongoUserDto>

  @GET("owners/{uid}")
  suspend fun getOwner(@Path("uid") uid: String): Response<MongoOwnerDto>

  @POST("owners")
  suspend fun saveOwner(@Body owner: MongoOwnerDto): Response<MongoOwnerDto>

  @PATCH("owners/{uid}/assign")
  suspend fun assignCanteen(
    @Path("uid") uid: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): Response<MongoOwnerDto>

  @GET("inventory/canteen/{canteenId}")
  suspend fun getCanteenInventory(@Path("canteenId") canteenId: String): Response<List<MongoInventoryDto>>

  @PATCH("inventory/{id}")
  suspend fun updateInventoryQuantity(
    @Path("id") id: String,
    @Body body: InventoryQuantityDto,
  ): Response<MongoInventoryDto>

  @POST("inventory")
  suspend fun addInventoryItem(@Body item: MongoInventoryDto): Response<MongoInventoryDto>

  @GET("notifications/user/{userId}")
  suspend fun getUserNotifications(@Path("userId") userId: String): Response<List<MongoNotificationDto>>

  @POST("notifications")
  suspend fun createNotification(@Body notification: MongoNotificationDto): Response<MongoNotificationDto>

  @PATCH("notifications/{id}/read")
  suspend fun markNotificationRead(@Path("id") id: String): Response<MongoNotificationDto>

  @GET("users/{uid}/favorites")
  suspend fun getUserFavorites(@Path("uid") uid: String): Response<UserFavoritesResponseDto>

  @POST("users/{uid}/favorites/{itemId}")
  suspend fun addFavorite(
    @Path("uid") uid: String,
    @Path("itemId") itemId: String,
  ): Response<UserFavoritesResponseDto>

  @DELETE("users/{uid}/favorites/{itemId}")
  suspend fun removeFavorite(
    @Path("uid") uid: String,
    @Path("itemId") itemId: String,
  ): Response<UserFavoritesResponseDto>

  @Streaming
  @POST("ai/stream")
  suspend fun streamAiMessage(
    @Body body: AiChatRequestDto,
    @retrofit2.http.Header("Authorization") authHeader: String? = null,
    @retrofit2.http.Header("X-Student-Id") studentIdHeader: String? = null,
    @retrofit2.http.Header("X-Student-Reg") studentRegHeader: String? = null,
    @retrofit2.http.Header("X-Student-Email") studentEmailHeader: String? = null,
    @retrofit2.http.Header("X-Student-Name") studentNameHeader: String? = null,
  ): Response<ResponseBody>

  @POST("ai/chat")
  suspend fun sendAiMessage(
    @Body body: AiChatRequestDto,
    @retrofit2.http.Header("Authorization") authHeader: String? = null,
    @retrofit2.http.Header("X-Student-Id") studentIdHeader: String? = null,
    @retrofit2.http.Header("X-Student-Reg") studentRegHeader: String? = null,
    @retrofit2.http.Header("X-Student-Email") studentEmailHeader: String? = null,
    @retrofit2.http.Header("X-Student-Name") studentNameHeader: String? = null,
  ): Response<AiChatResponseDto>

  @GET("ai/suggestions")
  suspend fun getAiSuggestions(): Response<AiSuggestionsResponseDto>

  @POST("ai/feedback")
  suspend fun submitAiFeedback(
    @Body body: AiFeedbackRequestDto,
    @retrofit2.http.Header("Authorization") authHeader: String? = null,
  ): Response<AiFeedbackResponseDto>

  @POST("suggestions")
  suspend fun submitProductSuggestion(
    @Body body: CreateSuggestionRequestDto,
    @retrofit2.http.Header("Authorization") authHeader: String? = null,
    @retrofit2.http.Header("X-Student-Id") studentIdHeader: String? = null,
    @retrofit2.http.Header("X-Student-Reg") studentRegHeader: String? = null,
    @retrofit2.http.Header("X-Student-Email") studentEmailHeader: String? = null,
    @retrofit2.http.Header("X-Student-Name") studentNameHeader: String? = null,
  ): Response<CreateSuggestionResponseDto>

  @GET("suggestions/eligibility")
  suspend fun checkSuggestionEligibility(
    @Query("canteenId") canteenId: String,
    @retrofit2.http.Header("Authorization") authHeader: String? = null,
    @retrofit2.http.Header("X-Student-Id") studentIdHeader: String? = null,
    @retrofit2.http.Header("X-Student-Reg") studentRegHeader: String? = null,
    @retrofit2.http.Header("X-Student-Email") studentEmailHeader: String? = null,
  ): Response<SuggestionEligibilityResponseDto>

  @GET("suggestions/owner")
  suspend fun getOwnerSuggestions(
    @retrofit2.http.Header("Authorization") authHeader: String? = null,
    @retrofit2.http.Header("x-owner-token") ownerToken: String? = null,
  ): Response<OwnerSuggestionsResponseDto>

  @PATCH("suggestions/owner/{suggestionId}/status")
  suspend fun updateSuggestionStatus(
    @Path("suggestionId") suggestionId: String,
    @Body body: UpdateSuggestionStatusRequestDto,
    @retrofit2.http.Header("Authorization") authHeader: String? = null,
    @retrofit2.http.Header("x-owner-token") ownerToken: String? = null,
  ): Response<ResponseBody>

  @GET("payments/config")
  suspend fun getPaymentConfig(): Response<PaymentConfigResponseDto>

  @POST("payments/create-order")
  suspend fun createPaymentOrder(
    @Body body: CreatePaymentOrderRequestDto,
    @retrofit2.http.Header("Authorization") authHeader: String? = null,
  ): Response<CreatePaymentOrderResponseDto>

  @POST("payments/verify-payment")
  suspend fun verifyPayment(
    @Body body: VerifyPaymentRequestDto,
    @retrofit2.http.Header("Authorization") authHeader: String? = null,
  ): Response<VerifyPaymentResponseDto>
}

