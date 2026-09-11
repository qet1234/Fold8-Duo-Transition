plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.qet1234.fold8duotransition"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.qet1234.fold8duotransition"
        minSdk = 33
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
