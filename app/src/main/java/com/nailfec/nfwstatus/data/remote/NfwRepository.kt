package com.nailfec.nfwstatus.data.remote

import android.content.Context
import android.net.Uri
import com.nailfec.nfwstatus.data.settings.SettingsRepository
import com.nailfec.nfwstatus.model.CalendarSubscription
import com.nailfec.nfwstatus.model.HistoryDay
import com.nailfec.nfwstatus.model.HistoryPage
import com.nailfec.nfwstatus.model.Meal
import com.nailfec.nfwstatus.model.MealPhoto
import com.nailfec.nfwstatus.model.Phrase
import com.nailfec.nfwstatus.model.SleepDay
import com.nailfec.nfwstatus.model.SleepStage
import com.nailfec.nfwstatus.model.SleepUpload
import com.nailfec.nfwstatus.model.StatusSegment
import com.nailfec.nfwstatus.model.UploadPhoto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class NfwRepository @Inject constructor(
    private val api: NfwApi,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) {
    suspend fun getHistory(end: String?): NetworkResult<HistoryPage> = authed(requireToken = false) { creds ->
        if (end == null) {
            api.getToday(creds).map { page ->
                historyPage(page.timezone, page.now, page.history, page.historyHasMore, page.current)
            }
        } else {
            api.getHistory(creds, end).map { page ->
                historyPage(page.timezone, page.now, page.days, page.hasMore, page.current)
            }
        }
    }

    private fun historyPage(
        timezone: String,
        now: String,
        days: List<HistoryDayDto>,
        hasMore: Boolean,
        current: CurrentStatusDto?,
    ): HistoryPage = HistoryPage(
        timezone = timezone,
        now = now,
        days = days.map { day ->
            HistoryDay(
                day = day.day,
                segments = day.segments.map { segment ->
                    StatusSegment(
                        id = segment.id,
                        level = segment.level,
                        content = segment.content,
                        startedAt = segment.startedAt,
                        endedAt = segment.endedAt,
                    )
                },
            )
        },
        hasMore = hasMore,
        current = current?.let {
            StatusSegment(
                id = it.id,
                level = it.level,
                content = it.content,
                startedAt = it.startedAt,
                endedAt = null,
            )
        },
    )

    suspend fun getSegments(): NetworkResult<List<StatusSegment>> = authed(requireToken = false) { creds ->
        api.getStatus(creds).map { list ->
            list.segments.map {
                StatusSegment(
                    id = it.id,
                    level = it.level,
                    content = it.content,
                    startedAt = it.startedAt,
                    endedAt = it.endedAt,
                )
            }
        }
    }

    suspend fun createSegment(level: String, content: String): NetworkResult<Unit> =
        authed(requireToken = true) { api.createStatus(it, level, content) }

    suspend fun updateSegment(
        id: String,
        level: String,
        content: String,
        startedAt: String,
    ): NetworkResult<Unit> = authed(requireToken = true) {
        api.patchStatus(it, id, level, content, startedAt)
    }

    suspend fun deleteSegment(id: String): NetworkResult<Unit> =
        authed(requireToken = true) { api.deleteStatus(it, id) }

    suspend fun getPhrases(): NetworkResult<List<Phrase>> {
        val saved = settings.current()
        if (saved.baseUrl.isBlank()) return NetworkResult.Failure(FailureKind.SettingsMissing)
        if (saved.token.isBlank()) return NetworkResult.Success(emptyList())
        return authed(requireToken = true) { creds ->
            api.getPhrases(creds).map { list -> list.phrases.map { Phrase(it.id, it.text) } }
        }
    }

    suspend fun deletePhrase(id: String): NetworkResult<Unit> =
        authed(requireToken = true) { api.deletePhrase(it, id) }

    suspend fun getMeals(): NetworkResult<List<Meal>> = authed(requireToken = false) { creds ->
        api.getMeals(creds).map { list ->
            list.meals.map { meal ->
                Meal(
                    id = meal.id,
                    day = meal.day,
                    slot = meal.slot,
                    eatenAt = meal.eatenAt,
                    photos = meal.photos.map { MealPhoto(it.id, absoluteUrl(creds.baseUrl, it.url)) },
                )
            }
        }
    }

    suspend fun createMeal(slot: String, eatenAt: String, photoUris: List<String>): NetworkResult<Unit> =
        withPhotos(photoUris) { photos ->
            authed(requireToken = true) { api.createMeal(it, slot, eatenAt, photos) }
        }

    suspend fun updateMeal(
        id: String,
        slot: String,
        eatenAt: String,
        removePhotoIds: List<String>,
        photoUris: List<String>,
    ): NetworkResult<Unit> = withPhotos(photoUris) { photos ->
        authed(requireToken = true) { api.patchMeal(it, id, slot, eatenAt, removePhotoIds, photos) }
    }

    suspend fun deleteMeal(id: String): NetworkResult<Unit> =
        authed(requireToken = true) { api.deleteMeal(it, id) }

    suspend fun getSleep(): NetworkResult<List<SleepDay>> = authed(requireToken = false) { creds ->
        api.getSleep(creds).map { list ->
            list.days.map { day ->
                SleepDay(
                    date = day.date,
                    bedtime = day.bedtime,
                    wakeTime = day.wakeTime,
                    durationMinutes = day.durationMinutes,
                    stages = day.stages.map { SleepStage(it.stage, it.start, it.end) },
                )
            }
        }
    }

    suspend fun getSleepDates(): NetworkResult<Set<String>> = authed(requireToken = false) { creds ->
        api.getSleepDates(creds).map { it.dates.toSet() }
    }

    suspend fun upsertSleep(upload: SleepUpload): NetworkResult<Unit> = authed(requireToken = true) { creds ->
        api.postSleep(
            creds,
            SleepPostDto(
                date = upload.date,
                bedtime = upload.bedtime,
                wakeTime = upload.wakeTime,
                durationMinutes = upload.durationMinutes,
                stages = upload.stages.map { StageDto(it.stage, it.start, it.end) },
            ),
        )
    }

    suspend fun deleteSleep(date: String): NetworkResult<Unit> =
        authed(requireToken = true) { api.deleteSleep(it, date) }

    suspend fun getCalendars(): NetworkResult<List<CalendarSubscription>> =
        authed(requireToken = true) { creds ->
            api.getCalendars(creds).map { list ->
                list.subscriptions.map {
                    CalendarSubscription(
                        id = it.id,
                        name = it.name,
                        url = it.url,
                        lastError = it.lastError,
                    )
                }
            }
        }

    suspend fun addCalendar(name: String, url: String): NetworkResult<Unit> =
        authed(requireToken = true) { api.postCalendar(it, name, url) }

    suspend fun deleteCalendar(id: String): NetworkResult<Unit> =
        authed(requireToken = true) { api.deleteCalendar(it, id) }

    suspend fun refreshCalendars(): NetworkResult<Unit> =
        authed(requireToken = true) { api.refreshCalendars(it) }

    private suspend fun <T> authed(
        requireToken: Boolean,
        block: suspend (Credentials) -> NetworkResult<T>,
    ): NetworkResult<T> {
        val saved = settings.current()
        val baseUrl = saved.baseUrl.trim().trimEnd('/')
        val token = saved.token.trim()
        if (baseUrl.isBlank() || (requireToken && token.isBlank())) {
            return NetworkResult.Failure(FailureKind.SettingsMissing)
        }
        return block(Credentials(baseUrl, token))
    }

    private suspend fun withPhotos(
        uris: List<String>,
        block: suspend (List<UploadPhoto>) -> NetworkResult<Unit>,
    ): NetworkResult<Unit> {
        if (uris.isEmpty()) {
            return block(emptyList())
        }
        val photos = loadPhotos(uris) ?: return NetworkResult.Failure(FailureKind.Photo)
        return block(photos)
    }

    private suspend fun loadPhotos(uris: List<String>): List<UploadPhoto>? = withContext(Dispatchers.IO) {
        val loaded = ArrayList<UploadPhoto>(uris.size)
        for (uri in uris) {
            loaded += loadPhoto(uri) ?: return@withContext null
        }
        loaded
    }

    private fun loadPhoto(uriString: String): UploadPhoto? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.isEmpty()) return null
        return UploadPhoto(fileName = "photo.${extensionFor(mime)}", mimeType = mime, bytes = bytes)
    }

    private fun extensionFor(mime: String): String = when {
        mime.endsWith("png") -> "png"
        mime.endsWith("webp") -> "webp"
        mime.endsWith("gif") -> "gif"
        else -> "jpg"
    }

    private fun absoluteUrl(baseUrl: String, url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val base = baseUrl.trimEnd('/')
        val path = if (url.startsWith("/")) url else "/$url"
        val scheme = base.indexOf("://")
        if (scheme < 0) return base + path
        val pathStart = base.indexOf('/', scheme + 3)
        if (pathStart < 0) return base + path
        val origin = base.substring(0, pathStart)
        val basePath = base.substring(pathStart)
        if (path == basePath || path.startsWith("$basePath/")) return origin + path
        return base + path
    }
}

private inline fun <T, R> NetworkResult<T>.map(transform: (T) -> R): NetworkResult<R> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(transform(value))
    is NetworkResult.Failure -> this
}
