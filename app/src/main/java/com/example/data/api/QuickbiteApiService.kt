package com.example.data.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface QuickbiteApiService {

  // ── Auth ──────────────────────────────────────────────────────────────────
  @POST("auth/login")
  suspend fun loginUser(@Body body: LoginRequestDto): Response<LoginResponseDto>

  @POST("auth/register")
  suspend fun registerUser(@Body body: RegisterRequestDto): Response<LoginResponseDto>

  @POST("auth/forgot-password")
  suspend fun forgotPassword(@Body body: ForgotPasswordRequestDto): Response<SimpleMessageResponseDto>

  @POST("auth/reset-password")
  suspend fun resetPassword(@Body body: ResetPasswordRequestDto): Response<SimpleMessageResponseDto>

  // ── Owner Auth ────────────────────────────────────────────────────────────
  @POST("owner/login")
  suspend fun ownerLogin(@Body body: OwnerLoginRequestDto): Response<OwnerLoginResponseDto>

  @POST("owner/verify-otp")
  suspend fun ownerVerifyOtp(@Body body: OwnerVerifyOtpRequestDto): Response<OwnerAuthSuccessResponseDto>

  @POST("owner/resend-otp")
  suspend fun ownerResendOtp(@Body body: OwnerResendOtpRequestDto): Response<SimpleMessageResponseDto>

  // ── Canteens ──────────────────────────────────────────────────────────────
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

  // ── Items ─────────────────────────────────────────────────────────────────
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

  @DELETE("items/{id}")
  suspend fun deleteItem(@Path("id") id: String): Response<Map<String, Any>>

  @Multipart
  @POST("items/upload-image")
  suspend fun uploadItemImage(
    @Part image: MultipartBody.Part,
  ): Response<ImageUploadResponseDto>

  // ── Orders ────────────────────────────────────────────────────────────────
  @GET("orders/student/{studentId}")
  suspend fun getStudentOrders(@Path("studentId") studentId: String): Response<List<MongoOrderDto>>

  @GET("orders/canteen/{canteenId}")
  suspend fun getCanteenOrders(@Path("canteenId") canteenId: String): Response<List<MongoOrderDto>>

  @GET("orders/{id}")
  suspend fun getOrder(@Path("id") id: String): Response<MongoOrderDto>

  @POST("orders")
  suspend fun createOrder(@Body order: MongoOrderDto): Response<MongoOrderDto>

  @GET("orders/queue/canteen/{canteenId}")
  suspend fun getCanteenQueue(@Path("canteenId") canteenId: String): Response<CanteenQueueDto>

  @POST("orders/queue/cart")
  suspend fun getCartQueue(@Body body: CartQueueRequestDto): Response<CartQueueResponseDto>

  @GET("orders/{id}/queue")
  suspend fun getOrderQueuePosition(@Path("id") id: String): Response<OrderQueuePositionDto>

  @PATCH("orders/{id}/status")
  suspend fun updateOrderStatus(
    @Path("id") id: String,
    @Body body: StatusUpdateDto,
  ): Response<MongoOrderDto>

  // ── Users ─────────────────────────────────────────────────────────────────
  @GET("users/{uid}")
  suspend fun getUser(@Path("uid") uid: String): Response<MongoUserDto>

  @POST("users")
  suspend fun saveUser(@Body user: MongoUserDto): Response<MongoUserDto>

  @PATCH("users/{uid}/fcm")
  suspend fun updateUserFcmToken(
    @Path("uid") uid: String,
    @Body body: FcmTokenDto,
  ): Response<MongoUserDto>

  // ── Owners ────────────────────────────────────────────────────────────────
  @GET("owners/{uid}")
  suspend fun getOwner(@Path("uid") uid: String): Response<MongoOwnerDto>

  @POST("owners")
  suspend fun saveOwner(@Body owner: MongoOwnerDto): Response<MongoOwnerDto>

  @PATCH("owners/{uid}/assign")
  suspend fun assignCanteen(
    @Path("uid") uid: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): Response<MongoOwnerDto>

  // ── Inventory ─────────────────────────────────────────────────────────────
  @GET("inventory/canteen/{canteenId}")
  suspend fun getCanteenInventory(@Path("canteenId") canteenId: String): Response<List<MongoInventoryDto>>

  @PATCH("inventory/{id}")
  suspend fun updateInventoryQuantity(
    @Path("id") id: String,
    @Body body: InventoryQuantityDto,
  ): Response<MongoInventoryDto>

  @POST("inventory")
  suspend fun addInventoryItem(@Body item: MongoInventoryDto): Response<MongoInventoryDto>

  // ── Notifications ─────────────────────────────────────────────────────────
  @GET("notifications/user/{userId}")
  suspend fun getUserNotifications(@Path("userId") userId: String): Response<List<MongoNotificationDto>>

  @POST("notifications")
  suspend fun createNotification(@Body notification: MongoNotificationDto): Response<MongoNotificationDto>

  @PATCH("notifications/{id}/read")
  suspend fun markNotificationRead(@Path("id") id: String): Response<MongoNotificationDto>
}
