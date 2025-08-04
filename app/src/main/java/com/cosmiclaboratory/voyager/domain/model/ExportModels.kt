package com.cosmiclaboratory.voyager.domain.model

import android.net.Uri
import com.cosmiclaboratory.voyager.domain.model.enums.ExportFormat

data class ExportConfig(
    val format: ExportFormat,
    val dateRange: DateRange,
    val includeRawSamples: Boolean = false,
    val stripExactCoordinates: Boolean = false
)

data class ImportSummary(
    val segmentsImported: Int,
    val visitsImported: Int,
    val placesImported: Int,
    val duplicatesSkipped: Int
)
