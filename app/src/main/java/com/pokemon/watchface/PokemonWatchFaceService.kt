package com.pokemon.watchface

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.SurfaceHolder
import androidx.wear.watchface.*
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.pokemon.watchface.data.PokemonStore
import com.pokemon.watchface.health.HealthTracker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

private const val TAG = "PokemonWatchFaceService"

/**
 * Entry point for the Pokémon watch face.
 *
 * Uses SensorManager directly for real-time step + HR updates while the
 * watch face is active. Health Services passive monitoring remains registered
 * so the DataStore stays fresh when the face is not visible.
 */
class PokemonWatchFaceService : WatchFaceService() {

    private lateinit var store: PokemonStore
    private lateinit var sensorManager: SensorManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Step tracking ─────────────────────────────────────────────────────────
    // TYPE_STEP_COUNTER gives cumulative steps since last boot.
    // We convert to daily steps by storing a baseline at the start of each day.
    @Volatile private var stepCumulativeBaseline = -1L
    @Volatile private var stepBaselineDate = ""

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {

                Sensor.TYPE_STEP_COUNTER -> {
                    val cumulative = event.values[0].toLong()
                    val today = todayString()

                    scope.launch {
                        if (stepBaselineDate != today || stepCumulativeBaseline < 0) {
                            // New day or first reading after a reboot/restart.
                            // Try to restore a persisted baseline first.
                            val persistedBaseline = store.stepBaseline.first()
                            val persistedDate     = ""   // date already checked via stepBaselineDate

                            if (persistedBaseline >= 0 && stepBaselineDate == today) {
                                stepCumulativeBaseline = persistedBaseline
                            } else {
                                // No valid persisted baseline — start fresh from now.
                                stepCumulativeBaseline = cumulative
                                stepBaselineDate       = today
                                store.updateStepBaseline(cumulative, today)
                            }
                        }

                        val dailySteps = (cumulative - stepCumulativeBaseline)
                            .toInt()
                            .coerceAtLeast(0)

                        store.updateSteps(dailySteps)
                        Log.d(TAG, "Steps: cumulative=$cumulative baseline=$stepCumulativeBaseline daily=$dailySteps")
                    }
                }

                Sensor.TYPE_HEART_RATE -> {
                    val bpm = event.values[0].toInt()
                    if (bpm > 0) {
                        scope.launch {
                            store.updateHeartRate(bpm)
                            Log.d(TAG, "HR: $bpm bpm")
                        }
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        store = PokemonStore(applicationContext)

        // Restore persisted step baseline so daily count survives service restarts
        stepCumulativeBaseline = store.stepBaseline.first()
        // stepBaselineDate is intentionally left blank here — the sensor listener
        // will repopulate it on the first TYPE_STEP_COUNTER event.

        // ── SensorManager (primary, real-time) ────────────────────────────────
        sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.also { sensor ->
            sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.i(TAG, "Step counter sensor registered")
        } ?: Log.w(TAG, "No step counter sensor available")

        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.also { sensor ->
            sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.i(TAG, "Heart rate sensor registered")
        } ?: Log.w(TAG, "No heart rate sensor available")

        // ── Health Services (background fallback) ─────────────────────────────
        // Keeps DataStore updated when the watch face is not the active face.
        HealthTracker(applicationContext).register()

        // ── Renderer ──────────────────────────────────────────────────────────
        val renderer = WatchFaceRenderer(
            context = applicationContext,
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            currentUserStyleRepository = currentUserStyleRepository,
            canvasType = CanvasType.HARDWARE,
            store = store
        )

        return WatchFace(WatchFaceType.DIGITAL, renderer)
            .setTapListener(object : WatchFace.TapListener {
                override fun onTapEvent(
                    tapType: Int,
                    tapEvent: TapEvent,
                    complicationSlot: ComplicationSlot?
                ) {
                    if (tapType == TapType.UP) {
                        val intent = android.content.Intent(
                            applicationContext,
                            com.pokemon.watchface.ui.PokemonPickerActivity::class.java
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        applicationContext.startActivity(intent)
                    }
                }
            })
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(sensorListener)
        scope.cancel()
        super.onDestroy()
    }

    private fun todayString(): String {
        val c = java.util.Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            c.get(java.util.Calendar.YEAR),
            c.get(java.util.Calendar.MONTH) + 1,
            c.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }
}
