plugins {
    id("org.jetbrains.dokka")
}

// Aggregates every published module into one API reference site: ./gradlew dokkaGenerate
dependencies {
    dokka(project(":faktel-core"))
    dokka(project(":faktel-face"))
    dokka(project(":faktel-ktp"))
    dokka(project(":faktel-ort"))
}

dokka {
    moduleName.set("Faktel")
}
