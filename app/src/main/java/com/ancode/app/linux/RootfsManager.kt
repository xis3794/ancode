package com.ancode.app.linux

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Downloads, verifies and extracts the Ubuntu base rootfs
 * (ubuntu-base-24.04.3-base-arm64.tar.gz) into the app files dir.
 */
class RootfsManager(private val context: Context) {

    enum class Status { NOT_INSTALLED, DOWNLOADING, EXTRACTING, READY, ERROR }

    data class State(
        val status: Status = Status.NOT_INSTALLED,
        val progress: Float = 0f,          // 0..1
        val message: String = "",
        val rootfsDir: File? = null,
        val error: String? = null
    )

    companion object {
        const val UBUNTU_VERSION = "24.04.3"
        const val ROOTFS_FILENAME = "ubuntu-base-24.04.3-base-arm64.tar.gz"
        const val ROOTFS_SHA256 = "7b2dced6dd56ad5e4a813fa25c8de307b655fdabc6ea9213175a92c48dabb048"

        /**
         * Download mirrors, best first. (2026-08 verified: cdimage + aliyun OK,
         * tuna/ustc return 403, sjtu/netease 404 — removed.)
         * Both the "24.04" and "24.04.3" release dirs are tried since mirrors
         * differ in layout.
         */
        val MIRRORS = listOf(
            "https://mirrors.aliyun.com/ubuntu-cdimage/ubuntu-base/releases/24.04.3/release/$ROOTFS_FILENAME",
            "https://mirrors.aliyun.com/ubuntu-cdimage/ubuntu-base/releases/24.04/release/$ROOTFS_FILENAME",
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04.3/release/$ROOTFS_FILENAME",
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/$ROOTFS_FILENAME"
        )

        /** Optional manual import path checked before any network download. */
        val MANUAL_IMPORT_PATHS = listOf(
            "/sdcard/Download/$ROOTFS_FILENAME",
            "/sdcard/Download/ubuntu-base-24.04.3-base-arm64.tar.gz",
            "/storage/emulated/0/Download/$ROOTFS_FILENAME"
        )
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    val linuxDir: File = File(context.filesDir, "linux")
    val rootfsDir: File = File(linuxDir, "rootfs")
    val prootBin: File = File(linuxDir, "bin/proot")
    val prootLibDir: File = File(linuxDir, "lib")
    val prootLoaderDir: File = File(linuxDir, "libexec/proot")

    /** Current session workspace id (each session owns a workspace). */
    @Volatile
    var currentWorkspace: String = "default"
        private set

    /** Host dir for a workspace id (guest /root/projects is bind-mounted to this). */
    fun workspaceHostDir(wsId: String = currentWorkspace): File =
        File(context.filesDir, "workspaces/$wsId")

    fun setCurrentWorkspace(wsId: String) {
        currentWorkspace = wsId
        runCatching { workspaceHostDir().mkdirs() }
    }

    private val installing = AtomicBoolean(false)

    fun currentStatus(): Status = when {
        isReady() -> Status.READY
        else -> _state.value.status
    }

    /**
     * Map a guest path onto the host filesystem.
     *  - /root/projects (workspace) → /sdcard/Ancode/projects (bind-mounted)
     *  - /root/... (home) → rootfs/root/...
     *  - other /... → rootfs/...
     *  - /sdcard, /storage → null (they already exist on the host; tools
     *    should use the terminal for them, since file access via Java File
     *    requires storage permission).
     */
    fun guestToHost(guestPath: String): File? {
        val p = guestPath.trim()
        return when {
            p.startsWith("/sdcard") || p.startsWith("/storage") -> null
            p == "/root/projects" || p.startsWith("/root/projects/") -> {
                val rel = p.removePrefix("/root/projects").trimStart('/')
                File(workspaceHostDir(), rel)
            }
            p.startsWith("/") -> File(rootfsDir, p.removePrefix("/"))
            else -> File(rootfsDir, p)
        }
    }

    /** Run a command inside the guest via ProotRunner (used by glob/grep tools). */
    suspend fun runViaProot(command: String, cwd: String = "/root/projects"): String {
        val runner = ProotRunner(this)
        val res = runner.execute(command, cwd = cwd, timeoutMs = 60_000, maxOutputChars = 20_000)
        return if (res.exitCode == 0) res.output else "(exit=${res.exitCode}) ${res.output}"
    }

    fun isReady(): Boolean =
        File(rootfsDir, "etc/os-release").exists() && prootBin.exists()

    /** Copy bundled proot binaries from assets into files dir (idempotent). */
    suspend fun ensureProotBinaries(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val assetManager = context.assets
            val entries = listOf(
                "linux/bin/proot" to prootBin,
                "linux/bin/termux-chroot" to File(linuxDir, "bin/termux-chroot"),
                "linux/lib/libtalloc.so.2" to File(prootLibDir, "libtalloc.so.2"),
                "linux/lib/libandroid-shmem.so" to File(prootLibDir, "libandroid-shmem.so"),
                "linux/libexec/proot/loader" to File(prootLoaderDir, "loader"),
                "linux/libexec/proot/loader32" to File(prootLoaderDir, "loader32")
            )
            entries.forEach { (assetPath, dest) ->
                if (!dest.exists() || dest.length() == 0L) {
                    dest.parentFile?.mkdirs()
                    assetManager.open(assetPath).use { input ->
                        FileOutputStream(dest).use { output -> input.copyTo(output) }
                    }
                }
                dest.setExecutable(true, false)
                dest.setReadable(true, false)
            }
            true
        }.getOrDefault(false)
    }

