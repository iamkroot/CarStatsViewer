pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

import java.util.Properties

val localProperties = Properties().apply {
    val localPropertiesFile = rootDir.resolve("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val useMapbox: Boolean = providers.gradleProperty("useMapbox")
    .orElse(providers.provider { localProperties.getProperty("useMapbox", "false") })
    .get().toBoolean()

val mapboxDownloadsToken: String = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN")
    .orElse(providers.provider { localProperties.getProperty("MAPBOX_DOWNLOADS_TOKEN", "") })
    .get()

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        if (useMapbox) {
            maven {
                url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
                authentication {
                    create<BasicAuthentication>("basic")
                }
                credentials {
                    username = "mapbox"
                    password = mapboxDownloadsToken
                }
            }
        }
    }
}

rootProject.name = "CarStatsViewer"

include(":automotive")
