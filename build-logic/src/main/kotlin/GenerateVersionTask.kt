import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/** Writes `Faktel.VERSION` so the published artifact can report its own version at runtime. */
@CacheableTask
abstract class GenerateVersionTask : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDir.get().file("io/github/cybersafetyid/faktel/FaktelVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package io.github.cybersafetyid.faktel

            /** Build-time metadata of the Faktel library. */
            public object Faktel {
                /** The library's semantic version, e.g. `0.1.0`. */
                public const val VERSION: String = "${version.get()}"
            }
            """.trimIndent() + "\n",
        )
    }
}
