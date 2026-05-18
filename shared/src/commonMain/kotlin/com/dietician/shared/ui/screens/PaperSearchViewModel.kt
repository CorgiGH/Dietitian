package com.dietician.shared.ui.screens

import com.dietician.shared.ui.data.PaperResult
import com.dietician.shared.ui.data.PaperSearchOutcome
import com.dietician.shared.ui.data.PaperSearchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * PaperSearch screen state holder.
 *
 * 4 corpus filters (all selected by default): papers, supplements, recipes, foods.
 * Search submits via [PaperSearchRepository.search] which POSTs to Plan-3 `/embed`;
 * outcomes are mapped to UI state:
 *   - [PaperSearchOutcome.Hits] → results populated
 *   - [PaperSearchOutcome.Empty] → empty-state "No matches"
 *   - [PaperSearchOutcome.NotImplemented] → "Search coming soon" banner
 *   - [PaperSearchOutcome.Failed] → error toast (one-off; Snackbar host)
 */
class PaperSearchViewModel(
    private val repo: PaperSearchRepository,
    private val coroutineScope: CoroutineScope = MainScope(),
) {
    private val _state = MutableStateFlow(
        PaperSearchState(corpora = setOf("papers", "supplements", "recipes", "foods")),
    )
    val state: StateFlow<PaperSearchState> = _state.asStateFlow()

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun toggleCorpus(corpus: String) {
        val cur = _state.value.corpora
        val next = if (corpus in cur) cur - corpus else cur + corpus
        _state.value = _state.value.copy(corpora = next)
    }

    fun submitQuery() {
        val q = _state.value.query
        if (q.isBlank()) return
        coroutineScope.launch {
            when (val out = repo.search(q, _state.value.corpora)) {
                is PaperSearchOutcome.Hits -> _state.value = _state.value.copy(
                    results = out.results,
                    searched = true,
                    embedNotImplemented = false,
                    errorToast = null,
                )
                PaperSearchOutcome.Empty -> _state.value = _state.value.copy(
                    results = emptyList(),
                    searched = true,
                    embedNotImplemented = false,
                    errorToast = null,
                )
                PaperSearchOutcome.NotImplemented -> _state.value = _state.value.copy(
                    results = emptyList(),
                    searched = true,
                    embedNotImplemented = true,
                    errorToast = null,
                )
                is PaperSearchOutcome.Failed -> _state.value = _state.value.copy(
                    results = emptyList(),
                    searched = true,
                    embedNotImplemented = false,
                    errorToast = "Search failed: ${out.reason}",
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorToast = null)
    }
}

data class PaperSearchState(
    val query: String = "",
    val corpora: Set<String> = emptySet(),
    val results: List<PaperResult> = emptyList(),
    val searched: Boolean = false,
    val embedNotImplemented: Boolean = false,
    val errorToast: String? = null,
)
