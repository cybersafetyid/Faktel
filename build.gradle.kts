plugins {
    id("faktel.docs")
    alias(libs.plugins.binaryCompat)
}

apiValidation {
    // The umbrella module only re-exports the others plus a per-release version constant.
    ignoredProjects.add("faktel")
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib { enabled = true }
}
