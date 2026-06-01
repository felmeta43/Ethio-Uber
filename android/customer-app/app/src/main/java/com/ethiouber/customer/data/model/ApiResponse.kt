package com.ethiouber.customer.data.model

import com.google.gson.annotations.SerializedName

/**
 * Generic API response wrapper that matches the backend envelope format.
 */
data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: T? = null,
    @SerializedName("errors") val errors: Map<String, List<String>>? = null,
    @SerializedName("count") val count: Int? = null,
    @SerializedName("next") val next: String? = null,
    @SerializedName("previous") val previous: String? = null
)

/**
 * Auth responses
 */
data class AuthData(
    @SerializedName("access") val accessToken: String,
    @SerializedName("refresh") val refreshToken: String,
    @SerializedName("user") val user: UserData
)

data class RegisterRequest(
    @SerializedName("full_name") val fullName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("password") val password: String,
    @SerializedName("password_confirm") val passwordConfirm: String
)

data class SendOtpRequest(
    @SerializedName("phone_number") val phoneNumber: String
)

data class VerifyOtpRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("otp_code") val otpCode: String
)

data class LoginRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("password") val password: String
)

data class RefreshTokenRequest(
    @SerializedName("refresh") val refresh: String
)

data class DeviceTokenRequest(
    @SerializedName("token") val token: String,
    @SerializedName("platform") val platform: String = "android"
)

/**
 * User / Profile data
 */
data class UserData(
    @SerializedName("id") val id: Int,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("profile_photo") val profilePhoto: String? = null,
    @SerializedName("rating") val rating: Double = 0.0,
    @SerializedName("total_orders") val totalOrders: Int = 0,
    @SerializedName("wallet_balance") val walletBalance: Double = 0.0,
    @SerializedName("is_verified") val isVerified: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null
)

data class SavedAddress(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("label") val label: String,
    @SerializedName("address") val address: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

data class NearbyDriver(
    @SerializedName("id") val id: Int,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("vehicle_type") val vehicleType: String,
    @SerializedName("vehicle_plate") val vehiclePlate: String,
    @SerializedName("rating") val rating: Double,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("distance_km") val distanceKm: Double
)
