package com.nailfec.nfwstatus.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.nailfec.nfwstatus.model.SleepStageUpload
import com.nailfec.nfwstatus.model.SleepUpload
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface SleepRead {
    data class Found(val uploads: List<SleepUpload>) : SleepRead
    data object Unavailable : SleepRead
    data object Empty : SleepRead
    data object Denied : SleepRead
}

@Singleton
class SleepHealthReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val readPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)

    fun isAvailable(): Boolean = try {
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    } catch (_: Exception) {
        false
    }

    suspend fun hasPermission(): Boolean {
        val client = clientOrNull() ?: return false
        return try {
            client.permissionController.getGrantedPermissions().contains(readPermission)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            false
        }
    }

    suspend fun readRecent(): SleepRead {
        val end = Instant.now()
        val start = end.minus(36, ChronoUnit.HOURS)
        return readGrouped(start, end) { true }
    }

    suspend fun readWakeDate(date: LocalDate): SleepRead {
        val zone = ZoneId.systemDefault()
        val start = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        return readGrouped(start, end) { wakeDate(it) == date }
    }

    private suspend fun readGrouped(
        start: Instant,
        end: Instant,
        include: (SleepSessionRecord) -> Boolean,
    ): SleepRead = withContext(Dispatchers.IO) {
        val client = clientOrNull() ?: return@withContext SleepRead.Unavailable
        try {
            val records = readAll(client, start, end).filter(include)
            val uploads = records
                .groupBy { wakeDate(it) }
                .mapNotNull { (_, group) -> merge(group) }
                .sortedBy { it.date }
            if (uploads.isEmpty()) SleepRead.Empty else SleepRead.Found(uploads)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: SecurityException) {
            SleepRead.Denied
        } catch (_: Exception) {
            SleepRead.Unavailable
        }
    }

    private fun clientOrNull(): HealthConnectClient? = try {
        if (!isAvailable()) null else HealthConnectClient.getOrCreate(context)
    } catch (_: Exception) {
        null
    }

    private suspend fun readAll(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
    ): List<SleepSessionRecord> {
        val records = mutableListOf<SleepSessionRecord>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken,
                ),
            )
            records += response.records
            pageToken = response.pageToken
        } while (!pageToken.isNullOrEmpty())
        return records
    }

    private fun merge(records: List<SleepSessionRecord>): SleepUpload? {
        if (records.isEmpty()) return null
        val earliest = records.minBy { it.startTime }
        val latest = records.maxBy { it.endTime }
        val duration = records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
        val stages = records
            .sortedBy { it.startTime }
            .flatMap { record -> record.stages.sortedBy { it.startTime } }
            .map { stage ->
                SleepStageUpload(
                    stage = mapStage(stage.stage),
                    start = instantIso(stage.startTime, null),
                    end = instantIso(stage.endTime, null),
                )
            }
        return SleepUpload(
            date = wakeDate(latest).toString(),
            bedtime = instantIso(earliest.startTime, earliest.startZoneOffset),
            wakeTime = instantIso(latest.endTime, latest.endZoneOffset),
            durationMinutes = duration,
            stages = stages,
        )
    }

    private fun wakeDate(record: SleepSessionRecord): LocalDate {
        val offset = record.endZoneOffset ?: ZoneId.systemDefault().rules.getOffset(record.endTime)
        return record.endTime.atOffset(offset).toLocalDate()
    }

    private fun instantIso(instant: Instant, offset: ZoneOffset?): String {
        val resolved = offset ?: ZoneId.systemDefault().rules.getOffset(instant)
        return instant.atOffset(resolved).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    private fun mapStage(stage: Int): String = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        -> "awake"
        SleepSessionRecord.STAGE_TYPE_LIGHT,
        SleepSessionRecord.STAGE_TYPE_SLEEPING,
        -> "light"
        SleepSessionRecord.STAGE_TYPE_DEEP -> "deep"
        SleepSessionRecord.STAGE_TYPE_REM -> "rem"
        else -> "unknown"
    }
}
