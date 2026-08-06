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
        val MIRRORS = listOf(
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/$ROOTFS_FILENAME",
            "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/releases/24.04/release/$ROOTFS_FILENAME",
            "https://mirrors.aliyun.com/ubuntu-cdimage/ubuntu-base/releases/24.04/release/$ROOTFS_FILENAME"
        )
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    val linuxDir: File = File(context.filesDir, "linux")
    val rootfsDir: File = File(linuxDir, "rootfs")
    val prootBin: File = File(linuxDir, "bin/proot")
    val prootLibDir: File = File(linuxDir, "lib")
    val prootLoaderDir: File = File(linuxDir, "libexec/proot")

    private val installing = AtomicBoolean(false)

    fun currentStatus(): Status = when {
        isReady() -> Status.READY
        else -> _state.value.status
    }

    /** Map a guest path (/root/...) onto the host rootfs path. Null for /sdcard (handled via proot). */
    fun guestToHost(guestPath: String): File? {
        val p = guestPath.trim()
        return when {
            p.startsWith("/sdcard") || p.startsWith("/storage") -> null
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
                doInstall(onProgress)
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

        // 1. Download (skip if already present & verified)
        if (tarFile.exists() && !sha256(tarFile).equals(ROOTFS_SHA256, ignoreCase = true)) {
            tarFile.delete()
        }
        if (!tarFile.exists()) {
            _state.value = State(Status.DOWNLOADING, 0f, "正在下载 Ubuntu base $UBUNTU_VERSION...")
            var downloaded = false
            for (mirror in MIRRORS) {
                downloaded = downloadWithProgress(mirror, tarFile) { p -> onProgress(p * 0.85f) }
                if (downloaded) break
            }
            if (!downloaded) {
                _state.value = State(Status.ERROR, 0f, error = "rootfs 下载失败（所有镜像不可用）")
                return false
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

    private fun downloadWithProgress(url: String, dest: File, onProgress: (Float) -> Unit): Boolean {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
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
            true
        } catch (e: Exception) {
            dest.delete()
            false
        }
    }

    /**
     * Extract the rootfs tarball by running tar *inside* the proot environment.
     * This preserves symlinks/ownership correctly and avoids a pure-Java tar
     * implementation. The tarball is bind-mounted into the guest as
     * /host-rootfs.tar.gz.
     */
    private fun extractTarGz(tar: File, destDir: File): Boolean {
        return try {
            destDir.mkdirs()
            val cmd = listOf(
                prootBin.absolutePath,
                "-0",
                "-r", rootfsDir.absolutePath,
                "-b", "${tar.absolutePath}:/host-rootfs.tar.gz",
                "-b", "/proc",
                "-b", "/dev",
                "-w", "/",
                "/bin/sh", "-c",
                "tar xzf /host-rootfs.tar.gz -C / && " +
                    "chmod 755 / /root /tmp /var /var/tmp /etc /bin /usr /usr/bin /usr/sbin 2>/dev/null; true"
            )
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().readText()
            val exit = proc.waitFor()
            if (exit != 0) {
                android.util.Log.e("RootfsManager", "extract failed: $output")
            }
            exit == 0
        } catch (e: Exception) {
            android.util.Log.e("RootfsManager", "extract exception", e)
            false
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