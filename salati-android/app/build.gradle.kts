plugins {
    id("com.android.application")
}

android {
    namespace = "com.momen.salati"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.momen.salati"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("salati.keystore")
            storePassword = "salati123"
            keyAlias = "salati"
            keyPassword = "salati123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
