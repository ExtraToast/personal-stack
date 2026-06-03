package com.jorisjonkers.personalstack.agentgateway.tmux

import org.slf4j.LoggerFactory
import java.io.RandomAccessFile
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Polls an append-only log file every `intervalMs` and streams any new
 * output as bounded text chunks. tmux's pipe-pane writes raw pane output
 * here, so this is the streaming-from-PTY mechanism without needing a
 * fifo or a JNI tmux library.
 *
 * The output is relayed straight through to the browser, never buffered
 * whole, so the terminal stream costs no standing heap. Two details make
 * that safe:
 *
 *  - **Bounded frames.** Each emitted chunk is at most [maxChunkChars]
 *    characters, so a JSON-wrapped output frame stays well under the
 *    default 8 KiB WebSocket buffer. A noisy agent that prints megabytes
 *    streams as many small frames rather than one frame that would force
 *    a multi-megabyte receive buffer per session.
 *  - **UTF-8 boundary carry.** A read can end midway through a multi-byte
 *    codepoint (the box-drawing glyphs a TUI emits are three bytes); the
 *    trailing partial bytes are held back and prepended to the next read
 *    so a character is never decoded — or split across frames — in halves.
 *
 * Single-tailer-per-file model: each WS attach gets its own tailer
 * starting at the current end of file, so a freshly-attached client
 * streams only new bytes. The whole-screen snapshot that gives the client
 * its initial state is sent separately on attach. If the session's log is
 * truncated to stay under its disk cap the tailer restarts from the new
 * beginning rather than stalling.
 */
class LogTailer(
    private val file: Path,
    private val intervalMs: Long = 40,
    private val maxChunkChars: Int = MAX_CHUNK_CHARS,
    private val onText: (String) -> Unit,
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(LogTailer::class.java)
    private val offset = AtomicLong(0)

    // Trailing bytes of an incomplete UTF-8 sequence held back from the
    // previous read until the rest of the codepoint arrives.
    private var carry = ByteArray(0)

    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "log-tailer-${file.fileName}").apply { isDaemon = true }
        }

    fun start() {
        offset.set(currentLength())
        executor.scheduleWithFixedDelay(::poll, 0, intervalMs, TimeUnit.MILLISECONDS)
    }

    private fun currentLength(): Long =
        try {
            RandomAccessFile(file.toFile(), "r").use { it.length() }
        } catch (e: java.io.IOException) {
            log.warn("sizing {} failed: {}", file, e.message)
            0L
        }

    private fun poll() {
        try {
            RandomAccessFile(file.toFile(), "r").use { raf ->
                val length = raf.length()
                var currentOffset = offset.get()
                if (length < currentOffset) {
                    // The log was truncated to stay under its disk cap;
                    // restart from the new beginning.
                    offset.set(0)
                    carry = ByteArray(0)
                    currentOffset = 0
                }
                if (length <= currentOffset) return
                raf.seek(currentOffset)
                val toRead = (length - currentOffset).coerceAtMost(MAX_READ_BYTES.toLong()).toInt()
                val raw = ByteArray(toRead)
                val read = raf.read(raw)
                if (read <= 0) return
                offset.addAndGet(read.toLong())
                emit(raw, read)
            }
        } catch (e: java.io.IOException) {
            log.warn("tail of {} failed: {}", file, e.message)
        }
    }

    private fun emit(
        raw: ByteArray,
        read: Int,
    ) {
        val buf = if (carry.isEmpty()) raw.copyOf(read) else carry + raw.copyOf(read)
        val complete = completeUtf8Length(buf)
        carry = if (complete < buf.size) buf.copyOfRange(complete, buf.size) else EMPTY
        if (complete == 0) return
        chunked(String(buf, 0, complete, Charsets.UTF_8), maxChunkChars, onText)
    }

    override fun close() {
        executor.shutdownNow()
    }

    companion object {
        const val MAX_CHUNK_CHARS = 1024
        private const val MAX_READ_BYTES = 64 * 1024
        private val EMPTY = ByteArray(0)

        /**
         * Index of the first byte of an incomplete trailing UTF-8
         * sequence, or `buf.size` if the buffer ends on a complete
         * codepoint. Malformed lead bytes are treated as complete so the
         * decoder substitutes them rather than the carry growing forever.
         */
        internal fun completeUtf8Length(buf: ByteArray): Int {
            if (buf.isEmpty()) return 0
            var i = buf.size - 1
            var continuations = 0
            while (i >= 0 && (buf[i].toInt() and 0xC0) == 0x80) {
                i--
                continuations++
            }
            if (i < 0) return buf.size
            val lead = buf[i].toInt() and 0xFF
            val expected =
                when {
                    lead < 0x80 -> 1
                    lead in 0xC0..0xDF -> 2
                    lead in 0xE0..0xEF -> 3
                    lead in 0xF0..0xF7 -> 4
                    else -> 1
                }
            return if (continuations + 1 >= expected) buf.size else i
        }

        /**
         * Feeds [text] to [action] in pieces of at most [maxChars],
         * never splitting a surrogate pair across two pieces.
         */
        internal fun chunked(
            text: String,
            maxChars: Int,
            action: (String) -> Unit,
        ) {
            var i = 0
            while (i < text.length) {
                var end = (i + maxChars).coerceAtMost(text.length)
                if (end < text.length && Character.isHighSurrogate(text[end - 1])) end--
                action(text.substring(i, end))
                i = end
            }
        }
    }
}
