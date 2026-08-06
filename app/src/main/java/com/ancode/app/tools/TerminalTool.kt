package com.ancode.app.tools

import com.ancode.app.linux.ProotRunner
import kotlinx.serialization.json.JsonObject

/**
 * Runs shell commands inside the proot'd Ubuntu environment.
 * Mirrors OpenCode/Claude Code's bash tool semantics: cwd + timeout + truncated output.
 */
class TerminalTool(private val runner: ProotRunner) : Tool {

    override val name = "terminal"
    override val description =
        "在 Ubuntu (proot) 环境中执行 shell 命令。可运行编译、测试、git、npm/pip 等任意命令。输出最多 30KB，超时会返回提示。当前工作目录用 cwd 指定。"

    override val parametersSpec: Map<String, JsonObject> = mapOf(
        "command" to Schema.string("要执行的 shell 命令，例如 'ls -la' 或 'python3 test.py'"),
        "cwd" to Schema.string("命令执行的工作目录（guest 内路径），默认 /root/projects"),
        "timeout_ms" to Schema.integer("超时毫秒数，默认 120000")
    )

    override val requiredParams = listOf("command")

    override suspend fun execute(args: Map<String, Any?>): String {
        val command = args["command"]?.toString() ?: return "错误：缺少 command 参数"
        val cwd = args["cwd"]?.toString()?.takeIf { it.isNotBlank() } ?: "/root/projects"
        val timeout = (args["timeout_ms"] as? Number)?.toLong() ?: 120_000L
        val res = runner.execute(command, cwd = cwd, timeoutMs = timeout)
        val exit = if (res.timedOut) "TIMEOUT(>${timeout}ms)" else "exit=${res.exitCode}"
        return "[$exit]\n${res.output}"
    }
}