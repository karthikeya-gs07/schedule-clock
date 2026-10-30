plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
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
        versionCode = 2
        versionName = "0.2.0"
    }

    buildFeatures { compose = true }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
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
}
