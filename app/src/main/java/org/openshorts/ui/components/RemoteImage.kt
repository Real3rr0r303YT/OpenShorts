package org.openshorts.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tiny in-memory image loader for thumbnails, avatars and local profile images.
 * Keeps the dependency list short and auditable; remote images are never written to disk.
 */
private object ImageCache {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(source: String): Bitmap? {
        cache.get(source)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                if (source.startsWith("https://")) download(source) else BitmapFactory.decodeFile(source)
            }.getOrNull()
        }?.also { cache.put(source, it) }
    }

    private fun download(url: String): Bitmap? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            connection.disconnect()
        }
    }
}

/** Shows an image from an `https://` URL or a local file path; nothing while loading. */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = url?.let { ImageCache.load(it) }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

/** Round avatar with the first letter of [name] as fallback. */
@Composable
fun Avatar(
    source: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().removePrefix("@").firstOrNull()?.uppercase() ?: "?",
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
        RemoteImage(
            url = source,
            contentDescription = null,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Crop,
        )
    }
}