    /** Download + verify + extract. [onProgress] receives 0..1. */
    suspend fun install(onProgress: (Float) -> Unit = {}): Boolean {
        if (installing.compareAndSet(false, true)) {
            return try {
                // ALL network / disk IO runs on Dispatchers.IO; the UI thread
                // only receives state updates via StateFlow (thread-safe).
                // Any exception is caught and surfaced as an error state
                // instead of crashing the app.
                withContext(Dispatchers.IO) {
                    doInstall(onProgress)
                }
            } catch (e: Exception) {
                android.util.Log.e("RootfsManager", "install failed", e)
                _state.value = State(Status.ERROR, 0f, error = "安装异常：${e.message ?: e.javaClass.simpleName}")
                false
            } finally {
                installing.set(false)
            }
        }
        return false
    }

    private suspend fun doInstall(onProgress: (Float) -> Unit): Boolean {
        if (isReady()) {
            _state.value = State(Status.READY, 1f, "已就绪", rootfsDir)
            return true
        }
        if (!ensureProotBinaries()) {
            _state.value = State(Status.ERROR, 0f, error = "proot 二进制部署失败")
            return false
        }

        linuxDir.mkdirs()
        val tarFile = File(linuxDir, ROOTFS_FILENAME)

        // 1. Obtain tarball: manual import (if user placed one in Download) OR download
        if (tarFile.exists() && !sha256(tarFile).equals(ROOTFS_SHA256, ignoreCase = true)) {
            tarFile.delete()
        }
        if (!tarFile.exists()) {
            // 1a. manual import from a well-known location (user downloaded it themselves)
            val imported = withContext(Dispatchers.IO) { tryImportFromDownload() }
            if (imported != null) {
                imported.copyTo(tarFile, overwrite = true)
                imported.delete()
                _state.value = State(Status.DOWNLOADING, 0.9f, "已导入本地 rootfs 文件，校验中...")
            } else {
                // 1b. network download from mirrors
                _state.value = State(Status.DOWNLOADING, 0f, "正在下载 Ubuntu base $UBUNTU_VERSION...")
                val errors = StringBuilder()
                var downloaded = false
                for (mirror in MIRRORS) {
                    val reason = downloadWithProgress(mirror, tarFile) { p -> onProgress(p * 0.85f) }
                    if (reason == null) { downloaded = true; break }
                    errors.append("\n• ").append(mirror).append(" → ").append(reason)
                }
                if (!downloaded) {
                    _state.value = State(
                        Status.ERROR, 0f,
                        error = "rootfs 下载失败（所有镜像不可用）：$errors\n\n" +
                            "请手动下载 $ROOTFS_FILENAME（约28MB）放到 /sdcard/Download/ 后重新安装：" +
                            "\nhttps://mirrors.aliyun.com/ubuntu-cdimage/ubuntu-base/releases/24.04.3/release/"
                    )
                    return false
                }
            }
        }

        // 2. Verify
        val actual = sha256(tarFile)
        if (!actual.equals(ROOTFS_SHA256, ignoreCase = true)) {
            tarFile.delete()
            _state.value = State(Status.ERROR, 0f, error = "SHA256 校验失败: $actual")
            return false
        }

        // 3. Extract
        _state.value = State(Status.EXTRACTING, 0.9f, "正在解压 rootfs...")
        val ok = withContext(Dispatchers.IO) {
            extractTarGz(tarFile, rootfsDir)
        }
        if (!ok) {
            _state.value = State(Status.ERROR, 0f, error = "rootfs 解压失败")
            return false
        }
        tarFile.delete()

        // 4. Post-install fixes
        postInstall()

        _state.value = State(Status.READY, 1f, "Ubuntu $UBUNTU_VERSION 就绪", rootfsDir)
        onProgress(1f)
        return true
    }

