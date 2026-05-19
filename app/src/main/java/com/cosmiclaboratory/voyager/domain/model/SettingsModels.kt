package com.cosmiclaboratory.voyager.domain.model

import com.cosmiclaboratory.voyager.domain.model.enums.*

data class UserSettings(
    // Tracking
    val trackingEnabled: Boolean = true,
    /** Primary tracking mode — sets structural GPS behaviour (see [TrackingTier]). */
    val trackingTier: TrackingTier = TrackingTier.BALANCED,
    val samplingPreset: SamplingPreset = SamplingPreset.BALANCED,
    val customSamplingIntervalMs: Long = 15000,
    val motionDetectionEnabled: Boolean = true,
    val activityRecognitionEnabled: Boolean = true,
    val stepCountingEnabled: Boolean = true,
    val stepCountSource: StepSource = StepSource.AUTO,

    // Battery
    val batterySaverThresholdPct: Int = 20,
    val batterySaverAction: BatterySaverAction = BatterySaverAction.INCREASE_INTERVAL,
    val chargingBoostEnabled: Boolean = true,
    /** Whole-day discharge ceiling; the budget controller steps the tracking
     *  tier down to honour it. 0 = no budget (feature off). */
    val batteryBudgetPctPerDay: Int = 0,

    // Sleep
    val sleepDetectionEnabled: Boolean = true,
    val sleepWindowStartHour: Int = 23,
    val sleepWindowStartMinute: Int = 0,
    val sleepWindowEndHour: Int = 7,
    val sleepWindowEndMinute: Int = 0,
    val sleepSamplingIntervalMs: Long = 300000,

    // Activity Inference
    val arConfidenceThreshold: Int = 50,
    val speedHeuristicEnabled: Boolean = true,
    val stepRateFusionEnabled: Boolean = true,

    // Place Detection
    val minDwellMinutes: Int = 5,
    val placeRadiusM: Int = 80,
    val entryHysteresisCount: Int = 2,
    val exitHysteresisCount: Int = 3,
    val exitBufferM: Int = 30,
    val autoDiscoveryEnabled: Boolean = true,
    val discoveryIntervalHours: Int = 6,

    // Timeline
    val dayBoundaryMode: DayBoundaryMode = DayBoundaryMode.HOME_TIMEZONE,
    val homeTimeZone: String = "UTC",
    val showGapSegments: Boolean = true,
    val showLowConfidenceSegments: Boolean = true,
    val unifyTravelSegments: Boolean = true,
    val minSegmentDurationMs: Long = 60000,

    // Map
    val mapProvider: MapProvider = MapProvider.MAPLIBRE,
    val tileProvider: TileProvider = TileProvider.OSM,
    val showRoutePolylines: Boolean = true,
    val routeColorByTransportMode: Boolean = true,
    val showVisitMarkers: Boolean = true,
    val visitMarkerNumbering: Boolean = true,
    val offlineMapsEnabled: Boolean = false,
    val clusterMarkersAtZoom: Int = 12,

    // Geocoding
    val providerOrder: List<GeocodingProviderId> = listOf(
        GeocodingProviderId.OVERPASS,
        GeocodingProviderId.ANDROID_GEOCODER,
        GeocodingProviderId.PHOTON,
        GeocodingProviderId.NOMINATIM
    ),
    val photonServerUrl: String = "https://photon.komoot.io",
    val autoGeocodeNewPlaces: Boolean = true,
    val geocodeLanguage: String = "",
    /** Opt-in privacy: round coordinates (~110 m) before sending to network geocoders. */
    val coarsenGeocodeQueries: Boolean = false,

    // Privacy
    val stripExactCoordinatesOnExport: Boolean = false,
    val exportIncludeRawSamples: Boolean = false,
    /** When on, sets FLAG_SECURE — hides app content in the recents switcher and
     *  blocks screenshots. */
    val flagSecureEnabled: Boolean = false,

    // Retention
    val rawSampleRetentionDays: Int = 90,
    val derivedDataRetentionDays: Int = 365,
    val rollupRetentionDays: Int = -1, // forever
    val correctionFeedbackRetentionDays: Int = 180,
    val autoCleanupEnabled: Boolean = true,

    // Notifications
    val trackingStatusNotificationEnabled: Boolean = true,
    val dailyInsightsEnabled: Boolean = true,
    val weeklyInsightsEnabled: Boolean = true,
    val anomalyAlertsEnabled: Boolean = true,
    val placeConfirmationPromptsEnabled: Boolean = true,
    val healthAlertsEnabled: Boolean = false,

    // Debug
    val showPipelineLatency: Boolean = false,
    val showSampleAccuracyOverlay: Boolean = false,
    val showConfidenceScores: Boolean = false,
    val logPipelineDecisions: Boolean = false,
    val forceProvider: GeocodingProviderId? = null,
    val exportDiagnostics: Boolean = false,

    // Presets & persona
    val activePreset: String = "DAILY_COMMUTER",
    val customBasePreset: String? = null,
    /** The user's chosen job (Job.id) — blank until the onboarding persona pick. */
    val activeJob: String = ""
)
