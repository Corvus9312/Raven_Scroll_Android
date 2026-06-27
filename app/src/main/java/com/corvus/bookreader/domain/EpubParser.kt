package ravens.scroll.domain

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

data class EpubChapter(val title: String, val anchor: String)

data class EpubBook(
    val title: String,
    val html: String,            // combined body HTML for all spine docs (images inlined)
    val chapters: List<EpubChapter>,
)

/**
 * Dependency-free EPUB parser. EPUB is an untrusted ZIP, so:
 *  - decompression is bounded (per-entry + total) against zip bombs,
 *  - scripts / inline event handlers / javascript: links are stripped,
 *  - only local images are inlined as data: URIs; remote srcs are blanked so
 *    the reader never makes a network request (the WebView also blocks network),
 *  - parsing is regex-based, sidestepping XXE.
 */
object EpubParser {

    private const val MAX_ENTRY_BYTES = 50 * 1024 * 1024   // 50 MB per inflated entry
    private const val MAX_TOTAL_BYTES = 200 * 1024 * 1024  // 200 MB inflated total

    fun parse(bytes: ByteArray): EpubBook {
        val files = unzip(bytes)

        // 1. container.xml → OPF path
        val container = files["META-INF/container.xml"]
            ?: throw IllegalArgumentException("EPUB 缺少 META-INF/container.xml")
        val opfPath = Regex("""<rootfile\b[^>]*\bfull-path\s*=\s*("([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
            .find(String(container, Charsets.UTF_8))
            ?.let { it.groups[2]?.value ?: it.groups[3]?.value } ?: ""
        val opfBytes = files[opfPath] ?: throw IllegalArgumentException("EPUB 缺少 OPF 檔案")

        val opf = String(opfBytes, Charsets.UTF_8)
        val opfDir = dirOf(opfPath)

        // 2. Title
        val title = Regex("""<dc:title\b[^>]*>([\s\S]*?)</dc:title>""", RegexOption.IGNORE_CASE)
            .find(opf)?.let { stripTags(it.groupValues[1]) } ?: ""

        // 3. Manifest: id → (href, properties)
        val manifest = HashMap<String, Pair<String, String>>()
        for (m in Regex("""<item\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(opf)) {
            val tag = m.value
            val id = attr(tag, "id")
            val href = attr(tag, "href")
            if (id != null && href != null) manifest[id] = href to (attr(tag, "properties") ?: "")
        }

        // 4. Spine: ordered idrefs + optional ncx toc id
        val ncxId = Regex("""<spine\b[^>]*>""", RegexOption.IGNORE_CASE).find(opf)?.let { attr(it.value, "toc") }
        val spineRefs = Regex("""<itemref\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(opf)
            .mapNotNull { attr(it.value, "idref") }.toList()

        // 5. Build combined HTML; map each file path → spine index
        val sectionOfPath = HashMap<String, Int>()
        val sectionHtml = arrayOfNulls<String>(spineRefs.size)
        spineRefs.forEachIndexed { i, idref ->
            val item = manifest[idref] ?: return@forEachIndexed
            val fullPath = resolvePath(opfDir, item.first)
            val raw = files[fullPath] ?: return@forEachIndexed
            val prefix = "epub-sec-$i-"
            var body = extractBody(String(raw, Charsets.UTF_8))
            body = inlineImages(body, dirOf(fullPath), files)
            body = namespaceIds(body, prefix)
            sectionOfPath[fullPath] = i
            sectionHtml[i] = """<section id="epub-sec-$i" data-href="$fullPath">$body</section>"""
        }

        // 6. Table of contents (NCX, then EPUB3 nav)
        var rawToc: List<Pair<String, String>> = emptyList() // title to href
        var tocPath = ""
        if (ncxId != null && manifest.containsKey(ncxId)) {
            tocPath = resolvePath(opfDir, manifest[ncxId]!!.first)
            files[tocPath]?.let { rawToc = parseNcx(String(it, Charsets.UTF_8)) }
        }
        if (rawToc.isEmpty()) {
            for ((_, item) in manifest) {
                if (Regex("""\bnav\b""").containsMatchIn(item.second)) {
                    tocPath = resolvePath(opfDir, item.first)
                    files[tocPath]?.let { rawToc = parseNavDoc(String(it, Charsets.UTF_8)) }
                    break
                }
            }
        }

        val tocDir = dirOf(tocPath)
        val chapters = mutableListOf<EpubChapter>()
        for ((cTitle, href) in rawToc) {
            val secIdx = sectionOfPath[resolvePath(tocDir, href)] ?: continue
            val frag = fragOf(href)
            var anchor = "epub-sec-$secIdx"
            if (frag.isNotEmpty()) {
                val nsId = "epub-sec-$secIdx-$frag"
                if (sectionHtml[secIdx]?.contains("""id="$nsId"""") == true) anchor = nsId
            }
            chapters.add(EpubChapter(cTitle, anchor))
        }

        // Fallback: no usable TOC → one entry per spine section
        if (chapters.isEmpty()) {
            spineRefs.forEachIndexed { i, _ ->
                if (sectionHtml[i] != null) chapters.add(EpubChapter("第 ${i + 1} 節", "epub-sec-$i"))
            }
        }

        return EpubBook(title, sectionHtml.filterNotNull().joinToString("\n"), chapters)
    }

    // ── ZIP reader (bounded against decompression bombs) ─────────────────────────
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val files = HashMap<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            val buf = ByteArray(16 * 1024)
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val out = ByteArrayOutputStream()
                    var entryTotal = 0
                    while (true) {
                        val n = zis.read(buf)
                        if (n < 0) break
                        entryTotal += n
                        if (entryTotal > MAX_ENTRY_BYTES) throw IllegalStateException("EPUB 單一檔案過大，已中止（疑似解壓炸彈）")
                        out.write(buf, 0, n)
                    }
                    total += entryTotal
                    if (total > MAX_TOTAL_BYTES) throw IllegalStateException("EPUB 解壓後過大，已中止（疑似解壓炸彈）")
                    // ZIP mandates '/' separators; normalise stray '\' from odd producers.
                    files[entry.name.replace('\\', '/')] = out.toByteArray()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return files
    }

    // ── Path helpers ─────────────────────────────────────────────────────────────
    private fun dirOf(p: String): String = p.substringBeforeLast('/', "")

    private fun decodeSeg(s: String): String =
        try { java.net.URLDecoder.decode(s, "UTF-8") } catch (_: Exception) { s }

    private fun resolvePath(base: String, rel: String): String {
        val r = decodeSeg(rel.substringBefore('#').substringBefore('?'))
        val combined = if (base.isEmpty()) r else "$base/$r"
        val out = ArrayList<String>()
        for (part in combined.split('/')) {
            when (part) {
                "", "." -> {}
                ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
                else -> out.add(part)
            }
        }
        return out.joinToString("/")
    }

    private fun fragOf(href: String): String =
        href.indexOf('#').let { if (it < 0) "" else href.substring(it + 1) }

    // ── XML / HTML helpers ─────────────────────────────────────────────────────────
    private fun attr(tag: String, name: String): String? {
        val m = Regex("""\b$name\s*=\s*("([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE).find(tag) ?: return null
        return m.groups[2]?.value ?: m.groups[3]?.value
    }

    private fun decodeEntities(s: String): String =
        s.replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("""&#x([0-9a-fA-F]+);""")) { String(Character.toChars(it.groupValues[1].toInt(16))) }
            .replace(Regex("""&#(\d+);""")) { String(Character.toChars(it.groupValues[1].toInt())) }
            .replace("&amp;", "&")

    private fun stripTags(s: String): String =
        decodeEntities(s.replace(Regex("""<[^>]+>"""), "")).replace(Regex("""\s+"""), " ").trim()

    private val MIME_BY_EXT = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif",
        "svg" to "image/svg+xml", "webp" to "image/webp", "bmp" to "image/bmp",
    )

