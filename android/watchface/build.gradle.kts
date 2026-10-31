import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
}

// Release signing (PRD §6.5): the keys live outside the repo; keystore.properties (not in git) says where.
// Without it, release builds fall back to the debug key so they still build anywhere.
val keystore = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun signingKey(name: String, block: (file: File, password: String, alias: String) -> Unit) {
    val file = keystore.getProperty("${name}StoreFile") ?: return
    block(file(file), file(keystore.getProperty("${name}PasswordFile")).readText().trim(), keystore.getProperty("${name}Alias"))
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
        create("quartz") { dimension = "colourway" }
        create("copper") { dimension = "colourway" }
        create("ocean") { dimension = "colourway" }
        create("aurora") { dimension = "colourway" }
        create("acid") { dimension = "colourway" }
        create("lunar") { dimension = "colourway" }
        create("desert") { dimension = "colourway" }
        create("lime") { dimension = "colourway" }
        create("plum") { dimension = "colourway" }
        create("forest") { dimension = "colourway" }
        create("arctic") { dimension = "colourway" }
    }

    signingConfigs {
        signingKey("faces") { f, password, alias ->
            create("faces") { storeFile = f; storePassword = password; keyAlias = alias; keyPassword = password }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
            // Same key as release: a face can only be swapped for one with the same signer.
            signingConfig = signingConfigs.findByName("faces") ?: signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            // Keep every resource: the system reads them from watchface.xml, not code.
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("faces") ?: signingConfigs.getByName("debug")
        }
    }
}
