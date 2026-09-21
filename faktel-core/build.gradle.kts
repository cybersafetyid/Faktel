plugins {
    id("faktel.kmp-library")
    id("faktel.publish")
}

kotlin {
    sourceSets {
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
