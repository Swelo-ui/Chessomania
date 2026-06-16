package com.chessomania.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CrashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val crashLog = intent.getStringExtra("CRASH_LOG") ?: "No crash log found"
        
        val textView = TextView(this).apply {
            text = crashLog
            textSize = 14f
            setPadding(32, 32, 32, 32)
            setTextColor(android.graphics.Color.RED)
            setTextIsSelectable(true)
        }
        
        val scrollView = android.widget.ScrollView(this)
        scrollView.addView(textView)
        
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.WHITE)
        }
        
        val copyButton = Button(this).apply {
            text = "Copy Stacktrace"
            setOnClickListener {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Crash Log", crashLog)
                clipboard.setPrimaryClip(clip)
            }
        }
        
        val restartButton = Button(this).apply {
            text = "Restart App"
            setOnClickListener {
                val intent = Intent(this@CrashActivity, SplashActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                finish()
            }
        }
        
        container.addView(copyButton)
        container.addView(restartButton)
        container.addView(scrollView)
        
        setContentView(container)
    }
}
