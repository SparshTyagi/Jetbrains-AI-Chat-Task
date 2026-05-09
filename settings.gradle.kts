// RepoLens AI — IntelliJ Platform Plugin
// Repository and dependency resolution configuration for Gradle Plugin 2.x

rootProject.name = "repolens-ai"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // IntelliJ Platform Gradle Plugin 2.x — manages IDE sandbox, plugin packaging, and verification
    id("org.jetbrains.intellij.platform.settings") version "2.5.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    repositories {
        mavenCentral()

        // JetBrains repository for IntelliJ Platform dependencies
        intellijPlatform {
            defaultRepositories()
        }
    }
}
