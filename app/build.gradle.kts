plugins {
    id("com.android.application")
}

android {
    namespace = "com.coeric.universalbrowser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.coeric.universalbrowser"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"

        // Redmi A1 uses a 32-bit ARM CPU/OS configuration.
        // Build a single ARMv7 APK so the phone receives the correct native GeckoView libraries.
        ndk {
            abiFilters += "armeabi-v7a"
        }
    }

    // Do not generate separate ABI APKs. The ARMv7 filter above produces one
    // directly installable APK for Redmi A1 instead of requiring the user to
    // choose/download the correct split.
    splits {
        abi {
            isEnable = false
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview:153.0.20260727124451")
}
