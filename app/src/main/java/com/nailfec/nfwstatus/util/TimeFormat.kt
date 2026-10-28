package com.nailfec.nfwstatus.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object TimeFormat {
    data class Stamp(val date: LocalDate, val time: LocalTime)

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US)
    private val timeFormatter =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.US)
    private val dateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT).withLocale(Locale.US)

    fun today(): LocalDate = LocalDate.now(zone)

    fun stampNow(): Stamp {
        val now = LocalDateTime.now(zone)
        return Stamp(now.toLocalDate(), LocalTime.of(now.hour, now.minute))
    }

    fun parse(iso: String): Stamp? {
        val zoned = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone) }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(iso).withZoneSameInstant(zone) }.getOrNull()
            ?: runCatching { Instant.parse(iso).atZone(zone) }.getOrNull()
            ?: return null
        return Stamp(zoned.toLocalDate(), zoned.toLocalTime().withNano(0))
    }

    fun formatDate(isoDate: String): String {
        val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
        return date.format(dateFormatter)
    }

    fun formatTime(hour: Int, minute: Int): String =
        LocalTime.of(hour, minute).format(timeFormatter)

    fun formatIso(iso: String): String {
        val stamp = parse(iso) ?: return iso
        return LocalDateTime.of(stamp.date, stamp.time).format(dateTimeFormatter)
    }

    fun dateFromUtcMillis(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    fun dateToUtcMillis(isoDate: String): Long? {
        val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return null
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    fun toIso(isoDate: String, hour: Int, minute: Int, second: Int = 0): String? {
        val dateTime = localDateTime(isoDate, hour, minute, second) ?: return null
        val offset = zone.rules.getOffset(dateTime)
        return OffsetDateTime.of(dateTime, offset).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    fun minutesBetween(
        startDate: String,
        startHour: Int,
        startMinute: Int,
        endDate: String,
        endHour: Int,
        endMinute: Int,
    ): Int? {
        val start = localDateTime(startDate, startHour, startMinute) ?: return null
        val end = localDateTime(endDate, endHour, endMinute) ?: return null
        return Duration.between(start, end).toMinutes().toInt()
    }

    private fun localDateTime(
        isoDate: String,
        hour: Int,
        minute: Int,
        second: Int = 0,
    ): LocalDateTime? {
        val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
        return LocalDateTime.of(date, LocalTime.of(hour, minute, second))
    }
}
