import java.util.Properties

/**
 * versionCode derived from versionName "MAJOR.MINOR.PATCH": MAJOR * 10000 +
 * MINOR * 100 + PATCH (0.1.0 -> 100, 1.2.3 -> 10203). Android only installs an
 * update with a higher versionCode, so this grows with every release as long as
 * MINOR and PATCH stay below 100.
 */
fun versionCodeOf(versionName: String): Int {
    val parts = versionName.split(".").map { it.toIntOrNull() }
    require(parts.size == 3 && parts.all { it != null && it in 0..99 }) {
        "versionName must be MAJOR.MINOR.PATCH with numbers 0-99, was '$versionName'"
    }
    val (major, minor, patch) = parts.map { it!! }
    return major * 10000 + minor * 100 + patch
}

/**
 * Release signing, kept out of git. CI passes environment variables (from the
 * repository secrets); locally, android/keystore.properties (git-ignored) holds
 * the same values. With neither, the release APK is built unsigned.
 * The keystore is PKCS12, so the key's password is the keystore's password.
 */
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun signingValue(envName: String, propertyName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotEmpty() } ?: keystoreProperties.getProperty(propertyName)

val releaseStoreFile = signingValue("RELEASE_KEYSTORE_FILE", "storeFile")

val appVersionName = "0.1.0" // bump for every release, see DEVELOPMENT.md "Releases"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.filbeq.fuelup"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.filbeq.fuelup"
        minSdk = 26
        targetSdk = 36
        versionName = appVersionName
        versionCode = versionCodeOf(appVersionName)
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("RELEASE_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("RELEASE_KEY_ALIAS", "keyAlias")
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        debug {
            // Installed as a separate app ("FuelUp Dev", badged icon in
            // src/debug/res), so testing never touches the release install and
            // its data. The Kotlin package (namespace) is unchanged.
            applicationIdSuffix = ".debug"
            // Debug builds only carry MapLibre's native code for 64-bit ARM phones
            // (~25 MB instead of ~62 MB per install). For an x86_64 emulator, add
            // "x86_64" here.
            ndk { abiFilters += "arm64-v8a" }
        }
        release {
            // R8: removes unused code (incl. the debug-only branches behind
            // BuildConfig.DEBUG), shortens names, and drops unused resources.
            // Keep rules: proguard-rules.pro (libraries bring their own).
            isMinifyEnabled = true
            isShrinkResources = true
            // One APK for every real phone: 64-bit and 32-bit ARM (budget phones
            // can run a 32-bit Android). x86/x86_64 (emulators, Chromebooks) would
            // add ~26 MB; reconsider for a Play Store bundle, where each phone
            // downloads only its own ABI.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // No x86_64 on purpose: see the ABI filters in buildTypes.
        disable += "ChromeOsAbiSupport"
    }

    androidResources {
        // Only the app's own languages (like res/xml/locales_config.xml): library
        // texts (e.g. Material's) then never appear in a third language, or in
        // a test pseudo-locale, next to the app's English or Italian.
        localeFilters += listOf("en", "it")
    }

    buildFeatures {
        compose = true
        buildConfig = true // the About screen shows BuildConfig.VERSION_NAME
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // In-app language (also on Android < 13) and the light/dark setting.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Window size classes: side panel instead of the bottom sheet on wide screens.
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.maplibre.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
