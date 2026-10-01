package com.paperly.app.data.reader

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/** Opens EPUB files with Readium (P3). Readium objects are created lazily on the first EPUB open (Rule #10). */
@Singleton
class EpubPublicationOpener @Inject constructor(@ApplicationContext private val context: Context) {
    private val httpClient by lazy { DefaultHttpClient() }
    private val assetRetriever by lazy { AssetRetriever(context.contentResolver, httpClient) }
    private val publicationOpener by lazy {
        PublicationOpener(DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory = null))
    }

    /** The parsed publication, or null on any failure. The caller must close it. */
    suspend fun open(file: File): Publication? = try {
        val asset = assetRetriever.retrieve(file.toUrl()).getOrNull()
        if (asset == null) null else publicationOpener.open(asset, allowUserInteraction = false).getOrNull()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
