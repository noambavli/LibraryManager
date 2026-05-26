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
