package com.ancode.app.tools

import com.ancode.app.linux.RootfsManager
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * File read/write/edit/glob/grep tools operating on the rootfs project tree.
 * Guest paths (/root/...) are mapped onto the host rootfs directory.
 * /sdcard paths are intentionally routed through the terminal tool (proot has
 * the storage permissions the app process lacks).
 */
class ReadTool(private val rootfs: RootfsManager) : Tool {
    override val name = "read_file"
    override val description = "读取文件内容并返回。对大型文件可限制行数。"
    override val parametersSpec: Map<String, JsonObject> = mapOf(
        "path" to Schema.string("guest 内文件路径，如 /root/projects/app/main.py"),
        "offset" to Schema.integer("起始行号（从 1 开始），默认 1"),
        "limit" to Schema.integer("最多读取行数，默认 500")
    )
    override val requiredParams = listOf("path")

    override suspend fun execute(args: Map<String, Any?>): String {
        val path = args["path"]?.toString() ?: return "错误：缺少 path"
        val host = rootfs.guestToHost(path) ?: return "错误：不支持的路径（/sdcard 请用 terminal 工具）"
        val f = File(host)
        if (!f.exists()) return "错误：文件不存在: $path"
        if (f.isDirectory) return "错误：$path 是目录，请用 terminal 的 ls 查看"
        val offset = (args["offset"] as? Number)?.toInt() ?: 1
        val limit = (args["limit"] as? Number)?.toInt() ?: 500
        val lines = f.readLines()
        val start = (offset - 1).coerceIn(0, lines.size)
        val end = (start + limit).coerceAtMost(lines.size)
        val sb = StringBuilder()
        for (i in start until end) {
            sb.append(i + 1).append(" | ").append(lines[i]).append('\n')
        }
        if (end < lines.size) sb.append("... (共 ${lines.size} 行，仅显示 ${start + 1}-$end)")
        return sb.toString()
    }
}

class WriteTool(private val rootfs: RootfsManager) : Tool {
    override val name = "write_file"
    override val description = "写入（覆盖）文件内容。父目录不存在时自动创建。"
    override val parametersSpec: Map<String, JsonObject> = mapOf(
        "path" to Schema.string("guest 内文件路径"),
        "content" to Schema.string("要写入的完整文件内容")
    )
    override val requiredParams = listOf("path", "content")

    override suspend fun execute(args: Map<String, Any?>): String {
        val path = args["path"]?.toString() ?: return "错误：缺少 path"
        val content = args["content"]?.toString() ?: return "错误：缺少 content"
        val host = rootfs.guestToHost(path) ?: return "错误：不支持的路径（/sdcard 请用 terminal 工具）"
        val f = File(host)
        f.parentFile?.mkdirs()
        f.writeText(content)
        return "已写入 ${f.length()} 字节 -> $path"
    }
}

class EditTool(private val rootfs: RootfsManager) : Tool {
    override val name = "edit_file"
    override val description =
        "对文件做精确编辑。方式一：old_string 必须唯一匹配文件中的一段文本，替换为 new_string；方式二：按 start_line/end_line 行号替换为 new_content。"
    override val parametersSpec: Map<String, JsonObject> = mapOf(
        "path" to Schema.string("guest 内文件路径"),
        "old_string" to Schema.string("要替换的原文（必须唯一出现）"),
        "new_string" to Schema.string("替换后的新文本"),
        "start_line" to Schema.integer("起始行号（方式二）"),
        "end_line" to Schema.integer("结束行号（方式二）"),
        "new_content" to Schema.string("新行内容（方式二）")
    )
    override val requiredParams = listOf("path")

