package com.ethiouber.driver.data.repository

import com.ethiouber.driver.data.api.ApiService
import com.ethiouber.driver.data.local.SessionManager
import com.ethiouber.driver.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val errors: Map<String, List<String>>? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) {
    fun registerDriver(
        fullName: String,
        phone: String,
        password: String,
        nationalIdNumber: String,
        licenseNumber: String,
        vehicleType: String,
        vehiclePlate: String,
        vehicleModel: String,
        nationalIdFile: File,
        licenseFile: File,
        vehiclePhotoFile: File
    ): Flow<Result<UserData>> = flow {
        emit(Result.Loading)
        try {
            fun String.toPlainBody(): RequestBody =
                toRequestBody("text/plain".toMediaTypeOrNull())

            fun File.toPart(partName: String): MultipartBody.Part {
                val mimeType = when (extension.lowercase()) {
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    else -> "image/*"
                }
                return MultipartBody.Part.createFormData(
                    partName, name,
                    asRequestBody(mimeType.toMediaTypeOrNull())
                )
            }

            val response = apiService.registerDriver(
                fullName = fullName.toPlainBody(),
                phone = phone.toPlainBody(),
                password = password.toPlainBody(),
                nationalIdNumber = nationalIdNumber.toPlainBody(),
                licenseNumber = licenseNumber.toPlainBody(),
                vehicleType = vehicleType.toPlainBody(),
                vehiclePlate = vehiclePlate.toPlainBody(),
                vehicleModel = vehicleModel.toPlainBody(),
                national_id_photo = nationalIdFile.toPart("national_id_photo"),
                driver_license_photo = licenseFile.toPart("driver_license_photo"),
                vehicle_photo = vehiclePhotoFile.toPart("vehicle_photo")
            )
            if (response.isSuccessful && response.body()?.success == true) {
                val userData = response.body()!!.data!!
                emit(Result.Success(userData))
            } else {
                val body = response.body()
                emit(Result.Error(body?.message ?: "Registration failed", body?.errors))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }

    fun login(phone: String, password: String): Flow<Result<AuthData>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.login(LoginRequest(phone, password))
            if (response.isSuccessful && response.body()?.success == true) {
                emit(Result.Success(response.body()!!.data!!))
            } else {
                val body = response.body()
                emit(Result.Error(body?.message ?: "Login failed", body?.errors))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }

    fun verifyOtp(phone: String, otp: String): Flow<Result<AuthData>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.verifyOtp(VerifyOtpRequest(phone, otp))
            if (response.isSuccessful && response.body()?.success == true) {
                val authData = response.body()!!.data!!
                val user = authData.user
                sessionManager.saveAuthData(
                    accessToken = authData.accessToken,
                    refreshToken = authData.refreshToken,
                    userId = user.id,
                    fullName = user.fullName,
                    phone = user.phone,
                    profilePhoto = user.profilePhoto,
                    isVerified = user.isVerified,
                    rating = user.rating,
                    totalDeliveries = user.totalDeliveries,
                    vehicleType = user.vehicleType,
                    vehiclePlate = user.vehiclePlate,
                    vehicleModel = user.vehicleModel
                )
                emit(Result.Success(authData))
            } else {
                val body = response.body()
                emit(Result.Error(body?.message ?: "OTP verification failed", body?.errors))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }

    fun resendOtp(phone: String): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.resendOtp(mapOf("phone" to phone))
            if (response.isSuccessful && response.body()?.success == true) {
                emit(Result.Success(Unit))
            } else {
                emit(Result.Error(response.body()?.message ?: "Failed to resend OTP"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }

    fun updateDriverStatus(isOnline: Boolean, lat: Double?, lng: Double?): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.updateStatus(DriverStatusRequest(isOnline, lat, lng))
            if (response.isSuccessful && response.body()?.success == true) {
                sessionManager.updateOnlineStatus(isOnline)
                emit(Result.Success(Unit))
            } else {
                emit(Result.Error(response.body()?.message ?: "Failed to update status"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }

    fun getProfile(): Flow<Result<UserData>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.getProfile()
            if (response.isSuccessful && response.body()?.success == true) {
                val user = response.body()!!.data!!
                emit(Result.Success(user))
            } else {
                emit(Result.Error(response.body()?.message ?: "Failed to get profile"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }

    fun updateProfile(fullName: String, photoFile: File?): Flow<Result<UserData>> = flow {
        emit(Result.Loading)
        try {
            val fullNameBody = fullName.toRequestBody("text/plain".toMediaTypeOrNull())
            val photoPart = photoFile?.let { file ->
                val mimeType = when (file.extension.lowercase()) {
                    "png" -> "image/png"
                    else -> "image/jpeg"
                }
                MultipartBody.Part.createFormData(
                    "profile_photo", file.name,
                    file.asRequestBody(mimeType.toMediaTypeOrNull())
                )
            }
            val response = apiService.updateProfile(fullNameBody, photoPart)
            if (response.isSuccessful && response.body()?.success == true) {
                val user = response.body()!!.data!!
                sessionManager.updateProfile(user.fullName)
                user.profilePhoto?.let { sessionManager.updateProfilePhoto(it) }
                emit(Result.Success(user))
            } else {
                emit(Result.Error(response.body()?.message ?: "Failed to update profile"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }

    fun logout(): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        try {
            apiService.logout()
            sessionManager.clearSession()
            emit(Result.Success(Unit))
        } catch (e: Exception) {
            sessionManager.clearSession()
            emit(Result.Success(Unit))
        }
    }

    fun registerDevice(token: String): Flow<Result<Unit>> = flow {
        try {
            val response = apiService.registerDevice(DeviceTokenRequest(token))
            if (response.isSuccessful && response.body()?.success == true) {
                emit(Result.Success(Unit))
            } else {
                emit(Result.Error(response.body()?.message ?: "Failed to register device"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Network error"))
        }
    }
}
