package com.ancode.app.linux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs commands inside the proot'd Ubuntu environment.
 *
 * Two entry points:
 *  - [execute]  : one-shot `bash -c` execution for the agent's TerminalTool
 *  - [buildCommand] : full proot argv for an interactive PTY shell
 */
class ProotRunner(private val rootfs: RootfsManager) {

    data class ExecResult(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean = false
    )

    /**
     * Base proot invocation. Bind-mounts /sdcard, /proc, /dev, /sys and the
     * project dir. LD_LIBRARY_PATH covers the bundled proot .so deps.
     */
    fun buildCommand(
        workDir: String = "/root",
        extraArgs: List<String> = emptyList()
    ): List<String> {
        val cmd = mutableListOf(
            rootfs.prootBin.absolutePath,
            "-0",                       // fake root
            "-r", rootfs.rootfsDir.absolutePath,
            "-b", "/proc",
            "-b", "/dev",
            "-b", "/sys",
            "-b", "/sdcard:/sdcard",
            "-b", "${android.os.Environment.getExternalStorageDirectory().absolutePath}:/sdcard",
            "-w", workDir
        )
        cmd.addAll(extraArgs)
        return cmd
    }

    /** Environment for the proot process: point dynamic linker at bundled libs. */
    private fun prootEnv(): Map<String, String> = mapOf(
        "LD_LIBRARY_PATH" to rootfs.prootLibDir.absolutePath,
        // override proot's compiled-in loader paths (Termux defaults don't exist here)
        "PROOT_LOADER" to File(rootfs.prootLoaderDir, "loader").absolutePath,
        "PROOT_LOADER32" to File(rootfs.prootLoaderDir, "loader32").absolutePath,
        "TERM" to "xterm-256color",
        "HOME" to "/root",
        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "LANG" to "C.UTF-8",
        "LC_ALL" to "C.UTF-8"
    )

    /**
     * Execute a shell command in the guest. [cwd] is a guest-relative path
     * (e.g. "/root/projects"). Output is truncated to [maxOutputChars].
     */
    suspend fun execute(
        command: String,
        cwd: String = "/root",
        timeoutMs: Long = 120_000,
        maxOutputChars: Int = 30_000
    ): ExecResult = withContext(Dispatchers.IO) {
        val full = buildCommand(cwd, listOf("/bin/bash", "-lc", command))
        val pb = ProcessBuilder(full)
        pb.environment().putAll(prootEnv())
        pb.redirectErrorStream(true)
        val proc = pb.start()

        val output = StringBuilder()
        val reader = Thread {
            proc.inputStream.bufferedReader().use { r ->
                val buf = CharArray(4096)
                var n: Int
                while (r.read(buf).also { n = it } != -1) {
                    output.append(buf, 0, n)
                    if (output.length > maxOutputChars) {
                        // stop reading further; process will be destroyed on timeout
                        break
                    }
                }
            }
        }.apply { isDaemon = true; start() }

        val finished = proc.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) {
            proc.destroy()
            proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            proc.destroyForcibly()
            return@withContext ExecResult(-1, truncate(output.toString(), maxOutputChars), timedOut = true)
        }
        reader.join(2000)
        val text = output.toString()
        ExecResult(proc.exitValue(), truncate(text, maxOutputChars))
    }

    /** Run a quick sanity probe: `uname -a` + `/etc/os-release`. */
    suspend fun probe(): String = withContext(Dispatchers.IO) {
        val res = execute("uname -a && echo '---' && cat /etc/os-release | head -3", timeoutMs = 60_000, maxOutputChars = 4096)
        if (res.exitCode == 0) res.output else "PROBE FAILED (exit=${res.exitCode}): ${res.output}"
    }

    /** Create the default project workspace directory inside the guest. */
    suspend fun ensureProjectsDir(): Boolean = withContext(Dispatchers.IO) {
        val res = execute("mkdir -p /root/projects && chmod 755 /root/projects", timeoutMs = 30_000)
        res.exitCode == 0
    }

    private fun truncate(s: String, max: Int): String =
        if (s.length > max) s.take(max) + "\n... [truncated ${s.length - max} chars]" else s
}