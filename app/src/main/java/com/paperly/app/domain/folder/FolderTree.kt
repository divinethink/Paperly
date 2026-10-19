package com.paperly.app.domain.folder

/** Deepest allowed nesting: a top-level folder is level 1. */
const val MAX_FOLDER_DEPTH = 1 // one level only (Notebloc-style); raise to allow folders inside folders

const val FOLDER_PATH_SEPARATOR = " › " // "Bills › 2026"

/** The parent that really exists; a missing parent (or a self-reference) makes [folder] a top-level folder. */
fun List<Folder>.parentOf(folder: Folder): Folder? =
    folder.parentId?.let { pid -> firstOrNull { it.id == pid && it.id != folder.id } }

/** Folders directly inside [parentId] (null = top level). Orphans (missing parent) are listed at top level. */
fun List<Folder>.childrenOf(parentId: String?): List<Folder> = filter { parentOf(it)?.id == parentId }

/** Top-level folder first, [id] last. Empty if [id] is unknown. Stops on a cycle, so it can never loop. */
fun List<Folder>.pathTo(id: String?): List<Folder> {
    val byId = associateBy { it.id }
    val chain = mutableListOf<Folder>()
    var current = id?.let { byId[it] }
    while (current != null) {
        val node: Folder = current
        if (chain.any { it.id == node.id }) break
        chain.add(node)
        current = parentOf(node)
    }
    return chain.reversed()
}

/** "Bills › 2026"; just the name for a top-level folder; empty if unknown. */
fun List<Folder>.pathLabel(id: String?): String = pathTo(id).joinToString(FOLDER_PATH_SEPARATOR) { it.name }

/** True if a new folder may be created inside [parentId] (null = top level, always allowed). */
fun List<Folder>.canNestUnder(parentId: String?): Boolean =
    parentId == null || pathTo(parentId).size in 1 until MAX_FOLDER_DEPTH

/** [id] and every folder below it. */
fun List<Folder>.subtreeIds(id: String): Set<String> {
    val found = linkedSetOf(id)
    var frontier = listOf(id)
    while (frontier.isNotEmpty()) {
        frontier = frontier.flatMap { parent -> childrenOf(parent).map { it.id } }.filter { found.add(it) }
    }
    return found
}
