package com.ethiouber.customer.utils

import com.ethiouber.customer.BuildConfig

object Constants {
    const val BASE_URL = BuildConfig.BASE_URL
    const val WS_BASE_URL = BuildConfig.WS_BASE_URL

    // Order statuses
    const val STATUS_PENDING = "PENDING"
    const val STATUS_FINDING_DRIVER = "FINDING_DRIVER"
    const val STATUS_DRIVER_ASSIGNED = "DRIVER_ASSIGNED"
    const val STATUS_DRIVER_ON_WAY = "DRIVER_ON_WAY"
    const val STATUS_ARRIVED_AT_PICKUP = "ARRIVED_AT_PICKUP"
    const val STATUS_PICKED_UP = "PICKED_UP"
    const val STATUS_IN_TRANSIT = "IN_TRANSIT"
    const val STATUS_ARRIVED_AT_DESTINATION = "ARRIVED_AT_DESTINATION"
    const val STATUS_DELIVERED = "DELIVERED"
    const val STATUS_CANCELLED = "CANCELLED"

    // Parcel sizes
    const val SIZE_SMALL = "SMALL"
    const val SIZE_MEDIUM = "MEDIUM"
    const val SIZE_LARGE = "LARGE"
    const val SIZE_EXTRA_LARGE = "EXTRA_LARGE"

    // Payment methods
    const val PAY_TELEBIRR = "TELEBIRR"
    const val PAY_CBE_BIRR = "CBE_BIRR"
    const val PAY_CHAPA = "CHAPA"
    const val PAY_CASH = "CASH"
    const val PAY_WALLET = "WALLET"

    // Notification channels
    const val CHANNEL_ORDERS = "orders"
    const val CHANNEL_PAYMENTS = "payments"

    // SharedPrefs keys
    const val PREF_ACCESS_TOKEN = "access_token"
    const val PREF_REFRESH_TOKEN = "refresh_token"
    const val PREF_USER_ID = "user_id"
    const val PREF_USER_NAME = "user_name"
    const val PREF_USER_PHONE = "user_phone"
    const val PREF_USER_TYPE = "user_type"
}
