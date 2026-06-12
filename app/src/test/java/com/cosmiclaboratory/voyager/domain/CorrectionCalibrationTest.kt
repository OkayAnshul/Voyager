package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.enums.CorrectionType
import com.cosmiclaboratory.voyager.domain.usecase.CorrectionCalibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionCalibrationTest {

    @Test
    fun `place-validation corrections are trust signals`() {
        listOf(
            CorrectionType.CONFIRM, CorrectionType.CONFIRM_VISIT,
            CorrectionType.RENAME, CorrectionType.RECATEGORIZE
        ).forEach { assertTrue(it.name, CorrectionCalibration.isPlaceTrustSignal(it.name)) }
    }

    @Test
    fun `corrections that don't validate a place identity are not trust signals`() {
        listOf(
            CorrectionType.RECLASSIFY_SEGMENT, CorrectionType.DELETE_VISIT, CorrectionType.ADJUST_TIMES,
            CorrectionType.MERGE_PLACE, CorrectionType.SPLIT_PLACE, CorrectionType.DISMISS_VISIT
        ).forEach { assertFalse(it.name, CorrectionCalibration.isPlaceTrustSignal(it.name)) }
    }

    @Test
    fun `a validation signal raises a low confidence to the validated floor`() {
        assertEquals(CorrectionCalibration.USER_VALIDATED_CONFIDENCE, CorrectionCalibration.boostedConfidence(0.4f), 1e-6f)
    }

    @Test
    fun `calibration is monotonic - it never lowers an already-higher confidence`() {
        assertEquals(0.98f, CorrectionCalibration.boostedConfidence(0.98f), 1e-6f)
    }

    @Test
    fun `a misclassification recurring at least the threshold is reported as systematic`() {
        val corrections = List(3) { "DRIVE" to "CYCLE" }
        val out = CorrectionCalibration.systematicMisclassifications(corrections)
        assertEquals(1, out.size)
        assertEquals("DRIVE", out.first().from)
        assertEquals("CYCLE", out.first().to)
        assertEquals(3, out.first().count)
    }

    @Test
    fun `a misclassification below the threshold is not reported`() {
        val corrections = List(2) { "DRIVE" to "CYCLE" }
        assertTrue(CorrectionCalibration.systematicMisclassifications(corrections).isEmpty())
    }

    @Test
    fun `corrections that keep the same type are not misclassifications`() {
        val corrections = List(5) { "WALK" to "WALK" }
        assertTrue(CorrectionCalibration.systematicMisclassifications(corrections).isEmpty())
    }

    @Test
    fun `patterns are sorted by descending recurrence count`() {
        val corrections = List(3) { "DRIVE" to "CYCLE" } + List(5) { "WALK" to "RUN" }
        val out = CorrectionCalibration.systematicMisclassifications(corrections)
        assertEquals(2, out.size)
        assertEquals("WALK" to "RUN", out[0].from to out[0].to)
        assertEquals(5, out[0].count)
        assertEquals("DRIVE" to "CYCLE", out[1].from to out[1].to)
        assertEquals(3, out[1].count)
    }
}
