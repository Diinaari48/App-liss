package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig

object SupabaseConfigManager {

    private const val TAG = "SupabaseConfigManager"
    private const val PREFS_NAME = "supabase_config_prefs"
    private const val KEY_URL = "supabase_url"
    private const val KEY_ANON_KEY = "supabase_anon_key"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSavedUrl(context: Context): String {
        val prefs = getPrefs(context)
        val saved = prefs.getString(KEY_URL, null)
        if (!saved.isNullOrBlank()) {
            return saved.trim()
        }
        // Fallback to BuildConfig only if it has a real non-placeholder value
        return try {
            val buildUrl = BuildConfig.SUPABASE_URL.trim()
            if (isValidHttpsUrl(buildUrl)) buildUrl else ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getSavedAnonKey(context: Context): String {
        val prefs = getPrefs(context)
        val saved = prefs.getString(KEY_ANON_KEY, null)
        if (!saved.isNullOrBlank()) {
            return saved.trim()
        }
        // Fallback to BuildConfig only if it has a real non-placeholder value
        return try {
            val buildKey = BuildConfig.SUPABASE_ANON_KEY.trim()
            if (!isPlaceholderKey(buildKey)) buildKey else ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getSupabaseUrl(context: Context): String = getSavedUrl(context)
    fun getSupabaseKey(context: Context): String = getSavedAnonKey(context)

    fun isValidHttpsUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.contains("your-project.supabase.co") || trimmed.contains("your-project")) return false
        if (!trimmed.startsWith("https://", ignoreCase = true)) return false
        if (!trimmed.contains(".")) return false
        return true
    }

    fun isPlaceholderKey(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return true
        if (trimmed.contains("your-supabase-anon-key") || trimmed.contains("your-anon-key")) return true
        return false
    }

    fun isValidAnonKey(key: String): Boolean = !isPlaceholderKey(key)

    fun isConfigured(context: Context): Boolean {
        val url = getSavedUrl(context)
        val key = getSavedAnonKey(context)
        return isValidHttpsUrl(url) && isValidAnonKey(key)
    }

    fun saveConfig(context: Context, url: String, anonKey: String): Boolean {
        val cleanUrl = url.trim().trimEnd('/')
        val cleanKey = anonKey.trim()

        if (!isValidHttpsUrl(cleanUrl) || !isValidAnonKey(cleanKey)) {
            Log.w(TAG, "Invalid Supabase config provided: url=$cleanUrl")
            return false
        }

        getPrefs(context).edit()
            .putString(KEY_URL, cleanUrl)
            .putString(KEY_ANON_KEY, cleanKey)
            .apply()

        // Re-initialize Supabase client immediately
        SupabaseManager.updateCredentials(cleanUrl, cleanKey)
        return true
    }

    fun clearConfig(context: Context) {
        getPrefs(context).edit().clear().apply()
        SupabaseManager.updateCredentials("", "")
    }
}
