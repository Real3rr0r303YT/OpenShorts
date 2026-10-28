package org.openshorts.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/** A logged-in PeerTube session. */
data class AuthSession(
    val instanceUrl: String,
    val username: String,
    val clientId: String,
    val clientSecret: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
)

/**
 * Stores OAuth tokens in [EncryptedSharedPreferences] (AES-256, key kept in the Android Keystore).
 * The password itself is never stored. The file is excluded from backups.
 */
class TokenStore(context: Context) {

    private val appContext = context.applicationContext

    // Created lazily (off the main thread by the callers): Keystore access can be slow or fail.
    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            FILE_NAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): AuthSession? {
        val instance = prefs.getString(INSTANCE, null) ?: return null
        return AuthSession(
            instanceUrl = instance,
            username = prefs.getString(USERNAME, null).orEmpty(),
            clientId = prefs.getString(CLIENT_ID, null) ?: return null,
            clientSecret = prefs.getString(CLIENT_SECRET, null) ?: return null,
            accessToken = prefs.getString(ACCESS_TOKEN, null) ?: return null,
            refreshToken = prefs.getString(REFRESH_TOKEN, null) ?: return null,
            expiresAtMillis = prefs.getLong(EXPIRES_AT, 0L),
        )
    }

    fun save(session: AuthSession) {
        prefs.edit {
            putString(INSTANCE, session.instanceUrl)
            putString(USERNAME, session.username)
            putString(CLIENT_ID, session.clientId)
            putString(CLIENT_SECRET, session.clientSecret)
            putString(ACCESS_TOKEN, session.accessToken)
            putString(REFRESH_TOKEN, session.refreshToken)
            putLong(EXPIRES_AT, session.expiresAtMillis)
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private companion object {
        const val FILE_NAME = "peertube_session"
        const val INSTANCE = "instance"
        const val USERNAME = "username"
        const val CLIENT_ID = "client_id"
        const val CLIENT_SECRET = "client_secret"
        const val ACCESS_TOKEN = "access_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val EXPIRES_AT = "expires_at"
    }
}
