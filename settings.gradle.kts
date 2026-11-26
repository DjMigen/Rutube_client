pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "RuTubeClient"
include(":core:model")
include(":core:network")
include(":core:database")
include(":core:player")
include(":app-mobile")
include(":app-tv")
