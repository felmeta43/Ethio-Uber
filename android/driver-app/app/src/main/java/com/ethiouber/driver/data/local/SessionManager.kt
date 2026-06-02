package com.ethiouber.driver.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "driver_session")

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER_ID = intPreferencesKey("user_id")
        private val KEY_FULL_NAME = stringPreferencesKey("full_name")
        private val KEY_PHONE = stringPreferencesKey("phone")
        private val KEY_PROFILE_PHOTO = stringPreferencesKey("profile_photo")
        private val KEY_IS_VERIFIED = booleanPreferencesKey("is_verified")
        private val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")
        private val KEY_IS_ONLINE = booleanPreferencesKey("is_online")
        private val KEY_RATING = floatPreferencesKey("rating")
        private val KEY_TOTAL_DELIVERIES = intPreferencesKey("total_deliveries")
        private val KEY_VEHICLE_TYPE = stringPreferencesKey("vehicle_type")
        private val KEY_VEHICLE_PLATE = stringPreferencesKey("vehicle_plate")
        private val KEY_VEHICLE_MODEL = stringPreferencesKey("vehicle_model")
    }

    val accessToken: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_ACCESS_TOKEN] }

    val refreshToken: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_REFRESH_TOKEN] }

    val userId: Flow<Int?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_USER_ID] }

    val fullName: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_FULL_NAME] }

    val phone: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_PHONE] }

    val profilePhoto: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_PROFILE_PHOTO] }

    val isVerified: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_IS_VERIFIED] ?: false }

    val isOnline: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_IS_ONLINE] ?: false }

    val fcmToken: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_FCM_TOKEN] }

    val rating: Flow<Float> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_RATING] ?: 0f }

    val totalDeliveries: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_TOTAL_DELIVERIES] ?: 0 }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { !it[KEY_ACCESS_TOKEN].isNullOrEmpty() }

    suspend fun saveAuthData(
        accessToken: String,
        refreshToken: String,
        userId: Int,
        fullName: String,
        phone: String,
        profilePhoto: String?,
        isVerified: Boolean,
        rating: Double,
        totalDeliveries: Int,
        vehicleType: String?,
        vehiclePlate: String?,
        vehicleModel: String?
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_USER_ID] = userId
            prefs[KEY_FULL_NAME] = fullName
            prefs[KEY_PHONE] = phone
            profilePhoto?.let { prefs[KEY_PROFILE_PHOTO] = it }
            prefs[KEY_IS_VERIFIED] = isVerified
            prefs[KEY_RATING] = rating.toFloat()
            prefs[KEY_TOTAL_DELIVERIES] = totalDeliveries
            vehicleType?.let { prefs[KEY_VEHICLE_TYPE] = it }
            vehiclePlate?.let { prefs[KEY_VEHICLE_PLATE] = it }
            vehicleModel?.let { prefs[KEY_VEHICLE_MODEL] = it }
        }
    }

    suspend fun updateOnlineStatus(isOnline: Boolean) {
        context.dataStore.edit { it[KEY_IS_ONLINE] = isOnline }
    }

    suspend fun saveFcmToken(token: String) {
        context.dataStore.edit { it[KEY_FCM_TOKEN] = token }
    }

    suspend fun updateProfilePhoto(url: String) {
        context.dataStore.edit { it[KEY_PROFILE_PHOTO] = url }
    }

    suspend fun updateProfile(fullName: String) {
        context.dataStore.edit { it[KEY_FULL_NAME] = fullName }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}
