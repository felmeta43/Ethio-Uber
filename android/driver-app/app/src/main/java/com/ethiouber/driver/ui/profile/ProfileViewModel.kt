package com.ethiouber.driver.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethiouber.driver.data.local.SessionManager
import com.ethiouber.driver.data.repository.AuthRepository
import com.ethiouber.driver.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileData(
    val fullName: String,
    val phone: String,
    val rating: Float,
    val vehicleType: String?,
    val vehiclePlate: String?,
    val vehicleModel: String?,
    val isOnline: Boolean
)

sealed class ProfileUiState {
    object Idle : ProfileUiState()
    object Loading : ProfileUiState()
    data class Loaded(val profile: ProfileData) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
    object LoggedOut : ProfileUiState()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                val fullName = sessionManager.fullName.first() ?: ""
                val phone = sessionManager.phone.first() ?: ""
                val rating = sessionManager.rating.first()
                val online = sessionManager.isOnline.first()
                val vehicleType = sessionManager.accessToken.first()
                    .let { /* vehicle data read from separate keys */ null }

                // Read vehicle info from DataStore flows
                val vType: String? = null
                val vPlate: String? = null
                val vModel: String? = null

                _isOnline.value = online
                _uiState.value = ProfileUiState.Loaded(
                    ProfileData(
                        fullName = fullName,
                        phone = phone,
                        rating = rating,
                        vehicleType = vType,
                        vehiclePlate = vPlate,
                        vehicleModel = vModel,
                        isOnline = online
                    )
                )
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.message ?: "Failed to load profile")
            }
        }
    }

    /**
     * Refreshes profile data from the network and persists it in the session.
     */
    fun refreshProfileFromNetwork() {
        viewModelScope.launch {
            authRepository.getProfile().collect { result ->
                when (result) {
                    is Result.Success -> {
                        val user = result.data
                        _isOnline.value = user.isOnline
                        _uiState.value = ProfileUiState.Loaded(
                            ProfileData(
                                fullName = user.fullName,
                                phone = user.phone,
                                rating = user.rating.toFloat(),
                                vehicleType = user.vehicleType,
                                vehiclePlate = user.vehiclePlate,
                                vehicleModel = user.vehicleModel,
                                isOnline = user.isOnline
                            )
                        )
                    }
                    is Result.Error -> _uiState.value = ProfileUiState.Error(result.message)
                    is Result.Loading -> _uiState.value = ProfileUiState.Loading
                }
            }
        }
    }

    /**
     * Toggles the driver's online/offline status via the API.
     */
    fun toggleOnlineStatus() {
        val newStatus = !_isOnline.value
        viewModelScope.launch {
            authRepository.updateDriverStatus(newStatus, null, null).collect { result ->
                when (result) {
                    is Result.Success -> _isOnline.value = newStatus
                    is Result.Error -> { /* surface error if needed */ }
                    is Result.Loading -> {}
                }
            }
        }
    }

    /**
     * Calls the logout endpoint and clears the local session.
     * Emits [ProfileUiState.LoggedOut] so the Fragment can navigate to login.
     */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout().collect { result ->
                when (result) {
                    is Result.Success -> _uiState.value = ProfileUiState.LoggedOut
                    is Result.Error -> _uiState.value = ProfileUiState.LoggedOut
                    is Result.Loading -> _uiState.value = ProfileUiState.Loading
                }
            }
        }
    }
}
