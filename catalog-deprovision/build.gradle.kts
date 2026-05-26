// Not included in settings.gradle.kts by default (no LAUNCHER → breaks Android Studio Run).
// To build: add include(":catalog-deprovision") in settings.gradle.kts, then:
//   ./gradlew :catalog-deprovision:assembleDebug

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.mh.librarycatalog"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mh.librarycatalog"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "deprovision"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
