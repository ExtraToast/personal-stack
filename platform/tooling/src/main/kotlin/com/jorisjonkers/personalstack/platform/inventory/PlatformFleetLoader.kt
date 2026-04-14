package com.jorisjonkers.personalstack.platform.inventory

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.nio.file.Path
import kotlin.io.path.inputStream

class PlatformFleetLoader(
    private val objectMapper: ObjectMapper =
        ObjectMapper(YAMLFactory())
            .registerModule(
                KotlinModule.Builder()
                    .build(),
            ).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true),
) {
    fun load(path: Path): PlatformFleet =
        path.inputStream().use { input ->
            objectMapper.readValue(input, PlatformFleet::class.java)
        }
}
