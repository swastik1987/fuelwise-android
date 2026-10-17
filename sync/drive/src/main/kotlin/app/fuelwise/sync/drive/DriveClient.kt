package app.fuelwise.sync.drive

import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Drive failures the status screen can explain and fix (PRD F-14.8). */
sealed class DriveException(message: String) : IOException(message) {
    class Unauthorized : DriveException("Google sign-in expired")

    class StorageFull : DriveException("Google Drive storage is full")

    class Http(val code: Int) : DriveException("Drive request failed: $code")
}

/**
 * Drive REST v3 against the hidden `appDataFolder` (scope `drive.appdata`, PRD F-14.1). The only network calls the
 * app makes for user data; nothing is sent anywhere else (CLAUDE.md rule 5).
 */
@Singleton
class DriveClient @Inject constructor(
    @param:Named(DRIVE_BASE_URL) private val baseUrl: String,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private val api: HttpUrl get() = "$baseUrl/drive/v3/files".toHttpUrl()

    suspend fun list(token: String): List<RemoteBackup> = withContext(io) {
        val files = mutableListOf<RemoteBackup>()
        var pageToken: String? = null
        do {
            val url = api.newBuilder()
                .addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("fields", "nextPageToken,files(id,name,size,createdTime)")
                .addQueryParameter("pageSize", PAGE_SIZE)
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val body = execute(Request.Builder().url(url).auth(token).build()).use { it.body.string() }
            val root = json.parseToJsonElement(body).jsonObject
            ((root["files"] as? JsonArray) ?: JsonArray(emptyList())).forEach { f ->
                val o = f.jsonObject
                files += RemoteBackup(
                    id = o.getValue("id").jsonPrimitive.content,
                    name = o.getValue("name").jsonPrimitive.content,
                    createdAtUtcMs =
                        o["createdTime"]?.jsonPrimitive?.content?.let { Instant.parse(it).toEpochMilli() } ?: 0,
                    sizeBytes = o["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                )
            }
            pageToken = root["nextPageToken"]?.jsonPrimitive?.content
        } while (pageToken != null)
        files
    }

    /** One multipart request: a dropped connection leaves no half file behind, so a rerun starts clean. */
    suspend fun upload(token: String, name: String, bytes: ByteArray): String = withContext(io) {
        val metadata = JsonObject(
            mapOf(
                "name" to JsonPrimitive(name),
                "parents" to JsonArray(listOf(JsonPrimitive("appDataFolder"))),
            ),
        )
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        val url = "$baseUrl/upload/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id")
            .build()
        execute(Request.Builder().url(url).auth(token).post(body).build()).use { response ->
            json.parseToJsonElement(response.body.string()).jsonObject.getValue("id").jsonPrimitive.content
        }
    }

    suspend fun download(token: String, id: String): ByteArray = withContext(io) {
        val url = api.newBuilder().addPathSegment(id).addQueryParameter("alt", "media").build()
        execute(Request.Builder().url(url).auth(token).build()).use { it.body.bytes() }
    }

    suspend fun delete(token: String, id: String) {
        withContext(io) {
            execute(
                Request.Builder().url(api.newBuilder().addPathSegment(id).build()).auth(token).delete().build(),
            ).close()
        }
    }

    private fun execute(request: Request): Response {
        val response = http.newCall(request).execute()
        if (response.isSuccessful) return response
        val body = response.body.string()
        response.close()
        throw when {
            response.code == HTTP_UNAUTHORIZED -> DriveException.Unauthorized()
            response.code == HTTP_FORBIDDEN && body.contains("storageQuotaExceeded") -> DriveException.StorageFull()
            else -> DriveException.Http(response.code)
        }
    }

    private fun Request.Builder.auth(token: String) = header("Authorization", "Bearer $token")

    companion object {
        const val DRIVE_BASE_URL = "driveBaseUrl"
        const val GOOGLE_APIS = "https://www.googleapis.com"
        private const val TIMEOUT_S = 60L
        private const val PAGE_SIZE = "1000"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }
}
