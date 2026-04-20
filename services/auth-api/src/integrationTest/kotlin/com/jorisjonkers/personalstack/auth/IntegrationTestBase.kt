package com.jorisjonkers.personalstack.auth

import org.junit.jupiter.api.Tag
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Testcontainers

@Tag("integration")
@SpringBootTest
@Testcontainers
abstract class IntegrationTestBase {
    companion object {
        @Suppress("DEPRECATION")
        private val postgres =
            PostgreSQLContainer<Nothing>("postgres:17-alpine").apply {
                withDatabaseName("auth_db")
                withUsername("auth_user")
                withPassword("auth_password")
            }

        @Suppress("DEPRECATION")
        private val valkey =
            GenericContainer<Nothing>("valkey/valkey:7-alpine").apply {
                withExposedPorts(6379)
            }

        @Suppress("DEPRECATION")
        private val rabbitmq = RabbitMQContainer("rabbitmq:3-management-alpine")

        init {
            postgres.start()
            valkey.start()
            rabbitmq.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { valkey.host }
            registry.add("spring.data.redis.port") { valkey.getMappedPort(6379).toString() }
            registry.add("spring.rabbitmq.host") { rabbitmq.host }
            registry.add("spring.rabbitmq.port") { rabbitmq.amqpPort.toString() }
            // No SMTP container in integration tests — the MailHealthIndicator
            // would otherwise flip the composite /actuator/health to DOWN and
            // break HealthIntegrationTest. The mail send paths are covered by
            // dedicated mail tests with mocks; this just silences the health
            // contributor.
            registry.add("management.health.mail.enabled") { "false" }
            // MockMvc uses http://localhost by default; issuer must match for OAuth2 endpoints
            registry.add("auth.issuer") { "http://localhost" }
            registry.add("auth.cors.allowed-origins") {
                listOf(
                    "http://localhost:5173",
                    "http://localhost:5174",
                    "http://localhost:5175",
                    "https://jorisjonkers.test",
                    "https://auth.jorisjonkers.test",
                    "https://assistant.jorisjonkers.test",
                    "https://vault.jorisjonkers.test",
                    "https://n8n.jorisjonkers.test",
                    "https://grafana.jorisjonkers.test",
                    "https://dashboard.jorisjonkers.test",
                    "https://rabbitmq.jorisjonkers.test",
                    "https://mail.jorisjonkers.test",
                    "https://stalwart.jorisjonkers.test",
                    "https://traefik.jorisjonkers.test",
                    "https://status.jorisjonkers.test",
                ).joinToString(",")
            }
        }
    }
}
