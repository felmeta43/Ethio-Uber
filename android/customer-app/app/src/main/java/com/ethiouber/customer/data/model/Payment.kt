package com.ethiouber.customer.data.model

import com.google.gson.annotations.SerializedName

data class WalletData(
    @SerializedName("id") val id: Int,
    @SerializedName("balance") val balance: Double,
    @SerializedName("currency") val currency: String = "ETB",
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class TransactionData(
    @SerializedName("id") val id: Int,
    @SerializedName("transaction_type") val transactionType: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("description") val description: String,
    @SerializedName("reference") val reference: String? = null,
    @SerializedName("status") val status: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("balance_after") val balanceAfter: Double? = null
)

data class InitiatePaymentRequest(
    @SerializedName("order_id") val orderId: Int,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("return_url") val returnUrl: String = "ethiouber://payment/callback"
)

data class TopUpRequest(
    @SerializedName("amount") val amount: Double,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("return_url") val returnUrl: String = "ethiouber://payment/topup-callback"
)

data class PaymentInitData(
    @SerializedName("payment_url") val paymentUrl: String? = null,
    @SerializedName("checkout_url") val checkoutUrl: String? = null,
    @SerializedName("reference") val reference: String,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("instructions") val instructions: String? = null
)

enum class TransactionType(val apiValue: String, val displayName: String) {
    CREDIT("CREDIT", "Credit"),
    DEBIT("DEBIT", "Debit"),
    REFUND("REFUND", "Refund"),
    TOP_UP("TOP_UP", "Top Up"),
    PAYMENT("PAYMENT", "Payment")
}
