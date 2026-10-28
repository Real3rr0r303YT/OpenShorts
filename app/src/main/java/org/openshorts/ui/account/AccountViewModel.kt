package org.openshorts.ui.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openshorts.OpenShortsApp
import org.openshorts.data.LoginResult
import org.openshorts.data.PeerTubeAccount
import org.openshorts.data.SyncResult

data class LoginUiState(
    val loading: Boolean = false,
    val needsTwoFactor: Boolean = false,
    val invalidCredentials: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

sealed interface SyncState {
    data object Idle : SyncState
    data object Running : SyncState
    data class Done(val result: SyncResult) : SyncState
    data class Failed(val message: String?) : SyncState
}

/** PeerTube login, logout and sync, shared by the login screen, profile and settings. */
class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val accounts = (application as OpenShortsApp).accountRepository

    val account: StateFlow<PeerTubeAccount?> = accounts.account
    val sessionExpired: StateFlow<Boolean> = accounts.sessionExpired

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    private val _sync = MutableStateFlow<SyncState>(SyncState.Idle)
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    fun login(instance: String, username: String, password: String, otp: String?) {
        if (_login.value.loading) return
        _login.update { it.copy(loading = true, error = null, invalidCredentials = false) }
        viewModelScope.launch {
            when (val result = accounts.login(instance, username, password, otp)) {
                LoginResult.Success -> {
                    _login.value = LoginUiState(success = true)
                    sync()
                }
                LoginResult.NeedsTwoFactor -> _login.update { it.copy(loading = false, needsTwoFactor = true) }
                is LoginResult.Error -> _login.update {
                    it.copy(loading = false, error = result.message, invalidCredentials = result.invalidCredentials)
                }
            }
        }
    }

    /** Resets the form state when the login screen is opened again. */
    fun resetLoginForm() {
        _login.value = LoginUiState()
    }

    fun logout() {
        viewModelScope.launch {
            accounts.logout()
            _sync.value = SyncState.Idle
        }
    }

    fun sync() {
        if (_sync.value == SyncState.Running) return
        _sync.value = SyncState.Running
        viewModelScope.launch {
            _sync.value = try {
                SyncState.Done(accounts.sync())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SyncState.Failed(e.message)
            }
        }
    }

    fun acknowledgeSessionExpired() = accounts.acknowledgeSessionExpired()
}
