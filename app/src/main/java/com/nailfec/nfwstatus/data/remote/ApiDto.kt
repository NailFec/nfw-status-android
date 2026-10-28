package com.nailfec.nfwstatus.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class ErrorDto(val error: String? = null)

@Serializable
data class StatusListDto(val segments: List<SegmentDto> = emptyList())

@Serializable
data class SegmentDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val level: String,
    val content: String = "",
    val startedAt: String,
    val endedAt: String? = null,
)

@Serializable
data class CreateStatusDto(
    val level: String,
    val content: String,
)

@Serializable
data class HistoryPageDto(
    val timezone: String = "Asia/Shanghai",
    val now: String,
    val days: List<HistoryDayDto> = emptyList(),
    val hasMore: Boolean = false,
    val current: CurrentStatusDto? = null,
)

@Serializable
data class HistoryDayDto(
    val day: String,
    val segments: List<SegmentDto> = emptyList(),
)

@Serializable
data class CurrentStatusDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val level: String,
    val content: String = "",
    val startedAt: String,
)

@Serializable
data class PatchStatusDto(
    val level: String,
    val content: String,
    val startedAt: String,
)

@Serializable
data class PhraseListDto(val phrases: List<PhraseDto> = emptyList())

@Serializable
data class PhraseDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val text: String,
    val lastUsedAt: String? = null,
)

@Serializable
data class MealListDto(val meals: List<MealDto> = emptyList())

@Serializable
data class MealDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val day: String,
    val slot: String,
    val eatenAt: String,
    val photos: List<PhotoDto> = emptyList(),
)

@Serializable
data class PhotoDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val url: String,
)

@Serializable
data class SleepListDto(val days: List<SleepDayDto> = emptyList())

@Serializable
data class SleepDayDto(
    val date: String,
    val bedtime: String? = null,
    val wakeTime: String? = null,
    val durationMinutes: Int? = null,
    val stages: List<StageDto> = emptyList(),
)

@Serializable
data class StageDto(
    val stage: String,
    val start: String,
    val end: String,
)

@Serializable
data class SleepPostDto(
    val date: String,
    val bedtime: String,
    val wakeTime: String,
    val durationMinutes: Int,
    val stages: List<StageDto>,
)

@Serializable
data class SleepDatesDto(val dates: List<String> = emptyList())

@Serializable
data class CalendarListDto(val subscriptions: List<CalendarDto> = emptyList())

@Serializable
data class CalendarDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val name: String,
    val url: String,
    val lastOkAt: String? = null,
    val lastError: String? = null,
)

@Serializable
data class CalendarPostDto(
    val name: String,
    val url: String,
)
