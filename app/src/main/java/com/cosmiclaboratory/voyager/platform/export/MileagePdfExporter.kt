package com.cosmiclaboratory.voyager.platform.export

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.cosmiclaboratory.voyager.domain.model.MileageLog
import com.cosmiclaboratory.voyager.domain.model.MileagePurpose
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders a [MileageLog] to a tax-ready PDF using the platform [PdfDocument].
 *
 * Layout is US Letter (612×792 pt): a per-purpose summary with an estimated deduction,
 * then a paginated table of every drive. The deduction uses the IRS 2025 standard
 * mileage rates and is clearly labelled as an estimate the filer must verify — rates
 * change yearly and other jurisdictions (e.g. HMRC) differ.
 */
@Singleton
class MileagePdfExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private companion object {
        const val PAGE_WIDTH = 612
        const val PAGE_HEIGHT = 792
        const val MARGIN = 48f
        const val LINE = 16f

        /** IRS 2025 standard mileage rates, USD per mile. Personal driving is non-deductible. */
        val IRS_2025_RATES: Map<MileagePurpose, Double> = mapOf(
            MileagePurpose.BUSINESS to 0.70,
            MileagePurpose.MEDICAL to 0.21,
            MileagePurpose.CHARITABLE to 0.14
        )

        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Writes [log] to a cached PDF and returns a shareable [Uri]. */
    fun export(log: MileageLog): Result<Uri> = runCatching {
        val doc = PdfDocument()
        try {
            renderInto(doc, log)
        } catch (e: Exception) {
            doc.close()
            throw e
        }

        val file = File(context.cacheDir, "voyager_mileage_${System.currentTimeMillis()}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun renderInto(doc: PdfDocument, log: MileageLog) {
        val title = Paint().apply { textSize = 20f; isFakeBoldText = true }
        val heading = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val body = Paint().apply { textSize = 10f }
        val muted = Paint().apply { textSize = 9f; color = 0xFF666666.toInt() }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = page.canvas
        var y = MARGIN

        // ── Title block ───────────────────────────────────────────────
        canvas.drawText("Mileage Log", MARGIN, y, title); y += LINE * 1.5f
        canvas.drawText("Period: ${log.rangeLabel}", MARGIN, y, body); y += LINE
        canvas.drawText(
            "Generated: ${DATE_FMT.format(Instant.now().atZone(zone))}",
            MARGIN, y, muted
        ); y += LINE * 1.6f

        // ── Summary ───────────────────────────────────────────────────
        canvas.drawText("Summary", MARGIN, y, heading); y += LINE * 1.2f
        canvas.drawText("Purpose", MARGIN, y, muted)
        canvas.drawText("Miles", MARGIN + 220f, y, muted)
        canvas.drawText("Est. deduction (USD)", MARGIN + 320f, y, muted); y += LINE

        var totalDeduction = 0.0
        for (purpose in MileagePurpose.entries) {
            val miles = log.milesFor(purpose)
            if (miles <= 0.0) continue
            val rate = IRS_2025_RATES[purpose]
            val deduction = rate?.let { it * miles } ?: 0.0
            totalDeduction += deduction
            canvas.drawText(purpose.displayName, MARGIN, y, body)
            canvas.drawText(formatMiles(miles), MARGIN + 220f, y, body)
            canvas.drawText(
                if (rate != null) "$%.2f".format(deduction) else "—",
                MARGIN + 320f, y, body
            )
            y += LINE
        }
        y += LINE * 0.4f
        canvas.drawText("Total miles", MARGIN, y, heading)
        canvas.drawText(formatMiles(log.totalMiles), MARGIN + 220f, y, heading)
        canvas.drawText("$%.2f".format(totalDeduction), MARGIN + 320f, y, heading); y += LINE * 1.4f

        canvas.drawText(
            "Estimate uses IRS 2025 standard mileage rates. Verify current rates for your",
            MARGIN, y, muted
        ); y += LINE * 0.9f
        canvas.drawText(
            "tax year and jurisdiction before filing. Personal mileage is not deductible.",
            MARGIN, y, muted
        ); y += LINE * 1.8f

        // ── Detail table ──────────────────────────────────────────────
        canvas.drawText("Drives", MARGIN, y, heading); y += LINE * 1.2f
        fun drawColumnHeaders() {
            canvas.drawText("Date", MARGIN, y, muted)
            canvas.drawText("Time", MARGIN + 90f, y, muted)
            canvas.drawText("Miles", MARGIN + 150f, y, muted)
            canvas.drawText("Purpose", MARGIN + 220f, y, muted)
            canvas.drawText("Note", MARGIN + 320f, y, muted)
            y += LINE
        }
        drawColumnHeaders()

        for (entry in log.entries) {
            if (y > PAGE_HEIGHT - MARGIN) {
                doc.finishPage(page)
                pageNum += 1
                page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                )
                canvas = page.canvas
                y = MARGIN
                drawColumnHeaders()
            }
            val start = Instant.ofEpochMilli(entry.startAt).atZone(zone)
            canvas.drawText(DATE_FMT.format(start), MARGIN, y, body)
            canvas.drawText(TIME_FMT.format(start), MARGIN + 90f, y, body)
            canvas.drawText(formatMiles(entry.distanceMiles), MARGIN + 150f, y, body)
            canvas.drawText(entry.purpose.displayName, MARGIN + 220f, y, body)
            canvas.drawText(truncateNote(entry.note), MARGIN + 320f, y, body)
            y += LINE
        }

        if (log.entries.isEmpty()) {
            canvas.drawText("No drives recorded in this period.", MARGIN, y, body)
        }

        doc.finishPage(page)
    }

    private fun formatMiles(miles: Double): String = "%.1f".format(miles)

    private fun truncateNote(note: String?): String {
        val n = note?.trim().orEmpty()
        return if (n.length > 32) n.take(31) + "…" else n
    }
}
