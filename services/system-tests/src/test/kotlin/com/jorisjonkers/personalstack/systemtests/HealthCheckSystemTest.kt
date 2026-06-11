package com.jorisjonkers.personalstack.systemtests

import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class HealthCheckSystemTest {
    private val authBaseUrl = TestHelper.authBaseUrl
    private val agentsBaseUrl = TestHelper.agentsBaseUrl

    @Test
    fun `auth-api health endpoint responds`() {
        TestHelper.givenApi()
            .baseUri(authBaseUrl)
            .`when`()
            .get("/api/actuator/health")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(503)))
    }

    @Test
    fun `agents-api health endpoint responds`() {
        TestHelper.givenApi()
            .baseUri(agentsBaseUrl)
            .`when`()
            .get("/api/actuator/health")
            .then()
            .statusCode(200)
    }

    @Test
    fun `auth-api v1 health endpoint responds`() {
        TestHelper.givenApi()
            .baseUri(authBaseUrl)
            .`when`()
            .get("/api/v1/health")
            .then()
            .statusCode(200)
            .body("status", org.hamcrest.Matchers.equalTo("ok"))
            .body("service", org.hamcrest.Matchers.equalTo("auth-api"))
    }

    @Test
    fun `agents-api v1 health endpoint responds`() {
        TestHelper.givenApi()
            .baseUri(agentsBaseUrl)
            .`when`()
            .get("/api/v1/health")
            .then()
            .statusCode(200)
            .body("status", org.hamcrest.Matchers.equalTo("ok"))
            .body("service", org.hamcrest.Matchers.equalTo("agents-api"))
    }

    @Test
    fun `auth-api oidc discovery is accessible`() {
        TestHelper.givenApi()
            .baseUri(authBaseUrl)
            .`when`()
            .get("/.well-known/openid-configuration")
            .then()
            .statusCode(200)
    }
}
