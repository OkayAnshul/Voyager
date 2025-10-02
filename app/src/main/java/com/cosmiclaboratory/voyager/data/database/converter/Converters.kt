package com.cosmiclaboratory.voyager.data.database.converter

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.TypeConverter
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
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
}