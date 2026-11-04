plugins {
    id("com.android.application") version "8.5.2" apply false
    // Kotlin, the Compose compiler plugin and the serialization plugin are
    // versioned together and must move together. The Compose compiler plugin
    // tracks the Kotlin version rather than the Compose version, so bumping one
    // alone yields a compiler that disagrees with its own output.
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21" apply false
    // Compose Multiplatform, for the desktop build. Versioned apart from the
    // Compose *compiler* plugin above, which tracks Kotlin: 1.8.0 is the
    // release that targets Kotlin 2.1.x.
    id("org.jetbrains.compose") version "1.8.0" apply false
}
