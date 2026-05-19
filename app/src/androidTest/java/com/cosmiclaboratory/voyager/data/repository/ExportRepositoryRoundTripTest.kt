package com.cosmiclaboratory.voyager.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cosmiclaboratory.voyager.domain.model.DateRange
import com.cosmiclaboratory.voyager.domain.model.UserSettings
import com.cosmiclaboratory.voyager.domain.model.enums.ExportFormat
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.storage.database.VoyagerDatabase
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.RawLocationSampleEntity
import com.cosmiclaboratory.voyager.storage.database.entity.TrackingSessionEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * End-to-end VoyagerJSON export → import round trip for [ExportRepositoryImpl].
 *
 * Covers the two Phase 3.8 changes: `exportRange` over a multi-day window, and
 * raw-sample import — every restored sample is parented to one synthetic "IMPORT"
 * tracking session (the FK that previously made raw samples export-only).
 */
@RunWith(AndroidJUnit4::class)
class ExportRepositoryRoundTripTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.systemDefault()
    private val day1 = LocalDate.parse("2024-06-01")
    private val day2 = LocalDate.parse("2024-06-02")

    private lateinit var sourceDb: VoyagerDatabase
    private lateinit var targetDb: VoyagerDatabase

    /** Minimal [SettingsRepository] — only [observeSettings] is exercised by export. */
    private class FakeSettings(settings: UserSettings) : SettingsRepository {
        private val flow = MutableStateFlow(settings)
        override fun observeSettings(): StateFlow<UserSettings> = flow
        override suspend fun updateSetting(key: String, value: Any) = Result.success(Unit)
        override suspend fun applyPreset(presetId: String) = Result.success(Unit)
        override suspend fun exportSettings() = Result.success("{}")
        override suspend fun importSettings(json: String) = Result.success(Unit)
    }

    private fun newDb(): VoyagerDatabase =
        Room.inMemoryDatabaseBuilder(context, VoyagerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private fun repoFor(db: VoyagerDatabase) = ExportRepositoryImpl(
        context = context,
        database = db,
        movementSegmentDao = db.movementSegmentDao(),
        visitDao = db.visitDao(),
        routeDao = db.routeDao(),
        placeDao = db.placeDao(),
        rawLocationSampleDao = db.rawLocationSampleDao(),
        trackingSessionDao = db.trackingSessionDao(),
        settingsRepository = FakeSettings(UserSettings(exportIncludeRawSamples = true))
    )

    private fun epochMs(date: LocalDate, hour: Int): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        sourceDb = newDb()
        targetDb = newDb()
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    /**
     * Seeds the source DB with a 2-day graph + raw samples, exports the range,
     * imports it into the target DB and returns the import counts.
     */
    private fun roundTrip() = runBlocking {
        val sessionId = sourceDb.trackingSessionDao().insert(
            TrackingSessionEntity(
                startedAt = epochMs(day1, 8),
                startedBy = "USER",
                localTimeZone = zone.id
            )
        )
        val placeId = sourceDb.placeDao().insert(
            PlaceEntity(
                centroidLat = 25.43, centroidLng = 81.84, geohash = "tsy12345",
                lifecycleStatus = "CONFIRMED", category = "HOME", createdAt = epochMs(day1, 8)
            )
        )
        for ((i, day) in listOf(day1, day2).withIndex()) {
            sourceDb.visitDao().insert(
                VisitEntity(
                    placeId = placeId, arrivalAt = epochMs(day, 9), departureAt = epochMs(day, 12),
                    dwellMs = 3 * 3_600_000L, source = "BATCH_DISCOVERY",
                    dayKey = day.toString(), centroidLat = 25.43, centroidLng = 81.84
                )
            )
            sourceDb.movementSegmentDao().insert(
                MovementSegmentEntity(
                    segmentType = "DRIVE", startAt = epochMs(day, 12), endAt = epochMs(day, 13),
                    distanceM = 8_000.0, dayKey = day.toString()
                )
            )
            sourceDb.rawLocationSampleDao().insert(
                RawLocationSampleEntity(
                    capturedAt = epochMs(day, 10), receivedAt = epochMs(day, 10) + 100,
                    lat = 25.43 + i * 0.01, lng = 81.84, accuracyM = 9f, provider = "fused",
                    permissionSnapshot = "fine", trackingSessionId = sessionId,
                    localTimeZone = zone.id, geohash = "tsy12345"
                )
            )
        }

        val uri = repoFor(sourceDb)
            .exportRange(DateRange(day1.toString(), day2.toString()), ExportFormat.VOYAGER_JSON)
            .getOrThrow()

        repoFor(targetDb).importData(uri, ExportFormat.VOYAGER_JSON).getOrThrow()
    }

    @Test
    fun exportRange_importData_roundTrip_preservesMultiDayGraph() {
        val summary = roundTrip()
        assertThat(summary.placesImported).isEqualTo(1)
        assertThat(summary.visitsImported).isEqualTo(2)
        assertThat(summary.segmentsImported).isEqualTo(2)
    }

    @Test
    fun rawSampleImport_parentsAllSamplesToOneSyntheticSession() = runBlocking {
        val summary = roundTrip()
        assertThat(summary.rawSamplesImported).isEqualTo(2)

        val sessions = targetDb.trackingSessionDao().getAll()
        assertThat(sessions).hasSize(1)
        assertThat(sessions.single().startedBy).isEqualTo("IMPORT")
    }
}
