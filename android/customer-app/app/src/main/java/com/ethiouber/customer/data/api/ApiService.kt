package com.ethiouber.customer.data.api

import com.ethiouber.customer.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ─── Authentication ───────────────────────────────────────────────────────

    @POST("accounts/register/customer/")
    suspend fun registerCustomer(
        @Body body: RegisterRequest
    ): Response<ApiResponse<UserData>>

    @POST("accounts/otp/send/")
    suspend fun sendOtp(
        @Body body: SendOtpRequest
    ): Response<ApiResponse<Any>>

    @POST("accounts/otp/verify/")
    suspend fun verifyOtp(
        @Body body: VerifyOtpRequest
    ): Response<ApiResponse<AuthData>>

    @POST("accounts/login/")
    suspend fun login(
        @Body body: LoginRequest
    ): Response<ApiResponse<AuthData>>

    @POST("accounts/logout/")
    suspend fun logout(
        @Body body: RefreshTokenRequest
    ): Response<ApiResponse<Any>>

    @POST("accounts/token/refresh/")
    suspend fun refreshToken(
        @Body body: RefreshTokenRequest
    ): Response<ApiResponse<AuthData>>

    // ─── Profile ──────────────────────────────────────────────────────────────

    @GET("accounts/profile/")
    suspend fun getProfile(): Response<ApiResponse<UserData>>

    @PATCH("accounts/profile/")
    suspend fun updateProfile(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ApiResponse<UserData>>

    @Multipart
    @PATCH("accounts/profile/photo/")
    suspend fun updateProfilePhoto(
        @Part photo: okhttp3.MultipartBody.Part
    ): Response<ApiResponse<UserData>>

    // ─── Nearby Drivers ───────────────────────────────────────────────────────

    @GET("accounts/drivers/nearby/")
    suspend fun getNearbyDrivers(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius_km") radiusKm: Double = 10.0
    ): Response<ApiResponse<List<NearbyDriver>>>

    // ─── Saved Addresses ──────────────────────────────────────────────────────

    @GET("accounts/saved-addresses/")
    suspend fun getSavedAddresses(): Response<ApiResponse<List<SavedAddress>>>

    @POST("accounts/saved-addresses/")
    suspend fun addSavedAddress(
        @Body body: SavedAddress
    ): Response<ApiResponse<SavedAddress>>

    @DELETE("accounts/saved-addresses/{id}/")
    suspend fun deleteSavedAddress(
        @Path("id") id: Int
    ): Response<ApiResponse<Any>>

    // ─── Orders ───────────────────────────────────────────────────────────────

    @POST("orders/estimate-fee/")
    suspend fun estimateFee(
        @Body body: EstimateFeeRequest
    ): Response<ApiResponse<FeeEstimate>>

    @POST("orders/create/")
    suspend fun createOrder(
        @Body body: CreateOrderRequest
    ): Response<ApiResponse<OrderData>>

    @GET("orders/")
    suspend fun getOrders(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1
    ): Response<ApiResponse<List<OrderData>>>

    @GET("orders/{id}/")
    suspend fun getOrder(
        @Path("id") id: Int
    ): Response<ApiResponse<OrderData>>

    @POST("orders/{id}/cancel/")
    suspend fun cancelOrder(
        @Path("id") id: Int,
        @Body body: Map<String, @JvmSuppressWildcards String>
    ): Response<ApiResponse<OrderData>>

    @POST("orders/{id}/rate-driver/")
    suspend fun rateDriver(
        @Path("id") id: Int,
        @Body body: RatingRequest
    ): Response<ApiResponse<Any>>

    // ─── Payments ─────────────────────────────────────────────────────────────

    @GET("payments/wallet/")
    suspend fun getWallet(): Response<ApiResponse<WalletData>>

    @GET("payments/wallet/transactions/")
    suspend fun getTransactions(
        @Query("page") page: Int = 1
    ): Response<ApiResponse<List<TransactionData>>>

    @POST("payments/initiate/")
    suspend fun initiatePayment(
        @Body body: InitiatePaymentRequest
    ): Response<ApiResponse<PaymentInitData>>

    @POST("payments/wallet/topup/")
    suspend fun topUpWallet(
        @Body body: TopUpRequest
    ): Response<ApiResponse<PaymentInitData>>

    @POST("payments/verify/{reference}/")
    suspend fun verifyPayment(
        @Path("reference") reference: String
    ): Response<ApiResponse<Any>>

    // ─── Notifications ────────────────────────────────────────────────────────

    @GET("notifications/")
    suspend fun getNotifications(
        @Query("page") page: Int = 1
    ): Response<ApiResponse<List<NotificationData>>>

    @GET("notifications/unread-count/")
    suspend fun getUnreadCount(): Response<ApiResponse<Map<String, Int>>>

    @POST("notifications/mark-all-read/")
    suspend fun markAllRead(): Response<ApiResponse<Any>>

    @POST("notifications/{id}/mark-read/")
    suspend fun markRead(
        @Path("id") id: Int
    ): Response<ApiResponse<Any>>

    @POST("notifications/device/")
    suspend fun registerDevice(
        @Body body: DeviceTokenRequest
    ): Response<ApiResponse<Any>>
}
