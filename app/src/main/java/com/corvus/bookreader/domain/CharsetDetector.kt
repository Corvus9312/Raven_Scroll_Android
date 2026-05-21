package com.corvus.bookreader.domain

object CharsetDetector {
    fun decode(bytes: ByteArray): String {
        var data = bytes
        // Strip UTF-8 BOM
        if (data.size >= 3 && data[0] == 0xEF.toByte() && data[1] == 0xBB.toByte() && data[2] == 0xBF.toByte()) {
            data = data.copyOfRange(3, data.size)
        }
        return try {
            String(data, Charsets.UTF_8).also {
                // Validate by re-encoding — if it round-trips, it's valid UTF-8
                if (it.toByteArray(Charsets.UTF_8).size != data.size && looksLikeGbk(data)) {
                    return String(data, charset("GB18030"))
                }
            }
        } catch (_: Exception) {
            try { String(data, charset("GB18030")) } catch (_: Exception) { String(data, Charsets.UTF_8) }
        }
    }

    private fun looksLikeGbk(bytes: ByteArray): Boolean {
        var i = 0
        var gbkScore = 0
        while (i < bytes.size - 1) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 0x81..0xFE) {
                val b2 = bytes[i + 1].toInt() and 0xFF
                if (b2 in 0x40..0xFE && b2 != 0x7F) { gbkScore++; i += 2; continue }
            }
            i++
        }
        return gbkScore > 10
    }
}
