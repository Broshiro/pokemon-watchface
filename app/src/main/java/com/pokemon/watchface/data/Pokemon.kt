package com.pokemon.watchface.data

import android.graphics.Color

// ── Step thresholds (daily steps reset at midnight) ──────────────────────────
const val STEPS_EVO_1    =  5_000   // first evolution
const val STEPS_EVO_2    = 10_000   // final evolution
const val STEPS_MEGA     = 15_000   // mega evolution
const val STEPS_DYNAMAX  = 20_000   // gigantamax / dynamax

// ── Data models ───────────────────────────────────────────────────────────────

data class EvolutionStage(
    val name: String,
    val spriteUrl: String,
    val stageLabel: String,   // shown below the sprite
    val minSteps: Int,
    val bgColor: Int          // ARGB background color (matches type)
)

data class StarterPokemon(
    val id: Int,              // dex ID of the base form (used as DataStore key)
    val displayName: String,  // picker label — always the starter's name
    val stages: List<EvolutionStage>  // exactly 5 entries, index 0..4
) {
    val previewUrl: String get() = stages.first().spriteUrl
}

// ── Sprite URL helpers ────────────────────────────────────────────────────────

// Official-artwork sprites look great on the watch's AMOLED screen.
private fun artwork(id: Int) =
    "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/$id.png"

// Some special forms don't have official-artwork entries; fall back to the
// standard front sprite for those.
private fun sprite(id: Int) =
    "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$id.png"

// ── Pokémon data ──────────────────────────────────────────────────────────────
//
// PokeAPI form IDs for Mega / Gigantamax forms:
//   Mega Venusaur        → 10033
//   Mega Charizard X     → 10034  (blue/dragon typing looks cool)
//   Mega Blastoise       → 10036
//   Gigantamax Venusaur  → 10186  (verify at pokeapi.co/api/v2/pokemon/10186)
//   Gigantamax Charizard → 10187  (verify at pokeapi.co/api/v2/pokemon/10187)
//   Gigantamax Blastoise → 10188  (verify at pokeapi.co/api/v2/pokemon/10188)
//   Alolan Raichu        → 10094
//   Gigantamax Pikachu   → 10190  (verify at pokeapi.co/api/v2/pokemon/10190)
//
// If any form sprite returns 404, swap `artwork(id)` → `sprite(id)` for that
// specific entry or update the ID after checking the PokeAPI endpoint above.

