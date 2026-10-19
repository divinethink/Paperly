# Changelog

## 2026-10-20c (Scan page grid: Move / Copy chosen pages to another scan or a new one; no schema change)

- Selection bottom bar is now Delete · Move · Copy · Share · More. Move/Copy open a picker: "New document" first, then your scanned documents (greyed when a scan has no kept originals). A Move cannot target its own document; a Copy can (duplicates the pages at the end).
- What comes along: the page's original picture **with its crop, rotation, filter, brightness and contrast**, and its **title and note**. Highlights stay behind (a copy is a plain page).
- Copy to a scan: the pages are added at its end through the page editor's safe rebuild (`ScanEditRepository.stageAdded` + `apply`: temp file, verify, backup kept 7 days, atomic replace). Copy to a new document: `stageGallery` + `importScan` (named "Scan <date time>", saved in the same folder as the source), then crop/filter are applied with the same safe rebuild and titles/notes are written to the new positions. OCR is queued for the new document.
- Move = Copy first; the originals are removed (same safe rebuild as Delete) only after the copy is checked (page count, edits, notes). If anything did not fully come along, the originals stay and a message says so. A Move is refused before copying when a chosen page has highlights or you chose every page (a Copy is still possible).
- New: `ScanPageTransfer` (data work), `ScanPageTransferViewModel`, `ScanPageTransferUi` (picker + messages), `domain/scanner/ScanPageCopy.kt` (`picked`, `copiedAs`, `copiedAt`, `isEdited`), `newScanTitle`, Move/Copy icons. Tests: `ScanPageCopyTest`, `TransferNoticeTest`, `ScanTitleTest`.
- Verified in the sandbox: the pure logic (`ScanPageCopy`, `ScanPageOps`, `ScanTitle` + their tests, 20 tests green, run with a stand-in JUnit). Not verified (no Android SDK/Gradle): build, ktlint, detekt, the Android classes and `TransferNoticeTest`. Needs on-device check: copy 2 pages (one cropped + one with a note) to another scan and to a new document; Move the same and see the source shrink; a highlighted page (Move refused, Copy works); a scan with no kept originals (greyed); large font; TalkBack.

## 2026-10-20b (Scan page grid: select pages, Delete, Share / Email / Print / Save for the chosen pages; no schema change)

- Page grid/list: long-press a page to start selecting; then a tap ticks/unticks. The title bar shows "N selected" + Select all (back leaves selecting). Pages get a tick box; TalkBack says Selected / Not selected.
- Selection bottom bar: Delete · Share · More (Send email, Print, Save to Gallery, Save to storage). Move/Copy are not there yet (step F).
- Delete (after a confirmation): uses the page editor's safe rebuild (`ScanEditRepository.apply`: temp file, verify, backup kept 7 days, atomic replace), so notes/bookmarks of the other pages follow their pages. Nothing is deleted if a chosen page has highlights, you chose every page, the scan has no kept originals, or the page editor is off (a message says why). New `ScanPageSelectionViewModel`, `withoutAll`/`canDeleteAll` in `ScanPageOps`.
- Share / Email / Gallery / Storage send the chosen pages as JPG pictures (file names keep the page number, e.g. `name-003.jpg`); Gallery saves to Pictures/Paperly (no permission needed, Android 10+); Storage = one page "save as", several = pick a folder. Print makes a PDF of only the chosen pages (`PdfPageSubset`, PdfBox) and opens the system print dialog. The document file is only read.
- `PdfJpgRenderer.render(..., only)` / `ExportViewModel.renderJpgs(..., pages)`; exports now run one at a time (lock) because each run empties the image folder.
- Not verified in the sandbox (no Android SDK/Gradle): build, ktlint, detekt, tests. Needs on-device check: select 2 of 5 pages and Delete (a highlighted page and "all pages" are refused); Share 1 page = 1 image, 3 pages = 3 images; Email app opens with the pictures; Print shows only the chosen pages; Gallery shows Pictures/Paperly; large font; TalkBack.

## 2026-10-20 (Scan: page-editor title, page sizes in the grid, Reader top-bar title/Crop/Delete; no schema change)

