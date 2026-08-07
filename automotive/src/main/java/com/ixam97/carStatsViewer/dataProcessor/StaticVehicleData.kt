package com.ixam97.carStatsViewer.dataProcessor

import com.ixam97.carStatsViewer.utils.DistanceUnitEnum

data class StaticVehicleData(
    val batteryCapacity: Float? = DEFAULT_BATTERY_CAPACITY,
    val vehicleMake: String? = null,
    val modelName: String? = null,
    val distanceUnit: DistanceUnitEnum? = null
) {
    fun isInitialized(): Boolean =
        isEssentialInitialized() && isOptionalInitialized()

    fun isEssentialInitialized(): Boolean = true

    fun isOptionalInitialized(): Boolean =
        vehicleMake != null
                && modelName != null
                && distanceUnit != null

    companion object {
        const val DEFAULT_BATTERY_CAPACITY = 78000f // 78 kWh default fallback if VHAL capacity is missing
    }
}
