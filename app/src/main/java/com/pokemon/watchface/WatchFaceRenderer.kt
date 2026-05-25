package com.pokemon.watchface

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.BatteryManager
import android.util.Log
import android.view.SurfaceHolder
import androidx.wear.watchface.DrawMode
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
private const val FRAME_PERIOD_MS = 1000L

// ── Layout constants (all fractions of screen width) ─────────────────────────
//
//  Circular 450×450 watch face — zones:
//
//      [    12:34    ]  ← timeY   = top + 0.20w
//      [  Mon May 25 ]  ← dateY   = top + 0.28w
//
//  [STEPS] [Pokemon ] [♥ 72 ]
//  [ ████] [Sprite  ] [bpm  ]
//  [ 7.2k] [        ] [⚡85%]
//
//      [ Charmeleon  ]  ← nameY   = cy + 0.25w
//      [  Evolution  ]  ← labelY  = cy + 0.32w
//
//  Left column  x = cx − 0.33w  (inside circle at mid-height)
//  Right column x = cx + 0.33w  (inside circle at mid-height)
//  Sprite center  = (cx, cy),  radius = 0.20w  →  top = cy−0.20w (clears dateY)

class WatchFaceRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int,
    private val store: PokemonStore
) : Renderer.CanvasRenderer2<WatchFaceRenderer.SharedAssets>(
    surfaceHolder, currentUserStyleRepository, watchState, canvasType,
    FRAME_PERIOD_MS, clearWithBackgroundTintBeforeRenderingHighlightLayer = false
) {

    class SharedAssets : Renderer.SharedAssets {
        override fun onDestroy() {}
    }

    override suspend fun createSharedAssets() = SharedAssets()

    // ── State ─────────────────────────────────────────────────────────────────
    @Volatile private var currentStarterId: Int = STARTERS.first().id
    @Volatile private var currentSteps: Int = 0
    @Volatile private var currentHeartRate: Int = 0

    // ── Sprite ────────────────────────────────────────────────────────────────
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
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val progressBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(80, 255, 255, 255)
    }
    private val progressFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val ambientSpritePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().also { it.setSaturation(0f) })
    }

    // ── Coroutine scope ───────────────────────────────────────────────────────
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    init {
        scope.launch {
            store.selectedPokemonId.collectLatest { id ->
                currentStarterId = id
                loadedSpriteUrl = null
                spriteBitmap = null
                invalidate()
            }
        }
        scope.launch { store.steps.collectLatest      { currentSteps     = it; invalidate() } }
        scope.launch { store.heartRate.collectLatest  { currentHeartRate = it; invalidate() } }
    }

    // ── Sprite loading ────────────────────────────────────────────────────────

    private fun loadSpriteIfNeeded(url: String) {
        if (url == loadedSpriteUrl) return
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(Size(300, 300))
            .allowHardware(false)
            .target(
                onSuccess = { drawable ->
                    spriteBitmap = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
                    loadedSpriteUrl = url
                    invalidate()
                },
                onError = { Log.w(TAG, "Sprite load failed: $url") }
            )
            .build()
        imageLoader.enqueue(request)
    }

    // ── Render ────────────────────────────────────────────────────────────────

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val w  = bounds.width().toFloat()
        val isAmbient = renderParameters.drawMode == DrawMode.AMBIENT

        val starter = starterById(currentStarterId)
        val stage   = starter.currentStage(currentSteps)
        loadSpriteIfNeeded(stage.spriteUrl)

        // Scale text sizes to screen
        timePaint.textSize  = w * 0.17f
        datePaint.textSize  = w * 0.065f
        namePaint.textSize  = w * 0.080f
        labelPaint.textSize = w * 0.058f
        sidePaint.textSize  = w * 0.065f

        // ── Background ────────────────────────────────────────────────────────
        if (isAmbient) canvas.drawColor(Color.BLACK)
        else           drawBackground(canvas, bounds, stage)

        // ── Time (top center) ─────────────────────────────────────────────────
        val timeStr = "%02d:%02d".format(zonedDateTime.hour, zonedDateTime.minute)
        canvas.drawText(timeStr, cx, bounds.top + w * 0.20f, timePaint)

        // ── Date (just below time) ────────────────────────────────────────────
        if (!isAmbient) {
            val dow = zonedDateTime.dayOfWeek.name.take(3)
                .lowercase().replaceFirstChar { it.uppercase() }
            val mon = zonedDateTime.month.name.take(3)
                .lowercase().replaceFirstChar { it.uppercase() }
            canvas.drawText(
                "$dow $mon ${zonedDateTime.dayOfMonth}",
                cx, bounds.top + w * 0.28f, datePaint
            )
        }

        // ── Pokémon sprite (center, radius 0.20w — top clears the date) ───────
        val spriteRadius = (w * 0.20f).toInt()
        spriteBitmap?.let { bmp ->
            val left = (cx - spriteRadius).toInt()
            val top  = (cy - spriteRadius).toInt()
            val dest = Rect(left, top, left + spriteRadius * 2, top + spriteRadius * 2)

            if (isAmbient) {
                canvas.drawBitmap(bmp, null, dest, ambientSpritePaint)
            } else {
                if (stage.stageLabel == "Dynamax") {
                    drawDynamaxGlow(canvas, cx, cy, spriteRadius.toFloat(), zonedDateTime)
                }
                canvas.drawBitmap(bmp, null, dest, null)
            }
        }

        // ── Pokémon name + stage (below sprite) ───────────────────────────────
        namePaint.color = Color.WHITE
        canvas.drawText(stage.name, cx, cy + w * 0.25f, namePaint)
        if (!isAmbient) {
            labelPaint.color = stageLabelColor(stage.stageLabel)
            canvas.drawText(stage.stageLabel, cx, cy + w * 0.32f, labelPaint)
        }

        // ── Side panels (interactive only) ────────────────────────────────────
        if (!isAmbient) {
            drawStepsLeft(canvas, cx, cy, w, starter)
            drawStatsRight(canvas, cx, cy, w)
        }
    }

    override fun renderHighlightLayer(
        canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: SharedAssets
    ) {
        canvas.drawColor(renderParameters.highlightLayer!!.backgroundTint)
    }

    // ── Left panel: steps + vertical progress bar ─────────────────────────────

    private fun drawStepsLeft(canvas: Canvas, cx: Float, cy: Float, w: Float, starter: StarterPokemon) {
        val x        = cx - w * 0.33f
        val progress = starter.progressToNext(currentSteps)
        val next     = starter.nextStage(currentSteps)

        // "STEPS" header
        sidePaint.color    = Color.argb(180, 255, 255, 255)
        sidePaint.textSize = w * 0.050f
        canvas.drawText("STEPS", x, cy - w * 0.14f, sidePaint)

        // Step count
        sidePaint.color    = Color.WHITE
        sidePaint.textSize = w * 0.072f
        canvas.drawText(formatSteps(currentSteps), x, cy - w * 0.04f, sidePaint)

        // Vertical progress bar — fills bottom-to-top
        val barW      = w * 0.038f
        val barTop    = cy + w * 0.02f
        val barBottom = cy + w * 0.20f
        val barH      = barBottom - barTop
        val r         = barW / 2f

        canvas.drawRoundRect(x - barW/2f, barTop, x + barW/2f, barBottom, r, r, progressBgPaint)
        if (progress > 0f) {
            val fillTop = barBottom - barH * progress
            canvas.drawRoundRect(x - barW/2f, fillTop, x + barW/2f, barBottom, r, r, progressFillPaint)
        }

        // Target label below bar
        sidePaint.color    = Color.argb(180, 255, 255, 255)
        sidePaint.textSize = w * 0.048f
        val target = if (next != null) "→${formatSteps(next.minSteps)}" else "MAX!"
        canvas.drawText(target, x, barBottom + w * 0.065f, sidePaint)
    }

    // ── Right panel: heart rate + battery ─────────────────────────────────────

    private fun drawStatsRight(canvas: Canvas, cx: Float, cy: Float, w: Float) {
        val x       = cx + w * 0.33f
        val battery = getBatteryLevel()

        // Heart rate icon
        sidePaint.color    = Color.argb(230, 255, 100, 100)
        sidePaint.textSize = w * 0.058f
        canvas.drawText("♥", x, cy - w * 0.13f, sidePaint)

        // BPM value
        sidePaint.textSize = w * 0.072f
        canvas.drawText(
            if (currentHeartRate > 0) "$currentHeartRate" else "--",
            x, cy - w * 0.04f, sidePaint
        )

        // "bpm" label
        sidePaint.color    = Color.argb(150, 255, 100, 100)
        sidePaint.textSize = w * 0.048f
        canvas.drawText("bpm", x, cy + w * 0.02f, sidePaint)

        // Battery icon
        sidePaint.color    = batteryColor(battery)
        sidePaint.textSize = w * 0.058f
        canvas.drawText("⚡", x, cy + w * 0.10f, sidePaint)

        // Battery percentage
        sidePaint.textSize = w * 0.072f
        canvas.drawText("$battery%", x, cy + w * 0.18f, sidePaint)
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private fun drawBackground(canvas: Canvas, bounds: Rect, stage: EvolutionStage) {
        canvas.drawColor(stage.bgColor)
        val shader = RadialGradient(
            bounds.exactCenterX(), bounds.exactCenterY(), bounds.width() * 0.55f,
            intArrayOf(Color.TRANSPARENT, Color.argb(120, 0, 0, 0)),
            floatArrayOf(0.5f, 1f), Shader.TileMode.CLAMP
        )
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), bounds.width() * 0.6f, p)
    }

    private fun drawDynamaxGlow(canvas: Canvas, cx: Float, cy: Float, radius: Float, time: ZonedDateTime) {
        val pulse = (sin(time.second * 0.3) * 0.15 + 0.85).toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for (ring in 5 downTo 1) {
            p.color = Color.argb((50 / ring).coerceIn(5, 50), 255, 60, 60)
            canvas.drawCircle(cx, cy, (radius + ring * 14f) * pulse, p)
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun getBatteryLevel(): Int {
        val i     = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (scale > 0) level * 100 / scale else 0
    }

    private fun batteryColor(pct: Int) = when {
        pct >= 50 -> Color.argb(230, 100, 220, 100)
        pct >= 20 -> Color.argb(230, 255, 200, 50)
        else      -> Color.argb(230, 255, 80, 80)
    }

    private fun stageLabelColor(label: String) = when (label) {
        "Mega Evolution" -> Color.argb(255, 255, 215, 0)
        "Dynamax"        -> Color.argb(255, 255, 80, 80)
        else             -> Color.argb(200, 255, 255, 255)
    }

    private fun formatSteps(steps: Int) = when {
        steps >= 1_000 -> "%.1fk".format(steps / 1000f)
        else           -> "$steps"
    }

    override fun onDestroy() {
        scope.cancel()
        imageLoader.shutdown()
        super.onDestroy()
    }
}