- Page editor: the top bar says "Border adjustment" only in the crop step; the filter/page step says "Edit page".
- Page grid/list: each page also shows its stored picture size ("142 kB") under the date, read once with PdfBox (`ScanPageSizes`); no sizes are shown while unknown or if the PDF cannot be read. The size is the stored picture, so it is approximate.
- Reader (scanned documents): the top bar shows the page title ("Page N" if none; tap = rename, same dialog as Note) and, when the page editor is available, Crop and Delete icons. Crop opens the editor on that page in the crop step; Delete asks, then the editor removes that page and saves at once (backup kept 7 days). A page with highlights, or the last page, is not deleted (message shown). Routes: `scan-edit/...&page=&crop=&delete=` (defaults keep every old call unchanged).
- Not verified in the sandbox (no Gradle access): build, lint, tests, PdfBox size read. Needs on-device check: 16-page scan grid sizes; Reader top bar on a small phone with default and large font; Crop/Delete from Reader on a normal, a highlighted and a 1-page scan.

## 2026-10-18d (EPUB popups like PDF: 4 mark types + Thin/Medium/Thick; no schema change)

- EPUB selection popup and tap popup now offer Highlight, Underline, Squiggly, Strikethrough, Note, Copy; the colour dot opens colours + Thin/Medium/Thick (closed by default, as in PDF). The tap popup shows colours, types and thickness for line marks.
- Thickness is stored in the existing `annotations.stroke` column (null = Medium). Line marks are drawn with a custom Readium decoration (`EpubLineStyle`); marks written into the book file also get `text-decoration-thickness`.
- Not verified in the sandbox (no Gradle/Readium access): Readium `HtmlDecorationTemplate` usage in `EpubLineStyle.kt`. Needs build + on-device check.

## 2026-10-18c (Folders belong to a tab; DB v12)

- Each Library tab (Scanned / PDF / EPUB) now shows only its own folders. New folders get the type of the tab they are created in. The folder chips, "Move to folder", Batch Move and the scan "Save" dialog list only that tab's folders (plus untyped ones).
- DB v12: nullable `folders.type` (hand-written `MIGRATION_11_12`, auto-backup runs before as always). Old folders get a type only when every document inside (Trash included) has the same one; empty or mixed old folders stay `null` and show in every tab, so nothing disappears. No data is deleted.
- Backup: optional `type` per folder in the manifest (`formatVersion` stays 1). An old backup restores its folders as `null` (shown in every tab); an unknown `type` value is ignored.
- Folders are still not cloud-synced (unchanged). Tests: `PaperlyMigrationTest`, `BackupArchiveTest`, `FolderRulesTest`.
- Needs on-device check: create a folder in PDF tab, open EPUB tab (not listed); old folders after update.

## 2026-10-18b (EPUB quick view remembers marks, bookmarks and position by file; no schema change)

- A book opened with Open-With (not in the Library) keeps its highlights, underlines, notes, bookmarks and reading position: they are stored in the app's private folder `files/quick-marks/<SHA-256>.json` (`QuickMarksStore`), so opening the same file again brings them back (like Moon+ Reader). The EPUB file is never changed. At most 200 books are remembered (oldest dropped); written atomically (.part -> move), best-effort.
- The "Keep this PDF in your Library?" prompt after the first EPUB highlight is gone (nothing is lost any more). "Save to Library" stays in the quick-view menu; it copies the marks into the Library and forgets the by-file copy.
- "Delete all my data" also empties `files/quick-marks/`. Not in Backup / cloud sync (a local convenience).
- Different file bytes (edited/re-downloaded copy) = different checksum = no marks. PDF quick view is unchanged (writes into the file).
- Needs on-device check: mark in Open-With EPUB, close, open the same file again (file manager/WhatsApp). Test: `QuickMarksStoreTest`.

## 2026-10-18 (EPUB: pinch = text size; PDF-style mark popup; tap-a-mark popup; no schema change)

- Two-finger pinch on EPUB text changes the font size like PDF zoom (`EpubPinchZoom.kt`: `PinchLayout` around the navigator, steps of 0.05 inside 0.7-2.5x, applied live with a "120%" badge, saved to the same global setting when the fingers lift). One-finger scroll/page-turn/long-press are untouched.
- Selecting text now shows a floating Compose bar like the PDF reader's (`EpubSelectionPopup.kt`): colour dot (six colours), Highlight, Underline, Note, Copy; a mark saves at once with the shown colour. The system selection menu is empty (`EpubSelectionMenu` only reports selected/not), the handles still work.
- Tapping a saved mark opens a popup next to it (`EpubAnnotationPopup.kt`): colours, Highlight/Underline, Edit (the old dialog), Delete, Cancel. A tap elsewhere or a page move closes it.
- `MarkIconRow` got an optional `types` list (EPUB shows only the two types Readium can draw).
- Not done: strikethrough, squiggly and line thickness in EPUB (Readium has no such decoration; needs custom decoration templates).
- Needs on-device check: pinch feel and that it does not turn the page, the bar position (Readium selection rect), selection survives a tap on the bar. Tests: `EpubPinchZoomTest`, `EpubPopupOriginTest`.

