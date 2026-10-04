package com.paperly.app.domain.converter

import java.io.InputStream
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/** WordprocessingML namespaces (transitional + strict); drawing/other namespaces share local names like `p`/`t`. */
internal val WORD_NAMESPACES = setOf(
    "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
    "http://purl.oclc.org/ooxml/wordprocessingml/main",
)

/** Attribute by local name, whatever its namespace prefix (`w:val`, `r:embed`, ...). */
internal fun Attributes.attr(local: String): String? {
    for (i in 0 until length) {
        if (getLocalName(i) == local) return getValue(i)
    }
    return null
}

/**
 * Streaming SAX parse (same API on JVM and Android). DOCTYPE/external entities are refused where the parser
 * supports it, and the resolver returns an empty source everywhere else, so no file or URL is ever fetched.
 */
internal fun readXml(input: InputStream, handler: DefaultHandler) {
    val factory = SAXParserFactory.newInstance()
    factory.isNamespaceAware = true
    factory.isValidating = false
    runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    val reader = factory.newSAXParser().xmlReader
    reader.contentHandler = handler
    reader.errorHandler = handler
    reader.entityResolver = EntityResolver { _, _ -> InputSource(StringReader("")) }
    reader.parse(InputSource(input))
}
