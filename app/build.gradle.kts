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
        versionCode = 5
        versionName = "0.3.1"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        create("diagnostic") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".diagnostics"
            versionNameSuffix = "-diagnostics"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
