plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

ksp {
    // Exported schemas are committed so future database migrations can be tested.
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.groovio.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.groovio.app"
        minSdk = 26
        targetSdk = 35
        // CI passes the commit count on main, so every release has a higher version.
        val build = (System.getenv("GROOVIO_BUILD_NUMBER") ?: "1").toInt()
        versionCode = build
        versionName = "0.1.$build"
    }

    // One fixed key for every build, so in-app updates install over the previous version.
    // Committed on purpose (owner decision, see DECISIONS.md): Groovio stores no accounts.
    signingConfigs {
        create("groovio") {
            storeFile = rootProject.file("signing/groovio.jks")
            storePassword = "EKem909avrEQQhqVk8eEUStjspsZCF9"
            keyAlias = "groovio"
            keyPassword = "EKem909avrEQQhqVk8eEUStjspsZCF9"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("groovio")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        // NewPipe Extractor uses java.nio APIs missing below API 33.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources { excludes += setOf("META-INF/DEPENDENCIES", "/META-INF/{AL2.0,LGPL2.1}") }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")

    // Online: on-device YouTube Music search and stream extraction (GPL-3.0).
    // v0.26.5, as in NewPipe 0.29.1. v0.27.0 (NewPipe 0.29.2) gets "confirm you're not a bot"
    // on every video: see DECISIONS.md and .github/extractor-blocklist.
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5")
    // JSON parser NewPipe already ships; pinned to the same commit to avoid duplicates.
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Artwork colour for the player background.
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.core:core-ktx:1.13.1")
    // Local library: favourites, playlists, history, saved queue.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // Screenshots of Compose screens rendered on the JVM (CI prints them for review).
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Show each test and its output in CI logs.
tasks.withType<Test>().configureEach {
    // Record screenshots only when asked (CI screenshot job), so normal tests stay fast.
    systemProperty("roborazzi.test.record", System.getenv("GROOVIO_SCREENSHOTS") == "1")
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}
