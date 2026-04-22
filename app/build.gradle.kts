/*
 * RakuRaku IME - EZ Input Method for Android
 * Copyright (C) 2026  RakuRaku IME Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
}

// Room schema JSONs are committed under app/schemas/ so future
// @AutoMigration / Migration additions have a baseline to diff against.
// See TODOs.md L8 for the migration policy.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "org.ghostsinthelab.app.rakurakuime"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "org.ghostsinthelab.app.rakurakuime"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.0.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // F-Droid's scanner rejects APKs containing AGP's "Dependency metadata"
    // signing block (an encrypted, Google-signed blob of the dependency list).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// Baseline profiles merged from AAR dependencies are not byte-reproducible
// across builds, which breaks F-Droid's reproducible-build verification
// (both assets/dexopt/baseline.prof and, via R8 profile-guided rewriting,
// classes.dex end up differing). Disable baseline-profile packaging and
// R8's profile-guided dex layout entirely.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.experimentalProperties.put(
            "android.experimental.art-profile-r8-rewriting", false
        )
        variant.experimentalProperties.put(
            "android.experimental.r8.dex-startup-optimization", false
        )
    }
}

tasks.matching { it.name.contains("ArtProfile") }.configureEach {
    enabled = false
}

// Name every build artifact as "<applicationId>-<versionName>", so the
// release APK lands as e.g. org.ghostsinthelab.app.rakurakuime-0.0.1-release.apk
// instead of the generic app-release.apk. Placed after the android block so
// defaultConfig.applicationId / versionName are populated at read-time.
base {
    archivesName = "${android.defaultConfig.applicationId}-${android.defaultConfig.versionName}"
}

dependencies {
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
// Captures showcase screenshots (settings + keyboard modes) without letting
// the test runner uninstall the app afterwards — AGP's connectedAndroidTest
// task always uninstalls, which wipes the app's external files dir, so we
// drive `am instrument` ourselves.
val adbPath: String = run {
    val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (sdk != null) "$sdk/platform-tools/adb" else "adb"
}
val screenshotRemoteDir = "/sdcard/Android/data/org.ghostsinthelab.app.rakurakuime/files/screenshots"
val screenshotLocalDir = layout.buildDirectory.dir("reports/screenshots")

// Locales to capture. Left: the `persist.sys.locale` tag we push to the
// device. Right: the fastlane locale folder under
// fastlane/metadata/android/ that the resulting PNGs land in.
val screenshotLocales = listOf(
    "en-US" to "en-US",
    "zh-Hant-TW" to "zh-TW",
)

tasks.register("screenshotKeyboard") {
    group = "verification"
    description = "Capture showcase screenshots in en-US and zh-Hant-TW, copying each set into its matching fastlane locale folder."
    dependsOn("installDebug", "installDebugAndroidTest")

    doLast {
        val local = screenshotLocalDir.get().asFile.apply { mkdirs() }
        val pulled = File(local, "screenshots")
        val mapping = mapOf(
            "settings.png" to "01_settings.png",
            "keyboard_ez.png" to "02_ez_keyboard.png",
            "keyboard_english.png" to "03_english_keyboard.png",
            "keyboard_emoji.png" to "04_emoji_keyboard.png",
        )
        val fastlaneRoot = rootProject.file("fastlane/metadata/android")

        // Gradle 9 removed `project.exec`; we shell out directly instead of
        // going through `ExecOperations` since this is a side-effectful task
        // that already can't use the configuration cache.
        fun adb(vararg args: String) {
            val cmd = listOf(adbPath) + args
            val process = ProcessBuilder(cmd).inheritIO().start()
            val code = process.waitFor()
            check(code == 0) { "adb ${args.joinToString(" ")} exited with code $code" }
        }
        fun adbShell(vararg args: String) = adb("shell", *args)
        fun adbReadProp(name: String): String {
            val process = ProcessBuilder(adbPath, "shell", "getprop", name)
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            check(code == 0) { "adb getprop $name exited with code $code" }
            return out.trim()
        }
        val packageName = "org.ghostsinthelab.app.rakurakuime"

        // Per-app locale override via `cmd locale set-app-locales` (API 33+).
        // Preferred over `setprop persist.sys.locale` + `stop; start` because:
        //   * it works on user-build emulators (setprop is restricted),
        //   * it doesn't reboot the device, so each run is ~20s not ~60s,
        //   * only this app's resources are affected — the next MainActivity
        //     launch (and the IME service, which is in the same package)
        //     renders under the requested locale. Pass an empty string to
        //     clear the override and fall back to the device default.
        fun setAppLocale(locale: String) {
            adbShell("cmd", "locale", "set-app-locales", packageName, "--locales", locale)
            // Force-stop so the next launch starts fresh under the new
            // override; otherwise a still-resident process keeps its old
            // Configuration.
            adbShell("am", "force-stop", packageName)
            Thread.sleep(500)
        }

        for ((appLocale, fastlaneLocale) in screenshotLocales) {
            logger.lifecycle("→ Capturing $fastlaneLocale (app locale=$appLocale)")
            setAppLocale(appLocale)

            adbShell(
                "am", "instrument", "-w",
                "-e", "class", "org.ghostsinthelab.app.rakurakuime.KeyboardScreenshotTest",
                "org.ghostsinthelab.app.rakurakuime.test/androidx.test.runner.AndroidJUnitRunner",
            )

            pulled.deleteRecursively()
            adb("pull", screenshotRemoteDir, local.absolutePath)

            val dstDir = File(fastlaneRoot, "$fastlaneLocale/images/phoneScreenshots").apply { mkdirs() }
            for ((src, dst) in mapping) {
                File(pulled, src).copyTo(File(dstDir, dst), overwrite = true)
            }
            logger.lifecycle("   Copied into fastlane/metadata/android/$fastlaneLocale/")
        }

        // Clear the per-app override so developers don't find the app
        // stuck in whichever locale we captured last.
        logger.lifecycle("Clearing per-app locale override")
        setAppLocale("")
    }
}
