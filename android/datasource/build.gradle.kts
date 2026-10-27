plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.karthikeyags07.scheduleclock"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.karthikeyags07.scheduleclock"
        minSdk = 34 // Wear OS 5
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-spike"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(libs.wear.complications.data.source.ktx)
    implementation(libs.play.services.wearable)   // events from the phone app (Data Layer)
}
