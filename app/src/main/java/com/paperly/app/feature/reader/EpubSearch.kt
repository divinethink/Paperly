package com.paperly.app.feature.reader

import kotlin.coroutines.cancellation.CancellationException
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.isSearchable
import org.readium.r2.shared.publication.services.search.search

private const val MAX_HITS = 100
private const val SNIPPET_CHARS = 30

/** One search result: where to jump ([locator]) and a short before/match/after snippet. */
data class EpubSearchHit(val locator: Locator, val before: String, val match: String, val after: String)

data class EpubSearchState(
    val query: String = "",
    val searching: Boolean = false,
    val hits: List<EpubSearchHit> = emptyList(),
    val searched: Boolean = false,
)

/** Capability check (P3-F): books without a search service get no search button. */
@OptIn(ExperimentalReadiumApi::class)
internal fun canSearch(publication: Publication): Boolean = publication.isSearchable

/** First [MAX_HITS] matches in reading order, or null if the search itself failed. Always closes the iterator. */
@OptIn(ExperimentalReadiumApi::class)
internal suspend fun searchPublication(publication: Publication, query: String): List<EpubSearchHit>? {
    val iterator = publication.search(query).getOrNull() ?: return null
    val result = runCatching {
        val hits = mutableListOf<EpubSearchHit>()
        while (hits.size < MAX_HITS) {
            val page = iterator.next().getOrNull() ?: break
            page.locators.forEach { hits += it.toHit() }
        }
        hits.take(MAX_HITS)
    }
    iterator.close()
    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
    return result.getOrNull()
}

private fun Locator.toHit() = EpubSearchHit(
    locator = this,
    before = text.before.orEmpty().takeLast(SNIPPET_CHARS),
    match = text.highlight.orEmpty(),
    after = text.after.orEmpty().take(SNIPPET_CHARS),
)
