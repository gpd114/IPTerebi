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
rootProject.name = "IPTerebi"

// Everything that speaks to an Xtream Codes panel, with no Android in it:
// URL normalisation, the JSON models, the HTTP calls and stream-URL building.
// Kept as a plain Kotlin JVM module so it can be tested on a workstation with
// `gradle :core:test` — no emulator, no SDK, no device. The panel quirks in
// here are the part most likely to be wrong against a provider nobody has
// tried yet, so they are the part that most needs tests.
include(":core")

// The Android application: Compose UI and the Media3 player. Depends on :core
// and holds nothing that could be tested without a device.
include(":app")
