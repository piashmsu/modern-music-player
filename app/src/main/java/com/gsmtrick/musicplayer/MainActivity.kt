package com.gsmtrick.musicplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.gsmtrick.musicplayer.ui.AppRoot
import com.gsmtrick.musicplayer.ui.PlayerViewModel
import kotlin.math.sqrt
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels { PlayerViewModel.Factory }

    private var sensorManager: SensorManager? = null
    private var accel: Sensor? = null
    private var lastShakeAt = 0L
    private var headphoneReceiver: BroadcastReceiver? = null
    private val shakeListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val gForce = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
            if (gForce > 2.7) {
                val now = System.currentTimeMillis()
                if (now - lastShakeAt > 1500) {
                    lastShakeAt = now
                    viewModel.next()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        lifecycleScope.launch {
            viewModel.prefs
                .distinctUntilChangedBy { it.language }
                .onEach { p -> applyLocale(p.language) }
                .collect {}
        }
        lifecycleScope.launch {
            viewModel.prefs
                .distinctUntilChangedBy { it.shakeToSkip }
                .onEach { p ->
                    val sm = sensorManager ?: return@onEach
                    val s = accel ?: return@onEach
                    if (p.shakeToSkip) {
                        sm.registerListener(shakeListener, s, SensorManager.SENSOR_DELAY_UI)
                    } else {
                        sm.unregisterListener(shakeListener)
                    }
                }
                .collect {}
        }
        lifecycleScope.launch {
            viewModel.prefs
                .distinctUntilChangedBy { it.autoResumeOnHeadphone }
                .onEach { p ->
                    if (p.autoResumeOnHeadphone && headphoneReceiver == null) {
                        val r = object : BroadcastReceiver() {
                            override fun onReceive(c: Context?, i: Intent?) {
                                val state = i?.getIntExtra("state", -1) ?: -1
                                if (state == 1 && viewModel.state.value.currentSong != null) {
                                    viewModel.play()
                                }
                            }
                        }
                        registerReceiver(r, IntentFilter(Intent.ACTION_HEADSET_PLUG))
                        headphoneReceiver = r
                    } else if (!p.autoResumeOnHeadphone && headphoneReceiver != null) {
                        try { unregisterReceiver(headphoneReceiver) } catch (_: Throwable) {}
                        headphoneReceiver = null
                    }
                }
                .collect {}
        }
        setContent {
            AppRoot(viewModel = viewModel)
        }
    }

    private fun applyLocale(language: String) {
        val tags = when (language) {
            "bn" -> "bn"
            "en" -> "en"
            else -> "" // system default
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
    }

    override fun onStart() {
        super.onStart()
        viewModel.connectController(this)
        // Re-register shake listener if user has it enabled — distinctUntilChangedBy
        // in onCreate won't re-emit on a stop/start cycle, so we re-register here.
        if (viewModel.prefs.value.shakeToSkip) {
            val sm = sensorManager
            val s = accel
            if (sm != null && s != null) {
                sm.registerListener(shakeListener, s, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        sensorManager?.unregisterListener(shakeListener)
        viewModel.releaseController()
    }

    override fun onDestroy() {
        headphoneReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Throwable) {}
            headphoneReceiver = null
        }
        super.onDestroy()
    }
}
