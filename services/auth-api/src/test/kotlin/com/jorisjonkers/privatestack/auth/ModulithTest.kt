package com.jorisjonkers.privatestack.auth

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

@Disabled("Enable after Spring Modulith is fully configured")
class ModulithTest {

    @Test
    fun `verify modular structure`() {
        val modules = ApplicationModules.of(AuthApiApplication::class.java)
        modules.verify()
    }
}
