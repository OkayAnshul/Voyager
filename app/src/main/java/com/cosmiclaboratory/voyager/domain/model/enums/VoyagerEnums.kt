package com.cosmiclaboratory.voyager.domain.model.enums

enum class SegmentType {
    VISIT, DWELL, WALK, RUN, CYCLE, DRIVE, TRANSIT, FLIGHT, GAP, UNKNOWN_MOTION
}

enum class ActivityType {
    STILL, WALKING, RUNNING, CYCLING, IN_VEHICLE, ON_BICYCLE, TILTING, UNKNOWN
}

enum class ActivitySource {
    TRANSITION_API, CLASSIFIER, SPEED_HEURISTIC, ACCELEROMETER
}

enum class TransitionType {
    ENTER, EXIT
}

enum class GapReason {
    PERMISSION, DOZE, PROCESS_DEAD, GPS_LOSS, MANUAL_PAUSE, UNKNOWN
}

enum class PlaceLifecycleStatus {
    CANDIDATE, CONFIRMED, ARCHIVED, MERGED
}


enum class VisitSource {
    LIVE_DETECTION, BATCH_DISCOVERY, USER_CREATED
}

enum class CorrectionType {
    RENAME, RECATEGORIZE, RECLASSIFY_SEGMENT, MERGE_PLACE, SPLIT_PLACE,
    DELETE_VISIT, ADJUST_TIMES, CONFIRM, CONFIRM_VISIT, DISMISS_VISIT,
    CHANGE_TRANSPORT_MODE, SPLIT_SEGMENT, MERGE_SEGMENTS
}

enum class StartReason {
    USER, BOOT, CRASH_RESTORE, SCHEDULE, PERMISSION_REGAINED
}

enum class StopReason {
    USER, PERMISSION_LOST, BATTERY_CRITICAL, SYSTEM_KILL, ERROR
}

enum class PauseReason {
    USER, BATTERY_SAVER, SLEEP_DETECTED, EXCLUDE_ZONE, PERMISSION_DEGRADED
}

enum class ResumeReason {
    USER, BATTERY_RESTORED, SLEEP_ENDED, LEFT_EXCLUDE_ZONE, PERMISSION_RESTORED
}

enum class StepSource {
    HEALTH_CONNECT, STEP_SENSOR, PEDOMETER, AUTO
}

enum class LicenseClass {
    FREE, ATTRIBUTION, PAID
}

enum class GeocodingProviderId(val displayName: String, val isFree: Boolean) {
    ANDROID_GEOCODER("Android Geocoder", true),
    OVERPASS("OpenStreetMap POI", true),
    PHOTON("Photon (Komoot)", true),
    NOMINATIM("Nominatim (OSM)", true),
    GOOGLE("Google", false),
    CUSTOM("Custom", false)
}

enum class PermissionState {
    FULL, FINE_LOCATION, COARSE_LOCATION, NO_LOCATION_WITH_AR, NOTHING, BACKGROUND_RESTRICTED
}

enum class BatterySaverAction {
    REDUCE_ACCURACY, INCREASE_INTERVAL, PAUSE_TRACKING
}

enum class DayBoundaryMode {
    HOME_TIMEZONE, TRAVEL_AWARE
}

enum class ExportFormat {
    GPX, GEOJSON, VOYAGER_JSON, CSV
}

enum class RouteColorMode {
    BY_TRANSPORT, BY_SPEED, SOLID
}

enum class RouteDetail {
    MINIMAL, STANDARD, DETAILED, FULL
}

enum class TimelineGrouping {
    BY_DAY, BY_CITY, BY_TRIP
}

enum class InsightCategory {
    PLACES_DISCOVERED, DISTANCE, COMMUTE, TRIP_SUMMARY, STEPS, ROUTINE,
    TRANSPORT_MIX, ANOMALY, ACHIEVEMENT
}

enum class AnomalySeverity {
    MILD, NOTABLE, SIGNIFICANT
}

enum class HealthEventType {
    SAMPLE_GAP, WORKER_FAILURE, PERMISSION_CHANGE, BATTERY_CRITICAL,
    CRASH_RESTORE, WATCHDOG_TRIGGER
}

enum class LocationProvider {
    GPS, NETWORK, FUSED, PASSIVE
}

enum class SamplingPreset {
    BATTERY_SAVER, BALANCED, HIGH_ACCURACY, CUSTOM
}

enum class MapProvider {
    MAPLIBRE
}

enum class TileProvider {
    OSM, CARTO, CUSTOM_URL
}
