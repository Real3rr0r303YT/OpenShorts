package org.openshorts.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Imports user-picked files (profile images, fonts) into app-private storage.
 * Only the copies are kept; the app never needs storage permissions.
 */
class LocalFiles(context: Context) {

    private val appContext = context.applicationContext

    /** Decodes, downsizes and stores an image as JPEG. Returns the file path. */
    suspend fun importImage(uri: Uri, name: String, maxSize: Int): String = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image")

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("Could not decode image")

        val dir = File(appContext.filesDir, "profile").apply { mkdirs() }
        // A new file name per import so image caches never show a stale picture.
        val file = File(dir, "${name}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        file.absolutePath
    }

    /** Copies a .ttf/.otf font, validates it and returns (path, display name). */
    suspend fun importFont(uri: Uri): Pair<String, String> = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: "Custom font"
        val extension = displayName.substringAfterLast('.', "ttf").lowercase()
        if (extension !in setOf("ttf", "otf")) throw IOException("Unsupported font type: $extension")

        val dir = File(appContext.filesDir, "fonts").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "imported_${System.currentTimeMillis()}.$extension")
        resolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            ?: throw IOException("Could not read font")

        val valid = runCatching { Typeface.Builder(file).build() }.getOrNull() != null
        if (!valid || file.length() == 0L) {
            file.delete()
            throw IOException("Invalid font file")
        }
        file.absolutePath to displayName.substringBeforeLast('.')
    }
}
