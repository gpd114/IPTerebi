plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ipterebi.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ipterebi.app"
        // 26 rather than 21: below it, cleartext HTTP needs a network security
        // config and Media3 drops several codecs. Almost every Xtream panel is
        // plain HTTP, so that is not a corner worth supporting.
        minSdk = 26
        targetSdk = 34

        // Raise this for every build that leaves this machine. Android compares
        // versionCode when deciding whether one APK may replace another, and it
        // is the only way to tell two installed builds apart.
        versionCode = 1
        // Taken from the tag when CI is building one, so a published APK can
        // never report a version that disagrees with its own tag.
        versionName = (System.getenv("RELEASE_TAG")?.removePrefix("v"))
            ?.takeIf { it.isNotBlank() }
            ?: "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    // The panel client and its models. Exports OkHttp, the JSON library and
    // coroutines, which is why those are not repeated here.
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    // Only needed for the HLS output format. Live MPEG-TS goes through the
    // extractor in media3-exoplayer and needs nothing extra.
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // Playing on with the screen off: the media notification, lock-screen and
    // headphone controls, and the foreground service that keeps the app alive.
    implementation("androidx.media3:media3-session:1.4.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
}
