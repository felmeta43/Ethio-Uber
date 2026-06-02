package com.ethiouber.driver.utils

object Constants {
    // Notification channels
    const val CHANNEL_ID_LOCATION = "ethiouber_location_channel"
    const val CHANNEL_ID_ORDERS = "ethiouber_orders_channel"
    const val CHANNEL_ID_GENERAL = "ethiouber_driver_channel"

    // Notification IDs
    const val NOTIF_ID_LOCATION = 1001
    const val NOTIF_ID_NEW_ORDER = 1002
    const val NOTIF_ID_GENERAL = 1003

    // Location
    const val LOCATION_UPDATE_INTERVAL_MS = 5000L
    const val LOCATION_FASTEST_INTERVAL_MS = 2000L
    const val LOCATION_DISPLACEMENT_METERS = 5f

    // WebSocket
    const val WS_RECONNECT_DELAY_MS = 5000L
    const val WS_MAX_RETRIES = 5
    const val WS_PING_INTERVAL_MS = 30_000L

    // Order timer
    const val NEW_ORDER_TIMEOUT_SECONDS = 30

    // Intent extras / actions
    const val ACTION_NEW_ORDER = "com.ethiouber.driver.ACTION_NEW_ORDER"
    const val ACTION_ORDER_CANCELLED = "com.ethiouber.driver.ACTION_ORDER_CANCELLED"
    const val ACTION_PAYMENT_RECEIVED = "com.ethiouber.driver.ACTION_PAYMENT_RECEIVED"
    const val EXTRA_ORDER_ID = "order_id"
    const val EXTRA_AMOUNT = "amount"

    // SharedFlow extra capacity
    const val SHARED_FLOW_REPLAY = 0
    const val SHARED_FLOW_EXTRA_BUFFER = 1

    // Date formats
    const val DATE_FORMAT_DISPLAY = "dd MMM yyyy"
    const val DATE_FORMAT_TIME = "HH:mm"
    const val DATE_FORMAT_FULL = "yyyy-MM-dd'T'HH:mm:ss"

    // Map zoom levels
    const val MAP_ZOOM_DEFAULT = 14f
    const val MAP_ZOOM_ROUTE = 12f

    // Request codes
    const val REQUEST_CAMERA = 101
    const val REQUEST_GALLERY = 102
    const val REQUEST_LOCATION_PERMISSION = 103
    const val REQUEST_NOTIFICATION_PERMISSION = 104
    const val REQUEST_BACKGROUND_LOCATION = 105
}
