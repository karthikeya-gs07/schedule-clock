import java.io.ByteArrayOutputStream
import javax.inject.Inject

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

// Watch Face Push (PRD §6.5): this app installs the face itself. It carries one face per colourway, each
// checked by Google's validator, whose token the install call needs. The amber face is also the
// "default watch face" the push library expects at assets/default_watchface.apk.
val colourways = listOf("amber", "mint", "violet", "rose", "solaris")
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

val bundleFaces = tasks.register<BundleFacesTask>("bundleFaces") {
    val faceProject = project(":watchface")
    names.set(colourways)
    clientPackage.set(android.defaultConfig.applicationId)
    validator.from(faceValidator)
    colourways.forEach { c ->
        dependsOn(":watchface:assemble${c.replaceFirstChar(Char::uppercase)}Release")
        faceApks.from(faceProject.layout.buildDirectory.file("outputs/apk/$c/release/watchface-$c-release.apk"))
    }
    assetsDir.set(layout.buildDirectory.dir("generated/faces/assets"))
    resDir.set(layout.buildDirectory.dir("generated/faces/res"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundleFaces, BundleFacesTask::assetsDir)
        variant.sources.res?.addGeneratedSourceDirectory(bundleFaces, BundleFacesTask::resDir)
    }
}

dependencies {
    implementation(libs.wear.complications.data.source.ktx)
    implementation(libs.play.services.wearable)   // events from the phone app (Data Layer)
    implementation(libs.wear.watchfacepush)       // installs and swaps the face (PRD §6.5)
    implementation(libs.coroutines.android)
    faceValidator(libs.face.validator.cli)
}
