package com.jorisjonkers.personalstack.auth.flow

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import com.jorisjonkers.personalstack.common.messaging.RabbitMqConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

class UserRegisteredEventPublishingIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var rabbitTemplate: RabbitTemplate

    @Autowired
    private lateinit var rabbitAdmin: RabbitAdmin

    private lateinit var mockMvc: MockMvc

    private val objectMapper: ObjectMapper =
        jacksonObjectMapper().registerModule(JavaTimeModule())

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        // Purge the queue before each test to ensure clean state
        rabbitAdmin.purgeQueue(RabbitMqConfig.USER_REGISTERED_QUEUE)
    }

    @Test
    fun `user registration publishes UserRegisteredEvent to RabbitMQ`() {
        val suffix = UUID.randomUUID().toString().take(8)

        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "event_pub_$suffix",
                      "email": "event_pub_$suffix@example.com",
                      "firstName": "Test",
                      "lastName": "User",
                      "password": "securepass123"
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }

        val message = rabbitTemplate.receive(RabbitMqConfig.USER_REGISTERED_QUEUE, 5000)
        assertThat(message).isNotNull
    }

    @Test
    fun `event contains correct user data`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "event_data_$suffix"
        val email = "event_data_$suffix@example.com"

        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "$username",
                      "email": "$email",
                      "firstName": "Test",
                      "lastName": "User",
                      "password": "securepass123"
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }

        val message = rabbitTemplate.receive(RabbitMqConfig.USER_REGISTERED_QUEUE, 5000)
        assertThat(message).isNotNull

        val body = String(message!!.body)
        val eventJson = objectMapper.readTree(body)
        assertThat(eventJson.get("username").asText()).isEqualTo(username)
        assertThat(eventJson.get("email").asText()).isEqualTo(email)
        assertThat(eventJson.has("userId")).isTrue()
        assertThat(eventJson.has("occurredAt")).isTrue()
    }

    @Test
    fun `event is published to correct exchange`() {
        val suffix = UUID.randomUUID().toString().take(8)

        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "event_exch_$suffix",
                      "email": "event_exch_$suffix@example.com",
                      "firstName": "Test",
                      "lastName": "User",
                      "password": "securepass123"
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }

        val message = rabbitTemplate.receive(RabbitMqConfig.USER_REGISTERED_QUEUE, 5000)
        assertThat(message).isNotNull

        // The message arrived on the correct queue which is bound to the events exchange
        // with the user registered routing key, proving it was published to the correct exchange
        assertThat(message!!.messageProperties.receivedExchange)
            .isEqualTo(RabbitMqConfig.EVENTS_EXCHANGE)
    }

    @Test
    fun `event uses correct routing key`() {
        val suffix = UUID.randomUUID().toString().take(8)

        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "event_rk_$suffix",
                      "email": "event_rk_$suffix@example.com",
                      "firstName": "Test",
                      "lastName": "User",
                      "password": "securepass123"
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }

        val message = rabbitTemplate.receive(RabbitMqConfig.USER_REGISTERED_QUEUE, 5000)
        assertThat(message).isNotNull

        assertThat(message!!.messageProperties.receivedRoutingKey)
            .isEqualTo(RabbitMqConfig.USER_REGISTERED_ROUTING_KEY)
    }

    @Test
    fun `duplicate registration does not publish duplicate event`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "event_dup_$suffix"

        // First registration succeeds
        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "$username",
                      "email": "$username@example.com",
                      "firstName": "Test",
                      "lastName": "User",
                      "password": "securepass123"
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }

        // Consume the first event
        val firstMessage = rabbitTemplate.receive(RabbitMqConfig.USER_REGISTERED_QUEUE, 5000)
        assertThat(firstMessage).isNotNull

        // Second registration with same username fails
        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "$username",
                      "email": "other_$username@example.com",
                      "firstName": "Test",
                      "lastName": "User",
                      "password": "securepass123"
                    }
                    """.trimIndent()
            }.andExpect { status { isBadRequest() } }

        // No second event should be published
        val secondMessage = rabbitTemplate.receive(RabbitMqConfig.USER_REGISTERED_QUEUE, 1000)
        assertThat(secondMessage).isNull()
    }
}
