# Changelog

## 2026-10-05
- Converter (DOCX -> EPUB): pure-JVM parser + EPUB packager (`domain/converter`), `ConversionWorker` (`data/convert`),
  `DocumentRepository.importConverted` (sets `convertedFrom = "docx"`), Library Import accepts .docx with a progress/result banner.
  No DB/schema/rules change.
- EPUB reader: progress shows 100% at the end of the last chapter (Readium's 1 KB positions made tiny books stop at 50%).
- UI: 5-tab bottom bar (Scanned, PDF, Scan, EPUB, Settings; opens on Scanned), one Library screen per document type, Settings regrouped into cards (Preferences / Data / Privacy). No DB/rules change.
- U3: single warm accent (primary = secondary), serif headings (`Color.kt`/`Theme.kt`/`Type.kt` only).
- U4: Library cover grid. EPUB declared cover / PDF first page via `PdfRenderer` (`data/cover/CoverRepositoryImpl`, JPEG cache in `cacheDir/covers` + LRU), letter-cover fallback. No DB/rules change, no new dependency.
