package com.chessomania.app.net

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chessomania.app.R
import com.chessomania.app.SettingsManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class SSEService : Service() {

    private var client: OkHttpClient? = null
    private var call: Call? = null
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectDelay = 1000L  // Starts at 1s
    private val MAX_RECONNECT_DELAY = 30000L
    private var isRunning = false
    private var pingTimeout: Runnable? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        const val ACTION_CONNECT = "CONNECT"
        const val ACTION_DISCONNECT = "DISCONNECT"
        const val NOTIFICATION_ID = 1001
        val eventBus = MutableSharedFlow<SseEvent>(extraBufferCapacity = 64)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                if (!isRunning) {
                    isRunning = true
                    startForegroundNotification()
                    connect()
                }
            }
            ACTION_DISCONNECT -> {
                isRunning = false
                disconnect()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun connect() {
        val context = applicationContext
        val token = SecurePrefs.getToken(context)
        if (token == null) {
            Log.e("SSEService", "Cannot connect to SSE: User not logged in (token is null)")
            return
        }
        val serverUrl = SettingsManager.getServerUrl(context)
        if (serverUrl.isEmpty()) {
            Log.e("SSEService", "Cannot connect to SSE: Server URL is empty")
            return
        }

        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)  // No read timeout for SSE stream
            .build()

        val request = Request.Builder()
            .url("$serverUrl/api/events")
            .header("Authorization", "Bearer $token")
            .build()

        call = client!!.newCall(request)
        call!!.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SSEService", "SSE stream failure", e)
                if (isRunning) scheduleReconnect()
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("SSEService", "SSE connection failed with code: ${response.code}")
                    if (isRunning) scheduleReconnect()
                    return
                }

                Log.d("SSEService", "SSE stream connected successfully")
                reconnectDelay = 1000L // Reset delay

                val source = response.body?.source() ?: return
                try {
                    while (!source.exhausted() && isRunning) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val json = line.substring(6)
                            handleEvent(json)
                            resetPingTimeout()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SSEService", "SSE read stream exception", e)
                }
                if (isRunning) scheduleReconnect()
            }
        })

        resetPingTimeout()
    }

    private fun handleEvent(json: String) {
        try {
            val event = Gson().fromJson(json, SseEvent::class.java)
            if (event.type == "ping") {
                resetPingTimeout()
                return
            }
            serviceScope.launch {
                eventBus.emit(event)
            }
        } catch (e: Exception) {
            Log.e("SSEService", "Error parsing SSE event: $json", e)
        }
    }

    private fun resetPingTimeout() {
        pingTimeout?.let { reconnectHandler.removeCallbacks(it) }
        pingTimeout = Runnable {
            if (isRunning) {
                Log.w("SSEService", "No heartbeat ping received in 45 seconds. Reconnecting...")
                call?.cancel()
                connect()
            }
        }
        reconnectHandler.postDelayed(pingTimeout!!, 45000L)
    }

    private fun scheduleReconnect() {
        pingTimeout?.let { reconnectHandler.removeCallbacks(it) }
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectHandler.postDelayed({
            if (isRunning) connect()
        }, reconnectDelay)
        reconnectDelay = minOf(reconnectDelay * 2, MAX_RECONNECT_DELAY)
    }

    private fun disconnect() {
        pingTimeout?.let { reconnectHandler.removeCallbacks(it) }
        reconnectHandler.removeCallbacksAndMessages(null)
        call?.cancel()
        client?.dispatcher?.executorService?.shutdown()
        Log.d("SSEService", "SSE stream disconnected and stopped")
    }

    private fun startForegroundNotification() {
        val channelId = "chess_online"
        val channelName = "Chess Online"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ChessOmania Online")
            .setContentText("Connected - Waiting for challenges & moves")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setOngoing(true)
            .build()
            
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
