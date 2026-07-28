package com.nothing.lyricwidget.utils

import com.nothing.lyricwidget.model.LyricLine
import java.util.regex.Pattern

object LrcParser {
    private val lrcPattern = Pattern.compile("\\[(\\d+):(\\d+)(?:\\.(\\d+))?]\\s*(.*)")

    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()
        
        val lines = mutableListOf<LyricLine>()
        val rawLines = lrcContent.split("\n")
        
        for (rawLine in rawLines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            
            val matcher = lrcPattern.matcher(trimmed)
            if (matcher.matches()) {
                try {
                    val min = matcher.group(1)?.toLong() ?: 0L
                    val sec = matcher.group(2)?.toLong() ?: 0L
                    val msStr = matcher.group(3) ?: "00"
                    
                    // Convert fractional seconds to milliseconds
                    val ms = when (msStr.length) {
                        1 -> msStr.toLong() * 100
                        2 -> msStr.toLong() * 10
                        3 -> msStr.toLong()
                        else -> msStr.substring(0, 3).toLong()
                    }
                    
                    val timeMs = (min * 60 + sec) * 1000 + ms
                    val text = matcher.group(4) ?: ""
                    
                    lines.add(LyricLine(timeMs, text))
                } catch (e: Exception) {
                    // Ignore parse errors on individual lines
                }
            }
        }
        
        return lines.sortedBy { it.timeMs }
    }
}
