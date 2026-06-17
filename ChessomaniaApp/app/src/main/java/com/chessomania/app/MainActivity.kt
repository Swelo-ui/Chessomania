package com.chessomania.app

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.chessomania.app.databinding.ActivityMainBinding
import com.chessomania.app.ui.CoordinatesFragment
import com.chessomania.app.ui.PlayFragment
import com.chessomania.app.ui.PuzzleFragment
import com.chessomania.app.ui.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var playFragment: PlayFragment? = null
    private var puzzleFragment: PuzzleFragment? = null
    private var coordinatesFragment: CoordinatesFragment? = null
    private var settingsFragment: SettingsFragment? = null
    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Status bar color
        window.statusBarColor = resources.getColor(R.color.bg_dark, theme)
        window.navigationBarColor = resources.getColor(R.color.panel, theme)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize fragments
        if (savedInstanceState == null) {
            val playFrag = PlayFragment()
            playFragment = playFrag
            activeFragment = playFrag
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, playFrag, "play")
                .commit()
        } else {
            playFragment = supportFragmentManager.findFragmentByTag("play") as? PlayFragment
            puzzleFragment = supportFragmentManager.findFragmentByTag("puzzle") as? PuzzleFragment
            coordinatesFragment = supportFragmentManager.findFragmentByTag("coord") as? CoordinatesFragment
            settingsFragment = supportFragmentManager.findFragmentByTag("settings") as? SettingsFragment
            
            // Find active fragment
            activeFragment = supportFragmentManager.fragments.find { it.isVisible }
        }

        // Bottom nav listener
        binding.bottomNav.setOnItemSelectedListener { item ->
            val tag = when (item.itemId) {
                R.id.nav_play -> "play"
                R.id.nav_puzzle -> "puzzle"
                R.id.nav_coord -> "coord"
                R.id.nav_settings -> "settings"
                else -> "play"
            }
            switchFragment(tag)
            true
        }
        // Initialize background music
        val bgMusic = com.chessomania.app.audio.BgMusicManager.getInstance(this)
        bgMusic.isEnabled = com.chessomania.app.net.SecurePrefs.isMusicEnabled(this)
        bgMusic.musicVolume = com.chessomania.app.net.SecurePrefs.getMusicVolume(this)
        if (savedInstanceState == null) {
            bgMusic.play(com.chessomania.app.audio.BgMusicManager.MusicTrack.MENU)
        }
    }

    private fun switchFragment(tag: String) {
        val fm = supportFragmentManager
        val active = activeFragment
        
        val target = when (tag) {
            "play" -> {
                if (playFragment == null) playFragment = PlayFragment()
                playFragment!!
            }
            "puzzle" -> {
                if (puzzleFragment == null) puzzleFragment = PuzzleFragment()
                puzzleFragment!!
            }
            "coord" -> {
                if (coordinatesFragment == null) coordinatesFragment = CoordinatesFragment()
                coordinatesFragment!!
            }
            "settings" -> {
                if (settingsFragment == null) settingsFragment = SettingsFragment()
                settingsFragment!!
            }
            else -> {
                if (playFragment == null) playFragment = PlayFragment()
                playFragment!!
            }
        }

        if (target == active) return

        val transaction = fm.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)

        if (active != null) {
            transaction.hide(active)
        }

        if (!target.isAdded) {
            transaction.add(R.id.fragment_container, target, tag)
        } else {
            transaction.show(target)
        }

        transaction.commit()
        activeFragment = target

        // Transition music when switching tabs
        val music = com.chessomania.app.audio.BgMusicManager.getInstance(this)
        when (tag) {
            "play" -> {
                // If in active game, play gameplay, else play menu
                val playFrag = playFragment
                if (playFrag != null && playFrag.isInActiveGame()) {
                    music.play(com.chessomania.app.audio.BgMusicManager.MusicTrack.GAMEPLAY)
                } else {
                    music.play(com.chessomania.app.audio.BgMusicManager.MusicTrack.MENU)
                }
            }
            "puzzle" -> {
                music.play(com.chessomania.app.audio.BgMusicManager.MusicTrack.PUZZLE)
            }
            else -> {
                music.play(com.chessomania.app.audio.BgMusicManager.MusicTrack.MENU)
            }
        }
    }

    fun updateStatusBadge(text: String) {
        binding.badgeStatus.text = text
    }

    override fun onBackPressed() {
        val currentFrag = activeFragment
        if (currentFrag is PlayFragment) {
            super.onBackPressed()
        } else {
            binding.bottomNav.selectedItemId = R.id.nav_play
        }
    }

    override fun onResume() {
        super.onResume()
        val bgMusic = com.chessomania.app.audio.BgMusicManager.getInstance(this)
        bgMusic.isEnabled = com.chessomania.app.net.SecurePrefs.isMusicEnabled(this)
        bgMusic.musicVolume = com.chessomania.app.net.SecurePrefs.getMusicVolume(this)
        bgMusic.resume()
    }

    override fun onPause() {
        super.onPause()
        com.chessomania.app.audio.BgMusicManager.getInstance(this).pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        com.chessomania.app.audio.BgMusicManager.getInstance(this).release()
    }
}
