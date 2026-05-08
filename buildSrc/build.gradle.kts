/*
 * RakuRaku IME - EZ Input Method for Android
 * Copyright (C) 2026  RakuRaku IME Contributors
 *
 * Licensed under GPL-3.0-or-later. See the project LICENSE file for the
 * full text.
 */

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    // SQLite driver for assembling the prebuilt Room asset at build time.
    // See ImeDbBuilder.kt and the :app:buildImeDb task.
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
}
