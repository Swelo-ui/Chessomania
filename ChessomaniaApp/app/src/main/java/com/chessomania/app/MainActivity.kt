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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Status bar color
        window.statusBarColor = resources.getColor(R.color.bg_dark, theme)
        window.navigationBarColor = resources.getColor(R.color.panel, theme)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(PlayFragment())
        }

        // Bottom nav listener
        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_play -> PlayFragment()
                R.id.nav_puzzle -> PuzzleFragment()
                R.id.nav_coord -> CoordinatesFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> PlayFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onBackPressed() {
        // If on play fragment, ask before exit; otherwise go to play
        val currentFrag = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFrag is PlayFragment) {
            super.onBackPressed()
        } else {
            binding.bottomNav.selectedItemId = R.id.nav_play
        }
    }
}
