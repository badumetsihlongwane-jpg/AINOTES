package com.ainotes.studyassistant.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM")
private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM HH:mm")

fun formatEpochDate(epochMillis: Long?): String {
    if (epochMillis == null) return "No date"
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateFormatter)
}

fun formatEpochDateTime(epochMillis: Long): String {
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateTimeFormatter)
}
