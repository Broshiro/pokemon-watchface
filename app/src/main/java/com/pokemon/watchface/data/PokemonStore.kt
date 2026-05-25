package com.pokemon.watchface.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "pokemon_state")

/**
 * Single source of truth for:
 *  - which starter the user chose
 *  - today's step count (written by PassiveDataService)
 *  - last known heart rate BPM (written by PassiveDataService)
 */
class PokemonStore(private val context: Context) {

    companion object {
        private val KEY_POKEMON_ID      = intPreferencesKey("selected_pokemon_id")
        private val KEY_STEPS           = intPreferencesKey("daily_steps")
        private val KEY_HEART_RATE      = intPreferencesKey("heart_rate_bpm")
        private val KEY_STEP_DATE       = stringPreferencesKey("step_date")       // "YYYY-MM-DD"
        private val KEY_STEP_BASELINE   = longPreferencesKey("step_baseline")     // cumulative steps at day start
    }

    val selectedPokemonId: Flow<Int> = context.dataStore.data
        .map { it[KEY_POKEMON_ID] ?: STARTERS.first().id }

    val steps: Flow<Int> = context.dataStore.data
        .map { it[KEY_STEPS] ?: 0 }

    val heartRate: Flow<Int> = context.dataStore.data
        .map { it[KEY_HEART_RATE] ?: 0 }

    val stepBaseline: Flow<Long> = context.dataStore.data
        .map { it[KEY_STEP_BASELINE] ?: -1L }

    /** Called from the picker when the user taps a starter. Resets step progress. */
    suspend fun selectPokemon(pokemonId: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_POKEMON_ID] = pokemonId
            prefs[KEY_STEPS] = 0
            prefs[KEY_STEP_DATE] = todayString()
        }
    }

    /** Called by SensorManager listener with raw daily step count. */
    suspend fun updateSteps(steps: Int) {
        context.dataStore.edit { prefs ->
            val today = todayString()
            if (prefs[KEY_STEP_DATE] != today) {
                prefs[KEY_STEP_DATE] = today
                prefs[KEY_STEPS] = 0
            }
            prefs[KEY_STEPS] = steps
        }
    }

    /**
     * Persists the cumulative step baseline (TYPE_STEP_COUNTER value at the
     * start of today). Survives service restarts so daily count doesn't reset.
     */
    suspend fun updateStepBaseline(baseline: Long, date: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_STEP_BASELINE] = baseline
            prefs[KEY_STEP_DATE]     = date
            prefs[KEY_STEPS]         = 0
        }
    }

    /** Called by PassiveDataService when a new HR sample arrives. */
    suspend fun updateHeartRate(bpm: Int) {
        context.dataStore.edit { it[KEY_HEART_RATE] = bpm }
    }

    private fun todayString(): String {
        val cal = java.util.Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }
}
