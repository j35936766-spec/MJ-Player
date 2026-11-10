package com.mj.player

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.abs

class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URIS = "uris"
        const val EXTRA_INDEX = "index"
        const val EXTRA_AUDIO = "audio"
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var audioManager: AudioManager

    private var volumeAccum = 0f
    private var brightness = -1f
    private var isAudio = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        isAudio = intent.getBooleanExtra(EXTRA_AUDIO, false)
        if (!isAudio) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        playerView = PlayerView(this).apply {
            setShowSubtitleButton(true)      // সাবটাইটেল বাটন
            setShowNextButton(true)
            setShowPreviousButton(true)
            controllerShowTimeoutMs = if (isAudio) 0 else 3000
            // গিয়ার আইকনে: Playback speed + Audio track
        }
        setContentView(playerView)
        hideSystemBars()
        setupGestures()
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
    }

    private fun initPlayer() {
        if (player != null) return
        val uriStrings: List<String>
        val start: Int
        val list = intent.getStringArrayListExtra(EXTRA_URIS)
        if (list != null) {
            uriStrings = list
            start = intent.getIntExtra(EXTRA_INDEX, 0)
        } else {
            val data = intent.data ?: run { finish(); return }
            uriStrings = listOf(data.toString())
            start = 0
        }
        player = ExoPlayer.Builder(this).build().also { p ->
            p.setMediaItems(uriStrings.map { MediaItem.fromUri(Uri.parse(it)) }, start, 0L)
            p.prepare()
            p.playWhenReady = true
            playerView.player = p
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) releasePlayer()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    // ---------- Picture-in-Picture ----------
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isAudio && Build.VERSION.SDK_INT >= 26 && player?.isPlaying == true) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        playerView.useController = !isInPip
    }

    // ---------- Gestures ----------
    private fun setupGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (playerView.isControllerFullyVisible) playerView.hideController()
                else playerView.showController()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val p = player ?: return true
                val w = playerView.width
                when {
                    e.x < w / 3f -> p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                    e.x > w * 2 / 3f -> p.seekTo(p.currentPosition + 10_000)
                    else -> if (p.isPlaying) p.pause() else p.play()
                }
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (e1 == null || abs(dy) < abs(dx)) return false
                val fraction = dy / playerView.height
                if (e1.x < playerView.width / 2f) changeBrightness(fraction)
                else changeVolume(fraction)
                return true
            }
        })

        playerView.setOnTouchListener { _, ev -> detector.onTouchEvent(ev) }
    }

    private fun changeVolume(fraction: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeAccum += fraction * max * 1.5f
        val step = volumeAccum.toInt()
        if (step != 0) {
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC, (cur + step).coerceIn(0, max), 0
            )
            volumeAccum -= step
        }
    }

    private fun changeBrightness(fraction: Float) {
        if (brightness < 0) {
            brightness = window.attributes.screenBrightness
            if (brightness < 0) brightness = 0.5f
        }
        brightness = (brightness + fraction * 1.2f).coerceIn(0.02f, 1f)
        window.attributes = window.attributes.apply { screenBrightness = brightness }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, playerView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
