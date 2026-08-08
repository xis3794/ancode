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

    companion object {
        /** Guest-side workspace path (inside the proot guest). */
        const val WORKSPACE_GUEST = "/root/projects"
    }

    /**
     * Base proot invocation. Bind-mounts /sdcard, /proc, /dev, /sys and the
     * project workspace (host files/projects → guest /root/projects).
     */
    fun buildCommand(
        workDir: String = "/root",
        extraArgs: List<String> = emptyList()
    ): List<String> {
        // ensure host workspace exists before bind
        runCatching {
            rootfs.workspaceHostDir().mkdirs()
        }
        val cmd = mutableListOf(
            rootfs.prootBin.absolutePath,
            "-0",                       // fake root
            "-r", rootfs.rootfsDir.absolutePath,
            "-b", "/proc",
            "-b", "/dev",
            "-b", "/sys",
            "-b", "/sdcard:/sdcard",
            "-b", "${android.os.Environment.getExternalStorageDirectory().absolutePath}:/sdcard",
            "-b", "${rootfs.workspaceHostDir().absolutePath}:$WORKSPACE_GUEST",
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
        "LC_ALL" to "C.UTF-8",
        // colorful prompt & tools (highlighting)
        "PS1" to "\\[\\e[1;32m\\]ubuntu@ancode\\[\\e[0m\\]:\\[\\e[1;34m\\]\\w\\[\\e[0m\\]\\$ ",
        "LS_COLORS" to "di=1;34:ln=1;36:ex=1;32:*.tar=1;31:*.zip=1;31:*.gz=1;31",
        "GREP_COLORS" to "mt=1;31"
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
        // ensure workspace + helpers exist before running anything
        prepareGuest()
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
        prepareGuest()
        true
    }

    /**
     * One-time guest setup: create the workspace dir and write a small
     * /root/.bashrc enabling colorized ls/grep output (terminal highlighting).
     * Cheap to run on every execute (idempotent).
     */
    private fun prepareGuest() {
        runCatching {
            // workspace dir (host files/workspaces/<id> is bind-mounted here)
            runCatching { rootfs.workspaceHostDir().mkdirs() }

            // colorized defaults for interactive & non-interactive shells
            val bashrc = File(rootfs.rootfsDir, "root/.bashrc")
            if (!bashrc.exists()) {
                bashrc.parentFile?.mkdirs()
                bashrc.writeText(
                    """
                    |# Ancode defaults
                    |export PS1='\[\e[1;32m\]ubuntu@ancode\[\e[0m\]:\[\e[1;34m\]\w\[\e[0m\]\$ '
                    |alias ls='ls --color=auto'
                    |alias ll='ls -la --color=auto'
                    |alias grep='grep --color=auto'
                    |alias egrep='egrep --color=auto'
                    |export LS_COLORS='di=1;34:ln=1;36:ex=1;32:*.tar=1;31:*.zip=1;31:*.gz=1;31'
                    |export GREP_COLORS='mt=1;31'
                    |cd /root/projects 2>/dev/null || true
                    """.trimMargin()
                )
            }
        }
    }

    private fun truncate(s: String, max: Int): String =
        if (s.length > max) s.take(max) + "\n... [truncated ${s.length - max} chars]" else s
}