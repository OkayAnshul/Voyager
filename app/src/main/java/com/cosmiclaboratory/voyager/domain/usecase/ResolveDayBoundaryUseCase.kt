package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.enums.DayBoundaryMode
import com.cosmiclaboratory.voyager.domain.util.DayBoundaryResolver
import javax.inject.Inject

class ResolveDayBoundaryUseCase @Inject constructor(
    private val dayBoundaryResolver: DayBoundaryResolver
) {
    fun resolve(
        epochMs: Long,
        mode: DayBoundaryMode = DayBoundaryMode.HOME_TIMEZONE,
        homeTimeZone: String = java.util.TimeZone.getDefault().id,
        sampleTimeZone: String? = null
    ): String {
        return dayBoundaryResolver.resolveDayKey(epochMs, mode, homeTimeZone, sampleTimeZone)
    }
}
