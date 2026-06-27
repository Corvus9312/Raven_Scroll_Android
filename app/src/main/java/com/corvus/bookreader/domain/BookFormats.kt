package ravens.scroll.domain

/** Supported book formats and helpers shared across the app. */

fun isBookFile(name: String): Boolean =
    name.endsWith(".txt", ignoreCase = true) || name.endsWith(".epub", ignoreCase = true)

fun isEpubFile(name: String): Boolean =
    name.endsWith(".epub", ignoreCase = true)

fun stripBookExt(name: String): String =
    name.replace(Regex("""\.(txt|epub)$""", RegexOption.IGNORE_CASE), "")

/**
 * Sniff whether [bytes] is a ZIP/EPUB container by its local-file-header magic
 * (`PK\x03\x04`). Reliable because EPUB mandates a ZIP container and a plain TXT
 * never starts with these bytes — lets us decide format from content alone,
 * without depending on a filename (Google Drive opens by file id, not name).
 */
fun looksLikeZip(bytes: ByteArray): Boolean =
    bytes.size >= 4 &&
        bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
        bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
