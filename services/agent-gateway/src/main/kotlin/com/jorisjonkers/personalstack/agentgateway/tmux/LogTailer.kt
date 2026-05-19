package com.jorisjonkers.personalstack.agentgateway.tmux

import org.slf4j.LoggerFactory
import java.io.RandomAccessFile
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Polls an append-only log file every `intervalMs` and emits any new
 * bytes via the consumer. tmux's pipe-pane writes raw pane output
 * here, so this is the streaming-from-PTY mechanism without needing
 * a fifo or a JNI tmux library.
 *
 * Single-tailer-per-file model: each WS attach gets its own tailer
 * starting at byte 0, so a freshly-attached client sees full history
 * + live output without coordinating offsets with anyone else.
 */
class LogTailer(
    private val file: Path,
    private val intervalMs: Long = 250,
    private val onBytes: (ByteArray) -> Unit,
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(LogTailer::class.java)
    private val offset = AtomicLong(0)
    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "log-tailer-${file.fileName}").apply { isDaemon = true }
        }

    fun start() {
        executor.scheduleWithFixedDelay(::poll, 0, intervalMs, TimeUnit.MILLISECONDS)
    }

    private fun poll() {
        try {
            RandomAccessFile(file.toFile(), "r").use { raf ->
                val currentOffset = offset.get()
                if (raf.length() <= currentOffset) return
                raf.seek(currentOffset)
                val toRead = raf.length() - currentOffset
                val bytes = ByteArray(toRead.toInt().coerceAtMost(64 * 1024))
                val read = raf.read(bytes)
                if (read > 0) {
                    onBytes(bytes.copyOf(read))
                    offset.addAndGet(read.toLong())
                }
            }
        } catch (e: java.io.IOException) {
            log.warn("tail of {} failed: {}", file, e.message)
        }
    }

    override fun close() {
        executor.shutdownNow()
    }
}
