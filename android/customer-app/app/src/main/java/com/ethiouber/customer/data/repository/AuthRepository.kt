package com.ethiouber.customer.data.repository

import com.ethiouber.customer.data.api.ApiService
import com.ethiouber.customer.data.local.SessionManager
import com.ethiouber.customer.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) {

    suspend fun register(
        fullName: String,
        phoneNumber: String,
        email: String?,
        password: String,
        passwordConfirm: String
    ): Result<UserData> = withContext(Dispatchers.IO) {
        try {
            val request = RegisterRequest(
                fullName = fullName,
                phoneNumber = phoneNumber,
                email = email,
                password = password,
                passwordConfirm = passwordConfirm
            )
            val response = apiService.registerCustomer(request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    Result.success(body.data)
                } else {
                    val errorMsg = body?.message ?: body?.errors?.values?.flatten()?.firstOrNull()
                        ?: "Registration failed"
                    Result.failure(Exception(errorMsg))
                }
            } else {
                Result.failure(Exception("Registration failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendOtp(phoneNumber: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.sendOtp(SendOtpRequest(phoneNumber))
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    Result.success(body.message ?: "OTP sent successfully")
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to send OTP"))
                }
            } else {
                Result.failure(Exception("Failed to send OTP: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(phoneNumber: String, otpCode: String): Result<AuthData> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.verifyOtp(VerifyOtpRequest(phoneNumber, otpCode))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        saveSession(body.data)
                        Result.success(body.data)
                    } else {
                        Result.failure(Exception(body?.message ?: "OTP verification failed"))
                    }
                } else {
                    Result.failure(Exception("OTP verification failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun login(phoneNumber: String, password: String): Result<AuthData> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.login(LoginRequest(phoneNumber, password))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        saveSession(body.data)
                        Result.success(body.data)
                    } else {
                        Result.failure(Exception(body?.message ?: "Login failed"))
                    }
                } else {
                    Result.failure(Exception("Login failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val refreshToken = sessionManager.getRefreshToken()
            if (!refreshToken.isNullOrBlank()) {
                apiService.logout(RefreshTokenRequest(refreshToken))
            }
            sessionManager.clearSession()
            Result.success(Unit)
        } catch (e: Exception) {
            // Still clear local session even if server logout fails
            sessionManager.clearSession()
            Result.success(Unit)
        }
    }

    suspend fun getProfile(): Result<UserData> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getProfile()
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    sessionManager.saveUser(body.data)
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to fetch profile"))
                }
            } else {
                Result.failure(Exception("Failed to fetch profile: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(fields: Map<String, Any>): Result<UserData> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.updateProfile(fields)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        sessionManager.saveUser(body.data)
                        Result.success(body.data)
                    } else {
                        Result.failure(Exception(body?.message ?: "Update failed"))
                    }
                } else {
                    Result.failure(Exception("Update failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getSavedAddresses(): Result<List<SavedAddress>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getSavedAddresses()
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    Result.success(body.data ?: emptyList())
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to fetch addresses"))
                }
            } else {
                Result.failure(Exception("Failed to fetch addresses: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addSavedAddress(address: SavedAddress): Result<SavedAddress> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.addSavedAddress(address)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        Result.success(body.data)
                    } else {
                        Result.failure(Exception(body?.message ?: "Failed to save address"))
                    }
                } else {
                    Result.failure(Exception("Failed to save address: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun registerDeviceToken(fcmToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.registerDevice(DeviceTokenRequest(token = fcmToken))
            if (response.isSuccessful) {
                sessionManager.saveFcmToken(fcmToken)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to register device: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveSession(authData: AuthData) {
        sessionManager.saveAuthToken(authData.accessToken)
        sessionManager.saveRefreshToken(authData.refreshToken)
        sessionManager.saveUser(authData.user)
    }

    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    fun getCachedUser(): UserData? = sessionManager.getUser()
}
