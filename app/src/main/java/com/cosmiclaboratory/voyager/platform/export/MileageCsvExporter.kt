package com.cosmiclaboratory.voyager.platform.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.cosmiclaboratory.voyager.domain.model.MileageLog
import com.cosmiclaboratory.voyager.domain.model.MileageRateConfig
import com.cosmiclaboratory.voyager.domain.model.shortLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders a [MileageLog] to a spreadsheet-friendly CSV — the format accountants and gig
 * platforms actually import, alongside the human-readable PDF.
 *
 * One row per drive, with the money already worked out from the user's [MileageRateConfig] so a
 * reviewer doesn't have to reapply rates. Each row carries the drive's Segment ID: the durable
 * on-device handle back to the GPS evidence (raw samples are exported separately), so the audit
 * trail survives the export rather than collapsing to a bare number.
 */
@Singleton
class MileageCsvExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private companion object {
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Writes [log] to a cached CSV and returns a shareable [Uri], using [config] for rates/units. */
    fun export(log: MileageLog, config: MileageRateConfig): Result<Uri> = runCatching {
        val unit = config.distanceUnit
        val unitLabel = unit.shortLabel

        val sb = StringBuilder()
        sb.append(
            "Date,Start,End,Duration (min),Distance ($unitLabel)," +
                "Purpose,Rate per $unitLabel (${config.currencyCode})," +
                "Amount (${config.currencyCode}),Note,Segment ID\n"
        )
        for (entry in log.entries) {
            val start = Instant.ofEpochMilli(entry.startAt).atZone(zone)
            val end = Instant.ofEpochMilli(entry.endAt).atZone(zone)
            val durationMin = ((entry.endAt - entry.startAt) / 60000L).coerceAtLeast(0L)
            val distance = entry.distanceIn(unit)
            val rate = config.rateFor(entry.purpose)
            val amount = rate?.let { it * distance } ?: 0.0
            sb.append(
                listOf(
                    DATE_FMT.format(start),
                    TIME_FMT.format(start),
                    TIME_FMT.format(end),
                    durationMin.toString(),
                    "%.2f".format(distance),
                    entry.purpose.displayName,
                    rate?.let { "%.4f".format(it) } ?: "",
                    "%.2f".format(amount),
                    entry.note.orEmpty(),
                    entry.segmentId.toString()
                ).joinToString(",") { csvEscape(it) }
            )
            sb.append("\n")
        }

        val file = File(context.cacheDir, "voyager_mileage_${System.currentTimeMillis()}.csv")
        file.writeText(sb.toString())
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** RFC-4180 quoting: wrap in quotes and double any embedded quote when the value needs it. */
    private fun csvEscape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
