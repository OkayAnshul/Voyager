package com.cosmiclaboratory.voyager.domain.repository

import android.net.Uri
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.model.enums.*
import com.cosmiclaboratory.voyager.domain.model.ids.PlaceId
import com.cosmiclaboratory.voyager.domain.model.ids.VisitId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TrackingRepository {
    suspend fun startTracking(reason: StartReason): Result<Long>
    suspend fun stopTracking(reason: StopReason): Result<Unit>
    suspend fun pauseTracking(reason: PauseReason): Result<Unit>
    suspend fun resumeTracking(reason: ResumeReason): Result<Unit>
    fun observeRuntimeState(): StateFlow<TrackingRuntimeState>
    fun observeHealth(): Flow<TrackingHealth>
}

interface TimelineRepository {
    fun observeDay(dayKey: String): Flow<TimelineDay>
    fun observeRange(startDay: String, endDay: String): Flow<List<TimelineDay>>
    suspend fun rebuildDay(dayKey: String): Result<Unit>
    suspend fun getSegmentDetails(segmentId: Long): TimelineSegment?
    fun observeLiveTimeline(): StateFlow<LiveTimelineState>
    fun observeDayKeys(): Flow<List<String>>
}

interface PlaceRepository {
    fun observePlaces(): Flow<List<TimelinePlace>>
    fun observePlace(placeId: PlaceId): Flow<TimelinePlace?>
    suspend fun renamePlace(placeId: PlaceId, name: String): Result<Unit>
    suspend fun mergePlaces(sourceIds: List<PlaceId>, targetId: PlaceId): Result<Unit>
    suspend fun splitPlace(placeId: PlaceId, visitIds: List<VisitId>): Result<PlaceId>
    suspend fun setPlaceCategory(placeId: PlaceId, category: PlaceCategory): Result<Unit>
    suspend fun confirmPlace(placeId: PlaceId): Result<Unit>
    suspend fun getFrequentPlaces(limit: Int): List<TimelinePlace>
    suspend fun getHomePlace(): TimelinePlace?
    suspend fun setPlaceEmoji(placeId: PlaceId, emoji: String?): Result<Unit>
}

interface EvidenceRepository {
    suspend fun getSegmentEvidence(segmentId: Long): EvidenceBlock?
    suspend fun getVisitEvidence(visitId: Long): ConfidenceBlock?
    suspend fun getPlaceEvidence(placeId: Long): ConfidenceBlock?
    suspend fun getInferenceExplanation(segmentId: Long): InferenceExplanation?
}

interface SearchRepository {
    fun search(query: String, filters: SearchFilters): Flow<SearchResults>
    suspend fun rebuildSearchIndex(): Result<Unit>
}

interface AnalyticsRepository {
    fun observeDashboard(range: DateRange): Flow<DashboardState>
    fun observeComparisons(periodA: DateRange, periodB: DateRange): Flow<ComparisonResult>
    fun observeAnomalies(range: DateRange): Flow<List<Anomaly>>
    suspend fun getPlaceAnalytics(placeId: Long): PlaceAnalytics?
}

// GeocodingRepository is defined in its own file: GeocodingRepository.kt

interface StepsRepository {
    fun observeDailySteps(dayKey: String): Flow<StepsSummary>
    fun observeHourlySteps(dayKey: String): Flow<List<HourlySteps>>
    suspend fun getStepsForSegment(segmentId: Long): Int?
    suspend fun getUserStrideCalibration(): StrideCalibration
}

interface MapRepository {
    suspend fun getRouteForSegment(segmentId: Long): MapRoute?
    suspend fun getVisitMarkers(dayKey: String): List<VisitMarker>
    suspend fun getVisitMarkerForSegment(segmentId: Long): VisitMarker?
    suspend fun getDayBoundingBox(dayKey: String): LatLngBounds?
}

interface ExportRepository {
    suspend fun exportDay(dayKey: String, format: ExportFormat): Result<Uri>
    suspend fun exportRange(range: DateRange, format: ExportFormat): Result<Uri>
    suspend fun importData(uri: Uri, format: ExportFormat): Result<ImportSummary>
}

interface SettingsRepository {
    fun observeSettings(): StateFlow<UserSettings>
    suspend fun updateSetting(key: String, value: Any): Result<Unit>
    suspend fun applyPreset(presetId: String): Result<Unit>
    suspend fun exportSettings(): Result<String>
    suspend fun importSettings(json: String): Result<Unit>
}

interface CorrectionRepository {
    suspend fun applyCorrection(
        correctionType: CorrectionType,
        entityType: String,
        entityId: Long,
        beforeValue: String?,
        afterValue: String?
    ): Result<Unit>
}
