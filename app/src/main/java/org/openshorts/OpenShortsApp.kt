package org.openshorts

import android.app.Application
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

    val peerTubeClient: PeerTubeClient by lazy { PeerTubeClient(userAgent) }
}
