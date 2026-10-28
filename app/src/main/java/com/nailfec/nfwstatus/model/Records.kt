package com.nailfec.nfwstatus.model

data class StatusSegment(
    val id: String,
    val level: String,
    val content: String,
    val startedAt: String,
    val endedAt: String?,
)

data class Phrase(
    val id: String,
    val text: String,
)

data class MealPhoto(
    val id: String,
    val url: String,
)

data class Meal(
    val id: String,
    val day: String,
    val slot: String,
    val eatenAt: String,
    val photos: List<MealPhoto>,
)

data class SleepStage(
    val stage: String,
    val start: String,
    val end: String,
)

data class SleepDay(
    val date: String,
    val bedtime: String?,
    val wakeTime: String?,
    val durationMinutes: Int?,
    val stages: List<SleepStage>,
)

data class CalendarSubscription(
    val id: String,
    val name: String,
    val url: String,
    val lastError: String?,
)

data class SleepStageUpload(
    val stage: String,
    val start: String,
    val end: String,
)

data class SleepUpload(
    val date: String,
    val bedtime: String,
    val wakeTime: String,
    val durationMinutes: Int,
    val stages: List<SleepStageUpload>,
)

class UploadPhoto(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)
