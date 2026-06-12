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
}
