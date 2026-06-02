package com.ethiouber.customer.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethiouber.customer.data.model.AuthData
import com.ethiouber.customer.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val data: AuthData? = null, val message: String = "") : AuthState()
    data class Error(val message: String) : AuthState()
    object OtpSent : AuthState()
    object Registered : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(private val repo: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state

    // Holds phone after registration so OTP screen can use it
    var pendingPhone: String = ""

    fun register(fullName: String, phone: String, email: String?, password: String, passwordConfirm: String) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            val result = repo.register(fullName, phone, email, password, passwordConfirm)
            result.fold(
                onSuccess = {
                    pendingPhone = phone
                    _state.value = AuthState.Registered
                },
                onFailure = { _state.value = AuthState.Error(it.message ?: "Registration failed") }
            )
        }
    }

    fun sendOtp(phone: String) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            val result = repo.sendOtp(phone)
            result.fold(
                onSuccess = {
                    pendingPhone = phone
                    _state.value = AuthState.OtpSent
                },
                onFailure = { _state.value = AuthState.Error(it.message ?: "Failed to send OTP") }
            )
        }
    }

    fun verifyOtp(otp: String) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            val result = repo.verifyOtp(pendingPhone, otp)
            result.fold(
                onSuccess = { _state.value = AuthState.Success(it, "Verified!") },
                onFailure = { _state.value = AuthState.Error(it.message ?: "Invalid OTP") }
            )
        }
    }

    fun login(phone: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            val result = repo.login(phone, password)
            result.fold(
                onSuccess = { _state.value = AuthState.Success(it, "Welcome back!") },
                onFailure = { _state.value = AuthState.Error(it.message ?: "Login failed") }
            )
        }
    }

    fun resetState() { _state.value = AuthState.Idle }
}
