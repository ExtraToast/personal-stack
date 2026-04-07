package com.jorisjonkers.personalstack.systemtests.playwright

import com.jorisjonkers.personalstack.systemtests.TestHelper
import com.microsoft.playwright.Page
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.assertj.core.api.Assertions.assertThat as assertThatValue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class RabbitMqOidcPlaywrightTest : PlaywrightTestBase() {
    @Test
    fun `rabbitmq service users complete browser oidc login into the management ui`() {
        val user = registerAndConfirm(uniqueUsername("rmq"))
        TestHelper.grantServicePermission(user.username, "RABBITMQ")
        loginViaApi(user)

        val seenUrls = mutableListOf<String>()
        context.onRequest { request ->
            val url = request.url()
            if (url.contains("rabbitmq.jorisjonkers.test") || url.contains("auth.jorisjonkers.test")) {
                seenUrls += url
            }
        }

        page.navigate("https://rabbitmq.jorisjonkers.test/")
        page.waitForLoadState()
        page.waitForTimeout(5000.0)
        waitForServicePageToSettle(page)
        page.waitForFunction(
            """
            () => {
              const text = document.body?.innerText?.toLowerCase() || '';
              return text.includes('rabbitmq') && !text.includes('not authorized');
            }
            """.trimIndent(),
            null,
            Page.WaitForFunctionOptions().setTimeout(20000.0),
        )

        val currentUrl = page.url()
        assertThatValue(currentUrl)
            .contains("rabbitmq.jorisjonkers.test")
            .doesNotContain("error=")
            .doesNotContain("login-callback.html?error")

        val pageText = buildString {
            append(page.title())
            append('\n')
            append(page.locator("body").textContent().orEmpty())
        }.lowercase()

        assertThatValue(
            seenUrls.any { it.contains("auth.jorisjonkers.test/.well-known/openid-configuration") },
        ).isTrue()
        assertThatValue(
            seenUrls.any { it.contains("auth.jorisjonkers.test/api/oauth2/authorize") && it.contains("client_id=rabbitmq") },
        ).isTrue()
        assertThatValue(
            seenUrls.any { it.contains("rabbitmq.jorisjonkers.test/js/oidc-oauth/login-callback.html?code=") },
        ).isTrue()
        assertThatValue(pageText).contains("rabbitmq")
        assertThatValue(pageText).doesNotContain("not authorized")
        assertThatValue(pageText).doesNotContain("oauth 2.0 parameter: scope")
        assertThatValue(pageText).doesNotContain("networkerror when attempting to fetch resource")
    }

    private fun waitForServicePageToSettle(servicePage: Page) {
        repeat(5) {
            servicePage.waitForLoadState()
            servicePage.waitForTimeout(1500.0)

            try {
                servicePage.title()
                servicePage.locator("body").textContent()
                return
            } catch (error: PlaywrightException) {
                if (!isNavigationInFlight(error)) {
                    throw error
                }
            }
        }
    }

    private fun isNavigationInFlight(error: PlaywrightException): Boolean =
        error.message.orEmpty().contains("Execution context was destroyed")
}
