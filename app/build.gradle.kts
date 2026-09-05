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
        versionCode = 2
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview:157.0.20260904092011")
}
