package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cosmiclaboratory.voyager.storage.database.VoyagerDatabase
import com.cosmiclaboratory.voyager.storage.database.entity.TripEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies [TripDao.replaceAll] preserves user-authored fields across a detection
 * rebuild — the core of the v6 trip-storytelling change.
 */
@RunWith(AndroidJUnit4::class)
class TripDaoTest {

    private lateinit var db: VoyagerDatabase
    private lateinit var dao: TripDao

    private fun trip(startDayKey: String, title: String, distance: Double) = TripEntity(
        startDayKey = startDayKey,
        endDayKey = startDayKey,
        title = title,
        placeCount = 1,
        visitCount = 1,
        distanceMeters = distance,
        isOngoing = false,
        detectedAt = 0L
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, VoyagerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.tripDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun replaceAll_preservesUserFields_andRefreshesSummary() = runBlocking {
        // A detected trip the user has since named and annotated.
        val id = dao.insert(trip("2026-05-01", title = "Trip to Paris", distance = 80_000.0))
        dao.updateUserFields(
            tripId = id,
            userTitle = "Honeymoon",
            notes = "best week",
            coverPhotoUri = "content://photo/1",
            dayCaptionsJson = """{"2026-05-01":"arrived"}""",
            modifiedAt = 5_000L
        )

        // Re-detection produces the same trip (same startDayKey) with a fresh summary.
        dao.replaceAll(listOf(trip("2026-05-01", title = "Trip to Paris", distance = 95_000.0)))

        val after = dao.getAll().single()
        // User-authored fields survived the rebuild...
        assertThat(after.userTitle).isEqualTo("Honeymoon")
        assertThat(after.notes).isEqualTo("best week")
        assertThat(after.coverPhotoUri).isEqualTo("content://photo/1")
        assertThat(after.dayCaptionsJson).isEqualTo("""{"2026-05-01":"arrived"}""")
        // ...while the derived summary was refreshed.
        assertThat(after.distanceMeters).isEqualTo(95_000.0)
    }

    @Test
    fun replaceAll_dropsVanishedTrips_andAddsNewOnesClean() = runBlocking {
        val oldId = dao.insert(trip("2026-05-01", "Trip to Paris", 80_000.0))
        dao.updateUserFields(oldId, "Honeymoon", null, null, null, 1_000L)

        // Re-detection no longer finds the May trip but finds a new June one.
        dao.replaceAll(listOf(trip("2026-06-10", "Trip to Goa", 60_000.0)))

        val all = dao.getAll()
        assertThat(all).hasSize(1)
        assertThat(all.single().startDayKey).isEqualTo("2026-06-10")
        // A brand-new trip carries no user fields.
        assertThat(all.single().userTitle).isNull()
    }
}
