import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Property
import org.jooq.meta.jaxb.Target

interface JooqCodegenExtension {
    val schemaName: Property<String>
    val packageName: Property<String>
    val migrationLocations: ListProperty<String>
}

val jooqCodegen = extensions.create<JooqCodegenExtension>("jooqCodegen")

val generateJooq by tasks.registering {
    group = "jooq"
    description = "Generate jOOQ classes from Flyway SQL migrations using DDLDatabase"

    val outputDir = layout.buildDirectory.dir("generated/jooq")
    outputs.dir(outputDir)

    // Declare inputs so task is up-to-date when migrations haven't changed
    inputs.files(fileTree("src/main/resources/db/migration").include("**/*.sql"))

    doLast {
        val schema = jooqCodegen.schemaName.getOrElse("public")
        val pkg = jooqCodegen.packageName.get()
        val outDir = outputDir.get().asFile.also { it.mkdirs() }

        val scripts = jooqCodegen.migrationLocations.get().joinToString(";") { location ->
            val path = location.removePrefix("filesystem:")
            project.file(path).absolutePath + "/*.sql"
        }

        GenerationTool.generate(
            Configuration()
                .withGenerator(
                    Generator()
                        .withDatabase(
                            Database()
                                .withName("org.jooq.meta.extensions.ddl.DDLDatabase")
                                .withProperties(
                                    listOf(
                                        Property().withKey("scripts").withValue(scripts),
                                        Property().withKey("sort").withValue("flyway"),
                                        Property().withKey("defaultNameCase").withValue("lower"),
                                    ),
                                )
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

project.extensions.getByType(org.gradle.api.tasks.SourceSetContainer::class.java)
    .getByName("main")
    .java
    .srcDir(generateJooq.map { layout.buildDirectory.dir("generated/jooq") })

tasks.named("compileKotlin") {
    dependsOn(generateJooq)
}
