# Keep rules added per-library when needed.

# P10 spike: Tesseract JNI looks these up by name
-keep class com.googlecode.tesseract.android.** { *; }

# L3: PdfBox-Android optionally references the JPEG2000 decoder (not shipped; we never decode JPX)
-dontwarn com.gemalto.jp2.**
