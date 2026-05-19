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
 * Tracks whether the first-launch "import from Google Timeline" prompt has been
 * shown. Once seen — whether the user imported or skipped — it is never shown again
 * unless app data is cleared, so a user migrating from Google Maps Timeline is
 * offered the import exactly once, before they start building a fresh timeline.
 */
@Singleton
class GoogleTimelineImportPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val hasSeenKey = booleanPreferencesKey("has_seen_google_import_prompt")

    val hasSeen: Flow<Boolean> = dataStore.data.map { it[hasSeenKey] ?: false }

    suspend fun markSeen() {
        dataStore.edit { it[hasSeenKey] = true }
    }
}
