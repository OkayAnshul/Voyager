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
 * Tracks whether the user has completed the first-run feature walkthrough.
 * Once set to true, the walkthrough is never shown again unless the user
 * clears app data.
 */
@Singleton
class WalkthroughPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val hasSeenKey = booleanPreferencesKey("has_seen_feature_walkthrough")

    val hasSeen: Flow<Boolean> = dataStore.data.map { it[hasSeenKey] ?: false }

    suspend fun markSeen() {
        dataStore.edit { it[hasSeenKey] = true }
    }
}
