package com.ethiouber.driver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ethiouber.driver.R
import com.ethiouber.driver.ui.MainActivity
import com.ethiouber.driver.utils.Constants
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

    companion object {
        private const val TAG = "LocationTrackingService"
        const val ACTION_START = "com.ethiouber.driver.ACTION_START_TRACKING"
        const val ACTION_STOP = "com.ethiouber.driver.ACTION_STOP_TRACKING"

        private val _currentLocation = MutableStateFlow<LocationData?>(null)
        val currentLocation: StateFlow<LocationData?> = _currentLocation.asStateFlow()

        data class LocationData(
            val lat: Double,
            val lng: Double,
            val heading: Float,
            val accuracy: Float
        )
    }

    @Inject
    lateinit var webSocket: DriverAvailabilityWebSocket

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannels()
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "Starting location tracking")
                startForeground(Constants.NOTIF_ID_LOCATION, buildNotification())
                startLocationUpdates()
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stopping location tracking")
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val locationData = LocationData(
                    lat = location.latitude,
                    lng = location.longitude,
                    heading = location.bearing,
                    accuracy = location.accuracy
                )
                _currentLocation.value = locationData
                serviceScope.launch {
                    webSocket.sendLocationUpdate(
                        location.latitude,
                        location.longitude,
                        location.bearing
                    )
                }
            }
        }
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            Constants.LOCATION_UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(Constants.LOCATION_FASTEST_INTERVAL_MS)
            setMinUpdateDistanceMeters(Constants.LOCATION_DISPLACEMENT_METERS)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied", e)
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.CHANNEL_ID_LOCATION)
            .setContentTitle("Ethio-Uber Driver")
            .setContentText("Tracking your location")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val locationChannel = NotificationChannel(
                Constants.CHANNEL_ID_LOCATION,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used to track driver location in the background"
                setShowBadge(false)
            }

            val ordersChannel = NotificationChannel(
                Constants.CHANNEL_ID_ORDERS,
                "New Orders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new delivery orders"
                enableVibration(true)
                enableLights(true)
            }

            val generalChannel = NotificationChannel(
                Constants.CHANNEL_ID_GENERAL,
                "General",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications"
            }

            notificationManager.createNotificationChannels(
                listOf(locationChannel, ordersChannel, generalChannel)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }
}
