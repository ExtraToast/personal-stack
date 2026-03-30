package com.jorisjonkers.personalstack.systemtests.playwright

import com.jorisjonkers.personalstack.systemtests.TestHelper
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.assertj.core.api.Assertions.assertThat as assertThatValue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class MainSiteServiceLaunchTest : PlaywrightTestBase() {
    private data class ServiceExpectation(
        val label: String,
        val href: String,
        val expectedHost: String,
        val expectedMarkers: List<String>,
        val forbiddenMarkers: List<String> = emptyList(),
    )

    private val servicesInLaunchOrder =
        listOf(
            ServiceExpectation(
                label = "Vault",
                href = "https://vault.jorisjonkers.test/",
                expectedHost = "vault.jorisjonkers.test",
                expectedMarkers = listOf("vault"),
            ),
            ServiceExpectation(
                label = "Stalwart",
                href = "https://mail.jorisjonkers.test/",
                expectedHost = "mail.jorisjonkers.test",
                expectedMarkers = listOf("stalwart", "mail"),
            ),
            ServiceExpectation(
                label = "n8n",
                href = "https://n8n.jorisjonkers.test/",
                expectedHost = "n8n.jorisjonkers.test",
                expectedMarkers = listOf("n8n", "workflow"),
                forbiddenMarkers = listOf("owner account", "sign in with email"),
            ),
            ServiceExpectation(
                label = "Grafana",
                href = "https://grafana.jorisjonkers.test/",
                expectedHost = "grafana.jorisjonkers.test",
                expectedMarkers = listOf("grafana"),
                forbiddenMarkers = listOf("failed to get token from provider", "login failed"),
            ),
            ServiceExpectation(
                label = "Assistant",
                href = "https://assistant.jorisjonkers.test/",
                expectedHost = "assistant.jorisjonkers.test",
                expectedMarkers = listOf("conversation", "assistant"),
            ),
            ServiceExpectation(
                label = "Traefik",
                href = "https://traefik.jorisjonkers.test/",
                expectedHost = "traefik.jorisjonkers.test",
                expectedMarkers = listOf("traefik", "dashboard"),
            ),
            ServiceExpectation(
                label = "Status",
                href = "https://status.jorisjonkers.test/",
                expectedHost = "status.jorisjonkers.test",
                expectedMarkers = listOf("uptime", "status"),
            ),
        )

    @Test
    fun `main site login can launch each service card into a real service ui`() {
        val user = registerAndConfirm()
        val totpSecret = loginFromMainSiteAndEnrollTotp(user)

        assertThat(page.locator("body")).containsText(user.username)

        TestHelper.makeUserAdmin(user.username)

        // The authenticated principal is stored in the session, so re-login is required
        // after changing the user's role directly in the database.
        context.clearCookies()
        loginFromMainSiteWithTotp(user, totpSecret)

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        assertThat(page.locator("text=My Apps")).isVisible()
        assertThat(page.locator("a[href='/admin']")).isVisible()

        servicesInLaunchOrder.forEach { service ->
            val servicePage = openServiceCard(service, context)
            assertServicePageLoaded(servicePage, service)
            servicePage.close()
        }
    }

    private fun loginFromMainSiteAndEnrollTotp(user: TestHelper.RegisteredUser): String {
        page.navigate(APP_UI_URL)
        page.waitForLoadState()

        page.locator("button:has-text('Login')").click()
        page.waitForURL(
            { it.contains("auth.jorisjonkers.test/login") },
            Page.WaitForURLOptions().setTimeout(15000.0),
        )

        page.locator("#username").fill(user.username)
        page.locator("#password").fill(user.password)
        page.locator("button[type='submit']").click()

        page.waitForURL(
            { it.contains("/totp-setup") },
            Page.WaitForURLOptions().setTimeout(20000.0),
        )
        page.waitForSelector("canvas", Page.WaitForSelectorOptions().setTimeout(15000.0))
        page.locator("details summary").click()
        val secret = page.locator("code").textContent().trim()
        page.locator("#totp-code").fill(generateTotpCode(secret))
        page.locator("button[type='submit']").click()

        waitForMainSite()
        return secret
    }

    private fun loginFromMainSiteWithTotp(
        user: TestHelper.RegisteredUser,
        totpSecret: String,
    ) {
        page.navigate(APP_UI_URL)
        page.waitForLoadState()

        page.locator("button:has-text('Login')").click()
        page.waitForURL(
            { it.contains("auth.jorisjonkers.test/login") },
            Page.WaitForURLOptions().setTimeout(15000.0),
        )

        page.locator("#username").fill(user.username)
        page.locator("#password").fill(user.password)
        page.locator("button[type='submit']").click()

        page.waitForSelector("#totp-code", Page.WaitForSelectorOptions().setTimeout(10000.0))
        page.locator("#totp-code").fill(generateTotpCode(totpSecret))
        page.locator("button[type='submit']").click()

        waitForMainSite()
    }

    private fun waitForMainSite() {
        page.waitForURL(
            { it.startsWith(APP_UI_URL) && !it.contains("/login") },
            Page.WaitForURLOptions().setTimeout(20000.0),
        )
        page.waitForLoadState()
        page.waitForTimeout(2000.0)
    }

    private fun openServiceCard(
        service: ServiceExpectation,
        browserContext: BrowserContext,
    ): Page {
        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        val card = page.locator("a[href='${service.href}']").first()
        assertThat(card).isVisible()

        val popup = browserContext.waitForPage(card::click)
        popup.waitForLoadState()
        popup.waitForTimeout(5000.0)
        return popup
    }

    private fun assertServicePageLoaded(
        servicePage: Page,
        service: ServiceExpectation,
    ) {
        waitForServicePageToSettle(servicePage)

        val currentUrl = servicePage.url()
        assertThatValue(currentUrl)
            .describedAs("${service.label} should open on its own host")
            .contains(service.expectedHost)
            .doesNotContain("auth.jorisjonkers.test/login")
            .doesNotContain("error=")

        val pageText = readPageText(servicePage)

        service.forbiddenMarkers.forEach { marker ->
            assertThatValue(pageText)
                .describedAs("${service.label} should not land on an intermediate error/setup screen")
                .doesNotContain(marker.lowercase())
        }

        assertThatValue(
            service.expectedMarkers.any { marker -> pageText.contains(marker.lowercase()) },
        ).describedAs("${service.label} should expose a recognizable service UI marker").isTrue()
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

    private fun readPageText(servicePage: Page): String {
        waitForServicePageToSettle(servicePage)
        return buildString {
            append(servicePage.title())
            append('\n')
            append(servicePage.locator("body").textContent().orEmpty())
        }.lowercase()
    }

    private fun isNavigationInFlight(error: PlaywrightException): Boolean =
        error.message.orEmpty().contains("Execution context was destroyed")
}
