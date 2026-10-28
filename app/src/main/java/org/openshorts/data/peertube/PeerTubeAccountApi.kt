package org.openshorts.data.peertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Authenticated PeerTube REST calls (https://docs.joinpeertube.org/api-rest-reference.html).
 * Stateless: every call gets the instance and access token from [org.openshorts.data.AccountRepository].
 */
class PeerTubeAccountApi(private val http: Http, private val json: Json, private val userAgent: String) {

    // --- OAuth2 -------------------------------------------------------------------------------

    suspend fun oauthClient(instance: String): OAuthClient =
        json.decodeFromString(http.request("$instance/api/v1/oauth-clients/local").body)

    /** OAuth2 password grant. [otp] is the 2FA code if the account requires one. */
    suspend fun login(instance: String, client: OAuthClient, username: String, password: String, otp: String?): OAuthToken {
        val response = http.form(
            "$instance/api/v1/users/token",
            mapOf(
                "client_id" to client.clientId,
                "client_secret" to client.clientSecret,
                "grant_type" to "password",
                "response_type" to "code",
                "username" to username,
                "password" to password,
            ),
            headers = if (otp.isNullOrBlank()) emptyMap() else mapOf("x-peertube-otp" to otp.trim()),
        )
        return json.decodeFromString(response.body)
    }

    suspend fun refresh(instance: String, clientId: String, clientSecret: String, refreshToken: String): OAuthToken {
        val response = http.form(
            "$instance/api/v1/users/token",
            mapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
            ),
        )
        return json.decodeFromString(response.body)
    }

    suspend fun revoke(instance: String, token: String) {
        http.request("$instance/api/v1/users/revoke-token", "POST", token, body = ByteArray(0))
    }

    suspend fun me(instance: String, token: String): MeResponse =
        json.decodeFromString(http.request("$instance/api/v1/users/me", token = token).body)

    // --- Videos & ratings ----------------------------------------------------------------------

    /**
     * Makes a (possibly remote) video known to the user's instance and returns its local copy.
     * PeerTube fetches remote videos when a logged-in user searches for their URL.
     */
    suspend fun resolveVideo(instance: String, token: String, videoUrl: String): Video? {
        val url = "$instance/api/v1/search/videos?search=${videoUrl.urlEncoded()}&searchTarget=local&count=1"
        val list: VideoListResponse = json.decodeFromString(http.request(url, token = token).body)
        return list.data.firstOrNull()?.toVideo(instance)
    }

    suspend fun rate(instance: String, token: String, videoId: Long, like: Boolean) {
        val body = buildJsonObject { put("rating", if (like) "like" else "none") }.toString()
        http.json("$instance/api/v1/videos/$videoId/rate", "PUT", token, body)
    }

    suspend fun likedVideos(instance: String, token: String, accountName: String, start: Int, count: Int): List<Video> {
        val url = "$instance/api/v1/accounts/${accountName.urlEncoded()}/ratings?rating=like&start=$start&count=$count"
        val response: RatingsResponse = json.decodeFromString(http.request(url, token = token).body)
        return response.data.mapNotNull { it.video?.toVideo(instance) }
    }

    suspend fun myVideos(instance: String, token: String, start: Int, count: Int): List<Video> {
        val url = "$instance/api/v1/users/me/videos?start=$start&count=$count&sort=-publishedAt"
        val response: VideoListResponse = json.decodeFromString(http.request(url, token = token).body)
        return response.data.map { it.toVideo(instance) }
    }

    // --- Subscriptions (following) -----------------------------------------------------------

    suspend fun subscribe(instance: String, token: String, channelHandle: String) {
        val body = buildJsonObject { put("uri", channelHandle) }.toString()
        http.json("$instance/api/v1/users/me/subscriptions", "POST", token, body)
    }

    suspend fun unsubscribe(instance: String, token: String, channelHandle: String) {
        http.request("$instance/api/v1/users/me/subscriptions/${channelHandle.urlEncoded()}", "DELETE", token)
    }

    suspend fun subscriptions(instance: String, token: String, start: Int, count: Int): List<Creator> {
        val url = "$instance/api/v1/users/me/subscriptions?start=$start&count=$count&sort=-createdAt"
        val response: SubscriptionsResponse = json.decodeFromString(http.request(url, token = token).body)
        return response.data.map { it.toCreator(instance) }
    }

    suspend fun subscriptionVideos(instance: String, token: String, start: Int, count: Int): List<Video> {
        val url = "$instance/api/v1/users/me/subscriptions/videos?start=$start&count=$count&sort=-publishedAt&skipCount=true"
        val response: VideoListResponse = json.decodeFromString(http.request(url, token = token).body)
        return response.data.map { it.toVideo(instance) }
    }

    // --- "Watch later" playlist (bookmarks) ----------------------------------------------------

    suspend fun watchLaterPlaylistId(instance: String, token: String, accountName: String): Long? {
        val url = "$instance/api/v1/accounts/${accountName.urlEncoded()}/video-playlists?playlistType=2&count=1"
        val response: PlaylistsResponse = json.decodeFromString(http.request(url, token = token).body)
        return response.data.firstOrNull()?.id
    }

    suspend fun playlistVideos(instance: String, token: String, playlistId: Long, start: Int, count: Int): List<Video> {
        val url = "$instance/api/v1/video-playlists/$playlistId/videos?start=$start&count=$count"
        val response: PlaylistElementsResponse = json.decodeFromString(http.request(url, token = token).body)
        return response.data.mapNotNull { it.video?.toVideo(instance) }
    }

    suspend fun addToPlaylist(instance: String, token: String, playlistId: Long, videoId: Long) {
        val body = buildJsonObject { put("videoId", videoId) }.toString()
        http.json("$instance/api/v1/video-playlists/$playlistId/videos", "POST", token, body)
    }

    /** Removes [videoId] from [playlistId], if it is in there. */
    suspend fun removeFromPlaylist(instance: String, token: String, playlistId: Long, videoId: Long) {
        val url = "$instance/api/v1/users/me/video-playlists/videos-exist?videoIds=$videoId"
        val existing: Map<String, List<PlaylistElementRef>> = json.decodeFromString(http.request(url, token = token).body)
        existing[videoId.toString()].orEmpty()
            .filter { it.playlistId == playlistId }
            .forEach { http.request("$instance/api/v1/video-playlists/$playlistId/videos/${it.playlistElementId}", "DELETE", token) }
    }

    // --- Comments -----------------------------------------------------------------------------

    suspend fun postComment(instance: String, token: String, videoId: Long, text: String): Comment? {
        val body = buildJsonObject { put("text", text) }.toString()
        val response = http.json("$instance/api/v1/videos/$videoId/comment-threads", "POST", token, body)
        return json.decodeFromString<CreatedComment>(response.body).comment?.toComment(instance)
    }

    // --- Resumable upload ---------------------------------------------------------------------

    /** Starts a resumable upload and returns the session URL to PUT the file to. */
    suspend fun startUpload(instance: String, token: String, metadata: UploadMetadata, size: Long, mimeType: String): String {
        val response = http.request(
            "$instance/api/v1/videos/upload-resumable",
            "POST",
            token,
            headers = mapOf("X-Upload-Content-Length" to size.toString(), "X-Upload-Content-Type" to mimeType),
            body = json.encodeToString(UploadMetadata.serializer(), metadata).toByteArray(),
            contentType = "application/json",
        )
        val location = response.header("Location") ?: throw IOException("Upload session missing")
        return when {
            location.startsWith("https://") -> location
            location.startsWith("//") -> "https:$location"
            else -> instance + location
        }
    }

    /**
     * Uploads [input] in chunks to [sessionUrl], reporting progress (0..1).
     * Returns the created video once the server has received the whole file.
     */
    suspend fun uploadChunks(
        sessionUrl: String,
        token: String,
        input: InputStream,
        size: Long,
        onProgress: (Float) -> Unit,
    ): UploadedVideoRef = withContext(Dispatchers.IO) {
        val buffer = ByteArray(CHUNK_SIZE)
        var offset = 0L
        while (offset < size) {
            ensureActive()
            val length = readFully(input, buffer, minOf(CHUNK_SIZE.toLong(), size - offset).toInt())
            if (length <= 0) throw IOException("Unexpected end of file")
            val connection = URL(sessionUrl).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "PUT"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 30_000
                connection.readTimeout = 120_000
                connection.doOutput = true
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                connection.setRequestProperty("Content-Range", "bytes $offset-${offset + length - 1}/$size")
                connection.setFixedLengthStreamingMode(length)
                connection.outputStream.use { it.write(buffer, 0, length) }
                val code = connection.responseCode
                offset += length
                onProgress(offset.toFloat() / size)
                when {
                    code == 308 -> Unit // chunk accepted, continue
                    code in 200..299 -> {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        return@withContext json.decodeFromString<UploadedVideo>(body).video
                            ?: throw IOException("Upload finished without video")
                    }
                    else -> {
                        val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        throw http.toException(HttpResponse(code, body, emptyMap()))
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Server did not confirm the upload")
    }

    suspend fun cancelUpload(sessionUrl: String, token: String) {
        runCatching { http.request(sessionUrl, "DELETE", token) }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, wanted: Int): Int {
        var read = 0
        while (read < wanted) {
            val n = input.read(buffer, read, wanted - read)
            if (n < 0) break
            read += n
        }
        return read
    }

    private companion object {
        const val CHUNK_SIZE = 8 * 1024 * 1024
    }
}
