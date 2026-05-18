package com.cosmiclaboratory.voyager.platform.export

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.cosmiclaboratory.voyager.domain.model.TripDay
import com.cosmiclaboratory.voyager.domain.model.TripDetail
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders a [TripDetail] to a printable "trip book" PDF using the platform [PdfDocument].
 *
 * Layout is US Letter (612×792 pt): a cover page with the trip's headline stats, then a
 * day-by-day journal of the places visited. Designed to read like a keepsake, not a
 * spreadsheet — the answer to Polarsteps' printed photo books, fully offline.
 */
@Singleton
class TripBookPdfExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private companion object {
        const val PAGE_WIDTH = 612
        const val PAGE_HEIGHT = 792
        const val MARGIN = 56f
        const val LINE = 16f

        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
        val RANGE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
        val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Writes [detail] to a cached PDF and returns a shareable [Uri]. */
    fun export(detail: TripDetail): Result<Uri> = runCatching {
        val doc = PdfDocument()
        try {
            renderInto(doc, detail)
        } catch (e: Exception) {
            doc.close()
            throw e
        }

        val file = File(context.cacheDir, "voyager_trip_${System.currentTimeMillis()}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun renderInto(doc: PdfDocument, detail: TripDetail) {
        val cover = Paint().apply { textSize = 30f; isFakeBoldText = true }
        val subtitle = Paint().apply { textSize = 13f; color = 0xFF444444.toInt() }
        val heading = Paint().apply { textSize = 14f; isFakeBoldText = true }
        val body = Paint().apply { textSize = 10.5f }
        val muted = Paint().apply { textSize = 9f; color = 0xFF666666.toInt() }

        val trip = detail.trip
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = page.canvas
        var y = MARGIN * 2.2f

        // ── Cover ─────────────────────────────────────────────────────
        canvas.drawText("TRIP BOOK", MARGIN, y, muted); y += LINE * 2.2f
        canvas.drawText(trip.title, MARGIN, y, cover); y += LINE * 2.4f

        val start = LocalDate.parse(trip.startDayKey)
        val end = LocalDate.parse(trip.endDayKey)
        canvas.drawText(
            "${RANGE_FMT.format(start)}  —  ${RANGE_FMT.format(end)}",
            MARGIN, y, subtitle
        ); y += LINE * 2f

        canvas.drawText("${trip.durationDays} days", MARGIN, y, heading); y += LINE * 1.4f
        canvas.drawText("${trip.placeCount} places visited", MARGIN, y, body); y += LINE
        canvas.drawText("${trip.visitCount} stops", MARGIN, y, body); y += LINE
        canvas.drawText("%.1f km travelled".format(trip.distanceKm), MARGIN, y, body); y += LINE
        if (trip.isOngoing) {
            y += LINE * 0.4f
            canvas.drawText("This trip may still be in progress.", MARGIN, y, muted)
        }

        // ── One section per day ───────────────────────────────────────
        y = PAGE_HEIGHT.toFloat() // force a fresh page for the journal
        for (day in detail.days) {
            val needed = LINE * (3 + day.places.size)
            if (y + needed > PAGE_HEIGHT - MARGIN) {
                if (y < PAGE_HEIGHT.toFloat()) doc.finishPage(page)
                pageNum += 1
                page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                )
                canvas = page.canvas
                y = MARGIN
            }
            y = drawDay(canvas, day, y, heading, body, muted)
            y += LINE * 1.2f
        }

        if (detail.days.isEmpty()) {
            canvas.drawText("No recorded days for this trip.", MARGIN, y, body)
        }

        doc.finishPage(page)
    }

    private fun drawDay(
        canvas: android.graphics.Canvas,
        day: TripDay,
        startY: Float,
        heading: Paint,
        body: Paint,
        muted: Paint
    ): Float {
        var y = startY
        val date = runCatching { LocalDate.parse(day.dayKey) }.getOrNull()
        canvas.drawText(date?.let { DATE_FMT.format(it) } ?: day.dayKey, MARGIN, y, heading)
        y += LINE
        canvas.drawText(
            "%.1f km · %d place(s)".format(day.distanceMeters / 1000.0, day.places.size),
            MARGIN, y, muted
        )
        y += LINE * 1.1f
        for (place in day.places) {
            val arrival = TIME_FMT.format(Instant.ofEpochMilli(place.arrivalAt).atZone(zone))
            val prefix = place.emoji?.let { "$it " } ?: ""
            canvas.drawText("$arrival   $prefix${place.displayName}", MARGIN + 8f, y, body)
            y += LINE
        }
        return y
    }
}
