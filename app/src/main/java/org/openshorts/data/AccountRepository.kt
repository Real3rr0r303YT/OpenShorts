package org.openshorts.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openshorts.data.auth.AuthSession
import org.openshorts.data.auth.TokenStore
import org.openshorts.data.peertube.Comment
import org.openshorts.data.peertube.Creator
import org.openshorts.data.peertube.MeResponse
import org.openshorts.data.peertube.PeerTubeAccountApi
import org.openshorts.data.peertube.PeerTubeApiException
import org.openshorts.data.peertube.UploadMetadata
import org.openshorts.data.peertube.UploadedVideoRef
import org.openshorts.data.peertube.Video
import org.openshorts.data.peertube.YOUTUBE_UUID_PREFIX
import java.io.InputStream

/** The PeerTube account the user is logged in with. */
data class PeerTubeAccount(
    val instanceUrl: String,
    val username: String,
    val accountName: String,
    val displayName: String,
    val description: String,
    val avatarUrl: String?,
    val followersCount: Long,
    val followingCount: Long,
    val channels: List<OwnChannel>,
) {
    val handle: String get() = "$username@${instanceUrl.removePrefix("https://")}"
}

data class OwnChannel(val id: Long, val name: String, val displayName: String)

sealed interface LoginResult {
    data object Success : LoginResult

    /** The account has 2FA enabled: ask for the code and log in again with it. */
    data object NeedsTwoFactor : LoginResult

    data class Error(val message: String?, val invalidCredentials: Boolean = false) : LoginResult
}

data class SyncResult(val pulled: Int, val pushed: Int, val failed: Int)

/**
 * PeerTube login (OAuth2 password grant) and every action that talks to the user's
 * own server: ratings, subscriptions, the "watch later" playlist, comments and uploads.
 * Access tokens are refreshed automatically.
 */
