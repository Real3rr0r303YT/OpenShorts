package org.openshorts.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.openshorts.data.peertube.Creator
import org.openshorts.data.peertube.Video
import java.io.File
import java.io.IOException

private val Context.profileStore: DataStore<Preferences> by preferencesDataStore(name = "profile")

/**
 * The local account: profile data, liked and saved videos and followed creators.
 * Everything is stored only on this device.
 */
class ProfileRepository(context: Context) {

    private val dataStore = context.applicationContext.profileStore
    private val json = Json { ignoreUnknownKeys = true }
    private val videoList = ListSerializer(Video.serializer())
    private val creatorList = ListSerializer(Creator.serializer())

    private val prefs: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val profile: Flow<UserProfile> = prefs.map { p ->
        UserProfile(
            displayName = p[DISPLAY_NAME].orEmpty(),
            username = p[USERNAME].orEmpty(),
            bio = p[BIO].orEmpty(),
            avatarPath = p[AVATAR_PATH]?.takeIf { File(it).exists() },
            bannerPath = p[BANNER_PATH]?.takeIf { File(it).exists() },
            bannerHue = p[BANNER_HUE] ?: UserProfile().bannerHue,
            supportUrl = p[SUPPORT_URL].orEmpty(),
            showSupporterBadge = p[SUPPORTER_BADGE] ?: false,
            createdAtMillis = p[CREATED_AT] ?: 0L,
        )
    }

    /** Newest first. */
    val likedVideos: Flow<List<Video>> = prefs.map { decode(it[LIKED_VIDEOS], videoList) }

    /** Bookmarked videos, newest first. */
    val savedVideos: Flow<List<Video>> = prefs.map { decode(it[SAVED_VIDEOS], videoList) }

    /** Followed creators, most recent first. */
    val following: Flow<List<Creator>> = prefs.map { decode(it[FOLLOWING], creatorList) }

    suspend fun saveProfile(profile: UserProfile) {
        dataStore.edit { p ->
            p[DISPLAY_NAME] = profile.displayName.trim().take(UserProfile.MAX_NAME_LENGTH)
            p[USERNAME] = sanitizeUsername(profile.username)
            p[BIO] = profile.bio.trim().take(UserProfile.MAX_BIO_LENGTH)
            p[BANNER_HUE] = profile.bannerHue
            p[SUPPORT_URL] = profile.supportUrl
            p[SUPPORTER_BADGE] = profile.showSupporterBadge
            if (p[CREATED_AT] == null) p[CREATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun setAvatarPath(path: String?) = setPath(AVATAR_PATH, path)

    suspend fun setBannerPath(path: String?) = setPath(BANNER_PATH, path)

    private suspend fun setPath(key: Preferences.Key<String>, path: String?) {
        dataStore.edit { p ->
            p[key]?.takeIf { it != path }?.let { File(it).delete() }
            if (path == null) p.remove(key) else p[key] = path
        }
    }

    suspend fun toggleLike(video: Video) = toggle(LIKED_VIDEOS, videoList, video) { it.uuid }

    suspend fun toggleSaved(video: Video) = toggle(SAVED_VIDEOS, videoList, video) { it.uuid }

    suspend fun toggleFollow(creator: Creator) = toggle(FOLLOWING, creatorList, creator) { it.handle }

    suspend fun clearLikes() {
        dataStore.edit { it.remove(LIKED_VIDEOS) }
    }

    /** Adds [item] to the front of the list or removes it if present. */
    private suspend fun <T> toggle(
        key: Preferences.Key<String>,
        serializer: KSerializer<List<T>>,
        item: T,
        id: (T) -> String,
    ) {
        dataStore.edit { p: MutablePreferences ->
            val list = decode(p[key], serializer)
            val updated = if (list.any { id(it) == id(item) }) {
                list.filterNot { id(it) == id(item) }
            } else {
                listOf(item) + list.take(MAX_ENTRIES - 1)
            }
            p[key] = json.encodeToString(serializer, updated)
        }
    }

    private fun <T> decode(raw: String?, serializer: KSerializer<List<T>>): List<T> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private companion object {
        const val MAX_ENTRIES = 500

        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val USERNAME = stringPreferencesKey("username")
        val BIO = stringPreferencesKey("bio")
        val AVATAR_PATH = stringPreferencesKey("avatar_path")
        val BANNER_PATH = stringPreferencesKey("banner_path")
        val BANNER_HUE = floatPreferencesKey("banner_hue")
        val SUPPORT_URL = stringPreferencesKey("support_url")
        val SUPPORTER_BADGE = booleanPreferencesKey("supporter_badge")
        val CREATED_AT = longPreferencesKey("created_at")
        val LIKED_VIDEOS = stringPreferencesKey("liked_videos")
        val SAVED_VIDEOS = stringPreferencesKey("saved_videos")
        val FOLLOWING = stringPreferencesKey("following")
    }
}
