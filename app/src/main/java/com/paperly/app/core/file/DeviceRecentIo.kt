package com.paperly.app.core.file

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

internal fun recentFile(dir: File, kind: RecentKind): File = File(dir, "${kind.key}.${kind.ext}")

private fun metaFile(dir: File, kind: RecentKind): File = File(dir, "${kind.key}.json")

/** The kept entry of [kind], or null when nothing (usable) is stored. */
internal fun readRecent(dir: File, kind: RecentKind): DeviceRecent? = runCatching {
    val meta = JSONObject(metaFile(dir, kind).readText())
    DeviceRecent(
        kind = kind,
        title = meta.getString("title"),
        checksum = meta.getString("checksum"),
        openedAt = meta.getLong("openedAt"),
        page = meta.optInt("page", -1).takeIf { it >= 0 },
        file = recentFile(dir, kind),
    ).takeIf { it.file.isFile }
}.getOrNull()

/** Copies [source] next to the kept file first, then swaps it in, so a failed copy never damages the old one. */
internal fun copyAtomic(source: File, dir: File, kind: RecentKind): Boolean {
    val part = File(dir, "${kind.key}.part")
    return runCatching {
        dir.mkdirs()
        source.copyTo(part, overwrite = true)
        check(part.length() == source.length()) { "short copy" }
        Files.move(part.toPath(), recentFile(dir, kind).toPath(), StandardCopyOption.REPLACE_EXISTING)
    }.onFailure { part.delete() }.isSuccess
}

internal fun deleteRecent(dir: File, kind: RecentKind) {
    runCatching { recentFile(dir, kind).delete() }
    runCatching { metaFile(dir, kind).delete() }
}

/** Saves the small description file, then shows the entry to the screen. */
internal fun MutableStateFlow<DeviceRecent?>.publish(dir: File, value: DeviceRecent) {
    runCatching {
        val meta = JSONObject()
            .put("title", value.title)
            .put("checksum", value.checksum)
            .put("openedAt", value.openedAt)
            .put("page", value.page ?: -1)
        val part = File(dir, "${value.kind.key}.json.part")
        part.writeText(meta.toString())
        Files.move(part.toPath(), metaFile(dir, value.kind).toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    this.value = value
}
