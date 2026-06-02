package com.ethiouber.customer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EthioUberApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val deliveryChannel = NotificationChannel(
                CHANNEL_DELIVERY,
                "Delivery Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for order and delivery status updates"
                enableVibration(true)
                enableLights(true)
            }

            val promoChannel = NotificationChannel(
                CHANNEL_PROMO,
                "Promotions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Promotional offers and announcements"
            }

            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL,
                "General",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications"
            }

            notificationManager.createNotificationChannels(
                listOf(deliveryChannel, promoChannel, generalChannel)
            )
        }
    }

    companion object {
        const val CHANNEL_DELIVERY = "ethio_uber_channel"
        const val CHANNEL_PROMO = "ethio_uber_promo"
        const val CHANNEL_GENERAL = "ethio_uber_general"
    }
}
