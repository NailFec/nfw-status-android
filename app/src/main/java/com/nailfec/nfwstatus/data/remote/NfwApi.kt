package com.nailfec.nfwstatus.data.remote

import com.nailfec.nfwstatus.model.UploadPhoto
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

@Singleton
class NfwApi @Inject constructor(
    client: OkHttpClient,
    private val json: Json,
) {
    private val normalClient = client
    private val uploadClient = client.newBuilder()
        .callTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun getStatus(creds: Credentials): NetworkResult<StatusListDto> =
        get(creds, "/api/status?limit=100", auth = false)

    suspend fun createStatus(creds: Credentials, level: String, content: String): NetworkResult<Unit> =
        sendJson(creds, "/api/status", CreateStatusDto(level, content), upload = false)

    suspend fun patchStatus(
        creds: Credentials,
        id: String,
        level: String,
        content: String,
        startedAt: String,
        endedAt: String?,
    ): NetworkResult<Unit> = sendEmpty(
        creds = creds,
        path = "/api/status/$id",
        method = "PATCH",
        body = json.encodeToString(PatchStatusDto(level, content, startedAt, endedAt)).toJsonBody(),
        auth = true,
        upload = false,
    )

    suspend fun deleteStatus(creds: Credentials, id: String): NetworkResult<Unit> =
        sendEmpty(creds, "/api/status/$id", "DELETE", null, auth = true, upload = false)

    suspend fun getPhrases(creds: Credentials): NetworkResult<PhraseListDto> =
        get(creds, "/api/phrases", auth = true)

    suspend fun deletePhrase(creds: Credentials, id: String): NetworkResult<Unit> =
        sendEmpty(creds, "/api/phrases/$id", "DELETE", null, auth = true, upload = false)

    suspend fun getMeals(creds: Credentials): NetworkResult<MealListDto> =
        get(creds, "/api/meals?limit=60", auth = false)

    suspend fun createMeal(
        creds: Credentials,
        slot: String,
        eatenAt: String,
        photos: List<UploadPhoto>,
    ): NetworkResult<Unit> {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("slot", slot)
            .addFormDataPart("eatenAt", eatenAt)
            .apply { photos.forEach { addPhoto(it) } }
            .build()
        return sendEmpty(creds, "/api/meals", "POST", body, auth = true, upload = true)
    }

    suspend fun patchMeal(
        creds: Credentials,
        id: String,
        slot: String,
        eatenAt: String,
        removePhotoIds: List<String>,
        photos: List<UploadPhoto>,
    ): NetworkResult<Unit> {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("slot", slot)
            .addFormDataPart("eatenAt", eatenAt)
            .apply {
                removePhotoIds.forEach { addFormDataPart("removePhotoIds", it) }
                photos.forEach { addPhoto(it) }
            }
            .build()
        return sendEmpty(creds, "/api/meals/$id", "PATCH", body, auth = true, upload = true)
    }

    suspend fun deleteMeal(creds: Credentials, id: String): NetworkResult<Unit> =
        sendEmpty(creds, "/api/meals/$id", "DELETE", null, auth = true, upload = false)

    suspend fun getSleep(creds: Credentials): NetworkResult<SleepListDto> =
        get(creds, "/api/sleep?limit=30", auth = false)

    suspend fun postSleep(creds: Credentials, body: SleepPostDto): NetworkResult<Unit> =
        sendJson(creds, "/api/sleep", body, upload = false)

    suspend fun patchSleep(creds: Credentials, date: String, body: SleepPatchDto): NetworkResult<Unit> =
        sendEmpty(
            creds,
            "/api/sleep/$date",
            "PATCH",
            json.encodeToString(body).toJsonBody(),
            auth = true,
            upload = false,
        )

    suspend fun deleteSleep(creds: Credentials, date: String): NetworkResult<Unit> =
        sendEmpty(creds, "/api/sleep/$date", "DELETE", null, auth = true, upload = false)

    suspend fun getCalendars(creds: Credentials): NetworkResult<CalendarListDto> =
        get(creds, "/api/calendars", auth = true)

    suspend fun postCalendar(creds: Credentials, name: String, url: String): NetworkResult<Unit> =
        sendJson(creds, "/api/calendars", CalendarPostDto(name, url), upload = false)

    suspend fun deleteCalendar(creds: Credentials, id: String): NetworkResult<Unit> =
        sendEmpty(creds, "/api/calendars/$id", "DELETE", null, auth = true, upload = false)

    suspend fun refreshCalendars(creds: Credentials): NetworkResult<Unit> =
        sendEmpty(
            creds,
            "/api/calendars/refresh",
            "POST",
            ByteArray(0).toRequestBody(null),
            auth = true,
            upload = true,
        )

    private suspend inline fun <reified T> get(
        creds: Credentials,
        path: String,
        auth: Boolean,
    ): NetworkResult<T> {
        val url = endpoint(creds, path) ?: return NetworkResult.Failure(FailureKind.BadUrl)
        val request = Request.Builder()
            .url(url)
            .get()
            .auth(if (auth) creds.token else null)
            .build()
        return send(normalClient, request) { decode(it) }
    }

    private suspend inline fun <reified T> sendJson(
        creds: Credentials,
        path: String,
        payload: T,
        upload: Boolean,
    ): NetworkResult<Unit> = sendEmpty(
        creds = creds,
        path = path,
        method = "POST",
        body = json.encodeToString(payload).toJsonBody(),
        auth = true,
        upload = upload,
    )

    private suspend fun sendEmpty(
        creds: Credentials,
        path: String,
        method: String,
        body: RequestBody?,
        auth: Boolean,
        upload: Boolean,
    ): NetworkResult<Unit> {
        val url = endpoint(creds, path) ?: return NetworkResult.Failure(FailureKind.BadUrl)
        val request = Request.Builder()
            .url(url)
            .method(method, body)
            .auth(if (auth) creds.token else null)
            .build()
        val http = if (upload) uploadClient else normalClient
        return send(http, request) { empty(it) }
    }

    private suspend fun <T> send(
        http: OkHttpClient,
        request: Request,
        parse: (Response) -> NetworkResult<T>,
    ): NetworkResult<T> = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use(parse)
        } catch (_: IOException) {
            NetworkResult.Failure(FailureKind.Unreachable)
        }
    }

    private inline fun <reified T> decode(response: Response): NetworkResult<T> {
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            return NetworkResult.Failure(mapFailure(response.code, errorCode(text)))
        }
        return runCatching { json.decodeFromString<T>(text) }.fold(
            onSuccess = { NetworkResult.Success(it) },
            onFailure = { NetworkResult.Failure(FailureKind.Invalid) },
        )
    }

    private fun empty(response: Response): NetworkResult<Unit> {
        val text = response.body?.string().orEmpty()
        return if (response.isSuccessful) {
            NetworkResult.Success(Unit)
        } else {
            NetworkResult.Failure(mapFailure(response.code, errorCode(text)))
        }
    }

    private fun errorCode(text: String): String? =
        runCatching { json.decodeFromString<ErrorDto>(text).error }.getOrNull()

    private fun mapFailure(code: Int, error: String?): FailureKind {
        if (code == 401 || error == "unauthorized") return FailureKind.TokenRejected
        return when (error) {
            "overlap" -> FailureKind.Overlap
            "invalid" -> FailureKind.Invalid
            "not_found" -> FailureKind.NotFound
            "exists" -> FailureKind.Exists
            else -> when (code) {
                409 -> FailureKind.Exists
                in 500..599 -> FailureKind.Unreachable
                else -> FailureKind.Invalid
            }
        }
    }

    private fun endpoint(creds: Credentials, path: String): HttpUrl? =
        runCatching { (creds.baseUrl + path).toHttpUrl() }.getOrNull()

    private fun Request.Builder.auth(token: String?): Request.Builder {
        if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
        return this
    }

    private fun String.toJsonBody(): RequestBody = toRequestBody(JSON_MEDIA)

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

private fun MultipartBody.Builder.addPhoto(photo: UploadPhoto): MultipartBody.Builder =
    addFormDataPart(
        "photo",
        photo.fileName,
        photo.bytes.toRequestBody(photo.mimeType.toMediaType()),
    )
