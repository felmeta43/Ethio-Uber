package com.ethiouber.driver.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

fun View.show() {
    visibility = View.VISIBLE
}

fun View.hide() {
    visibility = View.GONE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.showIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.GONE
}

fun Fragment.toast(message: String) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
}

fun Fragment.toastLong(message: String) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
}

fun View.snack(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    Snackbar.make(this, message, duration).show()
}

fun View.snackLong(message: String) {
    Snackbar.make(this, message, Snackbar.LENGTH_LONG).show()
}

fun Double.toEtb(): String {
    val format = NumberFormat.getNumberInstance(Locale.US)
    format.minimumFractionDigits = 2
    format.maximumFractionDigits = 2
    return "ETB ${format.format(this)}"
}

fun Double.toEtbShort(): String {
    val format = NumberFormat.getNumberInstance(Locale.US)
    format.minimumFractionDigits = 0
    format.maximumFractionDigits = 0
    return "ETB ${format.format(this)}"
}

fun Double.formatKm(): String = String.format("%.1f km", this)

fun String.toDisplayDate(): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val date = inputFormat.parse(this.substringBefore(".").substringBefore("+"))
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        this
    }
}

fun String.toDisplayTime(): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val date = inputFormat.parse(this.substringBefore(".").substringBefore("+"))
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        this
    }
}

fun Context.dpToPx(dp: Float): Int {
    return (dp * resources.displayMetrics.density).toInt()
}

fun Int.formatDuration(): String {
    val minutes = this / 60
    val seconds = this % 60
    return String.format("%02d:%02d", minutes, seconds)
}

fun Double.formatRating(): String = String.format("%.1f", this)
