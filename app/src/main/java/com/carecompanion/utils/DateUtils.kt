package com.carecompanion.utils

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object DateUtils {
    private val watTimeZone: TimeZone = TimeZone.getTimeZone("Africa/Lagos")
    private val inputFormats = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'","yyyy-MM-dd'T'HH:mm:ss'Z'","yyyy-MM-dd HH:mm:ss","yyyy-MM-dd","dd/MM/yyyy","MM/dd/yyyy")
    private val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).apply { timeZone = watTimeZone }
    private val timeFormat = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.getDefault()).apply { timeZone = watTimeZone }
    fun parseDate(dateString: String?): Date? {
        if (dateString.isNullOrBlank()) return null
        return try {
            when {
                dateString.contains("T") -> SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(dateString)
                else -> SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateString)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun formatIso8601(date: Date): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)
    fun formatDate(date: Date?): String = date?.let { outputFormat.format(it) } ?: "N/A"
    fun formatDateTime(date: Date?): String = date?.let { timeFormat.format(it) } ?: "N/A"
    fun calculateAge(dob: Date?): Int = if (dob==null) 0 else (TimeUnit.MILLISECONDS.toDays(Date().time-dob.time)/365).toInt()
}