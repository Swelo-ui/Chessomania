package com.chessomania.app

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen immersive
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.splash_logo)
        val progress = findViewById<ProgressBar>(R.id.splash_progress)

        // Animate full screen logo alpha
        logo.alpha = 0f
        logo.animate()
            .alpha(1f)
            .setDuration(800)
            .start()

        // Animate progress bar from 0 to 100
        val animator = ValueAnimator.ofInt(0, 100)
        animator.duration = 2200
        animator.addUpdateListener { animation ->
            progress.progress = animation.animatedValue as Int
        }
        animator.start()

        // Navigate to MainActivity after 2.5s
        logo.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2500)
    }
}
