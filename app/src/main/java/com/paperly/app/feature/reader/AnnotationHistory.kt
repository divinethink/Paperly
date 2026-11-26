package com.paperly.app.feature.reader

import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val MAX_STEPS = 50
private const val SETTLE_WAIT_MS = 2_000L // how long a stored change may take to show up in the list

/** One saved-mark step: [before] null = the mark was added, [after] null = it was deleted, both = it changed. */
internal data class MarkChange(val before: Annotation?, val after: Annotation?)

/** What turned [old] into [new], one step per mark; null when a mark moved to another page (not a user edit). */
internal fun markChanges(old: Map<String, Annotation>, new: Map<String, Annotation>): List<MarkChange>? {
    val steps = ArrayList<MarkChange>()
    for (now in new.values) {
        val was = old[now.id]
        when {
            was == null -> steps.add(MarkChange(null, now))
            was.page != now.page -> return null
            was != now -> steps.add(MarkChange(was, now))
        }
    }
    old.values.filter { it.id !in new }.forEach { steps.add(MarkChange(it, null)) }
    return steps
}

private fun Annotation.withId(old: String, new: String): Annotation = if (id == old) copy(id = new) else this

internal fun MarkChange.renamed(old: String, new: String): MarkChange =
    MarkChange(before?.withId(old, new), after?.withId(old, new))

private fun Annotation.content() = AnnotationContent(type, color, noteText, stroke)

/**
 * Undo / redo of saved marks (add, delete, type / colour / thickness / note, move / resize), found by watching the
 * stored list, so every way of changing a mark is covered. Lives as long as the reader; not persisted. A mark that
 * is brought back gets a new id (the old one is replaced in the remaining steps).
 */
internal class AnnotationHistory(
    private val scope: CoroutineScope,
    private val repository: AnnotationRepository,
    private val documentId: String,
) {
    private val undoSteps = ArrayDeque<MarkChange>()
    private val redoSteps = ArrayDeque<MarkChange>()
    private val undoable = MutableStateFlow(false)
    private val redoable = MutableStateFlow(false)
    private val ticks = MutableStateFlow(0)
    private var seen: Map<String, Annotation>? = null
    private var busy = false
    private var freshId: String? = null

    val canUndo: StateFlow<Boolean> = undoable.asStateFlow()
    val canRedo: StateFlow<Boolean> = redoable.asStateFlow()

    init {
        if (documentId.isNotEmpty()) scope.launch { repository.observe(documentId).collect { onList(it) } }
    }

    fun undo() = move(undoSteps, redoSteps, forward = false)

    fun redo() = move(redoSteps, undoSteps, forward = true)

    private fun onList(list: List<Annotation>) {
        val now = list.associateBy { it.id }
        val old = seen
        seen = now
        val steps = if (old == null) emptyList<MarkChange>() else markChanges(old, now)
        when {
            steps == null -> reset() // pages were rearranged (scan editor): old steps no longer fit
            busy -> steps.firstOrNull { it.before == null }?.let { freshId = it.after?.id }
            steps.isNotEmpty() -> record(steps)
        }
        ticks.value += 1
    }

    private fun record(steps: List<MarkChange>) {
        steps.forEach { undoSteps.addLast(it) }
        redoSteps.clear()
        while (undoSteps.size > MAX_STEPS) undoSteps.removeFirst()
        publish()
    }

    private fun reset() {
        undoSteps.clear()
        redoSteps.clear()
        publish()
    }

    private fun publish() {
        undoable.value = undoSteps.isNotEmpty()
        redoable.value = redoSteps.isNotEmpty()
    }

    private fun move(from: ArrayDeque<MarkChange>, to: ArrayDeque<MarkChange>, forward: Boolean) {
        if (busy) return
        val step = from.removeLastOrNull() ?: return
        busy = true
        publish()
        scope.launch {
            try {
                to.addLast(apply(step, forward))
            } finally {
                busy = false
                publish()
            }
        }
    }

    /** Brings the mark to the wanted state; returns the step for the opposite stack (ids kept current). */
    private suspend fun apply(step: MarkChange, forward: Boolean): MarkChange {
        val now = if (forward) step.before else step.after
        val goal = if (forward) step.after else step.before
        val tick = ticks.value
        var result = step
        when {
            now != null && goal == null -> repository.delete(now.id)
            now == null && goal != null -> result = bringBack(step, goal)
            now != null && goal != null -> restore(now, goal)
        }
        withTimeoutOrNull(SETTLE_WAIT_MS) { ticks.first { it > tick } }
        return result
    }

    private suspend fun bringBack(step: MarkChange, goal: Annotation): MarkChange {
        freshId = null
        val tick = ticks.value
        repository.add(documentId, goal.page, goal.rect, goal.content(), goal.rects)
        withTimeoutOrNull(SETTLE_WAIT_MS) { ticks.first { it > tick } }
        val fresh = freshId ?: return step
        for (i in undoSteps.indices) undoSteps[i] = undoSteps[i].renamed(goal.id, fresh)
        for (i in redoSteps.indices) redoSteps[i] = redoSteps[i].renamed(goal.id, fresh)
        return step.renamed(goal.id, fresh)
    }

    private suspend fun restore(now: Annotation, goal: Annotation) {
        repository.update(now.id, goal.content())
        if (goal.rects != now.rects) repository.setRects(now.id, goal.rects)
    }
}