val STARTERS: List<StarterPokemon> = listOf(

    // ── Bulbasaur line ────────────────────────────────────────────────────────
    StarterPokemon(
        id = 1,
        displayName = "Bulbasaur",
        stages = listOf(
            EvolutionStage(
                name = "Bulbasaur", spriteUrl = artwork(1),
                stageLabel = "Starter",
                minSteps = 0,
                bgColor = Color.argb(255, 56, 142, 60)
            ),
            EvolutionStage(
                name = "Ivysaur", spriteUrl = artwork(2),
                stageLabel = "Evolution",
                minSteps = STEPS_EVO_1,
                bgColor = Color.argb(255, 46, 125, 50)
            ),
            EvolutionStage(
                name = "Venusaur", spriteUrl = artwork(3),
                stageLabel = "Final Form",
                minSteps = STEPS_EVO_2,
                bgColor = Color.argb(255, 27, 94, 32)
            ),
            EvolutionStage(
                name = "Mega Venusaur", spriteUrl = artwork(10033),
                stageLabel = "Mega Evolution",
                minSteps = STEPS_MEGA,
                bgColor = Color.argb(255, 20, 73, 25)
            ),
            EvolutionStage(
                name = "Gigantamax Venusaur", spriteUrl = artwork(10186),
                stageLabel = "Dynamax",
                minSteps = STEPS_DYNAMAX,
                bgColor = Color.argb(255, 10, 50, 15)
            )
        )
    ),

    // ── Charmander line ───────────────────────────────────────────────────────
    StarterPokemon(
        id = 4,
        displayName = "Charmander",
        stages = listOf(
            EvolutionStage(
                name = "Charmander", spriteUrl = artwork(4),
                stageLabel = "Starter",
                minSteps = 0,
                bgColor = Color.argb(255, 230, 81, 0)
            ),
            EvolutionStage(
                name = "Charmeleon", spriteUrl = artwork(5),
                stageLabel = "Evolution",
                minSteps = STEPS_EVO_1,
                bgColor = Color.argb(255, 198, 40, 40)
            ),
            EvolutionStage(
                name = "Charizard", spriteUrl = artwork(6),
                stageLabel = "Final Form",
                minSteps = STEPS_EVO_2,
                bgColor = Color.argb(255, 183, 28, 28)
            ),
            EvolutionStage(
                name = "Mega Charizard X", spriteUrl = artwork(10034),
                stageLabel = "Mega Evolution",
                minSteps = STEPS_MEGA,
                bgColor = Color.argb(255, 74, 20, 140)   // Dragon typing = purple
            ),
            EvolutionStage(
                name = "Gigantamax Charizard", spriteUrl = artwork(10187),
                stageLabel = "Dynamax",
                minSteps = STEPS_DYNAMAX,
                bgColor = Color.argb(255, 50, 5, 100)
            )
        )
    ),

    // ── Squirtle line ─────────────────────────────────────────────────────────
    StarterPokemon(
        id = 7,
        displayName = "Squirtle",
        stages = listOf(
            EvolutionStage(
                name = "Squirtle", spriteUrl = artwork(7),
                stageLabel = "Starter",
                minSteps = 0,
                bgColor = Color.argb(255, 21, 101, 192)
            ),
            EvolutionStage(
                name = "Wartortle", spriteUrl = artwork(8),
                stageLabel = "Evolution",
                minSteps = STEPS_EVO_1,
                bgColor = Color.argb(255, 13, 71, 161)
            ),
            EvolutionStage(
                name = "Blastoise", spriteUrl = artwork(9),
                stageLabel = "Final Form",
                minSteps = STEPS_EVO_2,
                bgColor = Color.argb(255, 1, 57, 153)
            ),
            EvolutionStage(
                name = "Mega Blastoise", spriteUrl = artwork(10036),
                stageLabel = "Mega Evolution",
                minSteps = STEPS_MEGA,
                bgColor = Color.argb(255, 0, 40, 120)
            ),
            EvolutionStage(
                name = "Gigantamax Blastoise", spriteUrl = artwork(10188),
                stageLabel = "Dynamax",
                minSteps = STEPS_DYNAMAX,
                bgColor = Color.argb(255, 0, 25, 90)
            )
        )
    ),

    // ── Pikachu line ──────────────────────────────────────────────────────────
    // Starts as Pichu so all 5 stages feel distinct.
    StarterPokemon(
        id = 172,
        displayName = "Pikachu",
        stages = listOf(
            EvolutionStage(
                name = "Pichu", spriteUrl = artwork(172),
                stageLabel = "Starter",
                minSteps = 0,
                bgColor = Color.argb(255, 251, 192, 45)
            ),
            EvolutionStage(
                name = "Pikachu", spriteUrl = artwork(25),
                stageLabel = "Evolution",
                minSteps = STEPS_EVO_1,
                bgColor = Color.argb(255, 245, 167, 0)
            ),
            EvolutionStage(
                name = "Raichu", spriteUrl = artwork(26),
                stageLabel = "Final Form",
                minSteps = STEPS_EVO_2,
                bgColor = Color.argb(255, 230, 120, 0)
            ),
            EvolutionStage(
                name = "Alolan Raichu", spriteUrl = artwork(10094),
                stageLabel = "Mega Form",
                minSteps = STEPS_MEGA,
                bgColor = Color.argb(255, 106, 27, 154)   // Psychic typing = purple
            ),
            EvolutionStage(
                name = "Gigantamax Pikachu", spriteUrl = artwork(10190),
                stageLabel = "Dynamax",
                minSteps = STEPS_DYNAMAX,
                bgColor = Color.argb(255, 180, 120, 0)
            )
        )
    )
)

// ── Query helpers ─────────────────────────────────────────────────────────────

fun StarterPokemon.currentStage(steps: Int): EvolutionStage =
    stages.lastOrNull { steps >= it.minSteps } ?: stages.first()

fun StarterPokemon.nextStage(steps: Int): EvolutionStage? {
    val idx = stages.indexOf(currentStage(steps))
    return stages.getOrNull(idx + 1)
}

/** 0.0 → 1.0 progress toward the next evolution (1.0 if fully maxed). */
fun StarterPokemon.progressToNext(steps: Int): Float {
    val next = nextStage(steps) ?: return 1f
    val current = currentStage(steps)
    return ((steps - current.minSteps).toFloat() / (next.minSteps - current.minSteps))
        .coerceIn(0f, 1f)
}

fun starterById(id: Int): StarterPokemon =
    STARTERS.find { it.id == id } ?: STARTERS.first()
