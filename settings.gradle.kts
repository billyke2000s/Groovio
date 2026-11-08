pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipe Extractor is published on JitPack only; limit JitPack to it.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.(?i)teamnewpipe.*") }
        }
    }
}
rootProject.name = "Groovio"
include(":app")
