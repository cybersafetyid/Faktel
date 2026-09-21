plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()

    pom {
        name.set(project.name)
        description.set(providers.gradleProperty("POM_DESCRIPTION"))
        inceptionYear.set("2026")
        url.set("https://github.com/cybersafetyid/Faktel")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("cybersafetyid")
                name.set("Cyber Safety ID")
                url.set("https://github.com/cybersafetyid")
            }
        }
        scm {
            url.set("https://github.com/cybersafetyid/Faktel")
            connection.set("scm:git:git://github.com/cybersafetyid/Faktel.git")
            developerConnection.set("scm:git:ssh://git@github.com/cybersafetyid/Faktel.git")
        }
    }
}
