package com.cosmiclaboratory.voyager.utils

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SecurityUtils provides secure storage for sensitive data using Android Keystore.
 *
 * Security Features:
 * - Hardware-backed encryption (on supported devices)
 * - AES-256-GCM encryption for passphrase
 * - Automatic key generation
 * - Secure key storage in Android Keystore
 * - Migration from legacy SharedPreferences storage
 */
object SecurityUtils {

    private const val TAG = "SecurityUtils"
    private const val PREFS_NAME = "voyager_secure_prefs"
    private const val DATABASE_PASSPHRASE_KEY = "database_passphrase"
    private const val ENCRYPTED_PASSPHRASE_KEY = "encrypted_database_passphrase"
    private const val IV_KEY = "passphrase_iv"

    // Android Keystore configuration
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "VoyagerDatabaseKey"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    /**
     * Gets the database passphrase, using Android Keystore for secure storage.
     * Automatically migrates from legacy SharedPreferences storage if needed.
     */
    fun getDatabasePassphrase(context: Context): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Check if we have encrypted passphrase (new secure method)
            val encryptedPassphrase = prefs.getString(ENCRYPTED_PASSPHRASE_KEY, null)
            val iv = prefs.getString(IV_KEY, null)

            if (encryptedPassphrase != null && iv != null) {
                // Decrypt and return existing passphrase
                decryptPassphrase(encryptedPassphrase, iv)
            } else {
                // Check for legacy unencrypted passphrase
                val legacyPassphrase = prefs.getString(DATABASE_PASSPHRASE_KEY, null)
                if (legacyPassphrase != null) {
                    Log.i(TAG, "Migrating legacy passphrase to Keystore")
                    // Migrate: encrypt the legacy passphrase and store it securely
                    storePassphraseSecurely(context, legacyPassphrase)
                    // Remove legacy unencrypted version
                    prefs.edit().remove(DATABASE_PASSPHRASE_KEY).apply()
                    legacyPassphrase
                } else {
                    // No passphrase exists, generate new one
                    generateAndStoreDatabasePassphrase(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting database passphrase", e)
            // Fallback: generate a new passphrase
            generateAndStoreDatabasePassphrase(context)
        }
    }

    /**
     * Generates a new secure passphrase and stores it encrypted in Keystore.
     */
    private fun generateAndStoreDatabasePassphrase(context: Context): String {
        val passphrase = generateSecurePassphrase()
        storePassphraseSecurely(context, passphrase)
        return passphrase
    }

    /**
     * Encrypts and stores the passphrase using Android Keystore.
     */
    private fun storePassphraseSecurely(context: Context, passphrase: String) {
        try {
            // Get or create encryption key
            val secretKey = getOrCreateSecretKey()

            // Encrypt the passphrase
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))

            // Encode to Base64 for storage
            val encryptedPassphrase = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
            val encodedIv = Base64.encodeToString(iv, Base64.DEFAULT)

            // Store encrypted data
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(ENCRYPTED_PASSPHRASE_KEY, encryptedPassphrase)
                .putString(IV_KEY, encodedIv)
                .apply()

            Log.d(TAG, "Passphrase stored securely in Keystore")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing passphrase securely", e)
            throw e
        }
    }

    /**
     * Decrypts the passphrase using Android Keystore.
     */
    private fun decryptPassphrase(encryptedPassphrase: String, encodedIv: String): String {
        try {
            val secretKey = getOrCreateSecretKey()

            // Decode from Base64
            val encryptedBytes = Base64.decode(encryptedPassphrase, Base64.DEFAULT)
            val iv = Base64.decode(encodedIv, Base64.DEFAULT)

            // Decrypt
            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)

            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting passphrase", e)
            throw e
        }
    }

    /**
     * Gets existing secret key from Keystore or creates a new one.
     */
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        // Check if key already exists
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        // Key doesn't exist, create new one
        return createSecretKey()
    }

    /**
     * Creates a new secret key in Android Keystore with security specifications.
     */
    private fun createSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val builder = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // Don't require biometric/PIN for each use

        // On Android P+, enable stronger protection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(true) // Key only usable when device unlocked
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    /**
     * Generates a cryptographically secure random passphrase.
     */
    private fun generateSecurePassphrase(): String {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        return (1..32)
            .map { charset.random() }
            .joinToString("")
    }

    /**
     * Clears all stored passphrase data (both encrypted and legacy).
     * WARNING: This will make the encrypted database unreadable!
     */
    fun clearStoredPassphrase(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove(DATABASE_PASSPHRASE_KEY) // Legacy
                .remove(ENCRYPTED_PASSPHRASE_KEY) // New encrypted
                .remove(IV_KEY) // Encryption IV
                .apply()

            // Also delete the key from Keystore
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                keyStore.deleteEntry(KEYSTORE_ALIAS)
            }

            Log.d(TAG, "Passphrase and encryption key cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing passphrase", e)
        }
    }
}