package com.ethiouber.customer.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethiouber.customer.data.local.SessionManager
import com.ethiouber.customer.data.model.UserData
import com.ethiouber.customer.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ProfileUiState {
    object Idle : ProfileUiState()
    object Loading : ProfileUiState()
    data class ProfileLoaded(val user: UserData) : ProfileUiState()
    object LoggedOut : ProfileUiState()
    data class PasswordChanged(val message: String) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val state: StateFlow<ProfileUiState> = _state

    private val _user = MutableStateFlow<UserData?>(null)
    val user: StateFlow<UserData?> = _user

    init {
        loadProfile()
    }

    fun loadProfile() {
        val userData = sessionManager.getUser()
        if (userData != null) {
            _user.value = userData
            _state.value = ProfileUiState.ProfileLoaded(userData)
        } else {
            _state.value = ProfileUiState.Error("Profile not found. Please log in again.")
        }
    }

    fun logout() {
        viewModelScope.launch {
            _state.value = ProfileUiState.Loading
            try {
                // Attempt server-side logout (fire-and-forget; clear session regardless)
                authRepository.logout()
            } catch (_: Exception) {
                // Ignore network errors during logout
            } finally {
                sessionManager.clearSession()
                _state.value = ProfileUiState.LoggedOut
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String) {
        if (newPassword != confirmPassword) {
            _state.value = ProfileUiState.Error("Passwords do not match")
            return
        }
        if (newPassword.length < 6) {
            _state.value = ProfileUiState.Error("Password must be at least 6 characters")
            return
        }
        viewModelScope.launch {
            _state.value = ProfileUiState.Loading
            authRepository.changePassword(currentPassword, newPassword)
                .onSuccess {
                    _state.value = ProfileUiState.PasswordChanged("Password changed successfully")
                }
                .onFailure { error ->
                    _state.value = ProfileUiState.Error(
                        error.message ?: "Failed to change password"
                    )
                }
        }
    }

    fun resetState() {
        _state.value = if (_user.value != null) {
            ProfileUiState.ProfileLoaded(_user.value!!)
        } else {
            ProfileUiState.Idle
        }
    }
}
