package com.cosmiclaboratory.voyager.presentation.screen.settings.model

/**
 * Metadata for a configurable parameter with detailed explanations
 */
data class ParameterMetadata(
    val id: String,
    val name: String,
    val category: ParameterCategory,
    val description: String,
    val whatItDoes: String,
    val unit: String,
    val range: ParameterRange,
    val batteryImpact: ImpactLevel,
    val accuracyImpact: String,
    val performanceImpact: String,
    val useCases: List<UseCase>,
    val type: ParameterType
)

enum class ParameterCategory {
    LOCATION_TRACKING,
    PLACE_DETECTION,
    BATTERY_OPTIMIZATION,
    ANALYTICS,
    NOTIFICATIONS
}

data class ParameterRange(
    val min: Float,
    val max: Float,
    val default: Float,
    val step: Float = 1f
)

enum class ImpactLevel(val displayName: String, val description: String) {
    MINIMAL("Minimal", "Very low impact"),
    LOW("Low", "Minor impact"),
    MODERATE("Moderate", "Noticeable impact"),
    HIGH("High", "Significant impact"),
    CRITICAL("Critical", "Major impact")
}

data class UseCase(
    val scenario: String,
    val recommendation: String,
    val value: Float
)

enum class ParameterType {
    INTEGER,
    FLOAT,
    LONG,
    BOOLEAN,
    DOUBLE
}

/**
 * All configurable parameters with their metadata
 */
