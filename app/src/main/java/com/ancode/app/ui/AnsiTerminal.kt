package com.ancode.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ancode.app.ui.theme.AnsiBlack
import com.ancode.app.ui.theme.AnsiBlue
import com.ancode.app.ui.theme.AnsiBrightBlack
import com.ancode.app.ui.theme.AnsiBrightBlue
import com.ancode.app.ui.theme.AnsiBrightCyan
import com.ancode.app.ui.theme.AnsiBrightGreen
import com.ancode.app.ui.theme.AnsiBrightMagenta
import com.ancode.app.ui.theme.AnsiBrightRed
import com.ancode.app.ui.theme.AnsiBrightWhite
import com.ancode.app.ui.theme.AnsiBrightYellow
import com.ancode.app.ui.theme.AnsiCyan
import com.ancode.app.ui.theme.AnsiGreen
import com.ancode.app.ui.theme.AnsiMagenta
import com.ancode.app.ui.theme.AnsiRed
import com.ancode.app.ui.theme.AnsiWhite
import com.ancode.app.ui.theme.AnsiYellow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Minimal VT100-ish terminal emulator for the interactive terminal panel.
 * Supports: SGR colors (16 + 256), bold, cursor positioning, clear, scroll.
 * Rendering is line-based: each screen row becomes one AnnotatedString.
 */
