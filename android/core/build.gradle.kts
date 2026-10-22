// Data cleaning shared by the app and the command-line tool that publishes
// the data: plain Kotlin, no Android, no dependencies.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Same bytecode level as the app (Java 17), built with whichever JDK runs Gradle.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(libs.junit)
}

// ./gradlew :core:test -PupdateAppFixture rewrites the app's test fixture
// (app/src/test/resources/fixtures/*.json) from this module's output, after an
// intended change of the published format.
tasks.test {
    val update = providers.gradleProperty("updateAppFixture").isPresent
    inputs.property("updateAppFixture", update)
    systemProperty("updateAppFixture", update)
}
