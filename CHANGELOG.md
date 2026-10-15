# Changelog

## 2026-10-15 (L3 step 3: highlights written into Library PDFs)
- New setting Settings -> Reading "Write highlights into the PDF file" (DataStore `write_highlights_to_pdf`, default OFF).
- When ON: after a Library PDF/scanned-PDF Reader closes (engine closed first) and a highlight was added/edited/removed in that
  session, `PdfHighlightFileSync` (app scope, one write at a time) passes all of the document's annotations to `PdfAnnotationWriter`
  (dedupe by `/NM`; verify = the viewer engine opens the temp file). On `Written`: `LibraryPdfFiles.replaced` updates
  `checksum`/`sizeBytes`/`updatedAt`, clears `cloudRef`, sets `storageState = QUEUED`, re-arms the FILE item and queues the DOCUMENT
  item, so Drive gets the new bytes (the upload step already removes the stale Drive copy). Skipped/Failed = nothing changes.
- Limits (v1): only additions are written (later edit/delete stays in the file); highlights made while the setting was OFF are
  written the next time a highlight changes in that PDF; other devices keep their old file; a kill between the file swap and the
  row update would leave a checksum mismatch (retried 3x, window is milliseconds). Quick view and EPUB untouched.
- Unknown until device test: whether `androidx.pdf` also draws the file's own annotations (would show a highlight twice -> step 3b).
- No DB schema / Firestore rules change (one new `@Query` in `CloudSyncDao`). Tests: `PdfHighlightFileSyncImplTest`, `LibraryPdfFilesImplTest`.

## 2026-10-15 (L3 step 2: PDF highlight writer, not wired yet)
- New `PdfAnnotationWriter` (domain) + `PdfBoxAnnotationWriter` (data, PdfBox-Android 2.0.27.0): writes `/Highlight` `/Underline`
  `/StrikeOut` `/Text` annotations (with `/QuadPoints`, colour, `/NM` = our annotation id) into a Library PDF. Safe order: temp copy
  -> reopen/verify -> backup (7 days, `files/pdf_backups`) -> atomic replace; any failure leaves the original untouched.
  Skips: > 50 MB, encrypted, out of memory. `PdfAnnotationGeometry` maps page fractions to PDF space for /Rotate 0/90/180/270 + CropBox.
  Nothing calls it yet (setting, batching and checksum/sync update come in step 3). No DB/schema/rules change.

## 2026-10-15 (Quick view: EPUB)
- Open-With of an EPUB is read directly too (cache copy, `QuickViewStore` now handles pdf + epub; same book already in the Library
  opens that copy). Highlights/bookmarks stay in memory (`QuickEpubSession`); the first highlight asks to keep the book in the Library;
  top-bar menu: Save to Library / Share / Save a copy. Save to Library copies highlights, bookmarks and the position and reopens
  the Library copy. `importPdf` -> `importQuick(uri, title, type)`. No DB/schema change.

## 2026-10-15 (Quick view: highlights + bookmarks)
- Quick-view PDF: highlight/underline/note and bookmarks work (kept in memory; `QuickSession`). The first highlight asks
  "Keep this PDF in your Library?"; Save to Library imports the PDF, copies highlights, bookmarks and the page, then reopens
  the Library copy (`ReaderRedirects`). Not now = they are lost when the reader closes. No DB/schema change.

## 2026-10-14 (build fix + Quick view)
- Build fix: removed duplicate declarations left by a partial upload (`HitKind`/`PendingHit`/`hitPending`, `TypeOption`/`typeLabel`);
  pending boxes now also have side handles (`PdfPendingHit.kt`, `dragSide`).
- Quick view: Open-With of a PDF is read directly, not imported (cache copy via `QuickViewStore`; same content already in the
  Library opens that copy). No progress/bookmarks/annotations for a quick PDF; Share / Save a copy / Print and "Save to Library"
  are in the Reader's menu. The temporary copy is deleted when the Reader closes (older leftovers pruned after 24 h).
  Share (SEND) and EPUB keep the import flow. New `DocumentRepository.importPdf`. No DB/schema/rules change.

## 2026-10-14
- PDF annotate, easier: (A) long-press word selection (then Annotate) now also works on Unicode-Bengali text-layer PDFs; Copy is hidden
  there (extraction is lossy) and Bengali vowel signs/hasanta stay inside the picked word. (B) On pages with no readable text layer
  (scans, image PDFs, Bijoy text) a long-press drops a one-line box (drag sideways to stretch; handles/move/remove as in Annotate mode)
  and a floating menu saves it as Highlight / Underline / Strikethrough / Note in one tap (colour swatches in the menu); tap beside the
  box cancels. New `PdfQuickBox.kt`; `PdfPendingSegments`/`AnnotationUi` wiring. No DB/schema/rules change.
- PDF quick box also on scans that carry a stray text layer (e.g. a "Scanned by CamScanner" watermark): the engine snaps a press to the
  nearest character, so a pick now counts only when the press is on/next to its lines; otherwise `onMiss` drops the quick box.

## 2026-10-13
- CI/deploy workflow: `unzip-upload` now starts CI only when something was committed and also starts the Firestore rules tests when rules files changed; `build.yml` ignores zip-only pushes; new manual `deploy-rules` (rules tests, then `firebase deploy --only firestore:rules`, secret `FIREBASE_SERVICE_ACCOUNT`). No app/DB/rules change.

## 2026-10-05
- Converter (DOCX -> EPUB): pure-JVM parser + EPUB packager (`domain/converter`), `ConversionWorker` (`data/convert`),
  `DocumentRepository.importConverted` (sets `convertedFrom = "docx"`), Library Import accepts .docx with a progress/result banner.
  No DB/schema/rules change.
- EPUB reader: progress shows 100% at the end of the last chapter (Readium's 1 KB positions made tiny books stop at 50%).
- UI: 5-tab bottom bar (Scanned, PDF, Scan, EPUB, Settings; opens on Scanned), one Library screen per document type, Settings regrouped into cards (Preferences / Data / Privacy). No DB/rules change.
- U3: single warm accent (primary = secondary), serif headings (`Color.kt`/`Theme.kt`/`Type.kt` only).
- U4: Library cover grid. EPUB declared cover / PDF first page via `PdfRenderer` (`data/cover/CoverRepositoryImpl`, JPEG cache in `cacheDir/covers` + LRU), letter-cover fallback. No DB/rules change, no new dependency.
