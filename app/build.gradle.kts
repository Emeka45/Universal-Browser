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
        versionCode = 8
        versionName = "1.0.0"

        // One Universal Browser APK carrying both low-end ARMv7 and modern ARM64 native libraries.
        // Android selects the correct ABI at install/runtime; this is not two different browser products.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    // Do not split the application into separate ABI APKs. Direct APK distribution produces
    // one package containing both supported ARM ABIs.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        getByName("release") {
            // Keep release behavior stable for the first public device-validation build.
            // Shrinking/obfuscation can be enabled later once the production mapping rules are validated.
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview:153.0.20260727124451")
    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
