// Top-level build file where you can add configuration options common to all sub-projects/modules.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS) // ✅ Prefer settings repositories
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BioAuth"
include(":app")
