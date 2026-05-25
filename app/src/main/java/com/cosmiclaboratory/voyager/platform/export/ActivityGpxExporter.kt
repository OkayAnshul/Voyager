package com.cosmiclaboratory.voyager.platform.export

import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder

/**
 * Serialises a recorded [Activity]'s route to GPX 1.1 — the universal exchange
 * format every fitness app (Strava, Garmin, Komoot…) imports.
 *
 * Pure string output (the route is already an encoded polyline), so it's
 * unit-testable; the file-write/share wrapper lives with the (deferred) UI.
 */
object ActivityGpxExporter {

    fun toGpx(activity: Activity): String {
        val points = PolylineEncoder.decode(activity.encodedPolyline)
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="Voyager" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("""  <trk><name>${escapeXml(activity.displayTitle)}</name><trkseg>""")
        for ((lat, lng) in points) {
            sb.appendLine("""    <trkpt lat="$lat" lon="$lng"/>""")
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
