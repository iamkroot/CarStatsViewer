package com.ixam97.carStatsViewer.carApp

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import com.ixam97.carStatsViewer.R
import com.ixam97.carStatsViewer.database.tripData.ChargingSession
import com.ixam97.carStatsViewer.utils.FirebaseDiagnostics
import com.ixam97.carStatsViewer.utils.StringFormatters
import java.util.Date
import kotlin.math.roundToInt

class ChargingSessionListScreen(
    carContext: CarContext,
    private val chargingSessions: List<ChargingSession>,
    private val chargingLocations: List<String?>
): Screen(carContext), DefaultLifecycleObserver {

    init {
        lifecycle.addObserver(this)
    }

    override fun onGetTemplate(): Template {
        if (chargingSessions.isEmpty()) {
            return MessageTemplate.Builder("No recorded charging sessions").apply {
                setHeader(screenHeader())
            }.build()
        }
        return chargingSessionListTemplate()
    }

    private fun chargingSessionListTemplate() = ListTemplate.Builder().apply {
        setHeader(screenHeader())
        FirebaseDiagnostics.logRenderedValues("ChargingSessionListScreen", mapOf("session_count" to chargingSessions.size))
        setSingleList(ItemList.Builder().apply {
            chargingSessions.forEachIndexed { index, it ->
                addItem(Row.Builder().apply {
                    setTitle(StringFormatters.getDateString(Date(it.start_epoch_time)))
                    setOnClickListener {
                        // screenManager.push(ChargingSessionDetailsScreen(carContext, it, chargingLocations[index]))
                    }
                    val startSoc = it.chargingPoints?.firstOrNull()?.state_of_charge
                    val endSoc = it.chargingPoints?.lastOrNull()?.state_of_charge
                    val socRange = "${((startSoc ?: 0f) * 100).roundToInt()}% → ${((endSoc ?: 0f) * 100).roundToInt()}%"
                    addText("" +
                            "${StringFormatters.getElapsedTimeString(it.chargeTime, true)}, " +
                            "$socRange, " +
                            (chargingLocations.getOrNull(index) ?: "no location available")
                    )
                    setBrowsable(false)
                }.build())
            }
        }.build())
    }.build()

    private fun screenHeader() = Header.Builder().apply {
        setTitle(carContext.getString(R.string.summary_charging_sessions))
        setStartHeaderAction(Action.BACK)
    }.build()
}