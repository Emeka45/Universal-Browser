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

        // Ship both low-end ARMv7 and modern ARM64 builds from the same source.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
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
    implementation("com.google.android.gms:play-services-ads:25.4.0")
}
