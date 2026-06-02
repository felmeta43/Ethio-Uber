package com.ethiouber.customer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethiouber.customer.data.model.NearbyDriver
import com.ethiouber.customer.data.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(private val orderRepo: OrderRepository) : ViewModel() {

    private val _nearbyDrivers = MutableStateFlow<List<NearbyDriver>>(emptyList())
    val nearbyDrivers: StateFlow<List<NearbyDriver>> = _nearbyDrivers

    fun loadNearbyDrivers(lat: Double, lng: Double) {
        viewModelScope.launch {
            orderRepo.getNearbyDrivers(lat, lng).onSuccess { drivers ->
                _nearbyDrivers.value = drivers
            }
        }
    }
}
