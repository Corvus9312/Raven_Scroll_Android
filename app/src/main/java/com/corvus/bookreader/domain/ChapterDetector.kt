package com.corvus.bookreader.domain

import com.corvus.bookreader.data.model.Chapter

object ChapterDetector {

    private val PATTERNS = listOf(
        Regex("""^第[零○〇一二三四五六七八九十百千万億\d]+[章節节回篇卷幕]"""),
        Regex("""^Chapter\s+\d+""", RegexOption.IGNORE_CASE),
        Regex("""^(?:序[章言]|前言|後記|后记|尾聲|尾声|番外|楔子|引子|正文).{0,20}$"""),
        Regex("""^\d{1,4}[.、。]\s*.{1,30}$"""),
    )

    fun detect(text: String): List<Chapter> {
        val lines = text.split('\n')
        val result = mutableListOf<Chapter>()
        lines.forEachIndexed { idx, line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.length > 60) return@forEachIndexed
            for (pattern in PATTERNS) {
                if (pattern.containsMatchIn(trimmed)) {
                    result.add(Chapter(title = trimmed, lineIdx = idx))
                    break
                }
            }
        }
        return result
    }
}
