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
    }
}

rootProject.name = "ScheduleClock"

// watchface: Watch Face Format package (XML only, no code).
// datasource: Kotlin app that reads the calendar and feeds the face's complication slots.
// phone: companion app that reads the phone's calendar and sends it to the watch (same app id as datasource).
include(":watchface", ":datasource", ":phone")
