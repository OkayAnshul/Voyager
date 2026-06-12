package com.cosmiclaboratory.voyager.presentation.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: SearchResults? = null,
    val filters: SearchFilters = SearchFilters(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false
)

sealed interface SearchIntent {
    data class UpdateQuery(val query: String) : SearchIntent
    data class ToggleCategoryFilter(val category: PlaceCategory) : SearchIntent
    data class ToggleTransportFilter(val mode: SegmentType) : SearchIntent
    data class SetDateRange(val range: DateRange?) : SearchIntent
    data object ClearFilters : SearchIntent
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _filters = MutableStateFlow(SearchFilters())

    val immediateQuery: StateFlow<String> = _query.asStateFlow()

    val uiState: StateFlow<SearchUiState> = combine(_query, _filters) { query, filters ->
        Pair(query, filters)
    }.debounce(300)
        .flatMapLatest { (query, filters) ->
            if (query.length < 2) {
                flowOf(SearchUiState(query = query, filters = filters))
            } else {
                searchRepository.search(query, filters).map { results ->
                    SearchUiState(
                        query = query,
                        results = results,
                        filters = filters,
                        isSearching = false,
                        hasSearched = true
                    )
                }.onStart {
                    emit(SearchUiState(query = query, filters = filters, isSearching = true))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState())

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.UpdateQuery -> _query.value = intent.query
            is SearchIntent.ToggleCategoryFilter ->
                _filters.value = _filters.value.copy(
                    placeCategories = toggleFilter(_filters.value.placeCategories, intent.category)
                )
            is SearchIntent.ToggleTransportFilter ->
                _filters.value = _filters.value.copy(
                    transportModes = toggleFilter(_filters.value.transportModes, intent.mode)
                )
            is SearchIntent.SetDateRange -> {
                _filters.value = _filters.value.copy(dateRange = intent.range)
            }
            is SearchIntent.ClearFilters -> _filters.value = SearchFilters()
        }
    }

    companion object {
        /**
         * Toggle [item] in a nullable filter set: add if absent, remove if present.
         * Returns null when the set becomes empty — the repository treats null as
         * "no filter on this dimension". Exposed for testing.
         */
        internal fun <T> toggleFilter(current: Set<T>?, item: T): Set<T>? {
            val next = current?.toMutableSet() ?: mutableSetOf()
            if (!next.add(item)) next.remove(item) // add() returns false when already present
            return next.ifEmpty { null }
        }
    }
}
