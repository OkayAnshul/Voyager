package com.cosmiclaboratory.voyager.data.database.converter

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.TypeConverter
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.ReviewStatus
import com.cosmiclaboratory.voyager.domain.model.ReviewPriority
import com.cosmiclaboratory.voyager.domain.model.ReviewType
import com.cosmiclaboratory.voyager.domain.model.VisitReviewReason
import com.cosmiclaboratory.voyager.domain.model.CorrectionType
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Converters {
    
    @RequiresApi(Build.VERSION_CODES.O)
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    
    @RequiresApi(Build.VERSION_CODES.O)
    @TypeConverter
    fun fromLocalDateTime(dateTime: LocalDateTime?): String? {
        return dateTime?.format(formatter)
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    @TypeConverter
    fun toLocalDateTime(dateTimeString: String?): LocalDateTime? {
        return dateTimeString?.let {
            LocalDateTime.parse(it, formatter)
        }
    }
    
    @TypeConverter
    fun fromPlaceCategory(category: PlaceCategory): String {
        return category.name
    }
    
    @TypeConverter
    fun toPlaceCategory(categoryString: String): PlaceCategory {
        return try {
            PlaceCategory.valueOf(categoryString)
        } catch (e: IllegalArgumentException) {
            PlaceCategory.UNKNOWN
        }
    }

    // Instant converters for geocoding cache
    @RequiresApi(Build.VERSION_CODES.O)
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @TypeConverter
    fun toInstant(epochMilli: Long?): Instant? {
        return epochMilli?.let { Instant.ofEpochMilli(it) }
    }

    // Week 2: New enum converters for review system

    @TypeConverter
    fun fromReviewStatus(status: ReviewStatus): String {
        return status.name
    }

    @TypeConverter
    fun toReviewStatus(statusString: String): ReviewStatus {
        return try {
            ReviewStatus.valueOf(statusString)
        } catch (e: IllegalArgumentException) {
            ReviewStatus.PENDING
        }
    }

    @TypeConverter
    fun fromReviewPriority(priority: ReviewPriority): String {
        return priority.name
    }

    @TypeConverter
    fun toReviewPriority(priorityString: String): ReviewPriority {
        return try {
            ReviewPriority.valueOf(priorityString)
        } catch (e: IllegalArgumentException) {
            ReviewPriority.NORMAL
        }
    }

    @TypeConverter
    fun fromReviewType(type: ReviewType): String {
        return type.name
    }

    @TypeConverter
    fun toReviewType(typeString: String): ReviewType {
        return try {
            ReviewType.valueOf(typeString)
        } catch (e: IllegalArgumentException) {
            ReviewType.NEW_PLACE
        }
    }

    @TypeConverter
    fun fromVisitReviewReason(reason: VisitReviewReason): String {
        return reason.name
    }

    @TypeConverter
    fun toVisitReviewReason(reasonString: String): VisitReviewReason {
        return try {
            VisitReviewReason.valueOf(reasonString)
        } catch (e: IllegalArgumentException) {
            VisitReviewReason.MANUAL_REVIEW
        }
    }

    @TypeConverter
    fun fromCorrectionType(type: CorrectionType): String {
        return type.name
    }

    @TypeConverter
    fun toCorrectionType(typeString: String): CorrectionType {
        return try {
            CorrectionType.valueOf(typeString)
        } catch (e: IllegalArgumentException) {
            CorrectionType.NAME_CHANGE
        }
    }
}