package com.paperly.app.data.reader

import com.paperly.app.domain.reader.EpubOpenCheck
import java.io.File
import javax.inject.Inject

/** Opens the finished temp EPUB with Readium (the reader we use) and closes it again. */
class ReadiumEpubOpenCheck @Inject constructor(private val opener: EpubPublicationOpener) : EpubOpenCheck {
    override suspend fun opens(file: File): Boolean {
        val publication = opener.open(file) ?: return false
        publication.close()
        return true
    }
}
