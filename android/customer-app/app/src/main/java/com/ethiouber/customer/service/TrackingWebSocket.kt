package com.ethiouber.customer.service

import com.ethiouber.customer.data.local.SessionManager
import com.ethiouber.customer.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class LocationUpdate(val lat: Double, val lng: Double, val heading: Double?, val driverName: String?, val orderStatus: String?)

@Singleton
class TrackingWebSocket @Inject constructor(
    private val sessionManager: SessionManager,
    private val okHttpClient: OkHttpClient,
) {
    private var webSocket: WebSocket? = null

    private val _locationUpdates = MutableStateFlow<LocationUpdate?>(null)
    val locationUpdates: StateFlow<LocationUpdate?> = _locationUpdates

    private val _orderStatus = MutableStateFlow<String?>(null)
    val orderStatus: StateFlow<String?> = _orderStatus

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    fun connect(orderId: Int) {
        val token = sessionManager.getAccessToken() ?: return
        val url = "${Constants.WS_BASE_URL}tracking/$orderId/?token=$token"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                _isConnected.value = true
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    when (json.optString("type")) {
                        "location_update" -> {
                            _locationUpdates.value = LocationUpdate(
                                lat = json.getDouble("lat"),
                                lng = json.getDouble("lng"),
                                heading = json.optDouble("heading").takeIf { !it.isNaN() },
                                driverName = json.optString("driver_name").ifEmpty { null },
                                orderStatus = json.optString("order_status").ifEmpty { null },
                            )
                        }
                        "order_status_update" -> {
                            _orderStatus.value = json.optString("status")
                        }
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _isConnected.value = false
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User left tracking")
        webSocket = null
        _isConnected.value = false
        _locationUpdates.value = null
    }
}
