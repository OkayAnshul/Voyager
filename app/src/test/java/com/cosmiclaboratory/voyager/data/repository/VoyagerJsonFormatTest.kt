package com.cosmiclaboratory.voyager.data.repository

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the VoyagerJSON import/restore format contract (W7.2): the file format must outlive
 * internal refactors, so v1 files keep parsing, newer files don't break older apps, and the
 * wire model round-trips losslessly. Pure kotlinx.serialization — no DAO/Android.
 *
 * Mirrors the [Json] config `ExportRepositoryImpl` uses for import.
 */
class VoyagerJsonFormatTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `a v1 file without rawSamples parses with an empty rawSamples list`() {
        // v2 added rawSamples additively; a v1 file omits the field entirely.
        val v1 = """{"version":1,"exportedAt":123,"appVersion":"1.0","places":[],"segments":[],"visits":[]}"""

        val parsed = json.decodeFromString(VoyagerJsonExport.serializer(), v1)

        assertEquals(1, parsed.version)
        assertTrue("rawSamples must default to empty for v1", parsed.rawSamples.isEmpty())
        assertFalse("coordsStripped must default to false", parsed.coordsStripped)
    }

    @Test
    fun `unknown future fields are ignored so a newer file still parses`() {
        val future = """
            {"version":2,"exportedAt":1,"appVersion":"9.0","newTopLevelField":"x",
             "places":[],"segments":[],"visits":[],"rawSamples":[]}
        """.trimIndent()

        val parsed = json.decodeFromString(VoyagerJsonExport.serializer(), future)

        assertEquals(2, parsed.version)
    }

    @Test
    fun `the export envelope round-trips losslessly`() {
        val original = VoyagerJsonExport(
            version = VoyagerJsonExport.CURRENT_VERSION,
            exportedAt = 1_700_000_000_000L,
            appVersion = "1.2.3",
            coordsStripped = true,
            places = emptyList(),
            segments = emptyList(),
            visits = emptyList(),
            rawSamples = listOf(
                RawSampleWire(
                    capturedAt = 1L, receivedAt = 2L, lat = 12.34567, lng = 76.54321,
                    accuracyM = 5f, provider = "gps", permissionSnapshot = "FINE",
                    localTimeZone = "Asia/Kolkata", geohash = "abc"
                )
            )
        )

        val decoded = json.decodeFromString(
            VoyagerJsonExport.serializer(),
            json.encodeToString(VoyagerJsonExport.serializer(), original)
        )

        assertEquals(original, decoded)
    }

    @Test
    fun `a raw sample round-trips with every optional field preserved`() {
        // The heaviest wire type — all nullable/defaulted fields must survive a round trip.
        val sample = RawSampleWire(
            capturedAt = 10L, receivedAt = 11L, lat = 1.0, lng = 2.0, accuracyM = 3f,
            verticalAccuracyM = 4f, speedMps = 5f, bearingDeg = 6f, altitudeM = 7.0,
            provider = "fused", isMock = true, batteryPct = 80, isCharging = true,
            deviceIdleMode = true, permissionSnapshot = "FINE", localTimeZone = "UTC", geohash = "xyz"
        )

        val decoded = json.decodeFromString(
            RawSampleWire.serializer(),
            json.encodeToString(RawSampleWire.serializer(), sample)
        )

        assertEquals(sample, decoded)
    }

    @Test
    fun `a file newer than this app reports the version it carries`() {
        // importData gates on version <= CURRENT_VERSION; the parsed version drives that check.
        val newer = """{"version":99,"exportedAt":1,"appVersion":"x","places":[],"segments":[],"visits":[]}"""

        val parsed = json.decodeFromString(VoyagerJsonExport.serializer(), newer)

        assertTrue(parsed.version > VoyagerJsonExport.CURRENT_VERSION)
    }
}
