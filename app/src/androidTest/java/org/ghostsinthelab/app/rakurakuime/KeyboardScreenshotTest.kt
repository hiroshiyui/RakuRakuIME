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

package org.ghostsinthelab.app.rakurakuime

import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures the four showcase screenshots for the project:
 *   1. settings.png  — MainActivity (settings screen)
 *   2. keyboard_ez.png
 *   3. keyboard_english.png
 *   4. keyboard_emoji.png
 *
 * Run via the custom Gradle task (does NOT uninstall the app afterwards so
 * the screenshots remain readable under the app's external files dir):
 *
 *   ./gradlew :app:screenshotKeyboard
 *
 * The task pulls them to build/reports/screenshots/. Do not use
 * `connectedDebugAndroidTest` directly — it uninstalls the app at the end
 * and the external files dir is wiped with it.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardScreenshotTest {

    private val automation: UiAutomation
        get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private var previousIme: String? = null

    @Before
    fun switchToRakuRakuIme() {
        // Wake the device and dismiss the keyguard; a dark/locked screen
        // will make UiAutomation.takeScreenshot() return an all-black bitmap.
        device.wakeUp()
        shell("wm dismiss-keyguard")
        previousIme = shell("settings get secure default_input_method").trim().takeIf { it.isNotEmpty() }
        shell("ime enable $IME_ID")
        shell("ime set $IME_ID")
        device.waitForIdle(1_000)
        val active = shell("settings get secure default_input_method").trim()
        check(active == IME_ID) {
            "Failed to switch IME. Expected=$IME_ID active=$active"
        }
    }

    @After
    fun restorePreviousIme() {
        previousIme?.takeIf { it.isNotEmpty() && it != IME_ID && it != "null" }?.let { prev ->
            shell("ime set $prev")
        }
    }

    @Test
    fun captureShowcaseScreenshots() {
        // Write into the app's own external-files dir — always accessible
        // without runtime permissions on scoped storage, and still pullable
        // via adb from /sdcard/Android/data/<appPkg>/files/screenshots/.
        val testCtx = InstrumentationRegistry.getInstrumentation().context
        val appCtx = InstrumentationRegistry.getInstrumentation().targetContext
        val outDir = File(appCtx.getExternalFilesDir(null), "screenshots")
            .apply { mkdirs() }
        check(outDir.exists()) { "Could not create $outDir" }

        // 1. Settings screen (MainActivity). Extra sleep covers the
        // "Preparing dictionary…" splash on cold first launch.
        appCtx.startActivity(
            Intent(appCtx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        device.waitForIdle(2_000)
        Thread.sleep(6_000)
        capture(File(outDir, "settings.png"))

        // 2. Keyboard over a generic EditText, starting in EZ mode.
        testCtx.startActivity(
            Intent(testCtx, ScreenshotHostActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 5_000)
        device.findObject(By.clazz("android.widget.EditText"))?.click()
        device.waitForIdle(1_500)
        Thread.sleep(1_500)
        capture(File(outDir, "keyboard_ez.png"))

        // 3. English keyboard — tap the mode key (EZ → ENGLISH).
        tapModeKey(descOnCurrentMode = appCtx.getString(R.string.a11y_key_mode_to_english))
        capture(File(outDir, "keyboard_english.png"))

        // 4. Emoji keyboard — ENGLISH → NUMBER → EMOJI.
        tapModeKey(descOnCurrentMode = appCtx.getString(R.string.a11y_key_mode_to_numbers))
        tapModeKey(descOnCurrentMode = appCtx.getString(R.string.a11y_key_mode_to_emoji))
        capture(File(outDir, "keyboard_emoji.png"))
    }

    private fun tapModeKey(descOnCurrentMode: String) {
        // UiAutomator's default hierarchy dump excludes IME windows, so we
        // can't use By.desc(...). Instead we iterate AccessibilityWindowInfo
        // and search for the Compose node whose contentDescription matches
        // the mode key's current label.
        val bounds = findNodeBoundsInImeWindow(descOnCurrentMode)
            ?: error("Mode key with description '$descOnCurrentMode' not found in IME window")
        device.click(bounds.centerX(), bounds.centerY())
        device.waitForIdle(1_000)
        Thread.sleep(800)
    }

    private fun findNodeBoundsInImeWindow(description: String): Rect? {
        for (window in automation.windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
            val root = window.root ?: continue
            val match = findByDescription(root, description) ?: continue
            val rect = Rect()
            match.getBoundsInScreen(rect)
            return rect
        }
        return null
    }

    private fun findByDescription(node: AccessibilityNodeInfo, description: String): AccessibilityNodeInfo? {
        if (description == node.contentDescription?.toString()) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findByDescription(child, description)?.let { return it }
        }
        return null
    }

    private fun capture(out: File) {
        val bitmap: Bitmap = automation.takeScreenshot()
            ?: error("UiAutomation.takeScreenshot returned null")
        FileOutputStream(out).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    private fun shell(command: String): String {
        val pfd: ParcelFileDescriptor = automation.executeShellCommand(command)
        return FileInputStream(pfd.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    companion object {
        private const val IME_ID =
            "org.ghostsinthelab.app.rakurakuime/.ime.RakuRakuImeService"
    }
}
