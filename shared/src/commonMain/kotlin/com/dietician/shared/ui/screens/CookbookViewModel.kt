package com.dietician.shared.ui.screens

import com.dietician.shared.ui.data.Recipe
import com.dietician.shared.ui.data.RecipeIngestClient
import com.dietician.shared.ui.data.RecipeIngestResult
import com.dietician.shared.ui.data.RecipeReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CookbookScreen state holder.
 *
 * Browse: pulls full recipes corpus via [RecipeReader.all] (Plan-1 V010). Local
 * fuzzy filter is a simple `title.contains(query, ignoreCase = true)` — full
 * trigram / tokenized search lives in Plan-7. First-ship corpus is empty;
 * empty-state CTA points to ingest.
 *
 * Ingest: [submitIngest] POSTs to Plan-3 `/jobs/queue`. Three outcomes:
 *   - 200/202 → "Queued for processing" toast
 *   - 501 (pipeline 501-stub) → "Queued — will appear when ingest pipeline
 *     ships" + local-outbox semantics (the URL stays in [IngestState.url] so
 *     the user can retry; Batch E will wire a sqlite outbox table)
 *   - Network/4xx/5xx → "Ingest failed: <reason>"
 *
 * URL validation: accepts http:// or https:// prefix only. Anything else is
 * rejected before the network call to avoid burning a /jobs/queue POST on
 * obvious typos.
 */
class CookbookViewModel(
    private val reader: RecipeReader,
    private val ingest: RecipeIngestClient,
) {
    private val _state = MutableStateFlow(CookbookState())
    val state: StateFlow<CookbookState> = _state.asStateFlow()

    suspend fun load() {
        val recipes = reader.all()
        _state.value = _state.value.copy(
            recipes = recipes,
            filtered = recipes,
            loaded = true,
        )
    }

    fun onQueryChange(q: String) {
        val filtered = if (q.isBlank()) {
            _state.value.recipes
        } else {
            _state.value.recipes.filter { it.title.contains(q, ignoreCase = true) }
        }
        _state.value = _state.value.copy(query = q, filtered = filtered)
    }

    fun showIngestDialog() {
        _state.value = _state.value.copy(ingestDialogVisible = true)
    }

    fun hideIngestDialog() {
        _state.value = _state.value.copy(ingestDialogVisible = false)
    }

    fun onIngestUrlChange(url: String) {
        _state.value = _state.value.copy(ingestUrl = url)
    }

    suspend fun submitIngest() {
        val url = _state.value.ingestUrl
        if (!isValidUrl(url)) {
            _state.value = _state.value.copy(toast = "Invalid URL — must start with http:// or https://")
            return
        }
        val result = ingest.ingest(url)
        val toastMsg = when (result) {
            RecipeIngestResult.Queued -> "Queued for processing"
            RecipeIngestResult.NotImplemented ->
                "Queued — will appear when ingest pipeline ships"
            is RecipeIngestResult.Failed -> "Ingest failed: ${result.reason}"
        }
        _state.value = _state.value.copy(toast = toastMsg, ingestDialogVisible = false)
    }

    fun clearToast() {
        _state.value = _state.value.copy(toast = null)
    }

    private fun isValidUrl(s: String): Boolean =
        s.startsWith("http://") || s.startsWith("https://")
}

data class CookbookState(
    val query: String = "",
    val recipes: List<Recipe> = emptyList(),
    val filtered: List<Recipe> = emptyList(),
    val ingestDialogVisible: Boolean = false,
    val ingestUrl: String = "",
    val toast: String? = null,
    val loaded: Boolean = false,
)
