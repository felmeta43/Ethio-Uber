package com.ethiouber.driver.data.repository

import com.ethiouber.driver.data.api.ApiService
import com.ethiouber.driver.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    private val apiService: ApiService
) {
    fun getOrders(status: String? = null, page: Int = 1): Flow<Result<List<OrderData>>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.getOrders(status, page)
            if (response.isSuccessful && response.body()?.success == true) {
                emit(Result.Success(response.body()!!.data ?: emptyList()))
            } else {
                emit(Result.Error(response.body()?.message ?: "Failed to get orders"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }

    fun getOrder(id: Int): Flow<Result<OrderData>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.getOrder(id)
            if (response.isSuccessful && response.body()?.success == true) {
                emit(Result.Success(response.body()!!.data!!))
            } else {
                emit(Result.Error(response.body()?.message ?: "Failed to get order"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }

    fun acceptOrder(id: Int): Flow<Result<OrderData>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.acceptOrder(id)
            if (response.isSuccessful && response.body()?.success == true) {
                emit(Result.Success(response.body()!!.data!!))
            } else {
                emit(Result.Error(response.body()?.message ?: "Failed to accept order"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }

    fun rejectOrder(id: Int): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.rejectOrder(id)
            if (response.isSuccessful && response.body()?.success == true) {
                emit(Result.Success(Unit))
            } else {
                emit(Result.Error(response.body()?.message ?: "Failed to reject order"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }

    fun updateOrderStatus(id: Int, status: String): Flow<Result<OrderData>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.updateOrderStatus(id, StatusUpdateRequest(status))
            if (response.isSuccessful && response.body()?.success == true) {
                emit(Result.Success(response.body()!!.data!!))
            } else {
                emit(Result.Error(response.body()?.message ?: "Failed to update order status"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }

    fun verifyDeliveryOtp(id: Int, otp: String): Flow<Result<OrderData>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.verifyDeliveryOtp(id, OtpVerifyRequest(otp))
            if (response.isSuccessful && response.body()?.success == true) {
                emit(Result.Success(response.body()!!.data!!))
            } else {
                emit(Result.Error(response.body()?.message ?: "Invalid OTP"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }

    fun rateCustomer(id: Int, rating: Int, comment: String?): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.rateCustomer(id, RatingRequest(rating, comment))
            if (response.isSuccessful && response.body()?.success == true) {
                emit(Result.Success(Unit))
            } else {
                emit(Result.Error(response.body()?.message ?: "Failed to submit rating"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }
}