    /** Look for a manually downloaded tarball in well-known public locations.
     *  Guarded: scoped storage may throw EACCES / return false without permission. */
    private fun tryImportFromDownload(): File? {
        return runCatching {
            for (p in MANUAL_IMPORT_PATHS) {
                val f = File(p)
                if (f.exists() && f.canRead() && f.length() > 1_000_000) {
                    // probe readability — exists() can return true while open() fails
                    f.inputStream().use { it.read() }
                    return@runCatching f
                }
            }
            null
        }.getOrNull()
    }

    /**
     * Import a rootfs tarball selected via the system file picker (SAF —
     * no storage permission needed). Copies into the app files dir, then the
     * normal verify/extract flow continues. Returns null on success, or an
     * error message on failure.
     */
    suspend fun importFromUri(uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        try {
            if (!ensureProotBinaries()) return@withContext "proot 二进制部署失败"
            linuxDir.mkdirs()
            val tarFile = File(linuxDir, ROOTFS_FILENAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tarFile).use { output -> input.copyTo(output) }
            } ?: return@withContext "无法读取所选文件"
            _state.value = State(Status.EXTRACTING, 0.9f, "校验并解压中...")

            val actual = sha256(tarFile)
            if (!actual.equals(ROOTFS_SHA256, ignoreCase = true)) {
                tarFile.delete()
                return@withContext "SHA256 校验失败：所选文件不是官方 ubuntu-base-24.04.3-arm64\n期望 $ROOTFS_SHA256\n实际 $actual"
            }
            val ok = extractTarGz(tarFile, rootfsDir)
            if (!ok) {
                return@withContext "解压失败（文件可能损坏）"
            }
            tarFile.delete()
            postInstall()
            _state.value = State(Status.READY, 1f, "Ubuntu $UBUNTU_VERSION 就绪", rootfsDir)
            null
        } catch (e: Exception) {
            android.util.Log.e("RootfsManager", "importFromUri failed", e)
            "导入异常：${e.message ?: e.javaClass.simpleName}"
        }
    }

    /**
     * Download [url] to [dest]. Returns null on success, or a human-readable
     * failure reason on error (HTTP code / exception message).
     */
    private fun downloadWithProgress(url: String, dest: File, onProgress: (Float) -> Unit): String? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Ancode/0.1")
                .header("Accept", "*/*")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return "HTTP ${response.code}"
                }
                val body = response.body ?: return "空响应"
                val total = body.contentLength()
                val input = body.byteStream()
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total.toFloat()) * 0.85f)
                    }
                }
            }
            null
        } catch (e: Exception) {
            dest.delete()
            e.message ?: e.javaClass.simpleName
        }
    }

    /**
     * Extract the rootfs tarball with a pure-Java tar.gz reader.
     * (The previous proot-based extraction was fundamentally broken: it tried to
     * run /bin/sh inside an *empty* rootfs, which cannot work. Java extraction
     * handles GNU tar entries incl. symlinks and long names, and is fast enough
     * for the 28MB tarball.)
     */
    private fun extractTarGz(tar: File, destDir: File): Boolean {
        return try {
            destDir.mkdirs()
            val symlinks = mutableListOf<Pair<File, String>>()
            var count = 0
            java.util.zip.GZIPInputStream(tar.inputStream()).use { gz ->
                var pendingName: String? = null
                var pendingLink: String? = null
                while (true) {
                    val header = ByteArray(512)
                    var off = 0
                    while (off < 512) {
                        val n = gz.read(header, off, 512 - off)
                        if (n < 0) break
                        off += n
                    }
                    if (off < 512) break // EOF (padding zeros)
                    if (header.all { it == 0.toByte() }) {
                        // skip second zero block then stop
                        skipFully(gz, 512)
                        break
                    }
                    val name = parseTarString(header, 0, 100)
                    val type = header[156].toInt().toChar()
                    val size = parseOctal(header, 124, 12)
                    val linkName = parseTarString(header, 157, 100)
                    val prefix = parseTarString(header, 345, 155)
                    val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name

                    // GNU long name extension
                    if (type == 'L') {
                        pendingName = readLongName(gz, size)
                        skipFully(gz, blockAlign(size))
                        continue
                    }
                    if (type == 'K') {
                        pendingLink = readLongName(gz, size)
                        skipFully(gz, blockAlign(size))
                        continue
                    }

                    val targetName = pendingName ?: fullName
                    pendingName = null
                    val target = File(destDir, targetName)
                    val dataLen = size

                    when (type) {
                        '5' -> { target.mkdirs() }
                        '2' -> {
                            val linkTarget = pendingLink ?: linkName
                            pendingLink = null
                            symlinks.add(target to linkTarget)
                        }
                        '0', '\u0000', '7' -> {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { out ->
                                var remaining = dataLen
                                val chunk = ByteArray(64 * 1024)
                                while (remaining > 0) {
                                    val r = gz.read(chunk, 0, minOf(chunk.size.toLong(), remaining).toInt())
                                    if (r < 0) break
                                    out.write(chunk, 0, r)
                                    remaining -= r
                                }
                            }
                            count++
                        }
                        else -> { /* ignore other types */ }
                    }
                    skipFully(gz, blockAlign(dataLen))
                }
            }
            // create symlinks after all real files (order-independent)
            symlinks.forEach { (target, linkTarget) ->
                target.parentFile?.mkdirs()
                if (target.exists()) target.delete()
                try {
                    java.nio.file.Files.createSymbolicLink(target.toPath(), java.nio.file.Paths.get(linkTarget))
                } catch (e: Exception) {
                    android.util.Log.w("RootfsManager", "symlink skip $target -> $linkTarget")
                }
            }
            android.util.Log.i("RootfsManager", "extracted $count files")
            true
        } catch (e: Exception) {
            android.util.Log.e("RootfsManager", "extract exception", e)
            false
        }
    }

    private fun parseTarString(h: ByteArray, start: Int, len: Int): String {
        var end = start
        while (end < start + len && h[end] != 0.toByte()) end++
        return String(h, start, end - start, Charsets.UTF_8)
    }

    private fun parseOctal(h: ByteArray, start: Int, len: Int): Long {
        var v = 0L
        for (i in start until start + len) {
            val c = h[i].toInt()
            if (c == 0 || c == ' '.code) continue
            if (c < '0'.code || c > '7'.code) break
            v = v * 8 + (c - '0'.code)
        }
        return v
    }

    private fun readLongName(gz: java.util.zip.GZIPInputStream, size: Long): String {
        val bytes = ByteArray(size.toInt().coerceAtMost(1 shl 20))
        var off = 0
        while (off < bytes.size) {
            val n = gz.read(bytes, off, bytes.size - off)
            if (n < 0) break
            off += n
        }
        return String(bytes, 0, off, Charsets.UTF_8).trimEnd('\u0000')
    }

    private fun blockAlign(size: Long): Long = (size + 511) / 512 * 512

    /** Skip exactly [n] bytes (InputStream.skip may skip less). */
    private fun skipFully(input: java.io.InputStream, n: Long) {
        var remaining = n
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (r < 0) return
            remaining -= r
        }
    }

    /** Fix symlinks & mark critical binaries executable. */
    private fun postInstall() {
        runCatching {
            // ubuntu-base ships /bin -> usr/bin etc. Ensure exec bits for common tools
            listOf("bin/sh", "usr/bin/env", "usr/bin/bash", "usr/bin/tar", "usr/bin/uname")
                .forEach { rel ->
                    val f = File(rootfsDir, rel)
                    if (f.exists()) f.setExecutable(true, false)
                }
        }
    }

    private fun sha256(file: File): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    md.update(buf, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}