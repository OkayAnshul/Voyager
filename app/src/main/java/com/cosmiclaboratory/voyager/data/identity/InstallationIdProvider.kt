package com.cosmiclaboratory.voyager.data.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The stable per-install identity — a UUID generated once on first use and persisted
 * in DataStore.
 *
 * This is the value the `userId` column on syncable entities will carry once
 * multi-user / sync ships (a single-user device is one "user" = this install).
 * Provided now so that wiring is a population step, not a schema change.
 */
@Singleton
class InstallationIdProvider @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val key = stringPreferencesKey("installation_id")

    /** Returns the install id, generating and persisting it on first call. */
    suspend fun get(): String {
        dataStore.data.map { it[key] }.first()?.let { return it }
        val id = UUID.randomUUID().toString()
        dataStore.edit { prefs -> prefs[key] = prefs[key] ?: id }
        return dataStore.data.map { it[key] }.first() ?: id
    }
}
