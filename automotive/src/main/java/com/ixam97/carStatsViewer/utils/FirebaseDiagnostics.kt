package com.ixam97.carStatsViewer.utils

import android.os.Bundle
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import com.ixam97.carStatsViewer.CarStatsViewer
import com.ixam97.carStatsViewer.R
import java.util.concurrent.ConcurrentHashMap

object FirebaseDiagnostics {

    private val lastLoggedMap = ConcurrentHashMap<String, Long>()
    private const val DEFAULT_THROTTLE_MS = 30_000L // Throttle analytics events per screen

    val isFirebaseEnabled: Boolean
        get() = try {
            CarStatsViewer.appContext.getString(R.string.useFirebase) == "true"
        } catch (_: Throwable) {
            false
        }

    fun initFirebase() {
        if (!isFirebaseEnabled) return
        try {
            Firebase.crashlytics.setCrashlyticsCollectionEnabled(true)
            Firebase.crashlytics.sendUnsentReports()
            InAppLogger.i("[FirebaseDiagnostics] Firebase Crashlytics initialized and collection enabled")
        } catch (e: Throwable) {
            InAppLogger.e("[FirebaseDiagnostics] Failed to init Crashlytics: ${e.message}")
        }
    }

    fun setCustomKey(key: String, value: String) {
        if (!isFirebaseEnabled) return
        try {
            val safeKey = key.lowercase().replace(' ', '_').take(40)
            Firebase.crashlytics.setCustomKey(safeKey, value.take(100))
        } catch (_: Throwable) {}
    }

    fun setCustomKey(key: String, value: Double) {
        if (!isFirebaseEnabled) return
        try {
            val safeKey = key.lowercase().replace(' ', '_').take(40)
            Firebase.crashlytics.setCustomKey(safeKey, value)
        } catch (_: Throwable) {}
    }

    fun setCustomKey(key: String, value: Boolean) {
        if (!isFirebaseEnabled) return
        try {
            val safeKey = key.lowercase().replace(' ', '_').take(40)
            Firebase.crashlytics.setCustomKey(safeKey, value)
        } catch (_: Throwable) {}
    }

    fun logDiagnostic(category: String, message: String) {
        InAppLogger.log("[$category] $message", Log.INFO)
        if (!isFirebaseEnabled) return

        try {
            // Write to Crashlytics breadcrumbs buffer (up to 64KB, unlimited log calls)
            Firebase.crashlytics.log("[$category] $message")
        } catch (t: Throwable) {
            Log.e("FirebaseDiagnostics", "Error logging diagnostic: ${t.message}")
        }
    }

    fun logRenderedValues(
        screenName: String,
        renderedStats: Map<String, Any?>,
        throttleMs: Long = DEFAULT_THROTTLE_MS
    ) {
        val summary = renderedStats.entries.joinToString(", ") { "${it.key}=${it.value}" }
        InAppLogger.log("[RENDER][$screenName] $summary", Log.INFO)

        if (!isFirebaseEnabled) return

        try {
            // 1. Write to Crashlytics breadcrumbs log stream
            Firebase.crashlytics.log("[RENDER][$screenName] $summary")

            // 2. Keep only bounded, fixed session-level custom keys (avoid exceeding 64-key Crashlytics limit)
            setCustomKey("last_screen_name", screenName)
            setCustomKey("last_screen_summary", summary.take(100))

            // 3. Send throttled Firebase Analytics event
            val now = System.currentTimeMillis()
            val last = lastLoggedMap[screenName] ?: 0L
            if (now - last > throttleMs) {
                lastLoggedMap[screenName] = now
                val bundle = Bundle().apply {
                    putString("screen_name", screenName)
                    renderedStats.forEach { (k, v) ->
                        val safeParam = k.lowercase().replace(Regex("[^a-z0-9_]"), "_").take(40)
                        when (v) {
                            is Double -> putDouble(safeParam, v)
                            is Float -> putDouble(safeParam, v.toDouble())
                            is Long -> putLong(safeParam, v)
                            is Int -> putInt(safeParam, v)
                            is Boolean -> putBoolean(safeParam, v)
                            else -> putString(safeParam, (v?.toString() ?: "null").take(100))
                        }
                    }
                }
                Firebase.analytics.logEvent("rendered_screen_stats", bundle)
            }
        } catch (t: Throwable) {
            Log.e("FirebaseDiagnostics", "Error logging rendered values: ${t.message}")
        }
    }

    fun logException(category: String, message: String, throwable: Throwable? = null) {
        val fullMsg = "[$category] $message"
        InAppLogger.log(fullMsg, Log.ERROR)
        if (!isFirebaseEnabled) return

        try {
            Firebase.crashlytics.log(fullMsg)
            Firebase.crashlytics.recordException(throwable ?: Exception(fullMsg))
        } catch (t: Throwable) {
            Log.e("FirebaseDiagnostics", "Error recording exception: ${t.message}")
        }
    }
}