class AnsiTerminal(
    var cols: Int = 80,
    var rows: Int = 24
) {
    data class Cell(val ch: Char, val fg: Int, val bg: Int, val bold: Boolean)

    private val lines = CopyOnWriteArrayList<MutableList<Cell>>()
    private var cursorRow = 0
    private var cursorCol = 0
    private var curFg = 7
    private var curBg = -1
    private var curBold = false
    private var pending = StringBuilder()
    private var inEscape = false
    private val scrollback = ArrayDeque<String>()
    var maxScrollback = 2000
        private set

    init {
        ensureRows()
    }

    private fun ensureRows() {
        while (lines.size < rows) lines.add(MutableList(cols) { Cell(' ', 7, -1, false) })
    }

    private fun blankRow(): MutableList<Cell> = MutableList(cols) { Cell(' ', 7, -1, false) }

    /** Feed raw text (already decoded from bytes) into the emulator. */
    fun feed(text: String) {
        for (ch in text) {
            if (inEscape) {
                pending.append(ch)
                if (ch in '@'..'~') {           // CSI ends with a final byte
                    handleEscape(pending.toString())
                    pending.clear()
                    inEscape = false
                }
                continue
            }
            when (ch) {
                '\u001B' -> { inEscape = true; pending.clear(); pending.append('\u001B') }
                '\r' -> cursorCol = 0
                '\n' -> newline()
                '\b' -> if (cursorCol > 0) cursorCol--
                '\u0007', '\u0000' -> { /* bell / NUL: ignore */ }
                else -> putChar(ch)
            }
        }
    }

    private fun putChar(ch: Char) {
        ensureRows()
        val row = lines[cursorRow]
        if (cursorCol < cols) {
            row[cursorCol] = Cell(ch, curFg, curBg, curBold)
            cursorCol++
        } else {
            newline()
            putChar(ch)
        }
    }

    private fun newline() {
        cursorCol = 0
        cursorRow++
        if (cursorRow >= rows) {
            val top = lines.removeAt(0)
            scrollback.addLast(top.joinToString("") { it.ch.toString() })
            if (scrollback.size > maxScrollback) scrollback.removeFirst()
            lines.add(blankRow())
            cursorRow = rows - 1
        }
    }

    private fun handleEscape(seq: String) {
        if (!seq.startsWith("\u001B[")) return
        val body = seq.substring(2, seq.length - 1)
        val finalChar = seq.last()
        val params = body.split(';')

        when (finalChar) {
            'm' -> applySgr(params)
            'H', 'f' -> {
                val r = params.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val c = params.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                cursorRow = (r - 1).coerceIn(0, rows - 1)
                cursorCol = (c - 1).coerceIn(0, cols - 1)
            }
            'A' -> cursorRow = (cursorRow - (params.firstOrNull()?.toIntOrNull() ?: 1)).coerceAtLeast(0)
            'B' -> cursorRow = (cursorRow + (params.firstOrNull()?.toIntOrNull() ?: 1)).coerceAtMost(rows - 1)
            'C' -> cursorCol = (cursorCol + (params.firstOrNull()?.toIntOrNull() ?: 1)).coerceAtMost(cols - 1)
            'D' -> cursorCol = (cursorCol - (params.firstOrNull()?.toIntOrNull() ?: 1)).coerceAtLeast(0)
            'G' -> cursorCol = (params.firstOrNull()?.toIntOrNull()?.minus(1) ?: 0).coerceIn(0, cols - 1)
            'd' -> cursorRow = (params.firstOrNull()?.toIntOrNull()?.minus(1) ?: 0).coerceIn(0, rows - 1)
            'J' -> {
                when (params.firstOrNull()?.toIntOrNull() ?: 0) {
                    2, 3 -> {
                        lines.clear()
                        ensureRows()
                        cursorRow = 0; cursorCol = 0
                    }
                    0 -> clearFromCursor()
                }
            }
            'K' -> {
                val row = lines[cursorRow]
                for (c in cursorCol until cols) row[c] = Cell(' ', 7, -1, false)
            }
            'L' -> {
                lines.add(cursorRow, blankRow())
                while (lines.size > rows) {
                    scrollback.addLast(lines.removeAt(0).joinToString("") { it.ch.toString() })
                    if (scrollback.size > maxScrollback) scrollback.removeFirst()
                }
            }
            'M' -> {
                if (cursorRow in lines.indices) {
                    lines.removeAt(cursorRow)
                    lines.add(blankRow())
                }
            }
        }
    }

    private fun clearFromCursor() {
        val row = lines[cursorRow]
        for (c in cursorCol until cols) row[c] = Cell(' ', 7, -1, false)
        for (r in cursorRow + 1 until lines.size) {
            lines[r] = blankRow()
        }
    }

    private fun applySgr(params: List<String>) {
        if (params.isEmpty() || params.all { it.isEmpty() }) {
            resetSgr()
            return
        }
        var i = 0
        while (i < params.size) {
            val p = params[i].toIntOrNull() ?: 0
            when {
                p == 0 -> resetSgr()
                p == 1 -> curBold = true
                p == 22 -> curBold = false
                p == 7 -> { val t = curFg; curFg = if (curBg >= 0) curBg else 7; curBg = t }
                p == 39 -> curFg = 7
                p == 49 -> curBg = -1
                p in 30..37 -> curFg = p - 30
                p in 90..97 -> curFg = p - 90 + 8
                p in 40..47 -> curBg = p - 40
                p in 100..107 -> curBg = p - 100 + 8
                p == 38 || p == 48 -> {
                    val next = params.getOrNull(i + 1)
                    when (next) {
                        "5" -> {
                            val n = params.getOrNull(i + 2)?.toIntOrNull() ?: 7
                            if (p == 38) curFg = n else curBg = n
                            i += 2
                        }
                        "2" -> { i += 4 } // truecolor ignored in MVP
                        else -> {}
                    }
                }
            }
            i++
        }
    }

    private fun resetSgr() {
        curFg = 7; curBg = -1; curBold = false
    }

    /** Render visible screen rows as AnnotatedStrings. */
    fun render(): List<AnnotatedString> {
        return lines.map { row ->
            buildAnnotatedString {
                var lastStyle: Pair<Int, Int>? = null
                for (cell in row) {
                    val key = cell.fg to cell.bg
                    if (key != lastStyle) {
                        pushStyle(styleFor(cell))
                        lastStyle = key
                    }
                    append(cell.ch)
                }
                if (lastStyle != null) pop()
            }
        }
    }

    private fun styleFor(cell: Cell): SpanStyle = SpanStyle(
        color = ansiColor(cell.fg, cell.bold),
        background = if (cell.bg >= 0) ansiColor(cell.bg, false) else Color.Transparent,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal
    )

    private fun ansiColor(idx: Int, bright: Boolean): Color {
        val i = idx.coerceIn(0, 255)
        return when {
            i < 8 -> if (bright) listOf(AnsiBrightBlack, AnsiBrightRed, AnsiBrightGreen, AnsiBrightYellow, AnsiBrightBlue, AnsiBrightMagenta, AnsiBrightCyan, AnsiBrightWhite)[i] else listOf(AnsiBlack, AnsiRed, AnsiGreen, AnsiYellow, AnsiBlue, AnsiMagenta, AnsiCyan, AnsiWhite)[i]
            i < 16 -> listOf(AnsiBrightBlack, AnsiBrightRed, AnsiBrightGreen, AnsiBrightYellow, AnsiBrightBlue, AnsiBrightMagenta, AnsiBrightCyan, AnsiBrightWhite)[i - 8]
            i < 232 -> {
                // 6x6x6 color cube
                val cube = i - 16
                val r = (cube / 36) * 51
                val g = ((cube % 36) / 6) * 51
                val b = (cube % 6) * 51
                Color(android.graphics.Color.rgb(r, g, b))
            }
            else -> {
                val v = 8 + (i - 232) * 10
                Color(android.graphics.Color.rgb(v, v, v))
            }
        }
    }

    /** Clear all content (used when the PTY restarts). */
    fun reset() {
        lines.clear()
        scrollback.clear()
        cursorRow = 0; cursorCol = 0
        resetSgr()
        ensureRows()
    }
}