class AccountRepository(
    context: Context,
    private val api: PeerTubeAccountApi,
    private val profiles: ProfileRepository,
) {
    private val tokenStore = TokenStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    private val _session = MutableStateFlow<AuthSession?>(null)
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    private val _account = MutableStateFlow<PeerTubeAccount?>(null)
    val account: StateFlow<PeerTubeAccount?> = _account.asStateFlow()

    /** Set when the server rejected the stored session (e.g. revoked); the user must log in again. */
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    /** Local copies of remote videos on the user's instance, by UUID. */
    private val resolved = HashMap<String, Video>()
    private var watchLaterId: Long? = null

    val isLoggedIn: Boolean get() = _session.value != null

    init {
        scope.launch {
            _session.value = runCatching { tokenStore.load() }.getOrNull()
            if (_session.value != null) runCatching { refreshAccount() }
        }
    }

    suspend fun login(instanceInput: String, username: String, password: String, otp: String?): LoginResult {
        val instance = normalizeInstanceUrl(instanceInput) ?: return LoginResult.Error(null)
        return try {
            val client = api.oauthClient(instance)
            val token = api.login(instance, client, username.trim(), password, otp)
            val session = AuthSession(
                instanceUrl = instance,
                username = username.trim(),
                clientId = client.clientId,
                clientSecret = client.clientSecret,
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                expiresAtMillis = System.currentTimeMillis() + token.expiresIn * 1000,
            )
            tokenStore.save(session)
            resolved.clear()
            watchLaterId = null
            _session.value = session
            _sessionExpired.value = false
            refreshAccount()
            LoginResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: PeerTubeApiException) {
            when (e.errorCode) {
                "missing_two_factor" -> LoginResult.NeedsTwoFactor
                "invalid_grant", "invalid_client" -> LoginResult.Error(e.detail, invalidCredentials = true)
                else -> LoginResult.Error(e.detail ?: e.message)
            }
        } catch (e: Exception) {
            LoginResult.Error(e.message)
        }
    }

    suspend fun logout() {
        val session = _session.value
        clearSession()
        if (session != null) runCatching { api.revoke(session.instanceUrl, session.accessToken) }
    }

    private fun clearSession() {
        runCatching { tokenStore.clear() }
        _session.value = null
        _account.value = null
        resolved.clear()
        watchLaterId = null
    }

    suspend fun refreshAccount() {
        val me: MeResponse = authorized { s, token -> api.me(s.instanceUrl, token) }
        val instance = _session.value?.instanceUrl ?: return
        val account = me.account
        val avatarPath = account?.avatars?.filter { it.path != null && it.width >= 96 }?.minByOrNull { it.width }?.path
            ?: account?.avatars?.firstOrNull { it.path != null }?.path
            ?: account?.avatar?.path
        _account.value = PeerTubeAccount(
            instanceUrl = instance,
            username = me.username,
            accountName = account?.name ?: me.username,
            displayName = account?.displayName?.ifBlank { null } ?: me.username,
            description = account?.description.orEmpty(),
            avatarUrl = avatarPath?.let { "$instance$it" },
            followersCount = account?.followersCount ?: 0,
            followingCount = account?.followingCount ?: 0,
            channels = me.videoChannels.map { OwnChannel(it.id, it.name, it.displayName.ifBlank { it.name }) },
        )
    }

    // --- Interactions ---------------------------------------------------------------------------

    suspend fun setLiked(video: Video, liked: Boolean) {
        val local = resolve(video)
        authorized { s, token -> api.rate(s.instanceUrl, token, local.id, liked) }
    }

    suspend fun setFollowing(creator: Creator, follow: Boolean) {
        authorized { s, token ->
            if (follow) api.subscribe(s.instanceUrl, token, creator.handle) else api.unsubscribe(s.instanceUrl, token, creator.handle)
        }
    }

    suspend fun setSaved(video: Video, saved: Boolean) {
        val local = resolve(video)
        val playlist = watchLaterPlaylist() ?: return
        authorized { s, token ->
            if (saved) {
                api.addToPlaylist(s.instanceUrl, token, playlist, local.id)
            } else {
                api.removeFromPlaylist(s.instanceUrl, token, playlist, local.id)
            }
        }
    }

    suspend fun postComment(video: Video, text: String): Comment? {
        val local = resolve(video)
        return authorized { s, token -> api.postComment(s.instanceUrl, token, local.id, text.trim()) }
    }

    suspend fun subscriptionVideos(start: Int, count: Int): List<Video> =
        authorized { s, token -> api.subscriptionVideos(s.instanceUrl, token, start, count) }

    suspend fun myVideos(start: Int = 0, count: Int = 50): List<Video> =
        authorized { s, token -> api.myVideos(s.instanceUrl, token, start, count) }

    // --- Upload ---------------------------------------------------------------------------------

    suspend fun startUpload(metadata: UploadMetadata, size: Long, mimeType: String): String =
        authorized { s, token -> api.startUpload(s.instanceUrl, token, metadata, size, mimeType) }

    suspend fun uploadChunks(sessionUrl: String, input: InputStream, size: Long, onProgress: (Float) -> Unit): UploadedVideoRef {
        val token = validToken()
        return api.uploadChunks(sessionUrl, token, input, size, onProgress)
    }

    suspend fun cancelUpload(sessionUrl: String) {
        runCatching { api.cancelUpload(sessionUrl, validToken()) }
    }

    // --- Sync -----------------------------------------------------------------------------------

    /**
     * Two-way sync between the local profile and the server account:
     * pulls likes, "watch later" and subscriptions, then pushes local-only ones (max [PUSH_LIMIT] each).
     */
    suspend fun sync(): SyncResult {
        val account = _account.value ?: run { refreshAccount(); _account.value } ?: return SyncResult(0, 0, 0)
        var pulled = 0
        var pushed = 0
        var failed = 0

        val serverLikes = authorized { s, token -> api.likedVideos(s.instanceUrl, token, account.accountName, 0, 100) }
        val serverSaved = watchLaterPlaylist()?.let { id ->
            authorized { s, token -> api.playlistVideos(s.instanceUrl, token, id, 0, 100) }
        }.orEmpty()
        val serverFollowing = authorized { s, token -> api.subscriptions(s.instanceUrl, token, 0, 100) }

        pulled += profiles.mergeLikes(serverLikes)
        pulled += profiles.mergeSaved(serverSaved)
        pulled += profiles.mergeFollowing(serverFollowing)

        suspend fun push(block: suspend () -> Unit) {
            try {
                block()
                pushed++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed++
            }
        }

        val likedUuids = serverLikes.mapTo(HashSet()) { it.uuid }
        profiles.likedVideos.first().filter { !it.isYouTube && it.uuid !in likedUuids }.take(PUSH_LIMIT).forEach { push { setLiked(it, true) } }
        val savedUuids = serverSaved.mapTo(HashSet()) { it.uuid }
        profiles.savedVideos.first().filter { !it.isYouTube && it.uuid !in savedUuids }.take(PUSH_LIMIT).forEach { push { setSaved(it, true) } }
        val followed = serverFollowing.mapTo(HashSet()) { it.handle }
        profiles.following.first().filter { !it.handle.startsWith(YOUTUBE_UUID_PREFIX) && it.handle !in followed }.take(PUSH_LIMIT).forEach { push { setFollowing(it, true) } }

        runCatching { refreshAccount() }
        return SyncResult(pulled, pushed, failed)
    }

    // --- Internals ------------------------------------------------------------------------------

    /** Returns the user's instance copy of [video] (needed for its numeric id). */
    private suspend fun resolve(video: Video): Video {
        val session = _session.value ?: throw NotLoggedInException()
        if (video.instanceUrl == session.instanceUrl && video.id > 0) return video
        resolved[video.uuid]?.let { return it }
        val origin = video.instanceUrl.ifEmpty { video.watchUrl.substringBefore("/w/") }
        val canonicalUrl = "$origin/videos/watch/${video.uuid}"
        val local = authorized { s, token -> api.resolveVideo(s.instanceUrl, token, canonicalUrl) }
            ?: throw PeerTubeApiException(404, "video_not_found", "Video could not be found on your server")
        resolved[video.uuid] = local
        return local
    }

    private suspend fun watchLaterPlaylist(): Long? {
        watchLaterId?.let { return it }
        val account = _account.value ?: return null
        return authorized { s, token -> api.watchLaterPlaylistId(s.instanceUrl, token, account.accountName) }
            .also { watchLaterId = it }
    }

    /** Runs [block] with a valid access token, refreshing it once if the server rejects it. */
    private suspend fun <T> authorized(block: suspend (AuthSession, String) -> T): T {
        val token = validToken()
        val session = _session.value ?: throw NotLoggedInException()
        return try {
            block(session, token)
        } catch (e: PeerTubeApiException) {
            if (e.status != 401) throw e
            val refreshed = refresh(force = true)
            block(refreshed, refreshed.accessToken)
        }
    }

    private suspend fun validToken(): String {
        val session = _session.value ?: throw NotLoggedInException()
        return if (System.currentTimeMillis() < session.expiresAtMillis - 60_000) {
            session.accessToken
        } else {
            refresh(force = false).accessToken
        }
    }

    private suspend fun refresh(force: Boolean): AuthSession = refreshMutex.withLock {
        val current = _session.value ?: throw NotLoggedInException()
        if (!force && System.currentTimeMillis() < current.expiresAtMillis - 60_000) return current
        try {
            val token = api.refresh(current.instanceUrl, current.clientId, current.clientSecret, current.refreshToken)
            val updated = current.copy(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                expiresAtMillis = System.currentTimeMillis() + token.expiresIn * 1000,
            )
            tokenStore.save(updated)
            _session.value = updated
            updated
        } catch (e: PeerTubeApiException) {
            if (e.status in 400..401) {
                // Refresh token expired or revoked.
                clearSession()
                _sessionExpired.value = true
            }
            throw e
        }
    }

    fun acknowledgeSessionExpired() {
        _sessionExpired.value = false
    }

    private companion object {
        const val PUSH_LIMIT = 30
    }
}

class NotLoggedInException : IllegalStateException("Not logged in")
