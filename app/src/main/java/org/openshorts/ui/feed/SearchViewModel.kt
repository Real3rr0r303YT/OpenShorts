package org.openshorts.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openshorts.OpenShortsApp
import org.openshorts.data.peertube.Creator

data class SearchUiState(
    val input: String = "",
    val creators: List<Creator> = emptyList(),
    val loadingCreators: Boolean = false,
)

/** Live creator suggestions while typing in the search bar. */
@OptIn(FlowPreview::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenShortsApp
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private val input = MutableStateFlow("")

    init {
        viewModelScope.launch {
            input.debounce(350).distinctUntilChanged().collect { text -> findCreators(text) }
        }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text) }
        input.value = text
    }

    private suspend fun findCreators(text: String) {
        val query = text.trim().removePrefix("@").removePrefix("#")
        if (query.length < 2) {
            _state.update { it.copy(creators = emptyList(), loadingCreators = false) }
            return
        }
        _state.update { it.copy(loadingCreators = true) }
        val instance = app.settingsRepository.settings.first().instanceUrl
        val creators = try {
            app.peerTubeClient.searchCreators(instance, query)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        _state.update { it.copy(creators = creators, loadingCreators = false) }
    }
}
