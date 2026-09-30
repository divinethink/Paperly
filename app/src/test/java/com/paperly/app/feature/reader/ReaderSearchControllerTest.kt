package com.paperly.app.feature.reader

import android.graphics.Bitmap
import com.paperly.app.domain.reader.MatchRect
import com.paperly.app.domain.reader.OpenResult
import com.paperly.app.domain.reader.ReaderCapabilities
import com.paperly.app.domain.reader.ReaderEngine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeEngine(
    private val text: String,
    private val hits: Map<Int, List<MatchRect>>?,
) : ReaderEngine {
    override val capabilities = ReaderCapabilities(supportsSearch = true)

    override suspend fun open(file: File, password: String?): OpenResult = OpenResult.Success(10)

    override suspend fun pageAspect(index: Int): Float? = 1f

    override suspend fun renderPage(index: Int, maxWidthPx: Int): Bitmap? = null

    override suspend fun pageText(index: Int): String? = text

    override suspend fun search(query: String): Map<Int, List<MatchRect>>? = hits

    override fun close() = Unit
}

/** Unconfined scope + non-suspending fake = everything runs synchronously, no coroutines-test needed. */
class ReaderSearchControllerTest {
    private fun pages(vararg p: Int): Map<Int, List<MatchRect>> = p.associateWith { emptyList() }

    private fun controller(engine: ReaderEngine, page: Int = 0) = ReaderSearchController(
        scope = CoroutineScope(Job() + Dispatchers.Unconfined),
        engine = { engine },
        pageCount = { 10 },
        currentPage = { page },
    )

    @Test
    fun searchIsOfferedOnlyForReadableText() {
        val ok = controller(FakeEngine("The committee published its annual report today.", emptyMap()))
        ok.probe()
        assertTrue(ok.state.value.available)

        val scanned = controller(FakeEngine("", emptyMap()))
        scanned.probe()
        assertFalse(scanned.state.value.available)
    }

    @Test
    fun submitJumpsToFirstMatchAtOrAfterCurrentPageAndWraps() {
        val c = controller(FakeEngine("x", pages(1, 4, 8)), page = 3)
        c.onQueryChange("report")
        c.submit()
        assertEquals(4, c.state.value.jump?.page)
        c.next()
        assertEquals(8, c.state.value.jump?.page)
        c.next()
        assertEquals(1, c.state.value.jump?.page) // wraps
        c.previous()
        assertEquals(8, c.state.value.jump?.page)
    }

    @Test
    fun noMatchesAndFailureAreDistinct() {
        val none = controller(FakeEngine("x", emptyMap()))
        none.onQueryChange("zzz")
        none.submit()
        assertTrue(none.state.value.submitted && none.state.value.matches.isEmpty())

        val failed = controller(FakeEngine("x", null))
        failed.onQueryChange("zzz")
        failed.submit()
        assertTrue(failed.state.value.failed)
    }

    @Test
    fun hitRectanglesReachTheStateForHighlighting() {
        val rect = MatchRect(0.1f, 0.2f, 0.4f, 0.25f)
        val c = controller(FakeEngine("x", mapOf(2 to listOf(rect))))
        c.onQueryChange("report")
        c.submit()
        assertEquals(listOf(rect), c.state.value.rects[2])
        c.toggle() // closing search clears highlights
        assertTrue(c.state.value.rects.isEmpty())
    }

    @Test
    fun blankQueryDoesNothing() {
        val c = controller(FakeEngine("x", pages(1)))
        c.onQueryChange("   ")
        c.submit()
        assertFalse(c.state.value.searching || c.state.value.submitted)
    }
}
