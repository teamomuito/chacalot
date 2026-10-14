plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.chacalot"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.chacalot"
        minSdk = 26
        targetSdk = 34
        // CI run number = version code, so every build is newer than the last.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.$versionCode"
    }
    // Committed on purpose (personal app): one stable key lets updates install over the old APK.
    signingConfigs.getByName("debug") {
        storeFile = file("chacalot.jks")
        storePassword = "chacalot"
        keyAlias = "chacalot"
        keyPassword = "chacalot"
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    kotlinOptions { jvmTarget = "17" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
}
