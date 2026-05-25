package com.pokemon.watchface.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import coil.compose.AsyncImage
import com.pokemon.watchface.data.PokemonStore
import com.pokemon.watchface.data.StarterPokemon
import com.pokemon.watchface.data.STARTERS
import kotlinx.coroutines.launch

/**
 * On-watch starter picker.
 *
 * Launched from the watch face on tap, or as the app's launcher entry.
 * Requests health permissions on first run, then shows the four starters
 * in a scrollable Wear Compose list.
 */
class PokemonPickerActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied — tracker handles graceful degradation */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestHealthPermissionsIfNeeded()

        setContent {
            PokemonPickerScreen(
                onSelected = { starter ->
                    lifecycleScope.launch {
                        PokemonStore(applicationContext).selectPokemon(starter.id)
                        finish()
                    }
                }
            )
        }
    }

    private fun requestHealthPermissionsIfNeeded() {
        val needed = listOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BODY_SENSORS
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
fun PokemonPickerScreen(onSelected: (StarterPokemon) -> Unit) {
    val listState = rememberScalingLazyListState()

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        ScalingLazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "Choose Your Starter",
                    style = MaterialTheme.typography.title2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(STARTERS) { starter ->
                StarterCard(starter = starter, onClick = { onSelected(starter) })
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Text(
                    text = "Tap to select\nYour Pokémon evolves\nwith your daily steps!",
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                    color = Color(0xAAFFFFFF),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun StarterCard(starter: StarterPokemon, onClick: () -> Unit) {
    val bgColor = Color(starter.stages.first().bgColor.toLong() or 0xFF000000)

    Chip(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        onClick = onClick,
        colors = ChipDefaults.chipColors(backgroundColor = bgColor),
        label = {
            Column {
                Text(
                    text = starter.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "5 stages · up to 20k steps",
                    fontSize = 11.sp,
                    color = Color(0xCCFFFFFF)
                )
            }
        },
        icon = {
            AsyncImage(
                model = starter.previewUrl,
                contentDescription = starter.displayName,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
            )
        }
    )
}
