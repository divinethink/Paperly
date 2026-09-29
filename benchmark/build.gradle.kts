plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.paperly.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Must match the app's "benchmark" build type (release-like, debug-signed, never shipped).
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

// Only the benchmark variant is meaningful here, so plain CI tasks (testDebugUnitTest, assembleDebug) skip this module.
androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
}
