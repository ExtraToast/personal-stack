package com.jorisjonkers.personalstack.auth.health

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * Guards the class of bug where the composite /actuator/health reports
 * 503 because a single contributor (db, rabbit, redis, ...) is DOWN
 * while the kubelet-visible liveness + readiness groups stay UP —
 * exactly the shape that took /api/v1/auth/me and /api/v1/auth/session-login
 * down in production when database/roles/auth-api was missing from Vault.
 */
class HealthIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

    @Test
    fun `composite health returns 200 with every contributor UP`() {
        val result =
            mockMvc
                .get("/api/actuator/health")
                .andReturn()

        assertThat(result.response.status)
            .describedAs("composite /actuator/health body=%s", result.response.contentAsString)
            .isEqualTo(200)

        val body = objectMapper.readTree(result.response.contentAsString)
        assertThat(body["status"].asText()).isEqualTo("UP")

        val components: JsonNode =
            body["components"] ?: body["details"]
                ?: error("no components/details in /health response — show-details may be wrong: $body")

        // Fail only on DOWN (or OUT_OF_SERVICE). UNKNOWN is fine — e.g.
        // the auto-registered discoveryComposite is intentionally
        // uninitialised in this stack; that should not break the test.
        val downComponents =
            @Suppress("DEPRECATION")
            components
                .fields()
                .asSequence()
                .filter { (_, node) ->
                    val status = node["status"]?.asText()
                    status == "DOWN" || status == "OUT_OF_SERVICE"
                }.map { (name, node) -> "$name=${node["status"]?.asText()}" }
                .toList()

        assertThat(downComponents)
            .describedAs("composite UP but a contributor is DOWN: $downComponents full=$body")
            .isEmpty()
    }

    @Test
    fun `db and rabbit contributors are auto-configured`() {
        // Defends against a future refactor that accidentally disables the
        // built-in indicators (e.g. via management.health.db.enabled=false
        // or a starter getting removed). An UP overall status is
        // meaningless if the contributors aren't even registered.
        val body =
            mockMvc
                .get("/api/actuator/health")
                .andReturn()
                .response
                .contentAsString
                .let(objectMapper::readTree)

        val components = body["components"] ?: body["details"] ?: error("no components: $body")
        assertThat(components.fieldNames().asSequence().toList())
            .describedAs("expected core infra contributors to be present: $body")
            .contains("db", "rabbit", "redis", "ping")
    }
}
