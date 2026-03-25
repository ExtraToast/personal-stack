package com.jorisjonkers.privatestack.assistant.archunit

import com.jorisjonkers.privatestack.common.archunit.HexagonalArchitectureRules
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchitectureTest {

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.jorisjonkers.privatestack.assistant")

    @Test
    fun `domain must not depend on Spring framework`() {
        HexagonalArchitectureRules.DOMAIN_MUST_NOT_DEPEND_ON_SPRING.check(classes)
    }

    @Test
    fun `controllers must not access repositories directly`() {
        HexagonalArchitectureRules.CONTROLLERS_MUST_NOT_ACCESS_REPOSITORIES.check(classes)
    }

    @Test
    fun `no field injection allowed`() {
        HexagonalArchitectureRules.NO_FIELD_INJECTION.check(classes)
    }

    @Test
    fun `controllers must follow naming convention`() {
        HexagonalArchitectureRules.NAMING_CONTROLLERS.check(classes)
    }

    @Test
    fun `repositories must follow naming convention`() {
        HexagonalArchitectureRules.NAMING_REPOSITORIES.check(classes)
    }

    @Test
    fun `commands must not depend on infrastructure`() {
        HexagonalArchitectureRules.COMMANDS_MUST_NOT_DEPEND_ON_WEB_OR_INFRA.check(classes)
    }

    @Test
    fun `domain must not depend on infrastructure`() {
        HexagonalArchitectureRules.DOMAIN_MUST_NOT_DEPEND_ON_INFRASTRUCTURE.check(classes)
    }
}
