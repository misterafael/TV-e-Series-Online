pluginManagement {
    repositories {
        google()
        // Alternate Maven Central endpoint helps avoid transient HTTP 429 throttling.
        maven { url = uri("https://repo1.maven.org/maven2") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Keep the canonical repository as fallback.
        maven { url = uri("https://repo1.maven.org/maven2") }
        mavenCentral()
    }
}

rootProject.name = "TV e Series Online"
include(":app")
