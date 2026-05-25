package com.pokemon.watchface

import android.view.SurfaceHolder
import androidx.wear.watchface.*
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.pokemon.watchface.data.PokemonStore
import com.pokemon.watchface.health.HealthTracker
import kotlinx.coroutines.launch

/**
 * Entry point for the Pokémon watch face.
 *
 * Responsibilities:
 *  - Instantiate [WatchFaceRenderer] and hand it to [WatchFace]
 *  - Register / unregister Health Services passive monitoring via [HealthTracker]
 *
 * The WatchFaceService base class provides a coroutine scope tied to the
 * service lifecycle, which we use for health registration.
 */
class PokemonWatchFaceService : WatchFaceService() {

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        val store = PokemonStore(applicationContext)
        val healthTracker = HealthTracker(applicationContext)

        // Register passive health monitoring (runs for the lifetime of the service)
        serviceScope.launch {
            healthTracker.register()
        }

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
                override fun onTapEvent(tapType: Int, tapEvent: TapEvent, complicationSlot: ComplicationSlot?) {
                    // Single tap: open the Pokémon picker if tapped in the center
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
}
