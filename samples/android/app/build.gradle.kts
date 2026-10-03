plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.cybersafetyid.faktel.sample"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.cybersafetyid.faktel.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
    // Models and sample photos live once, at the repository root, and are packaged as assets.
    sourceSets["main"].assets.srcDirs("../../../models", "../../assets")
    androidResources { ignoreAssetsPattern = "!*.md" }
}

dependencies {
    implementation("io.github.cybersafetyid.faktel:faktel:0.1.1-SNAPSHOT")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
}
