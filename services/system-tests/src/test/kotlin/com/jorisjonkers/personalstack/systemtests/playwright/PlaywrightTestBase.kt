package com.jorisjonkers.personalstack.systemtests.playwright

import com.jorisjonkers.personalstack.systemtests.TestHelper
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import org.apache.commons.codec.binary.Base32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class PlaywrightTestBase {
    companion object {
        val AUTH_UI_URL: String =
            System.getProperty("test.auth-ui.url", "http://auth.localhost")
        val APP_UI_URL: String =
            System.getProperty("test.app-ui.url", "http://localhost")
        val ASSISTANT_UI_URL: String =
            System.getProperty("test.assistant-ui.url", "http://assistant.localhost")
    }

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    protected lateinit var context: BrowserContext
    protected lateinit var page: Page

    @BeforeAll
    fun launchBrowser() {
        playwright = Playwright.create()
        browser =
            playwright.chromium().launch(
                BrowserType.LaunchOptions().setHeadless(true),
            )
    }

    @AfterAll
    fun closeBrowser() {
        browser.close()
        playwright.close()
    }

    @BeforeEach
    fun createContext() {
        context = browser.newContext()
        page = context.newPage()
    }

    @AfterEach
    fun closeContext() {
        context.close()
    }

    protected fun uniqueUsername(prefix: String = "pw"): String = "${prefix}_${UUID.randomUUID().toString().take(8)}"

    protected fun registerAndConfirm(
        username: String = uniqueUsername(),
        password: String = "Test1234!",
    ): TestHelper.RegisteredUser = TestHelper.registerAndConfirm(username, password)

    protected fun loginViaUi(
        username: String,
        password: String,
    ) {
        page.navigate("$AUTH_UI_URL/login")
        page.locator("#username").fill(username)
        page.locator("#password").fill(password)
        page.locator("button[type='submit']").click()
        page.waitForURL { !it.contains("/login") }
    }

    protected fun loginViaApi(
        user: TestHelper.RegisteredUser,
        totpCode: String? = null,
    ) {
        // Use Playwright's API request context to POST session-login through
        // Traefik, so the browser gets real Set-Cookie headers from auth.localhost.
        val body =
            if (totpCode != null) {
                """{"username":"${user.username}","password":"${user.password}","totpCode":"$totpCode"}"""
            } else {
                """{"username":"${user.username}","password":"${user.password}"}"""
            }
        val response =
            context.request().post(
                "$AUTH_UI_URL/api/v1/auth/session-login",
                com.microsoft.playwright.options.RequestOptions
                    .create()
                    .setHeader("Content-Type", "application/json")
                    .setData(body),
            )
        require(response.status() == 200) {
            "Session login failed with ${response.status()}: ${response.text()}"
        }
    }

    protected fun loginAsAdmin(): TestHelper.RegisteredUser {
        val user = registerAndConfirm(uniqueUsername("adm"))
        TestHelper.makeUserAdmin(user.username)
        loginViaApi(user)
        return user
    }

    protected fun generateTotpCode(secret: String): String {
        val padded = secret.padEnd((secret.length + 7) / 8 * 8, '=')
        val secretBytes = Base32().decode(padded)
        val config =
            TimeBasedOneTimePasswordConfig(
                codeDigits = 6,
                hmacAlgorithm = HmacAlgorithm.SHA1,
                timeStep = 30,
                timeStepUnit = TimeUnit.SECONDS,
            )
        return TimeBasedOneTimePasswordGenerator(secretBytes, config).generate()
    }
}
