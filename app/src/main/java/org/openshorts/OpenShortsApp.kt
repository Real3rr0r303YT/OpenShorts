package org.openshorts

import android.app.Application
import org.openshorts.data.ChatRepository
import org.openshorts.data.LocalFiles
import org.openshorts.data.ProfileRepository
import org.openshorts.data.SettingsRepository
import org.openshorts.data.peertube.PeerTubeClient

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
}
