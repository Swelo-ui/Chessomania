package com.chessomania.app

import android.app.Application
import android.content.Intent
import java.io.PrintWriter
import java.io.StringWriter

class ChessApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val crashLog = sw.toString()
                
                val intent = Intent(applicationContext, CrashActivity::class.java)
                intent.putExtra("CRASH_LOG", crashLog)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                // Ignore if crash reporter itself crashes
            }
            // Kill the original process
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        }
    }
}
