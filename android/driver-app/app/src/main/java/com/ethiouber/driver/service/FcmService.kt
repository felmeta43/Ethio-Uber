package com.ethiouber.driver.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ethiouber.driver.R
import com.ethiouber.driver.data.local.SessionManager
import com.ethiouber.driver.data.repository.AuthRepository
import com.ethiouber.driver.ui.MainActivity
import com.ethiouber.driver.utils.Constants
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FcmService"
    }

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var authRepository: AuthRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        serviceScope.launch {
            sessionManager.saveFcmToken(token)
            val isLoggedIn = sessionManager.isLoggedIn.first()
            if (isLoggedIn) {
                authRepository.registerDevice(token).collect {}
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM received: ${message.data}, notification: ${message.notification?.title}")

        val type = message.data["type"] ?: message.notification?.title ?: "GENERAL"
        val title = message.notification?.title ?: message.data["title"] ?: "Ethio-Uber Driver"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val orderId = message.data["order_id"]?.toIntOrNull()
        val amount = message.data["amount"]

        when (type) {
            "NEW_ORDER" -> showNewOrderNotification(title, body, orderId)
            "ORDER_CANCELLED" -> showOrderCancelledNotification(title, body, orderId)
            "PAYMENT_RECEIVED" -> showPaymentNotification(title, body, amount)
            else -> showGeneralNotification(title, body)
        }
    }

    private fun showNewOrderNotification(title: String, body: String, orderId: Int?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Constants.ACTION_NEW_ORDER
            orderId?.let { putExtra(Constants.EXTRA_ORDER_ID, it) }
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, orderId ?: 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_ID_ORDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.NOTIF_ID_NEW_ORDER, notification)
    }

    private fun showOrderCancelledNotification(title: String, body: String, orderId: Int?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Constants.ACTION_ORDER_CANCELLED
            orderId?.let { putExtra(Constants.EXTRA_ORDER_ID, it) }
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, (orderId ?: 0) + 100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_ID_GENERAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.NOTIF_ID_GENERAL, notification)
    }

    private fun showPaymentNotification(title: String, body: String, amount: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Constants.ACTION_PAYMENT_RECEIVED
            amount?.let { putExtra(Constants.EXTRA_AMOUNT, it) }
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 200, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val displayBody = if (amount != null) "$body\nAmount: ETB $amount" else body

        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_ID_GENERAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(displayBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayBody))
            .setAutoCancel(true)
            .setSound(soundUri)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.NOTIF_ID_GENERAL + 1, notification)
    }

    private fun showGeneralNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 300, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_ID_GENERAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.NOTIF_ID_GENERAL + 2, notification)
    }
}
