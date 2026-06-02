package com.ethiouber.driver.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethiouber.driver.data.local.SessionManager
import com.ethiouber.driver.data.model.UserData
import com.ethiouber.driver.data.model.WalletData
import com.ethiouber.driver.data.repository.AuthRepository
import com.ethiouber.driver.data.repository.EarningsRepository
import com.ethiouber.driver.data.repository.Result
import com.ethiouber.driver.service.DriverAvailabilityWebSocket
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val earningsRepository: EarningsRepository,
    private val sessionManager: SessionManager,
    val webSocket: DriverAvailabilityWebSocket
) : ViewModel() {

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _profile = MutableStateFlow<UserData?>(null)
    val profile: StateFlow<UserData?> = _profile.asStateFlow()

    private val _wallet = MutableStateFlow<WalletData?>(null)
    val wallet: StateFlow<WalletData?> = _wallet.asStateFlow()

    private val _statusUpdateLoading = MutableStateFlow(false)
    val statusUpdateLoading: StateFlow<Boolean> = _statusUpdateLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadProfile()
        loadWallet()
        loadOnlineStatus()
    }

    private fun loadOnlineStatus() {
        viewModelScope.launch {
            sessionManager.isOnline.collect { online ->
                _isOnline.value = online
            }
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            authRepository.getProfile().collect { result ->
                when (result) {
                    is Result.Success -> _profile.value = result.data
                    is Result.Error -> _error.value = result.message
                    is Result.Loading -> {}
                }
            }
        }
    }

    fun loadWallet() {
        viewModelScope.launch {
            earningsRepository.getWallet().collect { result ->
                when (result) {
                    is Result.Success -> _wallet.value = result.data
                    is Result.Error -> {} // silently ignore
                    is Result.Loading -> {}
                }
            }
        }
    }

    fun toggleOnlineStatus(lat: Double?, lng: Double?) {
        val newStatus = !_isOnline.value
        viewModelScope.launch {
            _statusUpdateLoading.value = true
            authRepository.updateDriverStatus(newStatus, lat, lng).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _isOnline.value = newStatus
                        if (newStatus) {
                            webSocket.connect()
                        } else {
                            webSocket.disconnect()
                        }
                    }
                    is Result.Error -> _error.value = result.message
                    is Result.Loading -> {}
                }
                _statusUpdateLoading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }
}
