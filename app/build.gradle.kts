plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.iraqgameroute.x1"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.iraqgameroute.x1"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    buildFeatures { buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("com.wireguard.android:tunnel:1.0.20260102")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
}

// Kotlin 2.4 requires the typed compilerOptions DSL; kotlinOptions.jvmTarget is an error.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
