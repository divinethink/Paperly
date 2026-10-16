# Changelog

## 2026-10-16 (PDF marking fixes: per-line underline, handles that hold, table ends; no schema change)
- Underline/highlight/strikethrough are now drawn per saved line part (`Annotation.rects`), not as one bounding box: a multi-line
  underline shows on every line. Underline sits at 88% of the line box (`UNDERLINE_AT`), so it no longer hangs below Bengali lines.
- Selection handles: `selectHandleGesture` checks every new touch of a gesture (a resting palm/finger no longer hides the handle) and
  `pinchZoom` no longer consumes a second finger that only lands. New `GestureLock`: while a long-press selection or handle drag runs,
  the page neither pans nor zooms. Popup clearance 24 -> 36dp so the menu never covers a handle.
- Table cells: `clampEnds` trims the first line's left and the last line's right to the touch points (+3% page width).
- Table cells, round 2: a selection from one cell to a cell in the next row no longer includes the cells in between (`dropOtherColumns`: middle lines that miss the first/last line's column band are dropped).
- Quick view (PDF): highlights go into the original file when the reader closes (existing write-back; no Library prompt, by choice). Every outcome now shows a message, including the cases that used to be silent (setting off, original unknown, nothing to add).
- Needs on-device check: table selection and the 88% underline height (one constant). Test: `PdfSelectionClampTest` (JVM, logic also run standalone).

## 2026-10-15 (Scan S4a: add, delete and reorder pages of a scan; no schema change, DB stays v10)
- The page editor (Reader menu > Edit pages) gets a second button row: Move earlier / Move later, Add scan (ML Kit, appended at the end),
  Add pictures (gallery, appended), Delete page (asks first). Nothing changes until Save; Cancel/Back discards (staged pictures are removed).
- Rules: a page with highlights cannot be deleted (it can be moved); the last page cannot be deleted. Deleting a page also removes its
  title/note and bookmark (the confirm text says so). The current file stays as the usual 7-day backup.
- One save, one order: verified temp PDF + backup (`ScanPdfReplacer.prepare`) -> new image folder `files/scans/<id>.next` -> ONE Room
  transaction (`ScanPageRemapDao.remap`: page title/note, editor rows, highlights, bookmarks, reading position follow the new order,
  then the file is swapped in LAST via `ScanPdfReplacer.commitLast`; a failed swap rolls every row back) -> image folder swap.
  Highlights are re-written into the new file by the existing `PdfHighlightFileSync`; stored OCR text is dropped and re-queued as in S2.
- A save that only changes filters/crop/rotation takes the unchanged S2 path.
- Known / left: crash in the milliseconds between the transaction commit and the image-folder swap leaves the old image folder
  (editor then shows the old page set; PDF and rows are right) - re-saving rebuilds it from the next save only if page count matches.
  Library cover is not regenerated when page 1 changes (same as S2). Multi-file share and auto-naming are NOT in S4a (see roadmap S4b).
- Files: `core/database/ScanPageRemapDao` (new), `PaperlyDatabase`, `CoreModules`, `domain/scanner/ScanPageOps` (new), `ScanEditRepository`,
  `data/scanner/ScanEditRepositoryImpl|ScanSourceStore|ScanPdfReplacer|ScanPdfBuilder`, `feature/scanner/ScanEditScreen|ScanEditViewModel|ScanScreen`, strings.
- Tests: `ScanPageOpsTest`, `ScanPageRemapDaoTest` (remap, rollback on failed file step, highlight on removed page aborts).

## 2026-10-15 (Scan S3: optional text recognition of scans, DB v9 -> v10)
- New Settings switch "Scan text recognition (OCR)" (`scan_ocr_enabled`, OFF by default). ON: downloads the existing Bengali+English
  Tesseract data once (existing `OcrModel`), then reads scans in the background (WorkManager, battery-not-low, one scan at a time,
  not a foreground service). New scans are queued after saving; turning the switch ON queues the existing scans.
- Result is stored per page in the new table `scan_ocr(documentId, pageIndex, text, lang, engineVersion, updatedAt)` (v10, FK -> documents
  CASCADE, new table only; hand-written `MIGRATION_9_10`). Resumable: finished pages are kept; a page read while the file was replaced
  is dropped and retried (file stamp check); after 3 failed runs the rest stays unread. The PDF itself is never changed.
- Uses: Library search also matches the recognised text of scans (`LibraryViewModel`, `filterFor(scanTextIds)`); Reader menu for scans
  "Copy scanned text" (whole scan, pages joined). No invisible text layer inside the PDF (later, own approval).
- A page edit (S2 `ScanPdfReplacer.replace`) deletes the scan's stored text and queues a new read, so text always matches the file.
- Derived data: not in Backup/Sync (can be re-created). Turning the switch OFF stops queued work; stored text stays.
- Known: the Reader's own OCR cache (`cacheDir/ocr1`, used by Reflow) is not cleared by a page edit (older behaviour, unchanged here).
- Files: `core/database/ScanOcrDao.kt`, `PaperlyDatabase`, `PaperlyMigrations`, `CoreModules`, `domain/scanner/ScanOcr.kt`,
  `data/scanner/ScanOcrRunner|ScanOcrWorker|ScanOcrRepositoryImpl`, `PageOcr.freshText`, `ScanPdfReplacer`, `ScanViewModel`,
  `LibraryViewModel`, `ScanPageViewModel`, `ReaderActions`, `CopyScanText`, `ScanOcrRow|ScanOcrViewModel`, `SettingsScreen`, strings.
- Tests: `PaperlyMigrationTest.v10ScanOcrTableWorks`, `ScanOcrTextTest`, `LibraryFilterTest`.

## 2026-10-15 (Scan S1: name + folder when saving a scan)
- After the ML Kit scanner returns, a dialog asks for the scan's name (default `Scan <date>`) and a folder (none/existing). Not
  dismissible by Back/outside tap: "Save" or "Save as is" both store the scan, so it is never lost. A crash while the dialog is open
  re-imports the scan once with the default name (existing `pending_scan_uri` recovery). Blank name -> default (`scanTitle`).
- JPG/PNG export of a scan already exists (Library menu "Export as images"), so S1 adds no export. No DB/schema/rules change.
- Files: `ScanViewModel`, `ScanScreen`, new `ScanNameDialog`, `domain/scanner/ScanTitle.kt`, strings; test `ScanTitleTest`.

## 2026-10-15 (L3 step 3: highlights written into Library PDFs)
- New setting Settings -> Reading "Write highlights into the PDF file" (DataStore `write_highlights_to_pdf`, default ON).
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
