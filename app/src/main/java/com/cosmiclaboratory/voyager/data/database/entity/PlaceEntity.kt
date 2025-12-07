package com.cosmiclaboratory.voyager.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import java.time.LocalDateTime

@Entity(
    tableName = "places",
    indices = [
        Index(value = ["category"]),
        Index(value = ["latitude", "longitude"]),
        Index(value = ["visitCount"]),
        Index(value = ["lastVisit"]),
        Index(value = ["locality"])  // NEW: For area-based queries
    ]
)
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val category: PlaceCategory,
    val customCategoryName: String? = null,  // ISSUE #3: For CUSTOM category user names
    val latitude: Double,
    val longitude: Double,

    // Geocoding fields
    val address: String? = null,
    val streetName: String? = null,
    val locality: String? = null,
    val subLocality: String? = null,
    val postalCode: String? = null,
    val countryCode: String? = null,
    val isUserRenamed: Boolean = false,
    val needsUserNaming: Boolean = false,

    // OSM enrichment fields
    val osmSuggestedName: String? = null,
    val osmSuggestedCategory: String? = null,  // PlaceCategory enum name
    val osmPlaceType: String? = null,

    // Phase 2: Activity-based insights (stored as JSON strings for simplicity)
    val dominantActivity: String? = null,           // UserActivity enum name
    val activityDistributionJson: String? = null,   // JSON map of activity percentages
    val dominantSemanticContext: String? = null,    // SemanticContext enum name
    val contextDistributionJson: String? = null,    // JSON map of context percentages

    val visitCount: Int = 0,
    val totalTimeSpent: Long = 0L,
    val lastVisit: LocalDateTime? = null,
    val isCustom: Boolean = false,
    val radius: Double = 100.0,
    val placeId: String? = null,
    val confidence: Float = 1.0f
)