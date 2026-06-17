package com.chessomania.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.chessomania.app.R

/**
 * Background Ambient Music Manager for Chessomania.
 * Plays soft, looping ambient music during gameplay.
 * NOT move-based SFX — this is continuous background atmosphere.
 *
 * Inspired by: Splendor, Chess.com, Lichess premium feel.
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

    // MediaPlayer for long ambient tracks
    private var mediaPlayer: MediaPlayer? = null

    // Current music state
    private var isPlaying = false
    private var currentTrack: MusicTrack? = null

    // Volume (0.0 to 1.0) — keep LOW for ambient feel
    var musicVolume: Float = 0.25f
        set(value) {
            field = value.coerceIn(0f, 1f)
            mediaPlayer?.setVolume(field, field)
        }

    var isEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) stop()
            else if (currentTrack != null) play(currentTrack!!)
        }

    // Fade duration in ms
    private val fadeDuration = 2000
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Available ambient music tracks
     */
    enum class MusicTrack(val resId: Int, val displayName: String, val category: String) {
        MENU(R.raw.bg_music_menu, "Main Menu", "menu"),
        GAMEPLAY(R.raw.bg_music_gameplay, "Gameplay", "game"),
        PUZZLE(R.raw.bg_music_puzzle, "Puzzle Mode", "puzzle"),
        AI_BATTLE(R.raw.bg_music_gameplay, "AI Battle", "ai"),
        FRIEND_MATCH(R.raw.bg_music_gameplay, "Friend Match", "multiplayer"),
        VICTORY(R.raw.bg_music_victory, "Victory", "end"),
        DEFEAT(R.raw.bg_music_defeat, "Defeat", "end")
    }

    /**
     * Play a specific ambient track with smooth fade-in
     */
    fun play(track: MusicTrack) {
        if (!isEnabled) return

        // Don't restart if same track is already playing
        if (currentTrack == track && isPlaying) return

        currentTrack = track

        // Fade out current if playing
        if (mediaPlayer != null && isPlaying) {
            fadeOut {
                startNewTrack(track)
            }
        } else {
            startNewTrack(track)
        }
    }

    private fun startNewTrack(track: MusicTrack) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, track.resId).apply {
                isLooping = true
                setVolume(0f, 0f) // Start silent for fade-in
                start()
                fadeIn()
            }
            isPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Fade in music from 0 to set volume
     */
    private fun fadeIn() {
        val mp = mediaPlayer ?: return
        val steps = 20
        val stepDuration = fadeDuration / steps
        val volumeStep = musicVolume / steps
        var currentStep = 0

        val fadeRunnable = object : Runnable {
            override fun run() {
                currentStep++
                val newVolume = (volumeStep * currentStep).coerceAtMost(musicVolume)
                mediaPlayer?.setVolume(newVolume, newVolume)

                if (currentStep < steps && mediaPlayer == mp) {
                    handler.postDelayed(this, stepDuration.toLong())
                }
            }
        }
        handler.post(fadeRunnable)
    }

    /**
     * Fade out music then execute callback
     */
    private fun fadeOut(onComplete: () -> Unit) {
        val mp = mediaPlayer
        if (mp == null || !isPlaying) {
            onComplete()
            return
        }

        val steps = 20
        val stepDuration = fadeDuration / steps
        val volumeStep = musicVolume / steps
        var currentStep = 0

        val fadeRunnable = object : Runnable {
            override fun run() {
                currentStep++
                val newVolume = (musicVolume - (volumeStep * currentStep)).coerceAtLeast(0f)
                mediaPlayer?.setVolume(newVolume, newVolume)

                if (currentStep >= steps || mediaPlayer != mp) {
                    onComplete()
                } else {
                    handler.postDelayed(this, stepDuration.toLong())
                }
            }
        }
        handler.post(fadeRunnable)
    }

    /**
     * Pause music (when app goes to background)
     */
    fun pause() {
        if (isPlaying) {
            fadeOut {
                mediaPlayer?.pause()
                isPlaying = false
            }
        }
    }

    /**
     * Resume music (when app comes to foreground)
     */
    fun resume() {
        if (isEnabled && currentTrack != null && !isPlaying) {
            mediaPlayer?.start()
            fadeIn()
            isPlaying = true
        }
    }

    /**
     * Stop music completely
     */
    fun stop() {
        fadeOut {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            currentTrack = null
        }
    }

    /**
     * Play victory/defeat jingle (non-looping, short)
     */
    fun playJingle(jingleResId: Int) {
        if (!isEnabled) return
        MediaPlayer.create(context, jingleResId).apply {
            setVolume(musicVolume, musicVolume)
            setOnCompletionListener { release() }
            start()
        }
    }

    /**
     * Release all resources
     */
    fun release() {
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.release()
        mediaPlayer = null
        instance = null
    }
}
