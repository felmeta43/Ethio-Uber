package com.ethiouber.customer.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ethiouber.customer.EthioUberApp
import com.ethiouber.customer.R
import com.ethiouber.customer.ui.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Register token with backend — use WorkManager or coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = getSharedPreferences("ethio_uber_customer_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("fcm_token_pending", token).apply()
            } catch (_: Exception) {}
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"] ?: return
        val notifType = message.data["notification_type"] ?: "GENERAL"
        showNotification(title, body, notifType)
    }

    private fun showNotification(title: String, body: String, type: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = when (type) {
            "ORDER_ACCEPTED", "DRIVER_ARRIVED", "ORDER_DELIVERED", "ORDER_CANCELLED" -> EthioUberApp.CHANNEL_DELIVERY
            "PAYMENT_RECEIVED" -> EthioUberApp.CHANNEL_GENERAL
            else -> EthioUberApp.CHANNEL_GENERAL
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
