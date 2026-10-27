plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.karthikeyags07.scheduleclock.phone"
    compileSdk = 36

    defaultConfig {
        // Same id (and signing key) as the watch's datasource app: the Data Layer only connects
        // apps with matching package names and signatures.
        applicationId = "io.github.karthikeyags07.scheduleclock"
        minSdk = 29
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
    implementation(libs.play.services.wearable)
    implementation(libs.work.runtime)
}
