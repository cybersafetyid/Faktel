import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id("faktel.kmp-library")
    id("faktel.publish")
}

val generateVersion by tasks.registering(GenerateVersionTask::class) {
    version = providers.gradleProperty("VERSION_NAME")
    outputDir = layout.buildDirectory.dir("generated/version/kotlin")
}

kotlin {
    val xcframework = XCFramework("Faktel")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Faktel"
            isStatic = true
            export(project(":faktel-core"))
            export(project(":faktel-face"))
            export(project(":faktel-ktp"))
            export(project(":faktel-ort"))
            xcframework.add(this)
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateVersion)
            dependencies {
                api(project(":faktel-core"))
                api(project(":faktel-face"))
                api(project(":faktel-ktp"))
                api(project(":faktel-ort"))
            }
        }
    }
}
