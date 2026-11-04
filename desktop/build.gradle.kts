import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

// 17, as :core and :app are. See the note in core/build.gradle.kts about why
// this is pinned rather than resolved through a Java toolchain.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // The whole point of this module: the panel client is already plain
    // Kotlin, so every quirk it knows about comes across unchanged.
    implementation(project(":core"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // Compose Desktop dispatches on the AWT event thread, so a coroutine that
    // touches the UI needs Dispatchers.Main to exist. Without this it throws
    // at the first launch{} that comes back to the screen.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    // libvlc, which is what plays the stream. Media3 is Android-only, so the
    // player is the one part of :app that cannot come across at all -- see
    // the note in CLAUDE.md about why VLC rather than anything in the JDK.
    implementation("uk.co.caprica:vlcj:4.8.3")

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.ipterebi.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "IPTerebi"
            vendor = "IPTerebi"

            // jpackage refuses a Windows package whose major version is 0, so
            // this cannot follow the tv-v0.1.0 tags the way the APK's
            // versionName does. It is the installer's own number.
            packageVersion = "1.0.0"

            windows {
                menu = true
                shortcut = true
                // Fixed, so an upgrade replaces the install rather than
                // standing a second copy beside it. Generated once; never
                // change it for this product.
                upgradeUuid = "6F1C0A52-9B74-4E53-9C8E-2A41D7B6E5C1"
            }

            // The JDK modules jpackage has to keep. Without them the bundled
            // runtime is missing pieces nothing references by name: crypto for
            // https guide sources, and java.sql because vlcj's logging pulls
            // it in.
            modules("java.naming", "java.sql", "jdk.crypto.ec", "jdk.unsupported")
        }
    }
}

// The one test here needs libvlc and a fake panel, and skips without either --
// see PlaysAStreamTest for why that is a skip rather than a failure.
tasks.test { useJUnitPlatform() }
