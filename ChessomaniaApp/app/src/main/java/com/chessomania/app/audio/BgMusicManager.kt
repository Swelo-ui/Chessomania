package com.chessomania.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.chessomania.app.R

/**
 * Background Ambient Music Manager for Chessomania.
 * Plays soft, looping ambient music during gameplay.
 * Robust single-pipeline state machine to prevent track and volume clashes.
 */
class BgMusicManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: BgMusicManager? = null

        fun getInstance(context: Context): BgMusicManager {
            return instance ?: synchronized(this) {
                instance ?: BgMusicManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var mediaPlayer: MediaPlayer? = null

    // Track states
    var currentTrack: MusicTrack? = null
        private set
    private var targetTrack: MusicTrack? = null

    // Volume states
    private var currentVolume: Float = 0f
    private var targetVolume: Float = 0f

    // Action when volume reaches 0
    private enum class ZeroAction { NONE, PAUSE, STOP_RELEASE }
    private var zeroAction = ZeroAction.NONE

    // Timing parameters for fade
    private val tickMs = 50L
    private val fadeDurationMs = 2000.0f

    private val volumeStep: Float
        get() = if (musicVolume > 0f) (musicVolume / (fadeDurationMs / tickMs)).coerceAtLeast(0.005f) else 0.01f

    var musicVolume: Float = 0.50f
        set(value) {
            field = value.coerceIn(0f, 1f)
            // If playing or fading to active volume, update the target
            if (mediaPlayer != null && targetVolume > 0f) {
                targetVolume = field
            }
            startUpdateLoop()
        }

    var isEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                stop()
            } else if (targetTrack != null) {
                play(targetTrack!!)
            }
        }

    enum class MusicTrack(val resId: Int, val displayName: String, val category: String) {
        MENU(R.raw.bg_music_menu, "Main Menu", "menu"),
        GAMEPLAY(R.raw.bg_music_gameplay, "Gameplay", "game"),
        PUZZLE(R.raw.bg_music_puzzle, "Puzzle Mode", "puzzle"),
        AI_BATTLE(R.raw.bg_music_gameplay, "AI Battle", "ai"),
        FRIEND_MATCH(R.raw.bg_music_gameplay, "Friend Match", "multiplayer"),
        VICTORY(R.raw.bg_music_victory, "Victory", "end"),
        DEFEAT(R.raw.bg_music_defeat, "Defeat", "end")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isLoopRunning = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            val player = mediaPlayer
            var needsNextTick = false

            // 1. If targetTrack has changed and player is active, force a fade out first
            if (player != null && targetTrack != currentTrack && targetVolume != 0f) {
                targetVolume = 0f
                zeroAction = ZeroAction.STOP_RELEASE
            }

            // 2. If player is null but we have a targetTrack, initialize it
            if (player == null && targetTrack != null) {
                startNewTrack(targetTrack!!)
                needsNextTick = true
            } else if (player != null) {
                // Adjust current volume towards target volume
                val diff = targetVolume - currentVolume
                if (Math.abs(diff) < 0.001f) {
                    currentVolume = targetVolume
                    try {
                        player.setVolume(currentVolume, currentVolume)
                    } catch (e: Exception) {}

                    if (currentVolume == 0f) {
                        handleZeroVolumeReached()
                        if (targetTrack != null) {
                            needsNextTick = true
                        }
                    }
                } else {
                    val step = volumeStep
                    if (currentVolume < targetVolume) {
                        currentVolume = (currentVolume + step).coerceAtMost(targetVolume)
                    } else {
                        currentVolume = (currentVolume - step).coerceAtLeast(targetVolume)
                    }
                    try {
                        player.setVolume(currentVolume, currentVolume)
                    } catch (e: Exception) {}
                    needsNextTick = true
                }
            }

            if (needsNextTick) {
                handler.postDelayed(this, tickMs)
            } else {
                isLoopRunning = false
            }
        }
    }

    private fun startUpdateLoop() {
        if (!isLoopRunning) {
            isLoopRunning = true
            handler.post(updateRunnable)
        }
    }

    private fun startNewTrack(track: MusicTrack) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, track.resId).apply {
                isLooping = true
                setVolume(0f, 0f)
                start()
            }
            currentTrack = track
            currentVolume = 0f
            targetVolume = musicVolume
            zeroAction = ZeroAction.NONE
        } catch (e: Exception) {
            e.printStackTrace()
            mediaPlayer = null
            currentTrack = null
            targetTrack = null
        }
    }

    private fun handleZeroVolumeReached() {
        when (zeroAction) {
            ZeroAction.PAUSE -> {
                try {
                    mediaPlayer?.pause()
                } catch (e: Exception) {}
            }
            ZeroAction.STOP_RELEASE -> {
                try {
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                } catch (e: Exception) {}
                mediaPlayer = null
                currentTrack = null
            }
            ZeroAction.NONE -> {}
        }
        zeroAction = ZeroAction.NONE
    }

    fun play(track: MusicTrack) {
        if (!isEnabled) {
            targetTrack = track
            return
        }

        // Don't restart if already playing/targeting the same track
        if (currentTrack == track && targetTrack == track && mediaPlayer?.isPlaying == true) {
            // Ensure target volume matches set volume if it was fading/paused
            if (targetVolume == 0f) {
                targetVolume = musicVolume
                startUpdateLoop()
            }
            return
        }

        targetTrack = track
        startUpdateLoop()
    }

    fun pause() {
        if (mediaPlayer?.isPlaying == true) {
            targetVolume = 0f
            zeroAction = ZeroAction.PAUSE
            startUpdateLoop()
        }
    }

    fun resume() {
        if (isEnabled && currentTrack != null) {
            try {
                mediaPlayer?.start()
            } catch (e: Exception) {}
            targetVolume = musicVolume
            zeroAction = ZeroAction.NONE
            startUpdateLoop()
        }
    }

    fun stop() {
        targetTrack = null
        targetVolume = 0f
        zeroAction = ZeroAction.STOP_RELEASE
        startUpdateLoop()
    }

    fun playJingle(jingleResId: Int) {
        if (!isEnabled) return
        try {
            MediaPlayer.create(context, jingleResId).apply {
                setVolume(musicVolume, musicVolume)
                setOnCompletionListener { release() }
                start()
            }
        } catch (e: Exception) {}
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
        currentTrack = null
        targetTrack = null
        instance = null
    }
}
