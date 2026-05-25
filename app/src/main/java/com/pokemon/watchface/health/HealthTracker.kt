package com.pokemon.watchface.health

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import kotlinx.coroutines.guava.await

private const val TAG = "HealthTracker"

/**
 * Registers and unregisters the passive monitoring listener with Health Services.
 *
 * Call [register] once when the watch face service starts, and [unregister]
 * when it is destroyed. Health Services will wake up [PassiveDataService]
 * with batched data points even when this service is not running.
 */
class HealthTracker(context: Context) {

    private val client: PassiveMonitoringClient =
        HealthServices.getClient(context).passiveMonitoringClient

    private val config = PassiveListenerConfig.builder()
        .setDataTypes(
            setOf(
                DataType.STEPS_DAILY,      // resets at midnight, perfect for daily goals
                DataType.HEART_RATE_BPM    // periodic samples, low battery impact
            )
        )
        .build()

    /** Call from a coroutine (e.g., the WatchFaceService's lifecycle scope). */
    suspend fun register() {
        try {
            client.setPassiveListenerServiceAsync(
                PassiveDataService::class.java,
                config
            ).await()
            Log.i(TAG, "Passive listener registered (steps + HR)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register passive listener", e)
        }
    }

    suspend fun unregister() {
        try {
            client.clearPassiveListenerServiceAsync().await()
            Log.i(TAG, "Passive listener cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear passive listener", e)
        }
    }
}
