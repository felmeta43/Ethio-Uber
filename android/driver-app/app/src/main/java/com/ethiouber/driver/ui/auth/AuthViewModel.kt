package com.ethiouber.driver.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethiouber.driver.data.model.AuthData
import com.ethiouber.driver.data.model.UserData
import com.ethiouber.driver.data.repository.AuthRepository
import com.ethiouber.driver.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class AuthUiState<out T> {
    object Idle : AuthUiState<Nothing>()
    object Loading : AuthUiState<Nothing>()
    data class Success<T>(val data: T) : AuthUiState<T>()
    data class Error(val message: String) : AuthUiState<Nothing>()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    // Login
    private val _loginState = MutableStateFlow<AuthUiState<AuthData>>(AuthUiState.Idle)
    val loginState: StateFlow<AuthUiState<AuthData>> = _loginState.asStateFlow()

    // OTP
    private val _otpState = MutableStateFlow<AuthUiState<AuthData>>(AuthUiState.Idle)
    val otpState: StateFlow<AuthUiState<AuthData>> = _otpState.asStateFlow()

    private val _resendOtpState = MutableStateFlow<AuthUiState<Unit>>(AuthUiState.Idle)
    val resendOtpState: StateFlow<AuthUiState<Unit>> = _resendOtpState.asStateFlow()

    // Register
    private val _registerState = MutableStateFlow<AuthUiState<UserData>>(AuthUiState.Idle)
    val registerState: StateFlow<AuthUiState<UserData>> = _registerState.asStateFlow()

    // Store phone for OTP screen
    var pendingPhone: String = ""

    fun login(phone: String, password: String) {
        viewModelScope.launch {
            authRepository.login(phone, password).collect { result ->
                _loginState.value = when (result) {
                    is Result.Loading -> AuthUiState.Loading
                    is Result.Success -> {
                        pendingPhone = phone
                        AuthUiState.Success(result.data)
                    }
                    is Result.Error -> AuthUiState.Error(result.message)
                }
            }
        }
    }

    fun verifyOtp(phone: String, otp: String) {
        viewModelScope.launch {
            authRepository.verifyOtp(phone, otp).collect { result ->
                _otpState.value = when (result) {
                    is Result.Loading -> AuthUiState.Loading
                    is Result.Success -> AuthUiState.Success(result.data)
                    is Result.Error -> AuthUiState.Error(result.message)
                }
            }
        }
    }

    fun resendOtp(phone: String) {
        viewModelScope.launch {
            authRepository.resendOtp(phone).collect { result ->
                _resendOtpState.value = when (result) {
                    is Result.Loading -> AuthUiState.Loading
                    is Result.Success -> AuthUiState.Success(Unit)
                    is Result.Error -> AuthUiState.Error(result.message)
                }
            }
        }
    }

    fun registerDriver(
        fullName: String,
        phone: String,
        password: String,
        nationalIdNumber: String,
        licenseNumber: String,
        vehicleType: String,
        vehiclePlate: String,
        vehicleModel: String,
        nationalIdFile: File,
        licenseFile: File,
        vehiclePhotoFile: File
    ) {
        viewModelScope.launch {
            authRepository.registerDriver(
                fullName, phone, password,
                nationalIdNumber, licenseNumber,
                vehicleType, vehiclePlate, vehicleModel,
                nationalIdFile, licenseFile, vehiclePhotoFile
            ).collect { result ->
                _registerState.value = when (result) {
                    is Result.Loading -> AuthUiState.Loading
                    is Result.Success -> {
                        pendingPhone = phone
                        AuthUiState.Success(result.data)
                    }
                    is Result.Error -> AuthUiState.Error(result.message)
                }
            }
        }
    }

    fun resetLoginState() { _loginState.value = AuthUiState.Idle }
    fun resetOtpState() { _otpState.value = AuthUiState.Idle }
    fun resetRegisterState() { _registerState.value = AuthUiState.Idle }
}
