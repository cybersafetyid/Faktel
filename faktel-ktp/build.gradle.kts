plugins {
    id("faktel.kmp-library")
    id("faktel.publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies { api(project(":faktel-face")) }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