## 2026-10-17d (faster CI: R8 check runs in parallel; bigger Gradle/Kotlin heap; no code change)

- `build.yml`: the release/R8 build moved from the end of `build` into its own job `release-check`; both jobs run at the same time. Same checks, shorter wait. `build` keeps its name.
- `gradle.properties`: Gradle and Kotlin daemon heap 2 GB -> 3 GB (runner has 16 GB).

## 2026-10-17c (Selection drag scrolls the page near the top/bottom edge; detekt fix; no schema change)

- While a long-press drag or a handle drag is held in the top or bottom 15% of the screen, the page list scrolls (speed grows toward the edge, `edgeSpeed`; `PdfEdgeScroll.kt`). The selection end is re-picked after every scroll step, so a still finger keeps extending the selection. Stays inside one page (cross-page selection is a separate step).
- `AnnotationController`: `@Suppress("TooManyFunctions")` (detekt, 11 functions), same pattern as `ReaderViewModel`.
- Needs on-device check: scroll speed (`MAX_STEP_DP`, `BAND`) and that the end follows the finger. Test: `PdfEdgeScrollTest`.

## 2026-10-17b (Selection stays after a mark, so colour/type can change at once; no schema change)

- A mark placed from the selection bar no longer clears the selection (only Note and Copy do). `PdfSelection.markId` ties the selection to the mark just saved (`annotateSelection(onSaved)` waits for it in `items`, 2 s cap).
- With a linked mark, a new type, colour or thickness restyles that same mark in place (`AnnotationController.restyleMark`, existing `repository.update`) instead of adding a second one. The active type shows as selected in the bar.
- Moving a handle or picking new text drops the link: the next mark is a new one. No repository/interface change.
- Needs on-device check. Not unit-tested (controller needs the Room flow).

## 2026-10-17 (Selection handles vs Xodo: fixed end holds, drag keeps up, handles stay on screen; no schema change)

- Dragging one handle no longer moves the other: the grabbed line end is pinned (`pinEnds`, `SelectionPin`) while the moving end stays on its far side.
- Handle drag no longer starves: `PdfTextSelectController.update` queues the newest request instead of cancelling the running engine call on every move; a late long-press word pick can no longer overwrite a drag.
- The lift of a handle drag is consumed, so it is not read as a tap.
- Handles are kept inside the page (`handleCenters` clamps x), so a handle for text at the left/right page edge is fully visible and grabbable.
- Needs on-device check (not reproducible in the sandbox): items 1-3 of the Xodo comparison. Tests: `PdfSelectionClampTest` (pin + forward).

## 2026-10-16 (PDF marking fixes: per-line underline, handles that hold, table ends; no schema change)
- Underline/highlight/strikethrough are now drawn per saved line part (`Annotation.rects`), not as one bounding box: a multi-line
  underline shows on every line. Underline sits at 88% of the line box (`UNDERLINE_AT`), so it no longer hangs below Bengali lines.
- Selection handles: `selectHandleGesture` checks every new touch of a gesture (a resting palm/finger no longer hides the handle) and
  `pinchZoom` no longer consumes a second finger that only lands. New `GestureLock`: while a long-press selection or handle drag runs,
  the page neither pans nor zooms. Popup clearance 24 -> 36dp so the menu never covers a handle.
- Table cells: `clampEnds` trims the first line's left and the last line's right to the touch points (+3% page width).
- Table cells, round 2: a middle line outside the horizontal band the finger covered (start/end touch x, +-3% page width) is dropped (`dropOtherColumns`); where the finger goes, the selection goes. Selection handles now sit one radius outside the selection ends so they never overlap on a one-word pick.
- Quick view (PDF): highlights go into the original file when the reader closes (existing write-back; no Library prompt, by choice). Every outcome now shows a message, including the cases that used to be silent (setting off, original unknown, nothing to add).
- Quick view root cause: the EPUB view model (it exists for every reader route) deleted the quick copy of a PDF when the reader closed, before the write-back could use it ("original file is unknown"). It now deletes only EPUB copies.
- Magnifier (loupe) smaller (radius 48 -> 36dp) and higher above the finger (1.6 -> 2.4 radii), so it no longer hides the line being selected.
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
