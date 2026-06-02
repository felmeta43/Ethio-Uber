package com.ethiouber.customer.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

fun View.show() { visibility = View.VISIBLE }
fun View.hide() { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }

fun Fragment.toast(message: String) =
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

fun View.snack(message: String, duration: Int = Snackbar.LENGTH_SHORT) =
    Snackbar.make(this, message, duration).show()

fun Double.toEtb(): String = "%.2f ETB".format(this)

fun String.toStatusLabel(): String = when (this) {
    "PENDING" -> "Pending"
    "FINDING_DRIVER" -> "Finding Driver..."
    "DRIVER_ASSIGNED" -> "Driver Assigned"
    "DRIVER_ON_WAY" -> "Driver On The Way"
    "ARRIVED_AT_PICKUP" -> "Driver Arrived"
    "PICKED_UP" -> "Parcel Picked Up"
    "IN_TRANSIT" -> "In Transit"
    "ARRIVED_AT_DESTINATION" -> "Arrived at Destination"
    "DELIVERED" -> "Delivered"
    "CANCELLED" -> "Cancelled"
    else -> this
}

fun Context.dpToPx(dp: Float): Int = (dp * resources.displayMetrics.density).toInt()
