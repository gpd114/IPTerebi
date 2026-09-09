import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Pinned to 17 rather than resolved through a Java toolchain: a toolchain block
// makes Gradle go and fetch a JDK when the one it is running on does not match,
// which fails on any machine without that download available. Compiling to 17
// bytecode from a newer JDK is fine, and 17 is what :app dexes against.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // `api` rather than `implementation`: :app builds its player's HTTP stack
    // on the same OkHttp instance and parses nothing itself, so both are part
    // of this module's surface.
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.test { useJUnitPlatform() }
