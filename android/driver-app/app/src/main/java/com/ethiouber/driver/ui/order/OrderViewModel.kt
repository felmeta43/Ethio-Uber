package com.ethiouber.driver.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethiouber.driver.data.model.OrderData
import com.ethiouber.driver.data.repository.OrderRepository
import com.ethiouber.driver.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OrderUiState<out T> {
    object Idle : OrderUiState<Nothing>()
    object Loading : OrderUiState<Nothing>()
    data class Success<T>(val data: T) : OrderUiState<T>()
    data class Error(val message: String) : OrderUiState<Nothing>()
}

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _orders = MutableStateFlow<List<OrderData>>(emptyList())
    val orders: StateFlow<List<OrderData>> = _orders.asStateFlow()

    private val _activeOrder = MutableStateFlow<OrderData?>(null)
    val activeOrder: StateFlow<OrderData?> = _activeOrder.asStateFlow()

    private val _acceptOrderState = MutableStateFlow<OrderUiState<OrderData>>(OrderUiState.Idle)
    val acceptOrderState: StateFlow<OrderUiState<OrderData>> = _acceptOrderState.asStateFlow()

    private val _rejectOrderState = MutableStateFlow<OrderUiState<Unit>>(OrderUiState.Idle)
    val rejectOrderState: StateFlow<OrderUiState<Unit>> = _rejectOrderState.asStateFlow()

    private val _statusUpdateState = MutableStateFlow<OrderUiState<OrderData>>(OrderUiState.Idle)
    val statusUpdateState: StateFlow<OrderUiState<OrderData>> = _statusUpdateState.asStateFlow()

    private val _otpVerifyState = MutableStateFlow<OrderUiState<OrderData>>(OrderUiState.Idle)
    val otpVerifyState: StateFlow<OrderUiState<OrderData>> = _otpVerifyState.asStateFlow()

    private val _ratingState = MutableStateFlow<OrderUiState<Unit>>(OrderUiState.Idle)
    val ratingState: StateFlow<OrderUiState<Unit>> = _ratingState.asStateFlow()

    private val _ordersLoading = MutableStateFlow(false)
    val ordersLoading: StateFlow<Boolean> = _ordersLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadOrders(status: String? = null) {
        viewModelScope.launch {
            _ordersLoading.value = true
            orderRepository.getOrders(status).collect { result ->
                when (result) {
                    is Result.Loading -> _ordersLoading.value = true
                    is Result.Success -> {
                        _orders.value = result.data
                        _ordersLoading.value = false
                    }
                    is Result.Error -> {
                        _error.value = result.message
                        _ordersLoading.value = false
                    }
                }
            }
        }
    }

    fun setActiveOrder(order: OrderData) {
        _activeOrder.value = order
    }

    fun loadActiveOrder(id: Int) {
        viewModelScope.launch {
            orderRepository.getOrder(id).collect { result ->
                when (result) {
                    is Result.Success -> _activeOrder.value = result.data
                    is Result.Error -> _error.value = result.message
                    is Result.Loading -> {}
                }
            }
        }
    }

    fun acceptOrder(orderId: Int) {
        viewModelScope.launch {
            orderRepository.acceptOrder(orderId).collect { result ->
                _acceptOrderState.value = when (result) {
                    is Result.Loading -> OrderUiState.Loading
                    is Result.Success -> {
                        _activeOrder.value = result.data
                        OrderUiState.Success(result.data)
                    }
                    is Result.Error -> OrderUiState.Error(result.message)
                }
            }
        }
    }

    fun rejectOrder(orderId: Int) {
        viewModelScope.launch {
            orderRepository.rejectOrder(orderId).collect { result ->
                _rejectOrderState.value = when (result) {
                    is Result.Loading -> OrderUiState.Loading
                    is Result.Success -> OrderUiState.Success(Unit)
                    is Result.Error -> OrderUiState.Error(result.message)
                }
            }
        }
    }

    fun updateOrderStatus(orderId: Int, status: String) {
        viewModelScope.launch {
            orderRepository.updateOrderStatus(orderId, status).collect { result ->
                _statusUpdateState.value = when (result) {
                    is Result.Loading -> OrderUiState.Loading
                    is Result.Success -> {
                        _activeOrder.value = result.data
                        OrderUiState.Success(result.data)
                    }
                    is Result.Error -> OrderUiState.Error(result.message)
                }
            }
        }
    }

    fun verifyDeliveryOtp(orderId: Int, otp: String) {
        viewModelScope.launch {
            orderRepository.verifyDeliveryOtp(orderId, otp).collect { result ->
                _otpVerifyState.value = when (result) {
                    is Result.Loading -> OrderUiState.Loading
                    is Result.Success -> {
                        _activeOrder.value = result.data
                        OrderUiState.Success(result.data)
                    }
                    is Result.Error -> OrderUiState.Error(result.message)
                }
            }
        }
    }

    fun rateCustomer(orderId: Int, rating: Int, comment: String?) {
        viewModelScope.launch {
            orderRepository.rateCustomer(orderId, rating, comment).collect { result ->
                _ratingState.value = when (result) {
                    is Result.Loading -> OrderUiState.Loading
                    is Result.Success -> OrderUiState.Success(Unit)
                    is Result.Error -> OrderUiState.Error(result.message)
                }
            }
        }
    }

    fun resetAcceptState() { _acceptOrderState.value = OrderUiState.Idle }
    fun resetRejectState() { _rejectOrderState.value = OrderUiState.Idle }
    fun resetStatusUpdateState() { _statusUpdateState.value = OrderUiState.Idle }
    fun resetOtpVerifyState() { _otpVerifyState.value = OrderUiState.Idle }
    fun resetRatingState() { _ratingState.value = OrderUiState.Idle }
    fun clearError() { _error.value = null }
}
