package com.ethiouber.driver.data.model

object OrderStatus {
    const val PENDING = "PENDING"
    const val ACCEPTED = "ACCEPTED"
    const val DRIVER_ON_WAY = "DRIVER_ON_WAY"
    const val ARRIVED_AT_PICKUP = "ARRIVED_AT_PICKUP"
    const val PICKED_UP = "PICKED_UP"
    const val IN_TRANSIT = "IN_TRANSIT"
    const val ARRIVED_AT_DESTINATION = "ARRIVED_AT_DESTINATION"
    const val DELIVERED = "DELIVERED"
    const val CANCELLED = "CANCELLED"
    const val REJECTED = "REJECTED"

    fun getNextStatus(current: String): String? {
        return when (current) {
            DRIVER_ON_WAY -> ARRIVED_AT_PICKUP
            ARRIVED_AT_PICKUP -> PICKED_UP
            PICKED_UP -> IN_TRANSIT
            IN_TRANSIT -> ARRIVED_AT_DESTINATION
            ARRIVED_AT_DESTINATION -> DELIVERED
            else -> null
        }
    }

    fun getButtonLabel(current: String): String {
        return when (current) {
            DRIVER_ON_WAY -> "Arrived at Pickup"
            ARRIVED_AT_PICKUP -> "Parcel Picked Up"
            PICKED_UP -> "In Transit"
            IN_TRANSIT -> "Arrived at Destination"
            ARRIVED_AT_DESTINATION -> "Confirm Delivery (OTP)"
            else -> ""
        }
    }

    fun getStatusDisplayName(status: String): String {
        return when (status) {
            PENDING -> "Pending"
            ACCEPTED -> "Accepted"
            DRIVER_ON_WAY -> "On Way to Pickup"
            ARRIVED_AT_PICKUP -> "At Pickup"
            PICKED_UP -> "Parcel Picked Up"
            IN_TRANSIT -> "In Transit"
            ARRIVED_AT_DESTINATION -> "At Destination"
            DELIVERED -> "Delivered"
            CANCELLED -> "Cancelled"
            REJECTED -> "Rejected"
            else -> status
        }
    }
}

object ParcelSize {
    const val SMALL = "SMALL"
    const val MEDIUM = "MEDIUM"
    const val LARGE = "LARGE"
    const val EXTRA_LARGE = "EXTRA_LARGE"

    fun getDisplayName(size: String): String {
        return when (size) {
            SMALL -> "Small"
            MEDIUM -> "Medium"
            LARGE -> "Large"
            EXTRA_LARGE -> "Extra Large"
            else -> size
        }
    }
}
