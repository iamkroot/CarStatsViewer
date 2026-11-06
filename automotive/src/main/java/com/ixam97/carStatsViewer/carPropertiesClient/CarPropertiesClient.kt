package com.ixam97.carStatsViewer.carPropertiesClient

import android.car.Car
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.hardware.property.PropertyNotAvailableErrorCode
import android.car.hardware.property.PropertyNotAvailableException
import android.content.Context
import android.util.Log
import com.ixam97.carStatsViewer.utils.FirebaseDiagnostics
import com.ixam97.carStatsViewer.utils.InAppLogger

class CarPropertiesClient(
    context: Context,
    private val propertiesProcessor: (propertyId: Int) -> Unit,
    private val carPropertiesData: CarPropertiesData
) {

    // private var debugTemperatureAttempt = 0
    // private var debugDistanceUnitAttempt = 0

    private val _registeredProperties = mutableListOf<Int>()
    val registeredProperties: List<Int>
        get() = _registeredProperties

    private val carPropertyListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(carPropertyValue: CarPropertyValue<*>) {
            if (carPropertyValue.status != CarPropertyValue.STATUS_AVAILABLE) {
                val warnMsg = "[CarPropertiesClient.carPropertyListener] Property ${CarProperties.getNameById(carPropertyValue.propertyId)} (${carPropertyValue.propertyId}) is currently not available. Status: ${carPropertyValue.status}."
                InAppLogger.logWithFirebase(warnMsg, Log.WARN)
                return
            }
            // With OTA 5.0.10, Polestar 2 reports power in a very slow interval, while speed is
            // still updated quickly. Manually reading the power property delivers up-to-date
            // values, though. So this is a workaround to have accurate data.
            if (carPropertyValue.propertyId == CarProperties.PERF_VEHICLE_SPEED) {
                updateProperty(CarProperties.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE)
            }
            if (carPropertyValue.propertyId == CarProperties.EV_BATTERY_LEVEL) {
                FirebaseDiagnostics.logDiagnostic(
                    "HAL_BATTERY_EVENT",
                    "EV_BATTERY_LEVEL onChangeEvent: val=${carPropertyValue.value} (${carPropertyValue.value?.javaClass?.simpleName}), status=${carPropertyValue.status}"
                )
            }
            carPropertiesData.update(carPropertyValue, doLog = false)
            propertiesProcessor(carPropertyValue.propertyId)
        }
        override fun onErrorEvent(propertyId: Int, zone: Int) {
            val errorMsg = "[CarPropertiesClient.carPropertyListener] Received error car property event for ${CarProperties.getNameById(propertyId)} ($propertyId), zone=$zone"
            InAppLogger.logWithFirebase(errorMsg, Log.ERROR)
        }
    }

    private var car = Car.createCar(context)
    private var carPropertyManager = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager

    fun <T>getProperty(propertyId: Int, areaId: Int = 0): T? {

        try {
            val propertyValue = carPropertyManager.getProperty<T>(propertyId, areaId)
            // propertyValue.propertyStatus crashes Polestar 2 emulator
            if (propertyValue.status != CarPropertyValue.STATUS_AVAILABLE /* || (propertyId == CarProperties.DISTANCE_DISPLAY_UNITS && emulatorMode && debugDistanceUnitAttempt < 5) */) {
                InAppLogger.w("[CarPropertiesClient.getProperty] Property ${CarProperties.getNameById(propertyId)} (${propertyId}) is currently not available. Status: ${propertyValue.status}.")
                // if (emulatorMode && propertyId == CarProperties.DISTANCE_DISPLAY_UNITS)
                //     debugDistanceUnitAttempt++
                return null
            }
            return propertyValue.value
        } catch (e: Exception) {
            try {
                if (e is PropertyNotAvailableException) {
                    val errorMsg = "[CarPropertiesClient.getProperty] Property is not available: ${PropertyNotAvailableErrorCode.toString(e.detailedErrorCode)}.\n\r${e.stackTraceToString()}"
                    InAppLogger.logWithFirebase(errorMsg, Log.ERROR)
                } else { throw e }
            } catch (ee: Throwable) {
                InAppLogger.e(ee.stackTraceToString())
                val errorMsg = "[CarPropertiesClient.getProperty] Failed to get Property ${CarProperties.getNameById(propertyId)} ($propertyId).\n\r${e.stackTraceToString()}"
                InAppLogger.logWithFirebase(errorMsg, Log.ERROR)
            }
        }
        return null
    }

    fun updateProperty(propertyId: Int) {
        // if (emulatorMode && propertyId == CarProperties.ENV_OUTSIDE_TEMPERATURE && debugTemperatureAttempt < 2) return
        try {
            val propVal: CarPropertyValue<*>? = try {
                when (propertyId) {
                    CarProperties.PERF_VEHICLE_SPEED,
                    CarProperties.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE,
                    CarProperties.EV_BATTERY_LEVEL,
                    CarProperties.ENV_OUTSIDE_TEMPERATURE,
                    CarProperties.EV_BATTERY_AVERAGE_TEMPERATURE -> carPropertyManager.getProperty<Float>(propertyId, 0)
                    CarProperties.GEAR_SELECTION,
                    CarProperties.IGNITION_STATE,
                    CarProperties.DISTANCE_DISPLAY_UNITS -> carPropertyManager.getProperty<Int>(propertyId, 0)
                    CarProperties.EV_CHARGE_PORT_CONNECTED -> carPropertyManager.getProperty<Boolean>(propertyId, 0)
                    CarProperties.INFO_MAKE,
                    CarProperties.INFO_MODEL -> carPropertyManager.getProperty<String>(propertyId, 0)
                    else -> carPropertyManager.getProperty<Any>(propertyId, 0)
                }
            } catch (_: Exception) {
                try {
                    carPropertyManager.getProperty<Any>(propertyId, 0)
                } catch (_: Exception) { null }
            }

            if (propVal != null) {
                carPropertiesData.update(propVal, allowInvalidTimestamps = true, doLog = false)
                val safePropName = CarProperties.getNameById(propertyId).lowercase().replace(' ', '_')
                FirebaseDiagnostics.setCustomKey("prop_$safePropName", propVal.value?.toString() ?: "null")
            } else {
                val warnMsg = "[CarPropertiesClient] Failed to get property ${CarProperties.getNameById(propertyId)} ($propertyId) in updateProperty"
                FirebaseDiagnostics.logDiagnostic("PROP_POLL_FAIL", warnMsg)
            }
        } catch (e: Exception) {
            val warnMsg = "[CarPropertiesClient] Failed to update ${CarProperties.getNameById(propertyId)} ($propertyId): ${e.message}"
            InAppLogger.w(warnMsg)
            FirebaseDiagnostics.logDiagnostic("PROP_UPDATE_EXCEPTION", warnMsg)
            return
        }
        propertiesProcessor(propertyId)
    }

    /**
     * Check availability of a Car Property
     */
    fun checkPropertyAvailability(propertyId: Int, areaId: Int): Boolean {
        return carPropertyManager.isPropertyAvailable(propertyId, areaId)
    }

    fun getIntProperty(propertyId: Int, areaId: Int = 0) = getProperty<Int>(propertyId, areaId)
    fun getFloatProperty(propertyId: Int, areaId: Int = 0) = getProperty<Float>(propertyId, areaId)
    fun getBooleanProperty(propertyId: Int, areaId: Int = 0) = getProperty<Boolean>(propertyId, areaId)
    fun getStringProperty(propertyId: Int, areaId: Int = 0) = getProperty<String>(propertyId, areaId)

    /**
     * Try to register Car Property. Checks if Property is available beforehand.
     */
    fun getCarPropertyUpdates(propertyId: Int): Boolean {

        if (_registeredProperties.contains(propertyId)) return true

        if (!checkPropertyAvailability(propertyId, 0)) {
            val warnMsg = "[CarPropertiesClient] Property ${CarProperties.getNameById(propertyId)} ($propertyId) is NOT available via checkPropertyAvailability"
            InAppLogger.logWithFirebase(warnMsg, Log.WARN)
            return false
        }

        val rate = (CarProperties.sensorRateMap[propertyId]) ?: 0f
        try {
            if (carPropertyManager.registerCallback(carPropertyListener, propertyId, rate)) {
                _registeredProperties.add(propertyId)
                val infoMsg = "[CarPropertiesClient] ${CarProperties.getNameById(propertyId)} ($propertyId) registered successfully at rate $rate"
                InAppLogger.logWithFirebase(infoMsg, Log.INFO)
                return true
            }
        } catch (e: Exception) {
            val warnMsg = "[CarPropertiesClient] Registration with rate $rate threw exception for ${CarProperties.getNameById(propertyId)} ($propertyId): ${e.message}"
            InAppLogger.w(warnMsg)
            if (rate != 0f) {
                try {
                    InAppLogger.w("[CarPropertiesClient] Attempting fallback registration with rate 0f for ${CarProperties.getNameById(propertyId)}")
                    if (carPropertyManager.registerCallback(carPropertyListener, propertyId, 0f)) {
                        _registeredProperties.add(propertyId)
                        val infoMsg = "[CarPropertiesClient] ${CarProperties.getNameById(propertyId)} ($propertyId) registered successfully with fallback rate 0f"
                        InAppLogger.logWithFirebase(infoMsg, Log.INFO)
                        return true
                    }
                } catch (fallbackEx: Exception) {
                    val errMsg = "[CarPropertiesClient] Fallback registration also failed for ${CarProperties.getNameById(propertyId)}: ${fallbackEx.message}"
                    InAppLogger.e(errMsg)
                }
            }
        }

        val errorMsg = "[CarPropertiesClient] Failed to register callback for ${CarProperties.getNameById(propertyId)} ($propertyId)"
        InAppLogger.logWithFirebase(errorMsg, Log.ERROR)
        return false
    }

    fun disconnect() {
        car.disconnect()
    }
}