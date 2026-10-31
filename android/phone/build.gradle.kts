import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
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
    namespace = "io.github.karthikeyags07.scheduleclock.phone"
    compileSdk = 36

    defaultConfig {
        // Same id (and signing key) as the watch's datasource app: the Data Layer only connects
        // apps with matching package names and signatures.
        applicationId = "io.github.karthikeyags07.scheduleclock"
        minSdk = 29
        targetSdk = 36
        // Phone and watch apps share one Play listing, so their version codes must differ: phone 1xxx, watch 2xxx.
        versionCode = 1001
        versionName = "0.9.0"
    }

    buildFeatures { compose = true }

    signingConfigs {
        signingKey("upload") { f, password, alias ->
            create("upload") { storeFile = f; storePassword = password; keyAlias = alias; keyPassword = password }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }
}

// The watch previews are the real watch face: the watch prototype (prototype/index.html, embed mode)
// shipped in the app's assets and shown in a WebView, so the phone always matches the face's design.
val copyFacePreview = tasks.register<Sync>("copyFacePreview") {
    from(rootProject.file("../prototype")) { include("index.html", "anta.ttf") }
    into(layout.buildDirectory.dir("generated/facePreview/face"))
}
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(layout.buildDirectory.dir("generated/facePreview").get().asFile.path)
    }
}
tasks.named("preBuild") { dependsOn(copyFacePreview) }

dependencies {
    implementation(libs.play.services.wearable)
    implementation(libs.work.runtime)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    implementation(libs.coroutines.guava)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.webkit)
    implementation(libs.wear.remote.interactions)
    implementation(libs.haze)                     // frosted nav bar and blurred drawer
}
