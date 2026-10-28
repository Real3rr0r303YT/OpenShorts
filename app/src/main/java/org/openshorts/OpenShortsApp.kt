package org.openshorts

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.openshorts.data.AccountRepository
import org.openshorts.data.ChatRepository
import org.openshorts.data.LocalFiles
import org.openshorts.data.ProfileRepository
import org.openshorts.data.SettingsRepository
import org.openshorts.data.YouTubeInstanceType
import org.openshorts.data.peertube.Http
import org.openshorts.data.peertube.PeerTubeAccountApi
import org.openshorts.data.peertube.PeerTubeClient
import org.openshorts.data.youtube.YouTubeBackend
import org.openshorts.data.youtube.YouTubeClient

/**
 * Application entry point and minimal dependency container.
 *
 * OpenShorts deliberately ships without any analytics, crash reporting or
 * tracking SDKs. Nothing is initialised here except our own components.
 */
class OpenShortsApp : Application() {

    val userAgent: String by lazy { "OpenShorts/${BuildConfig.VERSION_NAME} (Android; +${BuildConfig.SOURCE_URL})" }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val profileRepository: ProfileRepository by lazy { ProfileRepository(this) }

    val localFiles: LocalFiles by lazy { LocalFiles(this) }

    val chatRepository: ChatRepository by lazy { ChatRepository(this) }

    val peerTubeClient: PeerTubeClient by lazy { PeerTubeClient(userAgent) }

    private val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }
    }

    private val http: Http by lazy { Http(userAgent, json) }

    /** PeerTube login, synced interactions and uploads. */
    val accountRepository: AccountRepository by lazy {
        AccountRepository(this, PeerTubeAccountApi(http, json, userAgent), profileRepository)
    }

    /** YouTube Shorts through public Piped/Invidious proxies. */
    val youTubeClient: YouTubeClient by lazy {
        YouTubeClient(http, json) {
            val settings = settingsRepository.settings.first()
            settings.youTubeCustomInstance?.let { url ->
                when (settings.youTubeCustomType) {
                    YouTubeInstanceType.PIPED -> YouTubeBackend.Piped(url)
                    YouTubeInstanceType.INVIDIOUS -> YouTubeBackend.Invidious(url)
                }
            }
        }
    }
}
