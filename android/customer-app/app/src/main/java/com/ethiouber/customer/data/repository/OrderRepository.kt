package com.ethiouber.customer.data.repository

import com.ethiouber.customer.data.api.ApiService
import com.ethiouber.customer.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun estimateFee(
        pickupLat: Double,
        pickupLng: Double,
        destinationLat: Double,
        destinationLng: Double,
        parcelSize: String,
        isUrgent: Boolean = false
    ): Result<FeeEstimate> = withContext(Dispatchers.IO) {
        try {
            val request = EstimateFeeRequest(
                pickupLatitude = pickupLat,
                pickupLongitude = pickupLng,
                destinationLatitude = destinationLat,
                destinationLongitude = destinationLng,
                parcelSize = parcelSize,
                isUrgent = isUrgent
            )
            val response = apiService.estimateFee(request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to estimate fee"))
                }
            } else {
                Result.failure(Exception("Fee estimation failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createOrder(request: CreateOrderRequest): Result<OrderData> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.createOrder(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        Result.success(body.data)
                    } else {
                        val errorMsg = body?.message
                            ?: body?.errors?.values?.flatten()?.firstOrNull()
                            ?: "Order creation failed"
                        Result.failure(Exception(errorMsg))
                    }
                } else {
                    Result.failure(Exception("Order creation failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getOrders(status: String? = null, page: Int = 1): Result<List<OrderData>> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getOrders(status = status, page = page)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        Result.success(body.data ?: emptyList())
                    } else {
                        Result.failure(Exception(body?.message ?: "Failed to fetch orders"))
                    }
                } else {
                    Result.failure(Exception("Failed to fetch orders: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getOrder(orderId: Int): Result<OrderData> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getOrder(orderId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to fetch order"))
                }
            } else {
                Result.failure(Exception("Failed to fetch order: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelOrder(orderId: Int, reason: String): Result<OrderData> =
        withContext(Dispatchers.IO) {
            try {
                val body = mapOf("reason" to reason)
                val response = apiService.cancelOrder(orderId, body)
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody?.success == true && responseBody.data != null) {
                        Result.success(responseBody.data)
                    } else {
                        Result.failure(Exception(responseBody?.message ?: "Cancellation failed"))
                    }
                } else {
                    Result.failure(Exception("Cancellation failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun rateDriver(orderId: Int, rating: Int, comment: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val request = RatingRequest(rating = rating, comment = comment)
                val response = apiService.rateDriver(orderId, request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception(body?.message ?: "Rating failed"))
                    }
                } else {
                    Result.failure(Exception("Rating failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getNearbyDrivers(lat: Double, lng: Double): Result<List<NearbyDriver>> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getNearbyDrivers(lat = lat, lng = lng)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        Result.success(body.data ?: emptyList())
                    } else {
                        Result.failure(Exception(body?.message ?: "Failed to fetch nearby drivers"))
                    }
                } else {
                    Result.failure(Exception("Failed to fetch nearby drivers: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
