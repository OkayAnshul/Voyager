package com.cosmiclaboratory.voyager.presentation.screen.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the user has seen the first-run intro screen (what Voyager does +
 * how it's different). Once set to true, the intro is never shown again unless the
 * user clears app data.
 */
@Singleton
class IntroPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val hasSeenKey = booleanPreferencesKey("has_seen_intro")

    val hasSeen: Flow<Boolean> = dataStore.data.map { it[hasSeenKey] ?: false }

    suspend fun markSeen() {
        dataStore.edit { it[hasSeenKey] = true }
    }
}
