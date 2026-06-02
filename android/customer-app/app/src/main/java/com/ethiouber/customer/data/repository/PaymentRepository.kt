package com.ethiouber.customer.data.repository

import com.ethiouber.customer.data.api.ApiService
import com.ethiouber.customer.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun getWallet(): Result<WalletData> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getWallet()
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to fetch wallet"))
                }
            } else {
                Result.failure(Exception("Failed to fetch wallet: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTransactions(page: Int = 1): Result<List<TransactionData>> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getTransactions(page = page)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        Result.success(body.data ?: emptyList())
                    } else {
                        Result.failure(Exception(body?.message ?: "Failed to fetch transactions"))
                    }
                } else {
                    Result.failure(Exception("Failed to fetch transactions: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun initiatePayment(
        orderId: Int,
        paymentMethod: String
    ): Result<PaymentInitData> = withContext(Dispatchers.IO) {
        try {
            val request = InitiatePaymentRequest(
                orderId = orderId,
                paymentMethod = paymentMethod
            )
            val response = apiService.initiatePayment(request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to initiate payment"))
                }
            } else {
                Result.failure(Exception("Payment initiation failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun topUpWallet(
        amount: Double,
        paymentMethod: String
    ): Result<PaymentInitData> = withContext(Dispatchers.IO) {
        try {
            val request = TopUpRequest(
                amount = amount,
                paymentMethod = paymentMethod
            )
            val response = apiService.topUpWallet(request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Top-up failed"))
                }
            } else {
                Result.failure(Exception("Top-up failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyPayment(reference: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.verifyPayment(reference)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(body?.message ?: "Payment verification failed"))
                }
            } else {
                Result.failure(Exception("Payment verification failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getNotifications(page: Int = 1): Result<List<NotificationData>> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getNotifications(page = page)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        Result.success(body.data ?: emptyList())
                    } else {
                        Result.failure(Exception(body?.message ?: "Failed to fetch notifications"))
                    }
                } else {
                    Result.failure(Exception("Failed to fetch notifications: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getUnreadCount(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getUnreadCount()
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    Result.success(body.data?.get("count") ?: 0)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to fetch unread count"))
                }
            } else {
                Result.failure(Exception("Failed to fetch unread count: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAllRead(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.markAllRead()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark notifications read: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