    override suspend fun execute(args: Map<String, Any?>): String {
        val path = args["path"]?.toString() ?: return "错误：缺少 path"
        val host = rootfs.guestToHost(path) ?: return "错误：不支持的路径（/sdcard 请用 terminal 工具）"
        val f = File(host)
        if (!f.exists()) return "错误：文件不存在: $path"
        val text = f.readText()

        val old = args["old_string"]?.toString()
        val new = args["new_string"]?.toString()
        if (!old.isNullOrEmpty()) {
            val count = text.windowed(old.length, 1).count { it == old } // occurrences
            if (count == 0) return "错误：old_string 未在文件中找到。请检查内容（含精确空白/换行）。"
            if (count > 1) return "错误：old_string 出现 $count 次，请提供更长的唯一片段。"
            f.writeText(text.replace(old, new ?: ""))
            return "已替换 1 处 -> $path"
        }

        val start = (args["start_line"] as? Number)?.toInt()
        val end = (args["end_line"] as? Number)?.toInt()
        val newContent = args["new_content"]?.toString()
        if (start != null && end != null && newContent != null) {
            val lines = text.split("\n").toMutableList()
            if (start < 1 || end > lines.size || start > end) {
                return "错误：行号越界（文件共 ${lines.size} 行）"
            }
            lines.subList(start - 1, end).clear()
            lines.addAll(start - 1, newContent.split("\n"))
            f.writeText(lines.joinToString("\n"))
            return "已替换第 $start-$end 行 -> $path"
        }
        return "错误：请提供 old_string/new_string 或 start_line/end_line/new_content"
    }
}

class GlobTool(private val rootfs: RootfsManager) : Tool {
    override val name = "glob"
    override val description = "按通配模式列出 guest 内文件（基于 find -name，不含隐藏目录/依赖目录）。"
    override val parametersSpec: Map<String, JsonObject> = mapOf(
        "pattern" to Schema.string("glob 模式，如 '**/*.kt' 或 'src/*.py'"),
        "base" to Schema.string("搜索基准目录，默认 /root/projects")
    )
    override val requiredParams = listOf("pattern")

    override suspend fun execute(args: Map<String, Any?>): String {
        val pattern = args["pattern"]?.toString() ?: return "错误：缺少 pattern"
        val base = args["base"]?.toString()?.takeIf { it.isNotBlank() } ?: "/root/projects"
        // glob is implemented via find (handles **), excluding heavy dirs
        val escaped = pattern.replace("'", "'\\''")
        val cmd = "cd '$base' 2>/dev/null && find . -path './node_modules' -prune -o " +
            "-path './.git' -prune -o -path './build' -prune -o -path './.venv' -prune -o " +
            "-name '$escaped' -print 2>/dev/null | head -200"
        // name matching with ** won't cross dirs; use -path for full globs
        val cmd2 = if (pattern.contains("**")) {
            "cd '$base' 2>/dev/null && find . -path './node_modules' -prune -o -path './.git' -prune -o " +
                "-path './build' -prune -o -path './.venv' -prune -o -path '$escaped' -print 2>/dev/null | head -200"
        } else cmd
        return "匹配文件（相对 $base）：\n" + rootfs.runViaProot(cmd2)
    }
}

class GrepTool(private val rootfs: RootfsManager) : Tool {
    override val name = "grep"
    override val description = "在 guest 文件中搜索文本（rg 或 grep），返回匹配行（最多 100 条）。"
    override val parametersSpec: Map<String, JsonObject> = mapOf(
        "pattern" to Schema.string("正则表达式"),
        "base" to Schema.string("搜索目录，默认 /root/projects"),
        "file_pattern" to Schema.string("文件过滤，如 '*.kt'，默认所有文件"),
        "case_insensitive" to Schema.boolean("是否忽略大小写")
    )
    override val requiredParams = listOf("pattern")

    override suspend fun execute(args: Map<String, Any?>): String {
        val pattern = args["pattern"]?.toString() ?: return "错误：缺少 pattern"
        val base = args["base"]?.toString()?.takeIf { it.isNotBlank() } ?: "/root/projects"
        val filePattern = args["file_pattern"]?.toString()?.takeIf { it.isNotBlank() }
        val ci = args["case_insensitive"] == true || args["case_insensitive"] == "true"
        val esc = pattern.replace("'", "'\\''")
        val ciFlag = if (ci) " -i" else ""
        val fileArg = filePattern?.let { " --include='$it'" } ?: ""
        val cmd = "cd '$base' 2>/dev/null && " +
            "(rg -n --no-heading$ciFlag -g '!node_modules' -g '!.git' -g '!build' -g '!*.lock' '$esc' . 2>/dev/null || " +
            "grep -rn$ciFlag --include='*' -E '$esc' . 2>/dev/null | grep -v -E 'node_modules|/\\.git/|/build/') | head -100"
        return "匹配结果（相对 $base）：\n" + rootfs.runViaProot(cmd)
    }
}