package com.jorisjonkers.personalstack.platform

import java.nio.file.Path
import kotlin.io.path.exists

class RepositoryRootLocator {
    fun locate(start: Path = Path.of("").toAbsolutePath()): Path {
        var candidate = start.normalize()
        while (true) {
            if (candidate.resolve("settings.gradle.kts").exists()) {
                return candidate
            }
            val parent = candidate.parent ?: break
            candidate = parent
        }
        error("Could not locate repository root from $start")
    }
}
