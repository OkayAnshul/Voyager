package com.cosmiclaboratory.voyager.storage.encryption

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Android Keystore operations for AES key management.
 * Keys stored in the hardware-backed (or TEE-backed) Keystore
 * are not extractable and survive app reinstalls on the same device.
 */
@Singleton
class KeystoreManager @Inject constructor() {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }

    /**
     * Generates a new AES-256 key under [alias].
     * If a key with the same alias already exists it is overwritten.
     */
    fun generateKey(alias: String): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // Must be false: DatabaseEncryptionManager derives a deterministic SQLCipher
            // passphrase by encrypting a fixed plaintext with a fixed IV. Randomized
            // encryption would force a per-call random IV and produce a different
            // passphrase every open, making the database unreadable.
            .setRandomizedEncryptionRequired(false)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }

    /**
     * Retrieves an existing key for [alias], or null if it doesn't exist.
     */
    fun getKey(alias: String): SecretKey? {
        val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey
    }

    /**
     * Deletes the key stored under [alias]. No-op if the alias doesn't exist.
     */
    fun deleteKey(alias: String) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    /**
     * Returns true if a key with [alias] exists in the Keystore.
     */
    fun keyExists(alias: String): Boolean = keyStore.containsAlias(alias)
}
