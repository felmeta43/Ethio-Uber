package com.ethiouber.customer.data.model

import com.google.gson.annotations.SerializedName

data class OrderData(
    @SerializedName("id") val id: Int,
    @SerializedName("order_number") val orderNumber: String,
    @SerializedName("status") val status: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("parcel_size") val parcelSize: String,
    @SerializedName("parcel_description") val parcelDescription: String? = null,
    @SerializedName("is_urgent") val isUrgent: Boolean = false,
    @SerializedName("delivery_fee") val deliveryFee: Double,
    @SerializedName("distance_km") val distanceKm: Double? = null,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("payment_status") val paymentStatus: String,
    @SerializedName("driver") val driver: DriverInfo? = null,
    @SerializedName("delivery_otp") val deliveryOtp: String? = null,
    @SerializedName("estimated_duration_minutes") val estimatedDurationMinutes: Int? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("picked_up_at") val pickedUpAt: String? = null,
    @SerializedName("delivered_at") val deliveredAt: String? = null,
    @SerializedName("cancelled_at") val cancelledAt: String? = null,
    @SerializedName("cancellation_reason") val cancellationReason: String? = null,
    @SerializedName("driver_rating") val driverRating: Int? = null,
    @SerializedName("rating_comment") val ratingComment: String? = null
)

data class DriverInfo(
    @SerializedName("id") val id: Int,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("profile_photo") val profilePhoto: String? = null,
    @SerializedName("rating") val rating: Double,
    @SerializedName("vehicle_type") val vehicleType: String,
    @SerializedName("vehicle_plate") val vehiclePlate: String,
    @SerializedName("vehicle_color") val vehicleColor: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null
)

data class CreateOrderRequest(
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("parcel_size") val parcelSize: String,
    @SerializedName("parcel_description") val parcelDescription: String? = null,
    @SerializedName("is_urgent") val isUrgent: Boolean = false,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("special_instructions") val specialInstructions: String? = null
)

data class EstimateFeeRequest(
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("parcel_size") val parcelSize: String,
    @SerializedName("is_urgent") val isUrgent: Boolean = false
)

data class FeeEstimate(
    @SerializedName("base_fee") val baseFee: Double,
    @SerializedName("distance_fee") val distanceFee: Double,
    @SerializedName("size_fee") val sizeFee: Double,
    @SerializedName("urgency_surcharge") val urgencySurcharge: Double,
    @SerializedName("total_fee") val totalFee: Double,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("estimated_duration_minutes") val estimatedDurationMinutes: Int,
    @SerializedName("currency") val currency: String = "ETB"
)

data class RatingRequest(
    @SerializedName("rating") val rating: Int,
    @SerializedName("comment") val comment: String? = null
)

data class LocationUpdate(
    val orderId: Int,
    val driverLatitude: Double,
    val driverLongitude: Double,
    val orderStatus: String,
    val estimatedArrivalMinutes: Int? = null,
    val message: String? = null
)

enum class ParcelSize(val displayName: String, val apiValue: String) {
    SMALL("Small (up to 2kg)", "SMALL"),
    MEDIUM("Medium (up to 10kg)", "MEDIUM"),
    LARGE("Large (up to 30kg)", "LARGE"),
    EXTRA_LARGE("Extra Large (30kg+)", "EXTRA_LARGE")
}

enum class OrderStatus(val apiValue: String, val displayName: String) {
    PENDING("PENDING", "Pending"),
    ACCEPTED("ACCEPTED", "Accepted"),
    DRIVER_ON_WAY("DRIVER_ON_WAY", "Driver on the Way"),
    PICKED_UP("PICKED_UP", "Picked Up"),
    IN_TRANSIT("IN_TRANSIT", "In Transit"),
    DELIVERED("DELIVERED", "Delivered"),
    CANCELLED("CANCELLED", "Cancelled"),
    FAILED("FAILED", "Failed")
}

enum class PaymentMethod(val apiValue: String, val displayName: String) {
    TELEBIRR("TELEBIRR", "Telebirr"),
    CBE("CBE_BIRR", "CBE Birr"),
    CHAPA("CHAPA", "Chapa"),
    WALLET("WALLET", "Ethio-Uber Wallet"),
    CASH("CASH", "Cash on Delivery")
}
