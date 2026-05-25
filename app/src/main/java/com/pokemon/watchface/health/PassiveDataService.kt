package com.pokemon.watchface.health

import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import com.pokemon.watchface.data.PokemonStore
import kotlinx.coroutines.*

/**
 * Background service that receives periodic step and heart-rate data from
 * the Health Services API without holding an active exercise session.
 *
 * Registered in AndroidManifest with action PASSIVE_LISTENER so that
 * Health Services can start it even when the app is not in the foreground.
 */
class PassiveDataService : PassiveListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: PokemonStore

    override fun onCreate() {
        super.onCreate()
        store = PokemonStore(applicationContext)
    }

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        scope.launch {
            // ── Daily step total ──────────────────────────────────────────────
            // STEPS_DAILY is an AggregateDataPoint (cumulative total for today).
            dataPoints.getData(DataType.STEPS_DAILY).lastOrNull()?.let { dp ->
                val steps = dp.value.toInt()
                store.updateSteps(steps)
            }

            // ── Heart rate ────────────────────────────────────────────────────
            // HEART_RATE_BPM is a SampleDataPoint<Double> (instantaneous BPM).
            dataPoints.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { dp ->
                val bpm = dp.value.toInt()
                if (bpm > 0) store.updateHeartRate(bpm)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
