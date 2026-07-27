package com.cosmiclaboratory.voyager.platform.export

import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.usecase.WorkoutStatsCalculator
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Serialises a recorded [Activity]'s route to GPX 1.1 — the universal exchange format every
 * fitness app (Strava, Garmin, Komoot…) imports.
 *
 * Emits `<ele>` (elevation) and `<time>` per track point when the activity carries the per-point
 * streams (recorded on v11+); older rows with only a lat/lng polyline degrade to plain track
 * points. Pure string output, so it's unit-testable; the file-write/share wrapper lives in the UI.
 */
object ActivityGpxExporter {

    private val ISO_TIME: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    fun toGpx(activity: Activity): String {
        val points = WorkoutStatsCalculator.reconstruct(
            encodedPolyline = activity.encodedPolyline,
            encodedTimes = activity.encodedTimes,
            encodedAltitudes = activity.encodedAltitudes,
            startedAt = activity.startedAt
        )
        val hasTime = activity.encodedTimes.isNotEmpty()

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="Voyager" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("""  <trk><name>${escapeXml(activity.displayTitle)}</name><trkseg>""")
        for (p in points) {
            val ele = p.altitudeM
            if (ele == null && !hasTime) {
                sb.appendLine("""    <trkpt lat="${p.lat}" lon="${p.lng}"/>""")
            } else {
                sb.append("""    <trkpt lat="${p.lat}" lon="${p.lng}">""")
                if (ele != null) sb.append("<ele>${String.format(Locale.US, "%.1f", ele)}</ele>")
                if (hasTime) sb.append("<time>${ISO_TIME.format(Instant.ofEpochMilli(p.timeMs))}</time>")
                sb.appendLine("</trkpt>")
            }
        }
        sb.appendLine("  </trkseg></trk>")
        sb.appendLine("</gpx>")
        return sb.toString()
    }

    /** & first to avoid double-escaping. */
    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
