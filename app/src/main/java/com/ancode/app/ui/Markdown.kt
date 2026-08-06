package com.ancode.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.ancode.app.ui.theme.AccentSoft
import com.ancode.app.ui.theme.Cyan
import com.ancode.app.ui.theme.TextPrimary
import com.ancode.app.ui.theme.TextSecondary
import com.ancode.app.ui.theme.Warning

/**
 * Lightweight Markdown renderer for chat messages (blocks + inline styles).
 * Deliberately dependency-free; covers the constructs vibe-coding agents
 * actually emit: headings, code blocks, lists, quotes, bold/italic/code/links.
 */
object Markdown {

    data class Block(
        val type: String,          // h1..h6, code, quote, ul, ol, hr, p
        val text: String,
        val lang: String? = null
    )

    fun parseBlocks(src: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val lines = src.replace("\r\n", "\n").split("\n")
        var i = 0
        val codeBuf = StringBuilder()
        var codeLang: String? = null
        var inCode = false

        fun flushCode() {
            if (codeBuf.isNotEmpty() || inCode) {
                blocks.add(Block("code", codeBuf.toString().trimEnd('\n'), codeLang))
                codeBuf.clear()
                codeLang = null
                inCode = false
            }
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                if (inCode) {
                    flushCode()
                } else {
                    flushCode()
                    inCode = true
                    codeLang = trimmed.removePrefix("```").trim().takeIf { it.isNotBlank() }
                }
                i++
                continue
            }
            if (inCode) {
                codeBuf.append(line).append('\n')
                i++
                continue
            }

            when {
                trimmed.isEmpty() -> { /* skip */ }
                trimmed.startsWith("###") -> blocks.add(Block("h3", trimmed.removePrefix("###").trim()))
                trimmed.startsWith("##") -> blocks.add(Block("h2", trimmed.removePrefix("##").trim()))
                trimmed.startsWith("#") -> blocks.add(Block("h1", trimmed.removePrefix("#").trim()))
                trimmed.startsWith(">") -> blocks.add(Block("quote", trimmed.removePrefix(">").trim()))
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> blocks.add(Block("ul", trimmed.drop(2).trim()))
                trimmed.matches(Regex("^\\d+\\.\\s.*")) -> blocks.add(Block("ol", trimmed.replaceFirst(Regex("^\\d+\\.\\s"), "")))
                trimmed.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) -> blocks.add(Block("hr", ""))
                else -> blocks.add(Block("p", trimmed))
            }
            i++
        }
        flushCode()
        return blocks
    }

    fun renderBlock(block: Block): AnnotatedString = when (block.type) {
        "h1" -> styled(block.text, SpanStyle(fontWeight = FontWeight.Bold), bold = true, big = true)
        "h2" -> styled(block.text, SpanStyle(fontWeight = FontWeight.Bold), bold = true)
        "h3" -> styled(block.text, SpanStyle(fontWeight = FontWeight.SemiBold), bold = true)
        "quote" -> styled(block.text, SpanStyle(color = TextSecondary, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
        "code" -> renderCode(block.text)
        "ul" -> buildAnnotatedString { append("•  "); append(inline(block.text)) }
        "ol" -> buildAnnotatedString { append("   "); append(inline(block.text)) }
        "hr" -> buildAnnotatedString { append("─".repeat(24)) }
        else -> inline(block.text)
    }

    private fun renderCode(code: String): AnnotatedString = buildAnnotatedString {
        code.split("\n").forEach { l ->
            pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Cyan, background = Color(0x1A22303F)))
            append(l)
            pop()
            append("\n")
        }
    }

    private fun styled(text: String, style: SpanStyle, bold: Boolean = false, big: Boolean = false): AnnotatedString {
        val base = if (bold) style.copy(fontWeight = FontWeight.Bold) else style
        val final = if (big) base.copy(fontSize = 20.sp) else base
        return buildAnnotatedString {
            pushStyle(final)
            append(inline(text))
            pop()
        }
    }

    /** Inline markdown: **bold**, *italic*, `code`, [text](url). */
    fun inline(text: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Cyan))
                        append(text.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else { append(c); i++ }
                }
                c == '[' -> {
                    val close = text.indexOf(']', i + 1)
                    if (close > i && close + 1 < text.length && text[close + 1] == '(') {
                        val paren = text.indexOf(')', close + 1)
                        if (paren > close) {
                            pushStyle(SpanStyle(color = AccentSoft, textDecoration = TextDecoration.Underline))
                            append(text.substring(i + 1, close))
                            pop()
                            i = paren + 1
                        } else { append(c); i++ }
                    } else { append(c); i++ }
                }
                c == '*' || c == '_' -> {
                    // detect ** or * with matching closer
                    val double = c == '*' && i + 1 < text.length && text[i + 1] == '*'
                    val marker = if (double) "**" else c.toString()
                    if (double && i + 2 < text.length) {
                        val end = text.indexOf("**", i + 2)
                        if (end > i) {
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append(text.substring(i + 2, end))
                            pop()
                            i = end + 2
                            continue
                        }
                    }
                    val end = text.indexOf(marker, i + 1)
                    if (end > i) {
                        if (c == '*') pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                        else pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                        append(text.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else { append(c); i++ }
                }
                else -> { append(c); i++ }
            }
        }
    }

    /** Plain-text preview (session list). */
    fun plainText(src: String): String = src
        .replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), " [代码] ")
        .replace(Regex("[#>*`]"), "")
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        .trim()
}