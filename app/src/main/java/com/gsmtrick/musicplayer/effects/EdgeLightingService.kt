package com.gsmtrick.musicplayer.effects

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.gsmtrick.musicplayer.MainActivity
import com.gsmtrick.musicplayer.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that draws a beat-reactive lighting frame on top of
 * every other app, including the home screen and lock screen, using a
 * `WindowManager.TYPE_APPLICATION_OVERLAY` window.
 *
 * Started/stopped from [MusicPlaybackService] whenever the user has both
 * "edge lighting" and "system-wide overlay" toggled on AND the player is
 * currently playing. The actual content of each frame is driven by the
 * shared [BeatBus] flow that the playback service keeps populated via
 * [BeatDetector], so this service does not need its own audio session
 * access.
 *
 * Requires the user to have granted "Display over other apps" in system
 * settings; callers should check [canDraw] before flipping the toggle.
 */
class EdgeLightingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var overlay: EdgeOverlayView? = null
    private var wm: WindowManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        if (!canDraw(this)) {
            // Without overlay permission we can't do anything useful.
            stopSelf()
            return
        }
        attachOverlay()
        val prefs = PreferencesRepository(applicationContext)
        collectJob = scope.launch {
            // Subscribe to both pulses (drives animation) and prefs (drives style).
            launch {
                BeatBus.pulses.collect { pulse ->
                    overlay?.applyPulse(pulse)
                }
            }
            launch {
                prefs.prefs.collectLatest { p ->
                    overlay?.applyStyle(
                        thicknessDp = p.edgeLightingThicknessDp,
                        intensity = p.edgeLightingIntensity,
                        colorMode = p.edgeLightingColorMode,
                        beatReactive = p.edgeLightingBeatReactive,
                    )
                }
            }
        }
    }

    private fun attachOverlay() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = windowManager
        val view = EdgeOverlayView(applicationContext)
        overlay = view
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        runCatching { windowManager.addView(view, params) }
            .onFailure {
                Log.w(TAG, "addView failed", it)
                stopSelf()
            }
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Edge lighting",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                setShowBadge(false)
                description = "Beat-reactive screen edge lighting"
            }
            nm.createNotificationChannel(channel)
        }
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Edge lighting active")
            .setContentText("Pulsing screen edges to the beat")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(tap)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        runCatching {
            overlay?.let { wm?.removeViewImmediate(it) }
        }
        overlay = null
        wm = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "EdgeLightingService"
        private const val CHANNEL_ID = "edge_lighting_v33"
        private const val NOTIF_ID = 0xED01

        fun canDraw(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(context)

        fun setRunning(context: Context, on: Boolean) {
            val intent = Intent(context, EdgeLightingService::class.java)
            if (on && canDraw(context)) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            } else {
                runCatching { context.stopService(intent) }
            }
        }
    }
}

/**
 * Native [View] that draws the pulsing edge frame on every frame.
 *
 * Compose can't easily draw into a `WindowManager` overlay (its
 * lifecycle/composition assumptions don't match a raw window) so we
 * implement the rendering with a plain Canvas + invalidate-on-vsync
 * loop. The visuals match the in-app Compose [EdgeLightingOverlay].
 */
private class EdgeOverlayView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val rect = RectF()

    private var bass = 0f
    private var mid = 0f
    private var high = 0f
    private var beat = 0
    private var beatFlash = 0f
    private var hue = 0f
    private val handler = Handler(Looper.getMainLooper())

    private var thicknessDp = 12
    private var intensity = 0.8f
    private var colorMode = "rainbow" // rainbow | album | single
    private var beatReactive = true

    private val tick = object : Runnable {
        override fun run() {
            // Slow rainbow spin even in idle; faster on beats.
            hue = (hue + (1f + bass * 6f)) % 360f
            beatFlash *= 0.85f
            invalidate()
            handler.postDelayed(this, 16)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(tick)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    fun applyPulse(p: BeatPulse) {
        bass = p.bass
        mid = p.mid
        high = p.high
        if (p.beat != beat) {
            beat = p.beat
            beatFlash = 1f
        }
    }

    fun applyStyle(thicknessDp: Int, intensity: Float, colorMode: String, beatReactive: Boolean) {
        this.thicknessDp = thicknessDp.coerceIn(2, 64)
        this.intensity = intensity.coerceIn(0f, 1f)
        this.colorMode = colorMode
        this.beatReactive = beatReactive
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val density = resources.displayMetrics.density
        val baseStroke = thicknessDp * density
        val pulse = if (beatReactive) {
            (0.35f + bass * 0.65f + beatFlash * 0.45f).coerceIn(0f, 1.4f)
        } else {
            0.55f + 0.25f * (System.currentTimeMillis() % 1800L) / 1800f
        }
        val stroke = baseStroke * (0.7f + 0.6f * pulse)
        val alpha = (0xFF * intensity * (0.45f + 0.55f * pulse)).toInt().coerceIn(0, 255)
        val baseColor = colorForMode()
        paint.color = (alpha shl 24) or (baseColor and 0x00FFFFFF)
        paint.strokeWidth = stroke
        rect.set(stroke / 2f, stroke / 2f, w - stroke / 2f, h - stroke / 2f)
        val corner = 36f * density
        canvas.drawRoundRect(rect, corner, corner, paint)

        // Inner soft glow, drawn at half stroke and lower alpha for depth.
        val innerAlpha = (alpha * 0.45f).toInt().coerceIn(0, 255)
        paint.color = (innerAlpha shl 24) or (baseColor and 0x00FFFFFF)
        paint.strokeWidth = stroke * 1.8f
        rect.set(stroke, stroke, w - stroke, h - stroke)
        canvas.drawRoundRect(rect, corner * 0.85f, corner * 0.85f, paint)
    }

    private fun colorForMode(): Int = when (colorMode) {
        "single" -> Color.HSVToColor(floatArrayOf(280f, 0.85f, 1f))
        "album" -> Color.HSVToColor(floatArrayOf(((hue + bass * 60f) % 360f), 0.7f, 1f))
        else -> Color.HSVToColor(floatArrayOf(hue, 0.85f, 1f))
    }
}
