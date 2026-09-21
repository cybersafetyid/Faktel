import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

plugins {
    id("faktel.kmp-library")
    id("faktel.publish")
}

kotlin {
    // Android and desktop JVM share one ONNX Runtime *Java API* implementation (`ortJavaMain`); only the native
    // artifact differs per target. iOS has no Kotlin implementation - see ios/FaktelOnnxRuntime.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common {
            group("ortJava") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    sourceSets {
        commonMain.dependencies { api(project(":faktel-core")) }

        // Compile against the API only; each target brings its own runtime artifact below.
        getByName("ortJavaMain").dependencies { compileOnly(libs.onnxruntime.jvm) }
        androidMain.dependencies { implementation(libs.onnxruntime.android) }
        jvmMain.dependencies { implementation(libs.onnxruntime.jvm) }

        jvmTest.dependencies {
            implementation(project(":faktel-face"))
            implementation(project(":faktel-ktp"))
            implementation(libs.kotlin.test)
        }
    }
}
