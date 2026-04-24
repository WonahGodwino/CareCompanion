package com.carecompanion.data.database

import androidx.room.TypeConverter
import java.util.Base64
import java.util.Date

class Converters {
    @TypeConverter fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }
    @TypeConverter fun dateToTimestamp(date: Date?): Long? = date?.time
    @TypeConverter fun fromByteArray(bytes: ByteArray?): String? = bytes?.let { Base64.getEncoder().encodeToString(it) }
    @TypeConverter fun toByteArray(encoded: String?): ByteArray? = encoded?.let { Base64.getDecoder().decode(it) }
}