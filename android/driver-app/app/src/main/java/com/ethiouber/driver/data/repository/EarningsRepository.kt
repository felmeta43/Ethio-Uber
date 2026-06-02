package com.ethiouber.driver.data.repository

import com.ethiouber.driver.data.api.ApiService
import com.ethiouber.driver.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EarningsRepository @Inject constructor(
    private val apiService: ApiService
) {
    fun getWallet(): Flow<Result<WalletData>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.getWallet()
            if (response.isSuccessful && response.body()?.success == true) {
                emit(Result.Success(response.body()!!.data!!))
            } else {
                emit(Result.Error(response.body()?.message ?: "Failed to get wallet"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }

    fun getTransactions(page: Int = 1): Flow<Result<List<TransactionData>>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.getTransactions(page)
            if (response.isSuccessful && response.body()?.success == true) {
                emit(Result.Success(response.body()!!.data ?: emptyList()))
            } else {
                emit(Result.Error(response.body()?.message ?: "Failed to get transactions"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }

    fun requestWithdrawal(
        amount: Double,
        bankName: String,
        accountNumber: String,
        accountHolderName: String
    ): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.requestWithdrawal(
                WithdrawalRequest(amount, bankName, accountNumber, accountHolderName)
            )
            if (response.isSuccessful && response.body()?.success == true) {
                emit(Result.Success(Unit))
            } else {
                emit(Result.Error(response.body()?.message ?: "Withdrawal request failed"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }

    fun getNotifications(): Flow<Result<List<NotificationData>>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.getNotifications()
            if (response.isSuccessful && response.body()?.success == true) {
                emit(Result.Success(response.body()!!.data ?: emptyList()))
            } else {
                emit(Result.Error(response.body()?.message ?: "Failed to get notifications"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }
}
