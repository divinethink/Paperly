# Changelog

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
