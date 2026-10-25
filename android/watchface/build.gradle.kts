plugins {
    alias(libs.plugins.android.application)
}

android {
    // Watch Face Format packages contain no code.
    enableKotlin = false
    namespace = "io.github.karthikeyags07.scheduleclock.watchface"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.karthikeyags07.scheduleclock.watchface"
        minSdk = 34 // Wear OS 5: WFF v2 (WeightedStroke on Arc)
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-spike"
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
        }
        release {
            isMinifyEnabled = true
            // Keep every resource: the system reads them from watchface.xml, not code.
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
