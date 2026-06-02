package com.ethiouber.customer.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethiouber.customer.data.model.FeeEstimate
import com.ethiouber.customer.data.model.OrderData
import com.ethiouber.customer.data.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OrderUiState {
    object Idle : OrderUiState()
    object Loading : OrderUiState()
    data class Success(val order: OrderData? = null, val message: String = "") : OrderUiState()
    data class FeeEstimated(val estimate: FeeEstimate) : OrderUiState()
    data class OrderList(val orders: List<OrderData>) : OrderUiState()
    data class Error(val message: String) : OrderUiState()
}

@HiltViewModel
class OrderViewModel @Inject constructor(private val repo: OrderRepository) : ViewModel() {

    private val _state = MutableStateFlow<OrderUiState>(OrderUiState.Idle)
    val state: StateFlow<OrderUiState> = _state

    private val _activeOrder = MutableStateFlow<OrderData?>(null)
    val activeOrder: StateFlow<OrderData?> = _activeOrder

    fun estimateFee(
        pickupLat: Double, pickupLng: Double,
        destLat: Double, destLng: Double,
        parcelSize: String, isUrgent: Boolean,
    ) {
        viewModelScope.launch {
            _state.value = OrderUiState.Loading
            repo.estimateFee(pickupLat, pickupLng, destLat, destLng, parcelSize, isUrgent)
                .onSuccess { _state.value = OrderUiState.FeeEstimated(it) }
                .onFailure { _state.value = OrderUiState.Error(it.message ?: "Could not estimate fee") }
        }
    }

    fun createOrder(
        pickupAddress: String, pickupLat: Double, pickupLng: Double,
        destAddress: String, destLat: Double, destLng: Double,
        description: String, parcelSize: String, isUrgent: Boolean, paymentMethod: String,
    ) {
        viewModelScope.launch {
            _state.value = OrderUiState.Loading
            val request = com.ethiouber.customer.data.model.CreateOrderRequest(
                pickupAddress = pickupAddress,
                pickupLatitude = pickupLat,
                pickupLongitude = pickupLng,
                destinationAddress = destAddress,
                destinationLatitude = destLat,
                destinationLongitude = destLng,
                parcelDescription = description,
                parcelSize = parcelSize,
                isUrgent = isUrgent,
                paymentMethod = paymentMethod
            )
            repo.createOrder(request)
                .onSuccess { _state.value = OrderUiState.Success(it, "Order placed!") }
                .onFailure { _state.value = OrderUiState.Error(it.message ?: "Failed to create order") }
        }
    }

    fun loadOrders() {
        viewModelScope.launch {
            _state.value = OrderUiState.Loading
            repo.getOrders()
                .onSuccess { _state.value = OrderUiState.OrderList(it) }
                .onFailure { _state.value = OrderUiState.Error(it.message ?: "Failed to load orders") }
        }
    }

    fun loadOrder(id: Int) {
        viewModelScope.launch {
            repo.getOrder(id)
                .onSuccess { _activeOrder.value = it }
        }
    }

    fun cancelOrder(id: Int, reason: String) {
        viewModelScope.launch {
            _state.value = OrderUiState.Loading
            repo.cancelOrder(id, reason)
                .onSuccess { _state.value = OrderUiState.Success(message = "Order cancelled") }
                .onFailure { _state.value = OrderUiState.Error(it.message ?: "Cancel failed") }
        }
    }

    fun rateDriver(orderId: Int, rating: Int, feedback: String) {
        viewModelScope.launch {
            repo.rateDriver(orderId, rating, feedback)
                .onSuccess { _state.value = OrderUiState.Success(message = "Rating submitted. Thank you!") }
        }
    }

    fun resetState() { _state.value = OrderUiState.Idle }
}