object ParameterDefinitions {
    val allParameters = listOf(
        // Location Tracking Parameters
        ParameterMetadata(
            id = "location_update_interval",
            name = "Location Update Interval",
            category = ParameterCategory.LOCATION_TRACKING,
            description = "How often the app requests location updates from GPS",
            whatItDoes = "Controls the frequency of GPS location checks. Lower values mean more frequent updates, providing smoother tracking but consuming more battery. Higher values save battery but may miss short stops.",
            unit = "seconds",
            range = ParameterRange(min = 10f, max = 300f, default = 60f, step = 5f),
            batteryImpact = ImpactLevel.HIGH,
            accuracyImpact = "Lower interval = more accurate movement tracking, higher interval = may miss brief stops",
            performanceImpact = "Lower values increase GPS usage and CPU wake-ups",
            useCases = listOf(
                UseCase("Detailed city exploration", "10-30 seconds for precise movement", 15f),
                UseCase("Daily commuting", "60 seconds for balanced tracking", 60f),
                UseCase("Battery saving mode", "120-180 seconds for minimal drain", 120f)
            ),
            type = ParameterType.LONG
        ),

        ParameterMetadata(
            id = "min_distance_change",
            name = "Minimum Distance Change",
            category = ParameterCategory.LOCATION_TRACKING,
            description = "Minimum distance you must move before location is recorded",
            whatItDoes = "Filters out GPS 'drift' when stationary. Only records a new location if you've moved at least this distance. Prevents recording false movement when you're actually standing still.",
            unit = "meters",
            range = ParameterRange(min = 0f, max = 100f, default = 10f, step = 5f),
            batteryImpact = ImpactLevel.MODERATE,
            accuracyImpact = "Lower = captures more detail, higher = ignores small movements",
            performanceImpact = "Higher values reduce database writes and processing",
            useCases = listOf(
                UseCase("Walking in city", "5-10 meters for detailed path", 5f),
                UseCase("Driving", "20-30 meters to ignore GPS noise", 25f),
                UseCase("Stationary work", "50+ meters to avoid false movements", 50f)
            ),
            type = ParameterType.FLOAT
        ),

        ParameterMetadata(
            id = "max_gps_accuracy",
            name = "Maximum GPS Accuracy",
            category = ParameterCategory.LOCATION_TRACKING,
            description = "Reject GPS readings with accuracy worse than this",
            whatItDoes = "GPS provides an 'accuracy radius' for each location. This setting rejects readings with large error margins, ensuring only high-quality location data is stored.",
            unit = "meters",
            range = ParameterRange(min = 20f, max = 200f, default = 50f, step = 10f),
            batteryImpact = ImpactLevel.LOW,
            accuracyImpact = "Lower = stricter quality filter, higher = accepts less precise locations",
            performanceImpact = "Lower values may cause more GPS readings to be discarded",
            useCases = listOf(
                UseCase("Urban area (good GPS)", "20-30 meters for precision", 25f),
                UseCase("Mixed environment", "50 meters balanced", 50f),
                UseCase("Indoor/poor signal", "100+ meters to capture any data", 100f)
            ),
            type = ParameterType.FLOAT
        ),

        ParameterMetadata(
            id = "max_speed_kmh",
            name = "Maximum Speed Filter",
            category = ParameterCategory.LOCATION_TRACKING,
            description = "Reject location readings indicating impossible speeds",
            whatItDoes = "Sometimes GPS glitches show you 'teleporting' at unrealistic speeds. This filter rejects readings that would require moving faster than this threshold.",
            unit = "km/h",
            range = ParameterRange(min = 50f, max = 300f, default = 200f, step = 10f),
            batteryImpact = ImpactLevel.MINIMAL,
            accuracyImpact = "Filters out GPS errors, preventing false long-distance jumps",
            performanceImpact = "Minimal - simple calculation per GPS reading",
            useCases = listOf(
                UseCase("Walking/cycling only", "50-80 km/h filters car-speed errors", 60f),
                UseCase("Mixed transportation", "150-200 km/h allows driving", 180f),
                UseCase("Including flights", "250+ km/h to track air travel", 280f)
            ),
            type = ParameterType.DOUBLE
        ),

        ParameterMetadata(
            id = "min_time_between_updates",
            name = "Minimum Time Between Updates",
            category = ParameterCategory.LOCATION_TRACKING,
            description = "Fastest rate the app will accept location updates",
            whatItDoes = "Even if GPS provides updates faster, the app won't process them more frequently than this. Prevents battery drain from excessive GPS activity during rapid movement.",
            unit = "seconds",
            range = ParameterRange(min = 5f, max = 60f, default = 15f, step = 5f),
            batteryImpact = ImpactLevel.HIGH,
            accuracyImpact = "Lower = smoother tracking, higher = choppier but saves battery",
            performanceImpact = "Lower values increase CPU and storage usage",
            useCases = listOf(
                UseCase("High-precision tracking", "5-10 seconds for smooth paths", 5f),
                UseCase("Standard tracking", "15 seconds balanced", 15f),
                UseCase("Battery conservation", "30+ seconds minimal processing", 30f)
            ),
            type = ParameterType.LONG
        ),

        // Place Detection Parameters
        ParameterMetadata(
            id = "clustering_distance",
            name = "Place Clustering Distance",
            category = ParameterCategory.PLACE_DETECTION,
            description = "Maximum distance to group location points into one place",
            whatItDoes = "When you visit the same area multiple times, this determines how close you need to be for it to count as the same place. Larger values merge nearby locations, smaller values create distinct places.",
            unit = "meters",
            range = ParameterRange(min = 20f, max = 200f, default = 50f, step = 10f),
            batteryImpact = ImpactLevel.LOW,
            accuracyImpact = "Lower = more distinct places, higher = broader place definitions",
            performanceImpact = "Affects clustering algorithm complexity",
            useCases = listOf(
                UseCase("Dense urban area", "20-40 meters to distinguish buildings", 30f),
                UseCase("Suburban area", "50-75 meters standard grouping", 60f),
                UseCase("Rural/large areas", "100+ meters for broad locations", 120f)
            ),
            type = ParameterType.FLOAT
        ),

        ParameterMetadata(
            id = "min_points_for_cluster",
            name = "Minimum Points for Place",
            category = ParameterCategory.PLACE_DETECTION,
            description = "How many location points needed to identify a place",
            whatItDoes = "Requires this many GPS readings in an area before recognizing it as a significant place. Higher values prevent one-time visits from becoming places.",
            unit = "points",
            range = ParameterRange(min = 3f, max = 20f, default = 5f, step = 1f),
            batteryImpact = ImpactLevel.MINIMAL,
            accuracyImpact = "Lower = detects places faster, higher = more confidence required",
            performanceImpact = "Minimal computational impact",
            useCases = listOf(
                UseCase("Catch quick stops", "3-5 points for brief visits", 3f),
                UseCase("Significant places only", "8-12 points for longer stays", 10f),
                UseCase("Very selective", "15+ points for frequent locations", 15f)
            ),
            type = ParameterType.INTEGER
        ),

        ParameterMetadata(
            id = "session_break_time",
            name = "Session Break Time",
            category = ParameterCategory.PLACE_DETECTION,
            description = "Time gap that splits visits into separate sessions",
            whatItDoes = "If you leave a place and return within this time, it's considered the same visit. Exceeding this creates a new visit session.",
            unit = "minutes",
            range = ParameterRange(min = 5f, max = 120f, default = 30f, step = 5f),
            batteryImpact = ImpactLevel.MINIMAL,
            accuracyImpact = "Lower = more visit sessions, higher = merges quick trips",
            performanceImpact = "Affects visit boundary detection logic",
            useCases = listOf(
                UseCase("Capture every visit", "5-15 minutes for distinct sessions", 10f),
                UseCase("Ignore quick errands", "30-45 minutes standard", 30f),
                UseCase("Merge long outings", "60+ minutes for extended trips", 90f)
            ),
            type = ParameterType.LONG
        ),

        ParameterMetadata(
            id = "min_distance_between_places",
            name = "Minimum Distance Between Places",
            category = ParameterCategory.PLACE_DETECTION,
            description = "Enforced minimum separation for distinct places",
            whatItDoes = "Prevents two places from being created too close together. If a new place would be closer than this to an existing place, they get merged.",
            unit = "meters",
            range = ParameterRange(min = 10f, max = 500f, default = 100f, step = 10f),
            batteryImpact = ImpactLevel.MINIMAL,
            accuracyImpact = "Lower = allows nearby distinct places, higher = broader merging",
            performanceImpact = "Affects place deduplication checks",
            useCases = listOf(
                UseCase("Dense locations", "10-50 meters for nearby places", 25f),
                UseCase("Standard separation", "100 meters balanced", 100f),
                UseCase("Broad areas only", "200+ meters for major locations", 250f)
            ),
            type = ParameterType.FLOAT
        ),

        // Battery Optimization Parameters
        ParameterMetadata(
            id = "stationary_interval_multiplier",
            name = "Stationary Interval Multiplier",
            category = ParameterCategory.BATTERY_OPTIMIZATION,
            description = "How much to slow down updates when stationary",
            whatItDoes = "When you're not moving, multiplies the update interval by this factor to save battery. For example, 2x means updates happen half as often when stationary.",
            unit = "multiplier",
            range = ParameterRange(min = 1f, max = 10f, default = 2f, step = 0.5f),
            batteryImpact = ImpactLevel.HIGH,
            accuracyImpact = "Higher values may delay detecting when you start moving",
            performanceImpact = "Significantly reduces GPS wake-ups when idle",
            useCases = listOf(
                UseCase("Fast movement detection", "1-1.5x minimal slowdown", 1f),
                UseCase("Balanced battery saving", "2-3x standard optimization", 2f),
                UseCase("Maximum battery saving", "5-10x aggressive reduction", 8f)
            ),
            type = ParameterType.FLOAT
        ),

        ParameterMetadata(
            id = "stationary_distance_multiplier",
            name = "Stationary Distance Multiplier",
            category = ParameterCategory.BATTERY_OPTIMIZATION,
            description = "Increase distance threshold when stationary",
            whatItDoes = "When idle, requires moving farther to trigger a location update. Reduces false positives from GPS drift while stationary.",
            unit = "multiplier",
            range = ParameterRange(min = 1f, max = 5f, default = 1.5f, step = 0.25f),
            batteryImpact = ImpactLevel.MODERATE,
            accuracyImpact = "Higher values filter more drift but may miss small movements",
            performanceImpact = "Reduces processing of insignificant movements",
            useCases = listOf(
                UseCase("Precise idle tracking", "1-1.25x minimal filtering", 1f),
                UseCase("Standard drift filter", "1.5-2x balanced", 1.5f),
                UseCase("Aggressive filtering", "3-5x heavy suppression", 3f)
            ),
            type = ParameterType.FLOAT
        ),

        ParameterMetadata(
            id = "stationary_threshold",
            name = "Stationary Movement Threshold",
            category = ParameterCategory.BATTERY_OPTIMIZATION,
            description = "Distance that counts as 'not moving'",
            whatItDoes = "If all movements over the check period are less than this distance, you're considered stationary and battery optimizations activate.",
            unit = "meters",
            range = ParameterRange(min = 5f, max = 100f, default = 20f, step = 5f),
            batteryImpact = ImpactLevel.HIGH,
            accuracyImpact = "Lower = stricter stationary detection, higher = easier to trigger",
            performanceImpact = "Determines when battery-saving mode activates",
            useCases = listOf(
                UseCase("High sensitivity", "5-10 meters for precise detection", 8f),
                UseCase("Standard detection", "20-30 meters balanced", 20f),
                UseCase("Relaxed detection", "50+ meters easier to trigger", 60f)
            ),
            type = ParameterType.FLOAT
        )
    )

    fun getParameterById(id: String): ParameterMetadata? = allParameters.find { it.id == id }

    fun getParametersByCategory(category: ParameterCategory): List<ParameterMetadata> =
        allParameters.filter { it.category == category }
}
