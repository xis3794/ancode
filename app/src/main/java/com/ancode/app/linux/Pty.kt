package com.ancode.app.linux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * JNI bridge to the native PTY shim (app/src/main/cpp/pty_shim.c).
 * Spawns a child process (e.g. proot + bash) attached to a real pseudo-terminal
 * and streams output bytes to a SharedFlow.
 */
object Pty {
    init {
        System.loadLibrary("pty_shim")
    }

    private external fun nativeSpawn(cmd: Array<String>): LongArray?
    private external fun nativeRead(fd: Int, buf: ByteArray): Int
    private external fun nativeWrite(fd: Int, data: ByteArray, off: Int, len: Int): Int
    private external fun nativeResize(fd: Int, rows: Int, cols: Int)
    private external fun nativeClose(fd: Int)

    class Session(
        val fd: Int,
        val pid: Long,
        private val onExit: () -> Unit = {}
    ) {
        private val closed = AtomicBoolean(false)
        private val _output = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
        val output: SharedFlow<ByteArray> = _output
        private var reader: Thread? = null

        fun startReading() {
            if (reader != null) return
            reader = Thread {
                val buf = ByteArray(8192)
                while (!closed.get()) {
                    val n = nativeRead(fd, buf)
                    if (n > 0) {
                        _output.tryEmit(buf.copyOf(n))
                    } else if (n < 0) {
                        break
                    } else {
                        // n == 0: EOF
                        break
                    }
                }
                close()
                onExit()
            }.apply { isDaemon = true; start() }
        }

        fun write(text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            write(bytes)
        }

        fun write(bytes: ByteArray) {
            if (closed.get()) return
            var off = 0
            while (off < bytes.size) {
                val n = nativeWrite(fd, bytes, off, bytes.size - off)
                if (n <= 0) break
                off += n
            }
        }

        fun resize(rows: Int, cols: Int) {
            if (closed.get()) return
            nativeResize(fd, rows, cols)
        }

        fun isClosed(): Boolean = closed.get()

        fun close() {
            if (closed.compareAndSet(false, true)) {
                nativeClose(fd)
            }
        }
    }

    /** Spawn [cmd] in a new PTY. Must be called from a background thread. */
    suspend fun spawn(cmd: List<String>): Session = withContext(Dispatchers.IO) {
        val arr = nativeSpawn(cmd.toTypedArray())
            ?: throw IllegalStateException("PTY spawn failed (fd/pid null)")
        Session(arr[0].toInt(), arr[1]).also { it.startReading() }
    }
}