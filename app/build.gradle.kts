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
        versionCode = 4
        versionName = "1.0.2"

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

val runKeyboardScreenshotTest = tasks.register<Exec>("runKeyboardScreenshotTest") {
    group = "verification"
    description = "Run KeyboardScreenshotTest without uninstalling the app afterwards."
    dependsOn("installDebug", "installDebugAndroidTest")
    commandLine(
        adbPath, "shell", "am", "instrument", "-w",
        "-e", "class", "org.ghostsinthelab.app.rakurakuime.KeyboardScreenshotTest",
        "org.ghostsinthelab.app.rakurakuime.test/androidx.test.runner.AndroidJUnitRunner",
    )
}

tasks.register<Exec>("screenshotKeyboard") {
    group = "verification"
    description = "Run KeyboardScreenshotTest, pull the PNGs, and copy them into fastlane/."
    dependsOn(runKeyboardScreenshotTest)
    val local = screenshotLocalDir.get().asFile
    doFirst { local.mkdirs() }
    commandLine(adbPath, "pull", screenshotRemoteDir, local.absolutePath)
    doLast {
        val pulled = File(local, "screenshots")
        val mapping = mapOf(
            "settings.png" to "01_settings.png",
            "keyboard_ez.png" to "02_ez_keyboard.png",
            "keyboard_english.png" to "03_english_keyboard.png",
            "keyboard_emoji.png" to "04_emoji_keyboard.png",
        )
        val fastlaneRoot = rootProject.file("fastlane/metadata/android")
        for (locale in listOf("zh-TW", "en-US")) {
            val dstDir = File(fastlaneRoot, "$locale/images/phoneScreenshots").apply { mkdirs() }
            for ((src, dst) in mapping) {
                File(pulled, src).copyTo(File(dstDir, dst), overwrite = true)
            }
        }
        logger.lifecycle("Screenshots pulled to: $local and copied into fastlane/ for zh-TW and en-US")
    }
}
