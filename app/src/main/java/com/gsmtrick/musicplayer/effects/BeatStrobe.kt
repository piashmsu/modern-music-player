package com.gsmtrick.musicplayer.effects

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Bridge between [BeatBus] and the device's camera torch. Whenever the
 * detector reports a new beat (the [BeatPulse.beat] counter
 * increments), the torch flashes on for ~80ms and then back off — a
 * party-style strobe synced to the music.
 *
 * Uses [CameraManager.setTorchMode], which works on most modern devices
 * without requiring the runtime CAMERA permission. We swallow any
 * failures gracefully so a phone without a back torch (or one whose
 * camera is currently in use) just no-ops instead of crashing.
 */
class BeatStrobe(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    private var torchOn = false
    private var lastBeat = -1
    private val cm: CameraManager? =
        runCatching { context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager }
            .getOrNull()
    private var cameraId: String? = null

    fun start() {
        if (job != null) return
        cameraId = pickTorchCameraId() ?: return
        job = scope.launch {
            BeatBus.pulses.collect { p ->
                if (p.beat != lastBeat) {
                    lastBeat = p.beat
                    flash()
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        setTorch(false)
        handler.removeCallbacksAndMessages(null)
    }

    private fun flash() {
        setTorch(true)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ setTorch(false) }, FLASH_MS)
    }

    private fun setTorch(on: Boolean) {
        val id = cameraId ?: return
        val mgr = cm ?: return
        runCatching { mgr.setTorchMode(id, on) }
            .onSuccess { torchOn = on }
            .onFailure { Log.w(TAG, "setTorchMode failed", it) }
    }

    private fun pickTorchCameraId(): String? {
        val mgr = cm ?: return null
        return runCatching {
            mgr.cameraIdList.firstOrNull { id ->
                val ch = mgr.getCameraCharacteristics(id)
                ch.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    companion object {
        private const val TAG = "BeatStrobe"
        private const val FLASH_MS = 80L
    }
}
