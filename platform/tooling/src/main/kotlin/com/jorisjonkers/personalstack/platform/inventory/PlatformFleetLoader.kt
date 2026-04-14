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
        }.also(::validate)

    private fun validate(fleet: PlatformFleet) {
        fleet.nodes.forEach { (nodeName, node) ->
            require(node.site in fleet.sites) {
                "node $nodeName references unknown site ${node.site}"
            }
            if (node.status == "active") {
                require(node.ssh != null) {
                    "active node $nodeName must define ssh connection details"
                }
            }
        }
    }
}
