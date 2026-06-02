package com.jorisjonkers.personalstack.agentgateway.tmux

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

class LogTailerTest {
    @Test
    fun `tailer emits bytes appended to the file`(
        @TempDir tmp: Path,
    ) {
        val file = tmp.resolve("agent.log")
        Files.createFile(file)
        val received = CopyOnWriteArrayList<String>()
        LogTailer(file, intervalMs = 50) { received.add(String(it)) }.use { tailer ->
            tailer.start()
            Files.writeString(file, "hello ", StandardOpenOption.APPEND)
            Files.writeString(file, "world\n", StandardOpenOption.APPEND)
            await().atMost(Duration.ofSeconds(2)).until {
                received.joinToString("").contains("hello world")
            }
            assertThat(received.joinToString("")).contains("hello world")
        }
    }

    @Test
    fun `tailer starts at EOF and ignores bytes written before start`(
        @TempDir tmp: Path,
    ) {
        val file = tmp.resolve("agent.log")
        Files.writeString(file, "OLD HISTORY THAT MUST NOT REPLAY\n")
        val received = CopyOnWriteArrayList<String>()
        LogTailer(file, intervalMs = 50) { received.add(String(it)) }.use { tailer ->
            tailer.start()
            Files.writeString(file, "fresh bytes\n", StandardOpenOption.APPEND)
            await().atMost(Duration.ofSeconds(2)).until {
                received.joinToString("").contains("fresh bytes")
            }
            assertThat(received.joinToString("")).contains("fresh bytes")
            assertThat(received.joinToString("")).doesNotContain("OLD HISTORY")
        }
    }
}
