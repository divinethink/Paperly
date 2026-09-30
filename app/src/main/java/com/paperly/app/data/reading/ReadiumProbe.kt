package com.paperly.app.data.reading

import org.readium.r2.shared.publication.Publication

/** P3-A spike probe: proves Readium compiles with our Kotlin/AGP. Remove once EpubReaderEngine exists. */
internal fun readiumProbe(): String = Publication::class.java.simpleName
