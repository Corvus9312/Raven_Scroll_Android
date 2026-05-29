package ravens.scroll.domain

import java.nio.charset.Charset

object CharsetDetector {

    fun decode(bytes: ByteArray): String {
        if (bytes.size < 2) return String(bytes, Charsets.UTF_8)

        // ── BOM Detection ──────────────────────────────────────────────────
        // UTF-32 必須在 UTF-16 之前判斷（FF FE 00 00 vs FF FE）
        if (bytes.size >= 4
            && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()
            && bytes[2] == 0x00.toByte() && bytes[3] == 0x00.toByte()
        ) return String(bytes.copyOfRange(4, bytes.size), Charset.forName("UTF-32LE"))

        if (bytes.size >= 4
            && bytes[0] == 0x00.toByte() && bytes[1] == 0x00.toByte()
            && bytes[2] == 0xFE.toByte() && bytes[3] == 0xFF.toByte()
        ) return String(bytes.copyOfRange(4, bytes.size), Charset.forName("UTF-32BE"))

        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return String(bytes.copyOfRange(2, bytes.size), Charsets.UTF_16LE)

        if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return String(bytes.copyOfRange(2, bytes.size), Charsets.UTF_16BE)

        // UTF-8 BOM: EF BB BF — BOM is authoritative, decode as UTF-8 lax (never fall back to CJK)
        if (bytes.size >= 3
            && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) return String(bytes.copyOfRange(3, bytes.size), Charsets.UTF_8)

        // ── UTF-8 驗證 ─────────────────────────────────────────────────────
        val data = bytes
        return try {
            val utf8 = String(data, Charsets.UTF_8)
            // Round-trip：編碼回來長度相同代表是合法 UTF-8
            if (utf8.toByteArray(Charsets.UTF_8).size == data.size) utf8
            else decodeCjk(data)
        } catch (_: Exception) {
            decodeCjk(data)
        }
    }

    /** 在 GB18030 與 BIG5 之間選擇並解碼 */
    private fun decodeCjk(bytes: ByteArray): String =
        try { String(bytes, Charset.forName(detectCjkCharset(bytes))) }
        catch (_: Exception) { String(bytes, Charsets.UTF_8) }

    /**
     * 啟發式判斷 GB18030 vs BIG5（無 BOM 情況）。
     *
     * 判斷規則：
     * 1. lead 在 0x81–0xA0 → GB 專屬（BIG5 lead 範圍僅 0xA1–0xF9）
     * 2. trail 在 0x80–0xA0 → GB 專屬（BIG5 trail 從 0x7E 直接跳到 0xA1）
     *    以上兩條命中任一 → GB18030
     * 3. 計算 trail 落在 0x40–0x7E 的比例：
     *    BIG5 Level-1/2 常用字約 40% 序列使用此區間；
     *    GB2312 常用字 trail 全在 0xA1–0xFE，比例趨近 0。
     *    比例 > 25% → BIG5，否則 → GB18030
     */
    private fun detectCjkCharset(bytes: ByteArray): String {
        var gbOnly    = 0   // 只在 GB 有效的序列
        var trailLow  = 0   // trail 0x40–0x7E（BIG5 常用字主力區）
        var trailHigh = 0   // trail 0xA1–0xFE（GB2312 常用字主力區）
        var total     = 0

        var i = 0
        while (i < bytes.size - 1) {
            val b1 = bytes[i].toInt() and 0xFF
            val b2 = bytes[i + 1].toInt() and 0xFF

            // GB/GBK 合法序列：lead 0x81–0xFE，trail 0x40–0xFE（排除 0x7F）
            if (b1 in 0x81..0xFE && b2 in 0x40..0xFE && b2 != 0x7F) {
                total++
                when {
                    b1 !in 0xA1..0xF9 -> gbOnly++          // BIG5 lead 範圍以外
                    b2 in 0x80..0xA0  -> gbOnly++          // BIG5 trail 跳空區
                    b2 in 0x40..0x7E  -> trailLow++
                    else              -> trailHigh++
                }
                i += 2; continue
            }
            i++
        }

        if (total < 5)  return "GB18030"   // 樣本不足，預設 GB
        if (gbOnly > 0) return "GB18030"   // 明確的 GB 特徵

        // 模糊區：trail 分佈決策
        return if (trailLow.toFloat() / total > 0.25f) "Big5" else "GB18030"
    }
}
