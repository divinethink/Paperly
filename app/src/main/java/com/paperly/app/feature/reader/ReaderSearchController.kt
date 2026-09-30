package com.paperly.app.feature.reader

import com.paperly.app.domain.reader.ReaderEngine
import com.paperly.app.domain.reader.TextReadability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [id] changes on every jump so the UI scrolls again even when the target page is unchanged. */
data class SearchJump(val page: Int, val id: Int)

data class SearchUiState(
    val available: Boolean = false, // text layer exists AND is readable (P2-E heuristic)
    val partialMatch: Boolean = false, // Unicode Bengali: extraction can be lossy
    val open: Boolean = false,
    val query: String = "",
    val searching: Boolean = false,
    val failed: Boolean = false,
    val submitted: Boolean = false,
    val matches: List<Int> = emptyList(),
    val index: Int = 0,
    val jump: SearchJump? = null,
)

/** In-document search for one open [ReaderEngine]. Page-level results (no highlight overlay yet). */
class ReaderSearchController(
    private val scope: CoroutineScope,
    private val engine: () -> ReaderEngine?,
    private val pageCount: () -> Int,
    private val currentPage: () -> Int,
) {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var job: Job? = null

    /** Samples the first pages off the critical path; search is offered only for a readable text layer. */
    fun probe() {
        scope.launch {
            val pdf = engine() ?: return@launch
            val count = minOf(pageCount(), SAMPLE_PAGES)
            val sample = (0 until count).mapNotNull { pdf.pageText(it) }.joinToString("\n")
            val readable = TextReadability.isReadable(sample)
            val bengali = TextReadability.hasBengali(sample)
            _state.update { it.copy(available = readable, partialMatch = bengali) }
        }
    }

    fun toggle() {
        job?.cancel()
        _state.update {
            if (it.open) {
                SearchUiState(available = it.available, partialMatch = it.partialMatch)
            } else {
                it.copy(open = true)
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, submitted = false, failed = false) }
    }

    fun submit() {
        val query = _state.value.query.trim()
        val pdf = engine()
        if (query.isEmpty() || pdf == null) return
        job?.cancel()
        _state.update {
            it.copy(searching = true, failed = false, submitted = false, matches = emptyList(), jump = null)
        }
        job = scope.launch {
            val pages = pdf.searchPages(query) // engine maps failures to null; cancellation propagates
            _state.update { st ->
                if (pages == null) {
                    st.copy(searching = false, failed = true)
                } else {
                    val start = pages.indexOfFirst { it >= currentPage() }.takeIf { it >= 0 } ?: 0
                    st.copy(searching = false, submitted = true, matches = pages, index = start).jumpTo(start)
                }
            }
        }
    }

    fun next() = step(+1)

    fun previous() = step(-1)

    private fun step(delta: Int) {
        _state.update { st ->
            if (st.matches.isEmpty()) st else st.jumpTo(Math.floorMod(st.index + delta, st.matches.size))
        }
    }

    private fun SearchUiState.jumpTo(target: Int): SearchUiState {
        val page = matches.getOrNull(target) ?: return this
        return copy(index = target, jump = SearchJump(page, (jump?.id ?: 0) + 1))
    }

    private companion object {
        const val SAMPLE_PAGES = 5
    }
}
