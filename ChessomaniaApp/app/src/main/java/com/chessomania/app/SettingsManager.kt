package com.chessomania.app

import android.content.Context
import android.media.MediaPlayer
import java.net.NetworkInterface
import java.util.Collections

object SettingsManager {
    private const val PREFS_NAME = "chessomania_prefs"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_SOUND_THEME = "sound_theme"
    private const val KEY_BOARD_THEME = "board_theme"
    private const val KEY_PIECE_THEME = "piece_theme"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_ACTIVE_GAME_ID = "active_game_id"

    fun getDefaultServerUrl(context: Context): String {
        // First check if there's a shared server URL from another device
        val sharedUrl = getSharedServerUrl(context)
        if (sharedUrl != null) {
            return sharedUrl
        }
        
        // Auto-detect based on device type
        if (isEmulator()) {
            return "http://10.0.2.2:3000"
        } else {
            return "http://${getLocalIpAddress()}:3000"
        }
    }
    
    private fun getSharedServerUrl(context: Context): String? {
        // Check if another device shared a server URL via a shared preference mechanism
        // For now, return null - can be extended to support QR code or manual share
        return null
    }

    private fun isEmulator(): Boolean {
        val model = android.os.Build.MODEL
        val product = android.os.Build.PRODUCT
        val hardware = android.os.Build.HARDWARE
        return (model.contains("google_sdk") || model.contains("Emulator") ||
                model.contains("Android SDK built for x86") ||
                hardware.contains("goldfish") || hardware.contains("ranchu") ||
                product.contains("sdk_gphone") || product.contains("sdk_google"))
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in Collections.list(interfaces)) {
                val addrs = intf.inetAddresses
                for (addr in Collections.list(addrs)) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        if (sAddr != null) {
                            val isIPv4 = sAddr.indexOf(':') < 0
                            if (isIPv4) {
                                return sAddr
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return "10.0.2.2"
    }

    fun getServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_URL, null) ?: getDefaultServerUrl(context)
    }

    fun setServerUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }
    
    fun shareServerUrl(context: Context): String {
        // Returns the current server URL for sharing via QR code or text
        return getServerUrl(context)
    }
    
    fun getCurrentServerInfo(context: Context): String {
        val url = getServerUrl(context)
        val deviceType = if (isEmulator()) "Emulator" else "Physical Device"
        val ip = if (isEmulator()) "10.0.2.2" else getLocalIpAddress()
        return "Server: $url\nDevice: $deviceType\nIP: $ip"
    }

    fun getActiveGameId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_GAME_ID, null)
    }

    fun setActiveGameId(context: Context, gameId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_GAME_ID, gameId).apply()
    }

    fun isSoundEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    fun getSoundTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SOUND_THEME, "standard") ?: "standard"
    }

    fun setSoundTheme(context: Context, theme: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SOUND_THEME, theme).apply()
    }

    fun getBoardTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BOARD_THEME, "Classic") ?: "Classic"
    }

    fun setBoardTheme(context: Context, theme: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BOARD_THEME, theme).apply()
    }

    fun getPieceTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PIECE_THEME, "cburnett") ?: "cburnett"
    }

    fun setPieceTheme(context: Context, theme: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PIECE_THEME, theme).apply()
    }

    // Sound player helper that reads from assets based on selected sound theme
    fun playSound(context: Context, soundName: String) {
        if (!isSoundEnabled(context)) return
        val soundTheme = getSoundTheme(context)
        if (soundTheme == "Silent") return

        try {
            val assetPath = "sounds/$soundTheme/$soundName.mp3"
            val afd = context.assets.openFd(assetPath)
            val mp = MediaPlayer()
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.prepare()
            mp.setOnCompletionListener { mediaPlayer ->
                mediaPlayer.release()
            }
            mp.start()
        } catch (e: Exception) {
            // Fallback to standard theme if there's an error
            if (soundTheme != "standard") {
                try {
                    val assetPath = "sounds/standard/$soundName.mp3"
                    val afd = context.assets.openFd(assetPath)
                    val mp = MediaPlayer()
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    mp.prepare()
                    mp.setOnCompletionListener { mediaPlayer ->
                        mediaPlayer.release()
                    }
                    mp.start()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            } else {
                e.printStackTrace()
            }
        }
    }

    val boardImageThemes = mapOf(
        "Blue Marble" to "blue-marble.jpg",
        "Blue" to "blue.png",
        "Blue 2" to "blue2.jpg",
        "Blue 3" to "blue3.jpg",
        "Brown" to "brown.png",
        "Canvas" to "canvas2.jpg",
        "Green Plastic" to "green-plastic.png",
        "Green" to "green.png",
        "Grey" to "grey.jpg",
        "Horsey" to "horsey.jpg",
        "IC" to "ic.png",
        "Leather" to "leather.jpg",
        "Maple" to "maple.jpg",
        "Maple 2" to "maple2.jpg",
        "Marble" to "marble.jpg",
        "Metal" to "metal.jpg",
        "Olive" to "olive.jpg",
        "Pink Pyramid" to "pink-pyramid.png",
        "Purple Diag" to "purple-diag.png",
        "Purple" to "purple.png",
        "Wood" to "wood.jpg",
        "Wood 2" to "wood2.jpg",
        "Wood 3" to "wood3.jpg",
        "Wood 4" to "wood4.jpg"
    )

    // Board Colors definitions
    data class BoardColors(val light: String, val dark: String)

    fun getBoardColors(theme: String): BoardColors {
        return when (theme) {
            "Classic" -> BoardColors("#f0d9b5", "#b58863")
            "Ocean" -> BoardColors("#dee3e6", "#8ca2ad")
            "Tournament" -> BoardColors("#eeeeee", "#769656")
            "Charcoal" -> BoardColors("#e2e4e6", "#706e7a")
            "Ice" -> BoardColors("#e0ecf8", "#7b9ec5")
            "Purple (Solid)" -> BoardColors("#9f90b0", "#7d4a8d")
            "Cherry" -> BoardColors("#f2d4d4", "#b35c5c")
            "Wood (Solid)" -> BoardColors("#e8d3bc", "#a87850")
            "Forest" -> BoardColors("#e2ecc8", "#598c64")
            "Midnight" -> BoardColors("#e2e7ec", "#475e7a")

            // Webapp Image Themes Fallbacks
            "Wood", "Wood 2", "Wood 3", "Wood 4", "Leather" -> BoardColors("#e8d3bc", "#a87850")
            "Maple", "Maple 2", "Horsey", "Brown" -> BoardColors("#f0d9b5", "#b58863")
            "Blue", "Blue 2", "Blue 3", "Blue Marble", "IC" -> BoardColors("#dee3e6", "#8ca2ad")
            "Canvas", "Grey", "Metal" -> BoardColors("#e2e4e6", "#706e7a")
            "Marble", "Green", "Green Plastic" -> BoardColors("#eeeeee", "#769656")
            "Olive" -> BoardColors("#e2ecc8", "#598c64")
            "Pink Pyramid" -> BoardColors("#f2d4d4", "#b35c5c")
            "Purple", "Purple Diag" -> BoardColors("#9f90b0", "#7d4a8d")
            else -> BoardColors("#f0d9b5", "#b58863")
        }
    }
}
