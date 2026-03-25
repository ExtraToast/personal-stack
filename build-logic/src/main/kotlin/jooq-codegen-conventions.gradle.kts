import org.flywaydb.core.Flyway
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import org.testcontainers.containers.PostgreSQLContainer

interface JooqCodegenExtension {
    val schemaName: Property<String>
    val packageName: Property<String>
    val migrationLocations: ListProperty<String>
}

val jooqCodegen = extensions.create<JooqCodegenExtension>("jooqCodegen")

val generateJooq by tasks.registering {
    group = "jooq"
    description = "Generate jOOQ classes from Flyway migrations using a Testcontainers PostgreSQL instance"

    val outputDir = layout.buildDirectory.dir("generated/jooq")
    outputs.dir(outputDir)

    // Declare inputs so task is up-to-date when migrations haven't changed
    inputs.files(fileTree("src/main/resources/db/migration").include("**/*.sql"))

    doLast {
        val schema = jooqCodegen.schemaName.getOrElse("public")
        val pkg = jooqCodegen.packageName.get()
        val migrations = jooqCodegen.migrationLocations.get().map { location ->
            if (location.startsWith("filesystem:")) {
                val relativePath = location.removePrefix("filesystem:")
                "filesystem:${project.file(relativePath).absolutePath}"
            } else {
                location
            }
        }
        val outDir = outputDir.get().asFile.also { it.mkdirs() }

        @Suppress("DEPRECATION")
        PostgreSQLContainer<Nothing>("postgres:17-alpine").use { pg ->
            pg.start()

            Flyway.configure()
                .dataSource(pg.jdbcUrl, pg.username, pg.password)
                .locations(*migrations.toTypedArray())
                .load()
                .migrate()

            GenerationTool.generate(
                Configuration()
                    .withJdbc(
                        Jdbc()
                            .withDriver("org.postgresql.Driver")
                            .withUrl(pg.jdbcUrl)
                            .withUser(pg.username)
                            .withPassword(pg.password),
                    )
                    .withGenerator(
                        Generator()
                            .withDatabase(
                                Database()
                                    .withName("org.jooq.meta.postgres.PostgresDatabase")
                                    .withIncludes(".*")
                                    .withExcludes("flyway_schema_history")
                                    .withInputSchema(schema),
                            )
                            .withTarget(
                                Target()
                                    .withPackageName(pkg)
                                    .withDirectory(outDir.absolutePath),
                            ),
                    ),
            )
        }
    }
}

project.extensions.getByType(org.gradle.api.tasks.SourceSetContainer::class.java)
    .getByName("main")
    .java
    .srcDir(generateJooq.map { layout.buildDirectory.dir("generated/jooq") })

tasks.named("compileKotlin") {
    dependsOn(generateJooq)
}
