pluginManagement {
    // Kotlin 2.4 metadata requires R8 9.1.29+; AGP 8.8's embedded shrinker predates it.
    buildscript {
        repositories {
            mavenCentral()
            maven { url = uri("https://storage.googleapis.com/r8-releases/raw") }
        }
        dependencies { classpath("com.android.tools:r8:9.1.29") }
    }
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "AndroidModLoader"
include(":domain", ":engine", ":bridge", ":app")
