package com.jorisjonkers.personalstack.assistant.archunit

import com.jorisjonkers.personalstack.common.archunit.HexagonalArchitectureRules
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchitectureTest {
    private val importedClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("com.jorisjonkers.personalstack.assistant")

    @Test
    fun `domain must not depend on Spring framework`() {
        HexagonalArchitectureRules.DOMAIN_MUST_NOT_DEPEND_ON_SPRING.check(importedClasses)
    }

    @Test
    fun `controllers must not access repositories directly`() {
        HexagonalArchitectureRules.CONTROLLERS_MUST_NOT_ACCESS_REPOSITORIES.check(importedClasses)
    }

    @Test
    fun `no field injection allowed`() {
        HexagonalArchitectureRules.NO_FIELD_INJECTION.check(importedClasses)
    }

    @Test
    fun `controllers must follow naming convention`() {
        HexagonalArchitectureRules.NAMING_CONTROLLERS.check(importedClasses)
    }

    @Test
    fun `repositories must follow naming convention`() {
        HexagonalArchitectureRules.NAMING_REPOSITORIES.check(importedClasses)
    }

    @Test
    fun `commands must not depend on infrastructure`() {
        HexagonalArchitectureRules.COMMANDS_MUST_NOT_DEPEND_ON_WEB_OR_INFRA.check(importedClasses)
    }

    @Test
    fun `domain must not depend on infrastructure`() {
        HexagonalArchitectureRules.DOMAIN_MUST_NOT_DEPEND_ON_INFRASTRUCTURE.check(importedClasses)
    }

    @Test
    fun `DTOs are in dto package`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("Request")
            .or()
            .haveSimpleNameEndingWith("Response")
            .should()
            .resideInAPackage("..dto..")
            .because("DTOs (Request/Response) must reside in a dto package (ADR-013)")
            .check(importedClasses)
    }

    @Test
    fun `command handlers end with CommandHandler`() {
        classes()
            .that()
            .resideInAPackage("..application.command..")
            .and()
            .areAnnotatedWith("org.springframework.stereotype.Service")
            .should()
            .haveSimpleNameEndingWith("CommandHandler")
            .because("command handlers must follow *CommandHandler naming convention")
            .check(importedClasses)
    }

    @Test
    fun `query services end with QueryService`() {
        classes()
            .that()
            .resideInAPackage("..application.query..")
            .and()
            .areAnnotatedWith("org.springframework.stereotype.Service")
            .should()
            .haveSimpleNameEndingWith("QueryService")
            .because("query services must follow *QueryService naming convention")
            .check(importedClasses)
    }
}