    private fun imageDataUri(src: String, xhtmlDir: String, files: Map<String, ByteArray>): String? {
        val full = resolvePath(xhtmlDir, src)
        val bytes = files[full] ?: return null
        val ext = full.substringAfterLast('.', "").lowercase()
        val mime = MIME_BY_EXT[ext] ?: "application/octet-stream"
        return "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun extractBody(xhtml: String): String {
        val m = Regex("""<body[^>]*>([\s\S]*?)</body>""", RegexOption.IGNORE_CASE).find(xhtml)
        var body = m?.groupValues?.get(1) ?: xhtml
        body = body
            .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""(href\s*=\s*)("|')\s*javascript:[^"']*\2""", RegexOption.IGNORE_CASE), "$1$2#$2")
        return body
    }

    private fun namespaceIds(html: String, prefix: String): String =
        Regex("""\bid\s*=\s*("([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE).replace(html) { mr ->
            val v = mr.groups[2]?.value ?: mr.groups[3]?.value ?: ""
            """id="$prefix$v""""
        }

    private fun inlineImages(html: String, xhtmlDir: String, files: Map<String, ByteArray>): String {
        // Local images become data: URIs; anything else (remote/missing) is blanked
        // so the reader cannot fetch it.
        fun rewrite(pre: String, q: String, src: String): String {
            if (src.startsWith("data:", ignoreCase = true)) return "$pre$q$src$q"
            val uri = imageDataUri(src, xhtmlDir, files)
            return "$pre$q${uri ?: ""}$q"
        }
        return html
            .replace(Regex("""(<img\b[^>]*?\bsrc\s*=\s*)(["'])([^"']*)\2""", RegexOption.IGNORE_CASE)) {
                rewrite(it.groupValues[1], it.groupValues[2], it.groupValues[3])
            }
            .replace(Regex("""(<image\b[^>]*?\b(?:xlink:href|href)\s*=\s*)(["'])([^"']*)\2""", RegexOption.IGNORE_CASE)) {
                rewrite(it.groupValues[1], it.groupValues[2], it.groupValues[3])
            }
    }

    // ── TOC parsing ──────────────────────────────────────────────────────────────
    private fun parseNcx(ncx: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val re = Regex(
            """<navPoint\b[\s\S]*?<text>([\s\S]*?)</text>[\s\S]*?<content\b[^>]*\bsrc\s*=\s*("([^"]*)"|'([^']*)')""",
            RegexOption.IGNORE_CASE,
        )
        for (m in re.findAll(ncx)) {
            val t = stripTags(m.groupValues[1])
            val href = m.groups[3]?.value ?: m.groups[4]?.value ?: ""
            if (t.isNotEmpty() && href.isNotEmpty()) out.add(t to href)
        }
        return out
    }

    private fun parseNavDoc(nav: String): List<Pair<String, String>> {
        val region = Regex(
            """<nav\b[^>]*epub:type\s*=\s*("[^"]*\btoc\b[^"]*"|'[^']*\btoc\b[^']*')[^>]*>([\s\S]*?)</nav>""",
            RegexOption.IGNORE_CASE,
        ).find(nav)
        val scope = region?.groupValues?.get(2) ?: nav
        val out = mutableListOf<Pair<String, String>>()
        val re = Regex("""<a\b[^>]*\bhref\s*=\s*("([^"]*)"|'([^']*)')[^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
        for (m in re.findAll(scope)) {
            val href = m.groups[2]?.value ?: m.groups[3]?.value ?: ""
            val t = stripTags(m.groupValues[4])
            if (t.isNotEmpty() && href.isNotEmpty()) out.add(t to href)
        }
        return out
    }
}
