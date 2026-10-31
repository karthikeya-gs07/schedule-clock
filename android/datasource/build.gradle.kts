import java.util.Properties
import java.io.ByteArrayOutputStream
import javax.inject.Inject

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
    namespace = "io.github.karthikeyags07.scheduleclock"
    compileSdk = 36

    buildFeatures { buildConfig = true }   // BuildConfig.DEBUG: test links (TapActivity)

    defaultConfig {
        applicationId = "io.github.karthikeyags07.scheduleclock"
        minSdk = 36 // Wear OS 6: Watch Face Push, which installs the face (Play only offers it to watches that can use it)
        targetSdk = 36
        // Phone and watch apps share one Play listing, so their version codes must differ: phone 1xxx, watch 2xxx.
        versionCode = 2002
        versionName = "0.9.1"
    }

    signingConfigs {
        signingKey("upload") { f, password, alias ->
            create("upload") { storeFile = f; storePassword = password; keyAlias = alias; keyPassword = password }
        }
    }

    buildTypes {
        // Debug builds are signed with the same key as release (when the key is on this machine), so the apps on
        // your devices can move between debug and release builds without being uninstalled.
        debug {
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }
}

// Watch Face Push (PRD §6.5): this app installs the face itself. It carries one face per colourway, each
// checked by Google's validator, whose token the install call needs. The amber face is also the
// "default watch face" the push library expects at assets/default_watchface.apk.
val colourways = listOf("amber", "mint", "violet", "rose", "solaris",
    "quartz", "copper", "ocean", "aurora", "acid", "lunar", "desert", "lime", "plum", "forest", "arctic")
val faceValidator: Configuration by configurations.creating { isTransitive = false }

abstract class BundleFacesTask : DefaultTask() {
    @get:InputFiles abstract val faceApks: ConfigurableFileCollection
    @get:Input abstract val names: ListProperty<String>
    @get:Input abstract val clientPackage: Property<String>
    @get:InputFiles abstract val validator: ConfigurableFileCollection
    @get:OutputDirectory abstract val assetsDir: DirectoryProperty
    @get:OutputDirectory abstract val resDir: DirectoryProperty
    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun bundle() {
        val faces = assetsDir.get().dir("faces").asFile.apply { deleteRecursively(); mkdirs() }
        val tokens = names.get().zip(faceApks.files.toList()).associate { (name, apk) ->
            apk.copyTo(faces.resolve("$name.apk"), overwrite = true)
            val out = ByteArrayOutputStream()
            exec.javaexec {
                classpath(validator)
                mainClass.set("com.google.android.wearable.watchface.validator.cli.DwfValidation")
                args("--apk_path=${apk.absolutePath}", "--package_name=${clientPackage.get()}")
                standardOutput = out
                errorOutput = out
            }
            val log = out.toString()
            val token = Regex("generated token: (\\S+)").find(log)?.groupValues?.get(1)
                ?: throw GradleException("The $name face failed Watch Face Push validation:\n$log")
            name to token
        }
        faces.resolve("tokens.json").writeText(
            tokens.entries.joinToString(",\n", "{\n", "\n}\n") { (n, t) -> "  \"$n\": \"$t\"" })
        faces.parentFile.resolve("default_watchface.apk").let { faces.resolve("amber.apk").copyTo(it, overwrite = true) }
        resDir.get().dir("values").asFile.apply { mkdirs() }.resolve("face_tokens.xml").writeText(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n" +
                "    <string name=\"default_wf_token\" translatable=\"false\">${tokens.getValue("amber")}</string>\n</resources>\n")
    }
}

// Debug builds carry debug-signed faces and release builds faces signed with the faces key. A face can only be
// swapped for one with the same signer, so a watch set up from a debug build keeps working with debug builds.
fun bundleFaces(buildType: String) = tasks.register<BundleFacesTask>("bundleFaces${buildType.replaceFirstChar(Char::uppercase)}") {
    val faceProject = project(":watchface")
    names.set(colourways)
    clientPackage.set(android.defaultConfig.applicationId)
    validator.from(faceValidator)
    colourways.forEach { c ->
        val variant = c + buildType.replaceFirstChar(Char::uppercase)
        dependsOn(":watchface:assemble${variant.replaceFirstChar(Char::uppercase)}")
        faceApks.from(faceProject.layout.buildDirectory.file("outputs/apk/$c/$buildType/watchface-$c-$buildType.apk"))
    }
    assetsDir.set(layout.buildDirectory.dir("generated/faces/$buildType/assets"))
    resDir.set(layout.buildDirectory.dir("generated/faces/$buildType/res"))
}
val bundleFacesDebug = bundleFaces("debug")
val bundleFacesRelease = bundleFaces("release")

androidComponents {
    onVariants { variant ->
        val faces = if (variant.buildType == "release") bundleFacesRelease else bundleFacesDebug
        variant.sources.assets?.addGeneratedSourceDirectory(faces, BundleFacesTask::assetsDir)
        variant.sources.res?.addGeneratedSourceDirectory(faces, BundleFacesTask::resDir)
    }
}

dependencies {
    implementation(libs.wear.complications.data.source.ktx)
    implementation(libs.play.services.wearable)   // events from the phone app (Data Layer)
    implementation(libs.wear.watchfacepush)       // installs and swaps the face (PRD §6.5)
    implementation(libs.coroutines.android)
    faceValidator(libs.face.validator.cli)
}
