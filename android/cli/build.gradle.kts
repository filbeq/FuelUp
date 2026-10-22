// Command line that builds the published data (stations.json, meta.json) with
// the same cleaning code as the app. Run: ./gradlew :cli:run --args="--help"
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

application {
    mainClass = "io.github.filbeq.fuelup.cli.MainKt"
    applicationName = "fuel-data"
}

// Relative paths in --args are relative to where Gradle was started.
tasks.named<JavaExec>("run") {
    workingDir = providers.environmentVariable("PWD").map { File(it) }.getOrElse(rootDir)
}

dependencies {
    implementation(project(":core"))
}
