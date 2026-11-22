import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---- Versioning -------------------------------------------------------------
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val verMajor = versionProps.getProperty("major", "1")
val verMinor = versionProps.getProperty("minor", "0")
// CI passes -PbuildNumber=<github run number>. Local builds default to 1.
val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1

// ---- Signing (optional, from CI secrets) ------------------------------------
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")
val hasReleaseKey = keystorePath != null && file(keystorePath).exists()

android {
    namespace = "com.obdroar.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.obdroar.app"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "$verMajor.$verMinor.$buildNumber"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
