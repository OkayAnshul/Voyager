package com.cosmiclaboratory.voyager.storage.database.converter

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    // Instant <-> Long (epoch millis)
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(epochMs: Long?): Instant? = epochMs?.let { Instant.ofEpochMilli(it) }

    // JSON String <-> Map<String, Int>
    @TypeConverter
    fun fromStringIntMap(map: Map<String, Int>?): String? =
        map?.let { json.encodeToString(it) }

    @TypeConverter
    fun toStringIntMap(value: String?): Map<String, Int>? =
        value?.let { json.decodeFromString(it) }

    // JSON String <-> Map<String, Long>
    @TypeConverter
    fun fromStringLongMap(map: Map<String, Long>?): String? =
        map?.let { json.encodeToString(it) }

    @TypeConverter
    fun toStringLongMap(value: String?): Map<String, Long>? =
        value?.let { json.decodeFromString(it) }

    // JSON String <-> List<Long>
    @TypeConverter
    fun fromLongList(list: List<Long>?): String? =
        list?.let { json.encodeToString(it) }

    @TypeConverter
    fun toLongList(value: String?): List<Long>? =
        value?.let { json.decodeFromString(it) }

    // JSON String <-> List<String>
    @TypeConverter
    fun fromStringList(list: List<String>?): String? =
        list?.let { json.encodeToString(it) }

    @TypeConverter
    fun toStringList(value: String?): List<String>? =
        value?.let { json.decodeFromString(it) }
}
