package com.example.feedsense.database

import androidx.room.TypeConverter
import org.json.JSONObject
import java.time.LocalDateTime

class Converters {

    @TypeConverter
    fun fromLocalDateTime(dateTime: LocalDateTime?): String? {
        return dateTime?.toString()
    }

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? {
        return value?.let {
            LocalDateTime.parse(it)
        }
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.joinToString("|")
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        return value
            ?.takeIf { it.isNotEmpty() }
            ?.split("|")
            ?: emptyList()
    }

    /*
     * Milestone 7W. Multi-label confidence scores,
     * serialized as JSON so per-category scores survive
     * into the feed item and the exported dataset.
     */
    @TypeConverter
    fun fromCategoryScores(value: Map<String, Double>?): String? {
        if (value.isNullOrEmpty()) return "{}"
        return try {
            JSONObject(value).toString()
        } catch (_: Exception) {
            "{}"
        }
    }

    @TypeConverter
    fun toCategoryScores(value: String?): Map<String, Double> {
        if (value.isNullOrBlank()) {
            return emptyMap()
        }
        return try {
            val json = JSONObject(value)
            buildMap {
                json.keys().forEach { key ->
                    put(
                        key,
                        json.optDouble(key, 0.0)
                    )
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}