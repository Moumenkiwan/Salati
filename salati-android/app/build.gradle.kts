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
        val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = run
        versionName = "1.$run"
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
