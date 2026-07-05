import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is read from keystore.properties at the repo root. That file
// (and the .jks keystore) is NOT committed to git — see keystore.properties.example.
// The same keystore MUST be reused for every update, because sealed tablets
// reject an APK signed with a different key and cannot be uninstalled.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

// App secrets (developer key + default management password) are read from
// secrets.properties at the repo root. That file is NOT committed to git — see
// secrets.properties.example. This keeps the passwords out of the source code.
val secretsPropertiesFile = rootProject.file("secrets.properties")
val secretsProperties = Properties().apply {
    if (secretsPropertiesFile.exists()) {
        FileInputStream(secretsPropertiesFile).use { load(it) }
    }
}

// Developer key gates the in-app developer dashboard (the ONLY place an APK can
// be installed). The plaintext key lives ONLY in secrets.properties (never
// shipped); the APK embeds a salted SHA-256 HASH of it, so decompiling the APK
// cannot reveal the actual key. Empty when secrets.properties is missing, which
// simply makes the developer dashboard unreachable until the file is created.
val developerKey: String = secretsProperties.getProperty("devKey", "")

// Salt is not secret — it only prevents precomputed (rainbow-table) lookups.
// Bump the suffix if you ever want to invalidate old hashes.
val developerKeySalt = "mh-library-devkey-v1"

fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

val developerKeyHash: String =
    if (developerKey.isBlank()) "" else sha256Hex(developerKeySalt + developerKey)

// Default management password. The management gate resets to this value.
// Comment: the app ships defaulting to "1111" (overridable in secrets.properties);
// it can be changed at runtime from the developer dashboard. (Not hashed — the
// management password is not treated as a secret.)
val managementDefaultPassword: String = secretsProperties.getProperty("managementDefaultPassword", "1111")

android {
    namespace = "com.mh.librarymanager"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mh.librarymanager"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Injected from secrets.properties (never hardcoded in Kotlin sources).
        // The developer key is embedded only as a salted hash + its length (the
        // length is needed so the keypad can always fit it); the plaintext key
        // is never placed in the APK. See secrets.properties.example.
        buildConfigField("String", "DEV_KEY_HASH", "\"$developerKeyHash\"")
        buildConfigField("String", "DEV_KEY_SALT", "\"$developerKeySalt\"")
        buildConfigField("int", "DEV_KEY_LENGTH", "${developerKey.length}")
        buildConfigField("String", "MANAGEMENT_DEFAULT_PASSWORD", "\"$managementDefaultPassword\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the release keystore only when it is configured; otherwise a
            // plain `assembleRelease` on a machine without keystore.properties
            // still configures (it just won't produce a signed APK).
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    lint {
        // Release builds must not depend on downloading the lint tool at build
        // time (the tablet/build network may block it). Lint still runs on
        // debug/explicit `lint` tasks.
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
