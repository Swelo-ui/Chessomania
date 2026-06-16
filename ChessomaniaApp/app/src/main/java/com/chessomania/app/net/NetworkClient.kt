package com.chessomania.app.net

import android.content.Context
import com.chessomania.app.SettingsManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object NetworkClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun getServerUrl(context: Context): String = SettingsManager.getServerUrl(context)
    private fun getToken(context: Context): String? = SecurePrefs.getToken(context)

    private fun buildRequest(context: Context, path: String): Request.Builder {
        val token = getToken(context)
        return Request.Builder()
            .url("${getServerUrl(context)}$path")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun post(context: Context, path: String, body: Map<String, Any?>): ApiResult {
        return withContext(Dispatchers.IO) {
            try {
                val json = Gson().toJson(body)
                val requestBody = json.toRequestBody("application/json".toMediaType())
                val request = buildRequest(context, path).post(requestBody).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: "{}"
                if (response.isSuccessful) {
                    val mapType = Gson().fromJson(responseBody, Map::class.java) as Map<String, Any?>
                    ApiResult.Success(mapType)
                } else {
                    val err = try {
                        Gson().fromJson(responseBody, Map::class.java) as Map<String, Any?>
                    } catch (e: Exception) {
                        emptyMap<String, Any?>()
                    }
                    ApiResult.Error(err["error"] as? String ?: "HTTP ${response.code}", response.code)
                }
            } catch (e: Exception) {
                ApiResult.Error(e.message ?: "Network error", -1)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun get(context: Context, path: String): ApiResult {
        return withContext(Dispatchers.IO) {
            try {
                val request = buildRequest(context, path).get().build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: "{}"
                if (response.isSuccessful) {
                    val mapType = Gson().fromJson(responseBody, Map::class.java) as Map<String, Any?>
                    ApiResult.Success(mapType)
                } else {
                    ApiResult.Error("HTTP ${response.code}", response.code)
                }
            } catch (e: Exception) {
                ApiResult.Error(e.message ?: "Network error", -1)
            }
        }
    }
}

sealed class ApiResult {
    data class Success(val data: Map<String, Any?>) : ApiResult()
    data class Error(val message: String, val code: Int) : ApiResult()
}
