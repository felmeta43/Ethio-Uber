package com.ethiouber.driver.data.model

object VehicleType {
    const val MOTORCYCLE = "MOTORCYCLE"
    const val CAR = "CAR"
    const val BICYCLE = "BICYCLE"
    const val TRUCK = "TRUCK"

    fun getAllTypes(): List<String> = listOf(MOTORCYCLE, CAR, BICYCLE, TRUCK)

    fun getDisplayName(type: String): String {
        return when (type) {
            MOTORCYCLE -> "Motorcycle"
            CAR -> "Car"
            BICYCLE -> "Bicycle"
            TRUCK -> "Truck"
            else -> type
        }
    }
}
