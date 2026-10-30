plugins {
    alias(libs.plugins.android.application)
}

android {
    // Watch Face Format packages contain no code.
    enableKotlin = false
    namespace = "io.github.karthikeyags07.scheduleclock.watchface"
    compileSdk = 36

    defaultConfig {
        // Watch Face Push (PRD §6.5): the watch app installs this face, so its id must be the watch app's id,
        // then `.watchfacepush.`, then the face's name.
        applicationId = "io.github.karthikeyags07.scheduleclock.watchfacepush.calentime"
        minSdk = 34 // Wear OS 5: WFF v2 (WeightedStroke on Arc)
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    // One face per colourway (RULES §9), same id: the watch app swaps them in its slot when the phone app
    // picks another. Each has its colours fixed (tools/build_faces.sh writes src/<colourway>/res).
    flavorDimensions += "colourway"
    productFlavors {
        create("amber") { dimension = "colourway" }
        create("mint") { dimension = "colourway" }
        create("violet") { dimension = "colourway" }
        create("rose") { dimension = "colourway" }
        create("solaris") { dimension = "colourway" }
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
