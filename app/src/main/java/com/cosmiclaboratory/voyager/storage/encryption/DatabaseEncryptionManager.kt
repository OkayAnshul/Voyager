package com.cosmiclaboratory.voyager.storage.encryption

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages SQLCipher database encryption using a key stored in Android Keystore.
 *
 * Encryption is always on — there is no unencrypted mode. The passphrase derives
 * from a non-extractable Keystore key, so the database can only be opened on the
 * device that created it.
 *
 * Flow:
 *  1. On first use, [generateOrRetrieveKey] creates an AES-256 key in the Keystore.
 *  2. [getPassphrase] derives a stable passphrase from the key (encrypt a fixed plaintext).
 *  3. The passphrase is passed to SQLCipher's SupportFactory.
 */
@Singleton
class DatabaseEncryptionManager @Inject constructor(
    private val keystoreManager: KeystoreManager
) {

    companion object {
        private const val KEY_ALIAS = "voyager_db_encryption_key"
        /** Fixed plaintext used to derive a deterministic passphrase from the Keystore key. */
        private const val PASSPHRASE_DERIVATION_INPUT = "voyager-sqlcipher-passphrase-v1"
        private const val GCM_TAG_LENGTH = 128
    }

    /**
     * Generates a new AES key in Android Keystore if one doesn't already exist,
     * otherwise retrieves the existing key.
     */
    fun generateOrRetrieveKey(): SecretKey {
        return keystoreManager.getKey(KEY_ALIAS)
            ?: keystoreManager.generateKey(KEY_ALIAS)
    }

    /**
     * Returns the passphrase bytes to pass to SQLCipher's SupportFactory.
     * The passphrase is derived by encrypting a fixed string with the Keystore key,
     * producing a stable byte sequence unique to this device + key.
     */
    fun getPassphrase(): ByteArray {
        val key = generateOrRetrieveKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // Use a fixed IV so the output is deterministic for the same key.
        // Security note: this is acceptable because the "plaintext" is fixed
        // and the output is used only as a passphrase, not for data encryption.
        val fixedIv = ByteArray(12) { (it + 0x42).toByte() }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, fixedIv))
        return cipher.doFinal(PASSPHRASE_DERIVATION_INPUT.toByteArray(Charsets.UTF_8))
    }
}
