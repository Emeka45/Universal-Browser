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
        versionCode = 7
        versionName = "0.7.0"

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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview:153.0.20260727124451")
    implementation("com.google.android.gms:play-services-ads:25.4.0")
}
