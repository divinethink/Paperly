# Changelog

## 2026-10-05
- Converter (DOCX -> EPUB): pure-JVM parser + EPUB packager (`domain/converter`), `ConversionWorker` (`data/convert`),
  `DocumentRepository.importConverted` (sets `convertedFrom = "docx"`), Library Import accepts .docx with a progress/result banner.
  No DB/schema/rules change.
