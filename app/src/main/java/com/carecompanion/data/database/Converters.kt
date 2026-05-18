package com.carecompanion.data.database

import android.util.Base64
import androidx.room.TypeConverter
import java.util.Date

class Converters {
    @TypeConverter fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }
    @TypeConverter fun dateToTimestamp(date: Date?): Long? = date?.time
    @TypeConverter fun fromByteArray(bytes: ByteArray?): String? = bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
    @TypeConverter fun toByteArray(encoded: String?): ByteArray? = encoded?.let { Base64.decode(it, Base64.DEFAULT) }
}