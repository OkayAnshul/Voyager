package com.cosmiclaboratory.voyager.platform.export

import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses a GPX 1.1 track into [RoutePoint]s so a user can bring their Strava/Garmin/Komoot history
 * into Voyager. Reads `<trkpt lat lon>` with optional `<ele>` and `<time>`. Pure and JVM-parseable
 * (DOM), so it's unit-tested; the DAO insert lives in
 * [com.cosmiclaboratory.voyager.domain.usecase.ImportGpxUseCase].
 */
object GpxImporter {

    data class ParsedGpx(val name: String?, val points: List<RoutePoint>) {
        val hasTimes: Boolean get() = points.any { it.timeMs > 0 }
    }

    fun parse(xml: String): ParsedGpx {
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
        doc.documentElement.normalize()

        val name = firstTagText(doc.documentElement, "name")
        val trkpts = doc.getElementsByTagName("trkpt")
        val points = ArrayList<RoutePoint>(trkpts.length)
        for (i in 0 until trkpts.length) {
            val el = trkpts.item(i) as? Element ?: continue
            val lat = el.getAttribute("lat").toDoubleOrNull() ?: continue
            val lon = el.getAttribute("lon").toDoubleOrNull() ?: continue
            val ele = childText(el, "ele")?.toDoubleOrNull()
            val time = childText(el, "time")?.let { parseTime(it) } ?: 0L
            points.add(RoutePoint(lat = lat, lng = lon, timeMs = time, altitudeM = ele))
        }
        return ParsedGpx(name, points)
    }

    private fun childText(el: Element, tag: String): String? =
        el.getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun firstTagText(root: Element, tag: String): String? =
        root.getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseTime(iso: String): Long =
        runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)
}
