pluginManagement {
    repositories {
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("com\\.google.*"); includeGroupByRegex("androidx.*") } }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("com\\.google.*"); includeGroupByRegex("androidx.*") } }
        mavenCentral()
    }
}
rootProject.name = "faktel-sample-android"
include(":app")

// Consume Faktel straight from this repository. In your own app, depend on the published artifact instead:
//   implementation("io.github.cybersafetyid.faktel:faktel:<version>")
includeBuild("../..") {
    dependencySubstitution {
        substitute(module("io.github.cybersafetyid.faktel:faktel")).using(project(":faktel"))
    }
}
