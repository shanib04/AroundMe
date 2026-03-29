package com.colman.aroundme.core.time

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object IsraelTime {
    private const val ISRAEL_TIME_ZONE_ID = "Asia/Jerusalem"
    val zone: TimeZone = TimeZone.getTimeZone(ISRAEL_TIME_ZONE_ID)

    fun calendar(): Calendar = Calendar.getInstance(zone)

    fun formatter(pattern: String, locale: Locale = Locale.getDefault()): SimpleDateFormat {
        return SimpleDateFormat(pattern, locale).apply {
            timeZone = zone
        }
    }

    fun formatDateTime(
        timestamp: Long,
        pattern: String = "EEE, MMM d 'at' HH:mm",
        locale: Locale = Locale.getDefault()
    ): String {
        return formatter(pattern, locale).format(Date(timestamp))
    }

    fun formatDate(
        timestamp: Long,
        pattern: String = "dd MMM yyyy",
        locale: Locale = Locale.getDefault()
    ): String {
        return formatter(pattern, locale).format(Date(timestamp))
    }
}