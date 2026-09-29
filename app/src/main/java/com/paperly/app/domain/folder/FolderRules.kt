package com.paperly.app.domain.folder

const val MAX_FOLDER_NAME_LENGTH = 60
const val MAX_TAGS = 20
const val MAX_TAG_LENGTH = 30

private const val TAG_STORAGE_SEPARATOR = "\u001F" // reserved by the Room tag converter

/** Trimmed, length-capped folder name; null if nothing is left. */
fun normalizeFolderName(raw: String): String? = raw.trim().take(MAX_FOLDER_NAME_LENGTH).ifEmpty { null }

/** Comma-separated user input -> clean, de-duplicated (case-insensitive), capped tag list. */
fun parseTags(raw: String): List<String> = raw.split(',')
    .map { it.replace(TAG_STORAGE_SEPARATOR, "").trim().take(MAX_TAG_LENGTH) }
    .filter { it.isNotEmpty() }
    .distinctBy { it.lowercase() }
    .take(MAX_TAGS)
