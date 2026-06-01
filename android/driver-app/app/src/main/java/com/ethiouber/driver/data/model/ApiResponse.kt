package com.ethiouber.driver.data.model

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: T?,
    @SerializedName("errors") val errors: Map<String, List<String>>?
)

data class AuthData(
    @SerializedName("access") val accessToken: String,
    @SerializedName("refresh") val refreshToken: String,
    @SerializedName("user") val user: UserData
)

data class UserData(
    @SerializedName("id") val id: Int,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("email") val email: String?,
    @SerializedName("profile_photo") val profilePhoto: String?,
    @SerializedName("is_online") val isOnline: Boolean = false,
    @SerializedName("rating") val rating: Double = 0.0,
    @SerializedName("total_deliveries") val totalDeliveries: Int = 0,
    @SerializedName("vehicle_type") val vehicleType: String?,
    @SerializedName("vehicle_plate") val vehiclePlate: String?,
    @SerializedName("vehicle_model") val vehicleModel: String?,
    @SerializedName("is_verified") val isVerified: Boolean = false
)

data class OrderData(
    @SerializedName("id") val id: Int,
    @SerializedName("order_number") val orderNumber: String,
    @SerializedName("status") val status: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("pickup_lat") val pickupLat: Double,
    @SerializedName("pickup_lng") val pickupLng: Double,
    @SerializedName("destination_lat") val destinationLat: Double,
    @SerializedName("destination_lng") val destinationLng: Double,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("total_fee") val totalFee: Double,
    @SerializedName("driver_fee") val driverFee: Double,
    @SerializedName("parcel_size") val parcelSize: String,
    @SerializedName("is_urgent") val isUrgent: Boolean = false,
    @SerializedName("customer_name") val customerName: String,
    @SerializedName("customer_phone") val customerPhone: String,
    @SerializedName("customer_rating") val customerRating: Double = 0.0,
    @SerializedName("notes") val notes: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("accepted_at") val acceptedAt: String?,
    @SerializedName("completed_at") val completedAt: String?,
    @SerializedName("driver_id") val driverId: Int?
)

data class WalletData(
    @SerializedName("id") val id: Int,
    @SerializedName("balance") val balance: Double,
    @SerializedName("currency") val currency: String = "ETB",
    @SerializedName("today_earnings") val todayEarnings: Double = 0.0,
    @SerializedName("week_earnings") val weekEarnings: Double = 0.0,
    @SerializedName("total_deliveries_today") val totalDeliveriesToday: Int = 0
)

data class TransactionData(
    @SerializedName("id") val id: Int,
    @SerializedName("type") val type: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("description") val description: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("status") val status: String,
    @SerializedName("reference") val reference: String?
)

data class NotificationData(
    @SerializedName("id") val id: Int,
    @SerializedName("type") val type: String,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("data") val data: Map<String, String>?,
    @SerializedName("is_read") val isRead: Boolean,
    @SerializedName("created_at") val createdAt: String
)

// Request Models
data class LoginRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("password") val password: String
)

data class VerifyOtpRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("otp") val otp: String
)

data class DriverStatusRequest(
    @SerializedName("is_online") val isOnline: Boolean,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null
)

data class StatusUpdateRequest(
    @SerializedName("status") val status: String
)

data class OtpVerifyRequest(
    @SerializedName("otp") val otp: String
)

data class RatingRequest(
    @SerializedName("rating") val rating: Int,
    @SerializedName("comment") val comment: String? = null
)

data class WithdrawalRequest(
    @SerializedName("amount") val amount: Double,
    @SerializedName("bank_name") val bankName: String,
    @SerializedName("account_number") val accountNumber: String,
    @SerializedName("account_holder_name") val accountHolderName: String
)

data class DeviceTokenRequest(
    @SerializedName("token") val token: String,
    @SerializedName("platform") val platform: String = "android"
)

// WebSocket Messages
data class WsLocationUpdate(
    val type: String = "location_update",
    val lat: Double,
    val lng: Double,
    val heading: Float
)

data class WsOrderAction(
    val type: String,
    val order_id: Int
)

data class WsNewOrderEvent(
    val type: String,
    val order: OrderData
)
