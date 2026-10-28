package org.openshorts.data.peertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpResponse(val code: Int, val body: String, val headers: Map<String, List<String>>) {
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
}

/**
 * Error returned by a PeerTube server. [errorCode] is PeerTube's machine-readable
 * `code` (e.g. `invalid_grant`, `missing_two_factor`), [detail] its human-readable message.
 */
class PeerTubeApiException(
    val status: Int,
    val errorCode: String?,
    val detail: String?,
) : IOException(detail ?: "HTTP $status")

/** Small HTTPS helper shared by the authenticated PeerTube calls. */
class Http(private val userAgent: String, private val json: Json) {

    suspend fun request(
        url: String,
        method: String = "GET",
        token: String? = null,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        contentType: String? = null,
        timeoutMs: Int = 20_000,
    ): HttpResponse = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) throw IOException("Only HTTPS is allowed")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (body != null) {
                connection.doOutput = true
                contentType?.let { connection.setRequestProperty("Content-Type", it) }
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val headerMap = connection.headerFields.entries
                .mapNotNull { (key, values) -> key?.let { it to values.orEmpty() } }
                .toMap()
            val response = HttpResponse(code, text, headerMap)
            if (code >= 400) throw toException(response)
            response
        } finally {
            connection.disconnect()
        }
    }

    suspend fun json(
        url: String,
        method: String,
        token: String?,
        body: String,
    ): HttpResponse = request(url, method, token, body = body.toByteArray(), contentType = "application/json")

    suspend fun form(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): HttpResponse {
        val body = fields.entries.joinToString("&") { (k, v) -> "${k.urlEncoded()}=${v.urlEncoded()}" }
        return request(
            url,
            "POST",
            headers = headers,
            body = body.toByteArray(),
            contentType = "application/x-www-form-urlencoded",
        )
    }

    fun toException(response: HttpResponse): PeerTubeApiException {
        val obj = runCatching { json.parseToJsonElement(response.body) as? JsonObject }.getOrNull()
        fun field(name: String) = obj?.get(name)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        return PeerTubeApiException(
            status = response.code,
            errorCode = field("code"),
            detail = field("detail") ?: field("error") ?: field("title"),
        )
    }
}

internal fun String.urlEncoded(): String = java.net.URLEncoder.encode(this, "UTF-8")
