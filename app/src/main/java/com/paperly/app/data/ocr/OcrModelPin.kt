package com.paperly.app.data.ocr

import java.io.File
import java.security.MessageDigest

/** Size and SHA-256 a downloaded model file must have. */
internal class ModelFingerprint(val bytes: Long, val sha256: String)

/**
 * The exact `tessdata_fast` files Paperly accepts: fixed to one upstream commit (not a branch that can change),
 * and a download is kept only when its size and SHA-256 match. Changing a model means updating this file on purpose.
 */
internal object OcrModelPin {
    const val COMMIT = "87416418657359cb625c412a48b6e1d6d41c29bd"

    private val fingerprints = mapOf(
        "ben" to ModelFingerprint(
            bytes = 855_841L,
            sha256 = "31163084c279aaebd376216f0c3d5c17ad4b5fee8db49dae79c20000b5de5964",
        ),
        "eng" to ModelFingerprint(
            bytes = 4_113_088L,
            sha256 = "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
        ),
    )

    fun url(lang: String) = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/$COMMIT/$lang.traineddata"

    fun fingerprint(lang: String): ModelFingerprint? = fingerprints[lang]

    /** True only for a known language whose file has exactly the pinned size and SHA-256. */
    fun matches(lang: String, file: File): Boolean = fingerprint(lang)?.let { matches(file, it) } ?: false

    fun matches(file: File, expected: ModelFingerprint): Boolean =
        file.length() == expected.bytes && sha256(file) == expected.sha256
}

private const val BUFFER_BYTES = 64 * 1024

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(BUFFER_BYTES)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
