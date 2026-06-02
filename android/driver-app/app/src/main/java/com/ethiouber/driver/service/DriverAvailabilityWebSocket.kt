package com.ethiouber.driver.service

import android.util.Log
import com.ethiouber.driver.BuildConfig
import com.ethiouber.driver.data.local.SessionManager
import com.ethiouber.driver.data.model.OrderData
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriverAvailabilityWebSocket @Inject constructor(
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "DriverWebSocket"
        private const val NORMAL_CLOSURE_STATUS = 1000
    }

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var webSocket: WebSocket? = null
    private var retryCount = 0
    private var shouldReconnect = false

    private val _newOrderEvent = MutableSharedFlow<OrderData>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val newOrderEvent: SharedFlow<OrderData> = _newOrderEvent.asSharedFlow()

    private val _connectionState = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _orderCancelledEvent = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 5
    )
    val orderCancelledEvent: SharedFlow<Int> = _orderCancelledEvent.asSharedFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    fun connect() {
        shouldReconnect = true
        scope.launch {
            val token = sessionManager.accessToken.first()
            if (token.isNullOrEmpty()) {
                Log.w(TAG, "No auth token, skipping WebSocket connection")
                return@launch
            }
            val url = "${BuildConfig.WS_URL}?token=$token"
            val request = Request.Builder().url(url).build()
            webSocket = okHttpClient.newWebSocket(request, createListener())
        }
    }

    fun disconnect() {
        shouldReconnect = false
        retryCount = 0
        webSocket?.close(NORMAL_CLOSURE_STATUS, "Driver went offline")
        webSocket = null
        _connectionState.value = false
    }

    fun sendLocationUpdate(lat: Double, lng: Double, heading: Float) {
        val message = buildJsonMessage {
            addProperty("type", "location_update")
            addProperty("lat", lat)
            addProperty("lng", lng)
            addProperty("heading", heading)
        }
        sendMessage(message)
    }

    fun sendAcceptOrder(orderId: Int) {
        val message = buildJsonMessage {
            addProperty("type", "order_accepted")
            addProperty("order_id", orderId)
        }
        sendMessage(message)
    }

    fun sendRejectOrder(orderId: Int) {
        val message = buildJsonMessage {
            addProperty("type", "order_rejected")
            addProperty("order_id", orderId)
        }
        sendMessage(message)
    }

    private fun sendMessage(json: String) {
        if (_connectionState.value) {
            webSocket?.send(json)
        } else {
            Log.w(TAG, "WebSocket not connected, dropping message: $json")
        }
    }

    private fun buildJsonMessage(block: JsonObject.() -> Unit): String {
        return JsonObject().apply(block).toString()
    }

    private fun createListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            _connectionState.value = true
            retryCount = 0
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "WebSocket message: $text")
            try {
                val json = JsonParser.parseString(text).asJsonObject
                val type = json.get("type")?.asString ?: return

                when (type) {
                    "new_order" -> {
                        val orderJson = json.get("order")
                        if (orderJson != null) {
                            val order = gson.fromJson(orderJson, OrderData::class.java)
                            scope.launch { _newOrderEvent.emit(order) }
                        }
                    }
                    "order_cancelled" -> {
                        val orderId = json.get("order_id")?.asInt ?: return
                        scope.launch { _orderCancelledEvent.emit(orderId) }
                    }
                    "ping" -> {
                        sendMessage(buildJsonMessage { addProperty("type", "pong") })
                    }
                    else -> Log.d(TAG, "Unhandled ws type: $type")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing WebSocket message", e)
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            onMessage(webSocket, bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code $reason")
            webSocket.close(NORMAL_CLOSURE_STATUS, null)
            _connectionState.value = false
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code $reason")
            _connectionState.value = false
            if (shouldReconnect && code != NORMAL_CLOSURE_STATUS) {
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure", t)
            _connectionState.value = false
            if (shouldReconnect) {
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (retryCount >= 5) {
            Log.w(TAG, "Max WebSocket retries reached")
            return
        }
        retryCount++
        val delayMs = minOf(5000L * retryCount, 30_000L)
        Log.d(TAG, "Reconnecting WebSocket in ${delayMs}ms (attempt $retryCount)")
        scope.launch {
            delay(delayMs)
            if (shouldReconnect) connect()
        }
    }
}
