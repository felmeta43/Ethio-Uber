package com.ethiouber.driver.data.api

import com.ethiouber.driver.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────

    @Multipart
    @POST("accounts/register/driver/")
    suspend fun registerDriver(
        @Part("full_name") fullName: RequestBody,
        @Part("phone") phone: RequestBody,
        @Part("password") password: RequestBody,
        @Part("national_id_number") nationalIdNumber: RequestBody,
        @Part("driver_license_number") licenseNumber: RequestBody,
        @Part("vehicle_type") vehicleType: RequestBody,
        @Part("vehicle_plate") vehiclePlate: RequestBody,
        @Part("vehicle_model") vehicleModel: RequestBody,
        @Part national_id_photo: MultipartBody.Part,
        @Part driver_license_photo: MultipartBody.Part,
        @Part vehicle_photo: MultipartBody.Part
    ): Response<ApiResponse<UserData>>

    @POST("accounts/login/")
    suspend fun login(
        @Body body: LoginRequest
    ): Response<ApiResponse<AuthData>>

    @POST("accounts/otp/verify/")
    suspend fun verifyOtp(
        @Body body: VerifyOtpRequest
    ): Response<ApiResponse<AuthData>>

    @POST("accounts/otp/resend/")
    suspend fun resendOtp(
        @Body body: Map<String, String>
    ): Response<ApiResponse<Any>>

    @PATCH("accounts/driver/status/")
    suspend fun updateStatus(
        @Body body: DriverStatusRequest
    ): Response<ApiResponse<Any>>

    @GET("accounts/profile/")
    suspend fun getProfile(): Response<ApiResponse<UserData>>

    @Multipart
    @PATCH("accounts/profile/")
    suspend fun updateProfile(
        @Part("full_name") fullName: RequestBody?,
        @Part profile_photo: MultipartBody.Part?
    ): Response<ApiResponse<UserData>>

    @POST("accounts/logout/")
    suspend fun logout(): Response<ApiResponse<Any>>

    // ── Orders ────────────────────────────────────────────────────────────────

    @GET("orders/")
    suspend fun getOrders(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1
    ): Response<ApiResponse<List<OrderData>>>

    @GET("orders/{id}/")
    suspend fun getOrder(
        @Path("id") id: Int
    ): Response<ApiResponse<OrderData>>

    @POST("orders/{id}/accept/")
    suspend fun acceptOrder(
        @Path("id") id: Int
    ): Response<ApiResponse<OrderData>>

    @POST("orders/{id}/reject/")
    suspend fun rejectOrder(
        @Path("id") id: Int
    ): Response<ApiResponse<Any>>

    @PATCH("orders/{id}/status/")
    suspend fun updateOrderStatus(
        @Path("id") id: Int,
        @Body body: StatusUpdateRequest
    ): Response<ApiResponse<OrderData>>

    @POST("orders/{id}/verify-otp/")
    suspend fun verifyDeliveryOtp(
        @Path("id") id: Int,
        @Body body: OtpVerifyRequest
    ): Response<ApiResponse<OrderData>>

    @POST("orders/{id}/rate-customer/")
    suspend fun rateCustomer(
        @Path("id") id: Int,
        @Body body: RatingRequest
    ): Response<ApiResponse<Any>>

    // ── Payments / Wallet ─────────────────────────────────────────────────────

    @GET("payments/wallet/")
    suspend fun getWallet(): Response<ApiResponse<WalletData>>

    @GET("payments/wallet/transactions/")
    suspend fun getTransactions(
        @Query("page") page: Int = 1
    ): Response<ApiResponse<List<TransactionData>>>

    @POST("payments/withdrawals/")
    suspend fun requestWithdrawal(
        @Body body: WithdrawalRequest
    ): Response<ApiResponse<Any>>

    // ── Notifications ─────────────────────────────────────────────────────────

    @POST("notifications/device/")
    suspend fun registerDevice(
        @Body body: DeviceTokenRequest
    ): Response<ApiResponse<Any>>

    @GET("notifications/")
    suspend fun getNotifications(): Response<ApiResponse<List<NotificationData>>>

    @PATCH("notifications/{id}/read/")
    suspend fun markNotificationRead(
        @Path("id") id: Int
    ): Response<ApiResponse<Any>>
}
