package com.paperly.app.domain.converter

/** DOCX -> [DocxDocument]. Optional parts (styles, numbering, rels) may be absent. */
object DocxParser {
    fun parse(pkg: DocxPackage): DocxDocument {
        val rels = RelsHandler().also { pkg.parseXml(DocxPackage.RELS_PART, it) }
        val styles = StylesHandler().also { pkg.parseXml(DocxPackage.STYLES_PART, it) }
        val numbering = NumberingHandler().also { pkg.parseXml(DocxPackage.NUMBERING_PART, it) }
        val body = DocxBodyHandler(styles.headingLevels, numbering, rels.targets)
        pkg.parseXml(DocxPackage.BODY_PART, body)
        return body.result
    }
}
