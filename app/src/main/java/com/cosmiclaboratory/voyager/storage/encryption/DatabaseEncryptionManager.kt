package com.cosmiclaboratory.voyager.storage.encryption

import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages SQLCipher database encryption using a key stored in Android Keystore.
 *
 * Flow:
 *  1. On first use, [generateOrRetrieveKey] creates an AES-256 key in the Keystore.
 *  2. [getPassphrase] derives a stable passphrase from the key (encrypt a fixed plaintext).
 *  3. The passphrase is passed to SQLCipher's SupportFactory.
 *
 * Migration between encrypted and unencrypted databases is stubbed for v1.
 */
@Singleton
class DatabaseEncryptionManager @Inject constructor(
    private val keystoreManager: KeystoreManager,
    private val settingsRepository: SettingsRepository
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

    /**
     * Checks whether database encryption is enabled in user settings.
     */
    suspend fun isEncryptionEnabled(): Boolean {
        val settings = settingsRepository.observeSettings().first()
        return settings.databaseEncryptionEnabled
    }

    /**
     * Migrates an unencrypted database to an encrypted one.
     * Stub for v1 — will use SQLCipher's sqlcipher_export in a future release.
     */
    suspend fun migrateToEncrypted(): Result<Unit> {
        // TODO: Implement in v2
        //  1. Open unencrypted DB
        //  2. ATTACH encrypted DB with new passphrase
        //  3. SELECT sqlcipher_export('encrypted')
        //  4. Swap files
        return Result.success(Unit)
    }

    /**
     * Migrates an encrypted database back to unencrypted.
     * Stub for v1 — will use SQLCipher's sqlcipher_export in a future release.
     */
    suspend fun migrateToUnencrypted(): Result<Unit> {
        // TODO: Implement in v2
        return Result.success(Unit)
    }
}
