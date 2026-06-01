package com.ethiouber.customer.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents the currently logged-in user stored locally.
 * Extends UserData with any app-specific fields.
 */
data class User(
    val id: Int,
    val fullName: String,
    val phoneNumber: String,
    val email: String?,
    val profilePhoto: String?,
    val rating: Double,
    val totalOrders: Int,
    val walletBalance: Double,
    val isVerified: Boolean
) {
    companion object {
        fun fromUserData(data: UserData): User = User(
            id = data.id,
            fullName = data.fullName,
            phoneNumber = data.phoneNumber,
            email = data.email,
            profilePhoto = data.profilePhoto,
            rating = data.rating,
            totalOrders = data.totalOrders,
            walletBalance = data.walletBalance,
            isVerified = data.isVerified
        )
    }
}

/**
 * UI state sealed class for auth screens.
 */
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val message: String = "") : AuthState()
    data class Error(val message: String) : AuthState()
    data class OtpRequired(val phoneNumber: String) : AuthState()
    data class Authenticated(val authData: AuthData) : AuthState()
}

/**
 * UI state for data-loading screens.
 */
sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val code: Int = 0) : UiState<Nothing>()
}
