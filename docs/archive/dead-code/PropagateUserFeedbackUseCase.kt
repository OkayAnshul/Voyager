package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.enums.CorrectionType
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.storage.database.dao.CorrectionFeedbackDao
import com.cosmiclaboratory.voyager.storage.database.entity.CorrectionFeedbackEntity
import javax.inject.Inject

data class PropagationResult(
    val processedCount: Int,
    val adjustments: List<CalibrationAdjustment>
)

data class CalibrationAdjustment(
    val settingKey: String,
    val oldValue: Any,
    val newValue: Any,
    val reason: String
)

class PropagateUserFeedbackUseCase @Inject constructor(
    private val correctionFeedbackDao: CorrectionFeedbackDao,
    private val settingsRepository: SettingsRepository
) {

    companion object {
        private const val WINDOW_30_DAYS_MS = 30L * 24 * 60 * 60 * 1000
        private const val RENAME_THRESHOLD = 5
        private const val RECLASSIFY_THRESHOLD = 5
        private const val ADJUST_TIMES_THRESHOLD = 3
        private const val CONFIRM_BOOST_THRESHOLD = 10
    }

    suspend fun propagate(): PropagationResult {
        val unpropagated = correctionFeedbackDao.getUnpropagated()
        if (unpropagated.isEmpty()) {
            return PropagationResult(processedCount = 0, adjustments = emptyList())
        }

        val now = System.currentTimeMillis()
        val windowStart = now - WINDOW_30_DAYS_MS
        val adjustments = mutableListOf<CalibrationAdjustment>()
        val currentSettings = settingsRepository.observeSettings().value

        // Group all corrections in the 30-day window by type
        val recentByType = unpropagated
            .filter { it.createdAt >= windowStart }
            .groupBy { it.correctionType }

        // Many RENAME corrections -> geocoding may be inaccurate, no direct calibration
        // but log the pattern for diagnostics
        val renameCount = recentByType[CorrectionType.RENAME.name]?.size ?: 0
        if (renameCount >= RENAME_THRESHOLD) {
            adjustments.add(
                CalibrationAdjustment(
                    settingKey = "geocoding_accuracy_flag",
                    oldValue = false,
                    newValue = true,
                    reason = "$renameCount RENAME corrections in 30 days — geocoding may need provider reorder"
                )
            )
        }

        // Many RECLASSIFY_SEGMENT -> activity recognition weights need adjustment
        val reclassifyCount = recentByType[CorrectionType.RECLASSIFY_SEGMENT.name]?.size ?: 0
        if (reclassifyCount >= RECLASSIFY_THRESHOLD) {
            val currentArThreshold = currentSettings.arConfidenceThreshold
            val newThreshold = (currentArThreshold - 5).coerceAtLeast(30)
            if (newThreshold != currentArThreshold) {
                settingsRepository.updateSetting("ar_confidence_threshold", newThreshold)
                adjustments.add(
                    CalibrationAdjustment(
                        settingKey = "ar_confidence_threshold",
                        oldValue = currentArThreshold,
                        newValue = newThreshold,
                        reason = "$reclassifyCount RECLASSIFY corrections — lowering AR confidence threshold"
                    )
                )
            }
        }

        // Many ADJUST_TIMES -> dwell threshold may need tuning
        val adjustTimesCount = recentByType[CorrectionType.ADJUST_TIMES.name]?.size ?: 0
        if (adjustTimesCount >= ADJUST_TIMES_THRESHOLD) {
            val avgDwellShiftMs = estimateAverageDwellShift(
                recentByType[CorrectionType.ADJUST_TIMES.name] ?: emptyList()
            )
            if (avgDwellShiftMs != null) {
                val currentDwell = currentSettings.minDwellMinutes
                val shiftMinutes = (avgDwellShiftMs / 60_000).toInt()
                // If users consistently extend visits, lower dwell threshold; if shortening, raise it
                val newDwell = (currentDwell - shiftMinutes).coerceIn(2, 30)
                if (newDwell != currentDwell) {
                    settingsRepository.updateSetting("min_dwell_minutes", newDwell)
                    adjustments.add(
                        CalibrationAdjustment(
                            settingKey = "min_dwell_minutes",
                            oldValue = currentDwell,
                            newValue = newDwell,
                            reason = "$adjustTimesCount ADJUST_TIMES corrections — adjusting dwell from ${currentDwell}m to ${newDwell}m"
                        )
                    )
                }
            }
        }

        // Many CONFIRM -> system is working well, boost place radius slightly for tighter matching
        val confirmCount = (recentByType[CorrectionType.CONFIRM.name]?.size ?: 0) +
            (recentByType[CorrectionType.CONFIRM_VISIT.name]?.size ?: 0)
        if (confirmCount >= CONFIRM_BOOST_THRESHOLD) {
            val currentRadius = currentSettings.placeRadiusM
            val newRadius = (currentRadius - 5).coerceAtLeast(30)
            if (newRadius != currentRadius) {
                settingsRepository.updateSetting("place_radius_m", newRadius)
                adjustments.add(
                    CalibrationAdjustment(
                        settingKey = "place_radius_m",
                        oldValue = currentRadius,
                        newValue = newRadius,
                        reason = "$confirmCount CONFIRM corrections — tightening place radius from ${currentRadius}m to ${newRadius}m"
                    )
                )
            }
        }

        // Mark all processed corrections as propagated
        for (feedback in unpropagated) {
            correctionFeedbackDao.markPropagated(feedback.feedbackId)
        }

        return PropagationResult(
            processedCount = unpropagated.size,
            adjustments = adjustments
        )
    }

    /**
     * Estimate average dwell shift direction from ADJUST_TIMES corrections.
     * Positive = user extended visits (lower dwell threshold needed).
     * Negative = user shortened visits (raise dwell threshold).
     * Returns null if no parseable data.
     */
    private fun estimateAverageDwellShift(
        corrections: List<CorrectionFeedbackEntity>
    ): Long? {
        val shifts = corrections.mapNotNull { correction ->
            val before = correction.beforeValueJson ?: return@mapNotNull null
            val after = correction.afterValueJson ?: return@mapNotNull null
            try {
                val beforeDeparture = parseLongField(before, "departureAt") ?: return@mapNotNull null
                val beforeArrival = parseLongField(before, "arrivalAt") ?: return@mapNotNull null
                val afterDeparture = parseLongField(after, "departureAt") ?: return@mapNotNull null
                val afterArrival = parseLongField(after, "arrivalAt") ?: return@mapNotNull null
                val beforeDwell = beforeDeparture - beforeArrival
                val afterDwell = afterDeparture - afterArrival
                afterDwell - beforeDwell
            } catch (_: Exception) {
                null
            }
        }
        return if (shifts.isNotEmpty()) shifts.average().toLong() else null
    }

    private fun parseLongField(text: String, field: String): Long? {
        val regex = Regex("""$field\s*[:=]\s*(\d+)""")
        return regex.find(text)?.groupValues?.get(1)?.toLongOrNull()
    }
}
