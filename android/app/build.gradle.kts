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
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            // Debug builds only carry MapLibre's native code for 64-bit ARM phones
            // (~25 MB instead of ~62 MB per install). For an x86_64 emulator, add
            // "x86_64" here. Release bundles are unaffected: the Play Store delivers
            // only the code each phone needs.
            ndk { abiFilters += "arm64-v8a" }
        }
        release {
            isMinifyEnabled = false
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
        // Debug builds are arm64-only on purpose (see buildTypes.debug); release
        // bundles keep every ABI, including x86_64 for ChromeOS.
        disable += "ChromeOsAbiSupport"
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
    implementation(libs.maplibre.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
