package com.pokemon.watchface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.BatteryManager
import android.util.Log
import android.view.SurfaceHolder
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.RenderParameters
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.pokemon.watchface.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.time.ZonedDateTime
import kotlin.math.sin

private const val TAG = "PokemonRenderer"
private const val FRAME_PERIOD_MS = 1000L          // 1 fps in interactive mode
private const val AMBIENT_FRAME_PERIOD_MS = 60_000L // 1 fps ambient (battery saver)

class WatchFaceRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int,
    private val store: PokemonStore
) : Renderer.CanvasRenderer2<WatchFaceRenderer.SharedAssets>(
    surfaceHolder, currentUserStyleRepository, watchState, canvasType,
    FRAME_PERIOD_MS, clearWithBackgroundTintBeforeEach = false
) {

    class SharedAssets : Renderer.SharedAssets {
        override fun onDestroy() {}
    }

    override suspend fun createSharedAssets() = SharedAssets()

    // ── State (updated by coroutines, read on render thread) ──────────────────
    @Volatile private var currentStarterId: Int = STARTERS.first().id
    @Volatile private var currentSteps: Int = 0
    @Volatile private var currentHeartRate: Int = 0

    // ── Sprite loading ────────────────────────────────────────────────────────
    private val imageLoader = ImageLoader(context)
    @Volatile private var spriteBitmap: Bitmap? = null
    @Volatile private var loadedSpriteUrl: String? = null

    // ── Paints ────────────────────────────────────────────────────────────────
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val statPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val stepsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val progressBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val progressFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val ambientSpritePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().also { it.setSaturation(0f) })
    }

    // ── Coroutine scope for collecting DataStore flows ────────────────────────
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    init {
        scope.launch {
            store.selectedPokemonId.collectLatest { id ->
                currentStarterId = id
                // Force sprite reload when starter changes
                loadedSpriteUrl = null
                spriteBitmap = null
                invalidate()
            }
        }
        scope.launch {
            store.steps.collectLatest { s ->
                currentSteps = s
                invalidate()
            }
        }
        scope.launch {
            store.heartRate.collectLatest { hr ->
                currentHeartRate = hr
                invalidate()
            }
        }
    }

    // ── Sprite loading ────────────────────────────────────────────────────────

    private fun loadSpriteIfNeeded(url: String) {
        if (url == loadedSpriteUrl) return
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(Size(300, 300))
            .allowHardware(false)   // software bitmap required for Canvas drawing
            .target(
                onSuccess = { drawable ->
                    spriteBitmap = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
                    loadedSpriteUrl = url
                    invalidate()
                },
                onError = {
                    Log.w(TAG, "Failed to load sprite: $url")
                }
            )
            .build()
        imageLoader.enqueue(request)
    }

    // ── Main render ───────────────────────────────────────────────────────────

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val isAmbient = renderParameters.drawMode == DrawMode.AMBIENT

        val starter = starterById(currentStarterId)
        val stage = starter.currentStage(currentSteps)

        loadSpriteIfNeeded(stage.spriteUrl)

        // Text sizes scale with screen width
        val w = bounds.width().toFloat()
        timePaint.textSize  = w * 0.18f
        datePaint.textSize  = w * 0.07f
        namePaint.textSize  = w * 0.09f
        labelPaint.textSize = w * 0.065f
        statPaint.textSize  = w * 0.075f
        stepsPaint.textSize = w * 0.060f

        // ── Background ────────────────────────────────────────────────────────
        if (isAmbient) {
            canvas.drawColor(Color.BLACK)
        } else {
            drawBackground(canvas, bounds, stage)
        }

        // ── Time ──────────────────────────────────────────────────────────────
        val timeStr = "%02d:%02d".format(zonedDateTime.hour, zonedDateTime.minute)
        canvas.drawText(timeStr, cx, bounds.top + w * 0.22f, timePaint)

        if (!isAmbient) {
            // Date
            val dow  = zonedDateTime.dayOfWeek.name.take(3).lowercase()
                .replaceFirstChar { it.uppercase() }
            val day  = zonedDateTime.dayOfMonth
            val mon  = zonedDateTime.month.name.take(3).lowercase()
                .replaceFirstChar { it.uppercase() }
            canvas.drawText("$dow $mon $day", cx, bounds.top + w * 0.31f, datePaint)
        }

        // ── Pokémon sprite ────────────────────────────────────────────────────
        spriteBitmap?.let { bmp ->
            val spriteRadius = (w * 0.30f).toInt()
            val left   = (cx - spriteRadius).toInt()
            val top    = (cy - spriteRadius - w * 0.04f).toInt()
            val dest   = Rect(left, top, left + spriteRadius * 2, top + spriteRadius * 2)

            if (isAmbient) {
                canvas.drawBitmap(bmp, null, dest, ambientSpritePaint)
            } else {
                // Animated Dynamax glow on the final stage
                if (stage.stageLabel == "Dynamax") {
                    drawDynamaxGlow(canvas, cx, cy - w * 0.04f, spriteRadius.toFloat(), zonedDateTime)
                }
                canvas.drawBitmap(bmp, null, dest, null)
            }
        }

        // ── Pokémon name + stage label ─────────────────────────────────────────
        val nameY = cy + w * 0.34f
        if (isAmbient) {
            namePaint.color = Color.WHITE
            canvas.drawText(stage.name, cx, nameY, namePaint)
        } else {
            namePaint.color = Color.WHITE
            canvas.drawText(stage.name, cx, nameY, namePaint)
            labelPaint.color = stageLabelColor(stage.stageLabel)
            canvas.drawText(stage.stageLabel, cx, nameY + w * 0.08f, labelPaint)
        }

        // ── Stats (heart rate + battery) ───────────────────────────────────────
        if (!isAmbient) {
            val statsY = cy - w * 0.38f
            val battery = getBatteryLevel()

            // HR on the left, battery on the right
            statPaint.color = Color.argb(230, 255, 100, 100)
            canvas.drawText(
                if (currentHeartRate > 0) "♥ ${currentHeartRate}" else "♥ --",
                cx - w * 0.25f, statsY, statPaint
            )
            statPaint.color = batteryColor(battery)
            canvas.drawText("⚡$battery%", cx + w * 0.22f, statsY, statPaint)

            // ── Step progress bar ──────────────────────────────────────────────
            drawProgressBar(canvas, bounds, starter)
        }
    }

    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        // No complications — nothing to highlight
        canvas.drawColor(renderParameters.highlightLayer!!.backgroundTint)
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private fun drawBackground(canvas: Canvas, bounds: Rect, stage: EvolutionStage) {
        // Solid type-themed color with a subtle radial vignette
        canvas.drawColor(stage.bgColor)
        val vignetteShader = RadialGradient(
            bounds.exactCenterX(), bounds.exactCenterY(),
            bounds.width() * 0.55f,
            intArrayOf(Color.TRANSPARENT, Color.argb(120, 0, 0, 0)),
            floatArrayOf(0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = vignetteShader }
        canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(),
            bounds.width() * 0.6f, vignettePaint)
    }

    private fun drawDynamaxGlow(canvas: Canvas, cx: Float, cy: Float, radius: Float, time: ZonedDateTime) {
        // Pulse using the current second as a sine wave driver
        val pulse = (sin(time.second * 0.3) * 0.15 + 0.85).toFloat()
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for (ring in 5 downTo 1) {
            val r = (radius + ring * 14f) * pulse
            val alpha = (50 / ring).coerceIn(5, 50)
            glowPaint.color = Color.argb(alpha, 255, 60, 60)
            canvas.drawCircle(cx, cy, r, glowPaint)
        }
    }

    private fun drawProgressBar(canvas: Canvas, bounds: Rect, starter: StarterPokemon) {
        val w = bounds.width().toFloat()
        val cx = bounds.exactCenterX()
        val progress = starter.progressToNext(currentSteps)
        val next = starter.nextStage(currentSteps)

        val barW = w * 0.60f
        val barH = 10f
        val barLeft = cx - barW / 2f
        val barTop = bounds.bottom - w * 0.175f
        val cornerR = barH / 2f

        // Track
        progressBgPaint.color = Color.argb(80, 255, 255, 255)
        canvas.drawRoundRect(barLeft, barTop, barLeft + barW, barTop + barH,
            cornerR, cornerR, progressBgPaint)

        // Fill
        if (progress > 0f) {
            canvas.drawRoundRect(barLeft, barTop, barLeft + barW * progress, barTop + barH,
                cornerR, cornerR, progressFillPaint)
        }

        // Steps label
        val stepsStr = formatSteps(currentSteps)
        val caption = if (next != null) {
            "$stepsStr / ${formatSteps(next.minSteps)} steps → ${next.name}"
        } else {
            "$stepsStr steps  ★ MAX FORM ★"
        }
        canvas.drawText(caption, cx, barTop + barH + w * 0.065f, stepsPaint)
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun getBatteryLevel(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level  = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale  = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (scale > 0) (level * 100 / scale) else 0
    }

    private fun batteryColor(pct: Int) = when {
        pct >= 50 -> Color.argb(230, 100, 220, 100)
        pct >= 20 -> Color.argb(230, 255, 200, 50)
        else      -> Color.argb(230, 255, 80, 80)
    }

    private fun stageLabelColor(label: String) = when (label) {
        "Mega Evolution" -> Color.argb(255, 255, 215, 0)    // gold
        "Dynamax"        -> Color.argb(255, 255, 80, 80)    // red
        else             -> Color.argb(200, 255, 255, 255)
    }

    private fun formatSteps(steps: Int): String = when {
        steps >= 10_000 -> "%.1fk".format(steps / 1000f)
        steps >= 1_000  -> "%.1fk".format(steps / 1000f)
        else            -> "$steps"
    }

    override fun onDestroy() {
        scope.cancel()
        imageLoader.shutdown()
        super.onDestroy()
    }
}
