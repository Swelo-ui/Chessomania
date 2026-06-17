package com.chessomania.app.net

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "chess_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveSession(context: Context, token: String, username: String) {
        getPrefs(context).edit()
            .putString("jwt_token", token)
            .putString("username", username)
            .apply()
    }

    fun getToken(context: Context): String? = getPrefs(context).getString("jwt_token", null)
    
    fun getUsername(context: Context): String? = getPrefs(context).getString("username", null)
    
    fun isLoggedIn(context: Context): Boolean = getToken(context) != null

    fun setHostHintEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("host_hint_enabled", enabled).apply()
    }

    fun getHostHintEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("host_hint_enabled", false)
    }

    fun clearHostHintEnabled(context: Context) {
        setHostHintEnabled(context, false)
    }

    fun clearSession(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
