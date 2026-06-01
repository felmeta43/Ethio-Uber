package com.ethiouber.customer.data.model

import com.google.gson.annotations.SerializedName

data class NotificationData(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("notification_type") val notificationType: String,
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("order_id") val orderId: Int? = null,
    @SerializedName("data") val data: Map<String, String>? = null,
    @SerializedName("created_at") val createdAt: String
)

enum class NotificationType(val value: String) {
    ORDER_ACCEPTED("ORDER_ACCEPTED"),
    ORDER_CANCELLED("ORDER_CANCELLED"),
    DRIVER_ON_WAY("DRIVER_ON_WAY"),
    DRIVER_ARRIVED("DRIVER_ARRIVED"),
    ORDER_PICKED_UP("ORDER_PICKED_UP"),
    ORDER_IN_TRANSIT("ORDER_IN_TRANSIT"),
    ORDER_DELIVERED("ORDER_DELIVERED"),
    PAYMENT_SUCCESS("PAYMENT_SUCCESS"),
    PAYMENT_FAILED("PAYMENT_FAILED"),
    WALLET_CREDITED("WALLET_CREDITED"),
    PROMOTIONAL("PROMOTIONAL"),
    SYSTEM("SYSTEM")
}
