package com.cosmiclaboratory.voyager.data.imports

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Tests for [GoogleTimelineImporter] parsing & mapping — the pure, DB-free half.
 * The DB deps are mocked and never touched by [GoogleTimelineImporter.parseAndMap].
 */
class GoogleTimelineImporterTest {

    private val importer = GoogleTimelineImporter(
        context = mockk(),
        database = mockk(),
        placeDao = mockk(),
        visitDao = mockk(),
        movementSegmentDao = mockk(),
    )

    private fun parse(json: String) =
        importer.parseAndMap(ByteArrayInputStream(json.toByteArray()))

    @Test
    fun `legacy Semantic Location History maps places, visits and segments`() {
        val json = """
            {"timelineObjects": [
              {"placeVisit": {
                "location": {"latitudeE7": 377749000, "longitudeE7": -1224194000,
                             "placeId": "PLACE_A", "name": "Home", "semanticType": "TYPE_HOME"},
                "duration": {"startTimestamp": "2023-01-15T08:00:00.000Z",
                             "endTimestamp": "2023-01-15T09:00:00.000Z"}}},
              {"activitySegment": {
                "duration": {"startTimestamp": "2023-01-15T09:00:00.000Z",
                             "endTimestamp": "2023-01-15T09:30:00.000Z"},
                "distance": 4200, "activityType": "WALKING"}}
            ]}
        """.trimIndent()

        val parsed = parse(json)

        assertThat(parsed.places).hasSize(1)
        assertThat(parsed.places[0].key).isEqualTo("PLACE_A")
        assertThat(parsed.places[0].entity.centroidLat).isWithin(1e-4).of(37.7749)
        assertThat(parsed.places[0].entity.centroidLng).isWithin(1e-4).of(-122.4194)
        assertThat(parsed.places[0].entity.category).isEqualTo("HOME")

        assertThat(parsed.visits).hasSize(1)
        assertThat(parsed.visits[0].placeKey).isEqualTo("PLACE_A")
        assertThat(parsed.visits[0].entity.dwellMs).isEqualTo(3_600_000L)

        assertThat(parsed.segments).hasSize(1)
        assertThat(parsed.segments[0].segmentType).isEqualTo("WALK")
        assertThat(parsed.segments[0].distanceM).isEqualTo(4200.0)
    }

    @Test
    fun `new Timeline json maps visits and activities`() {
        val json = """
            {"semanticSegments": [
              {"startTime": "2024-03-10T10:00:00.000Z", "endTime": "2024-03-10T11:00:00.000Z",
               "visit": {"topCandidate": {"placeId": "PLACE_X", "semanticType": "Work",
                         "placeLocation": {"latLng": "40.7128°, -74.0060°"}}}},
              {"startTime": "2024-03-10T11:00:00.000Z", "endTime": "2024-03-10T11:20:00.000Z",
               "activity": {"distanceMeters": 1500.0,
                            "topCandidate": {"type": "in passenger vehicle"}}}
            ]}
        """.trimIndent()

        val parsed = parse(json)

        assertThat(parsed.places).hasSize(1)
        assertThat(parsed.places[0].entity.category).isEqualTo("WORK")
        assertThat(parsed.places[0].entity.centroidLat).isWithin(1e-4).of(40.7128)
        assertThat(parsed.visits).hasSize(1)
        assertThat(parsed.segments).hasSize(1)
        assertThat(parsed.segments[0].segmentType).isEqualTo("DRIVE")
        assertThat(parsed.segments[0].distanceM).isEqualTo(1500.0)
    }

    @Test
    fun `repeated place id is deduplicated to one place with two visits`() {
        val json = """
            {"timelineObjects": [
              {"placeVisit": {"location": {"latitudeE7": 100000000, "longitudeE7": 200000000,
                "placeId": "DUP"},
                "duration": {"startTimestamp": "2023-01-15T08:00:00Z",
                             "endTimestamp": "2023-01-15T09:00:00Z"}}},
              {"placeVisit": {"location": {"latitudeE7": 100000000, "longitudeE7": 200000000,
                "placeId": "DUP"},
                "duration": {"startTimestamp": "2023-01-16T08:00:00Z",
                             "endTimestamp": "2023-01-16T09:00:00Z"}}}
            ]}
        """.trimIndent()

        val parsed = parse(json)

        assertThat(parsed.places).hasSize(1)
        assertThat(parsed.visits).hasSize(2)
    }

    @Test
    fun `raw Records json yields nothing — only locations, no semantic data`() {
        val json = """{"locations": [{"latitudeE7": 1, "longitudeE7": 2, "timestampMs": "3"}]}"""

        val parsed = parse(json)

        assertThat(parsed.places).isEmpty()
        assertThat(parsed.visits).isEmpty()
        assertThat(parsed.segments).isEmpty()
    }

    @Test
    fun `activity label mapping covers the common Google types`() {
        assertThat(importer.mapActivity("WALKING")).isEqualTo("WALK")
        assertThat(importer.mapActivity("running")).isEqualTo("RUN")
        assertThat(importer.mapActivity("IN_PASSENGER_VEHICLE")).isEqualTo("DRIVE")
        assertThat(importer.mapActivity("in bus")).isEqualTo("TRANSIT")
        assertThat(importer.mapActivity("cycling")).isEqualTo("CYCLE")
        assertThat(importer.mapActivity("flying")).isEqualTo("FLIGHT")
        assertThat(importer.mapActivity(null)).isEqualTo("UNKNOWN_MOTION")
        assertThat(importer.mapActivity("teleporting")).isEqualTo("UNKNOWN_MOTION")
    }

    @Test
    fun `latLng string parsing handles the degree-symbol format`() {
        assertThat(importer.parseLatLng("40.7128°, -74.0060°"))
            .isEqualTo(40.7128 to -74.0060)
        assertThat(importer.parseLatLng("bad input")).isNull()
        assertThat(importer.parseLatLng("999.0°, 0.0°")).isNull()
    }

    @Test
    fun `timestamp parsing accepts ISO-8601 and epoch millis`() {
        assertThat(importer.parseTimestamp("1970-01-01T00:00:01Z")).isEqualTo(1000L)
        assertThat(importer.parseTimestamp("1500")).isEqualTo(1500L)
        assertThat(importer.parseTimestamp("not a date")).isNull()
        assertThat(importer.parseTimestamp(null)).isNull()
    }
}
