package com.cosmiclaboratory.voyager.utils

import android.content.Context

object SecurityUtils {
    
    private const val PREFS_NAME = "voyager_secure_prefs"
    private const val DATABASE_PASSPHRASE_KEY = "database_passphrase"
    
    fun getDatabasePassphrase(context: Context): String {
        return try {
            // Try to get existing passphrase from regular SharedPreferences for now
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(DATABASE_PASSPHRASE_KEY, null) ?: generateAndStoreDatabasePassphrase(context)
        } catch (e: Exception) {
            // Fallback: generate a new passphrase
            generateAndStoreDatabasePassphrase(context)
        }
    }
    
    private fun generateAndStoreDatabasePassphrase(context: Context): String {
        val passphrase = generateSecurePassphrase()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(DATABASE_PASSPHRASE_KEY, passphrase)
            .apply()
        return passphrase
    }
    
    private fun generateSecurePassphrase(): String {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        return (1..32)
            .map { charset.random() }
            .joinToString("")
    }
    
    fun clearStoredPassphrase(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove(DATABASE_PASSPHRASE_KEY)
                .apply()
        } catch (e: Exception) {
            // Handle error silently
        }
    }
}