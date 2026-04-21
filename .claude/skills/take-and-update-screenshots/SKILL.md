---
name: take-and-update-screenshots
description: Capture the four RakuRaku showcase screenshots on a connected device and refresh the fastlane metadata. Use when the user asks to take, update, or regenerate screenshots.
---

# Take and Update Screenshots

You are refreshing the showcase screenshots for RakuRaku IME (輕鬆輸入法):

1. `01_settings.png` — MainActivity (settings screen)
2. `02_ez_keyboard.png` — EZ Chinese keyboard
3. `03_english_keyboard.png` — English keyboard
4. `04_emoji_keyboard.png` — Emoji picker

All four are produced by a single Gradle task, which:

- runs the instrumented `KeyboardScreenshotTest`,
- writes PNGs to `app/build/reports/screenshots/screenshots/`,
- and copies them into both
  `fastlane/metadata/android/zh-TW/images/phoneScreenshots/` and
  `fastlane/metadata/android/en-US/images/phoneScreenshots/` under the
  existing `01_…`–`04_…` filenames.

The task uses `adb shell am instrument` rather than
`connectedAndroidTest` so the app is not uninstalled after the run —
otherwise the app's external-files dir (where screenshots land) gets
wiped before we can pull them.

## Preconditions

- A single device (physical or emulator) is connected (`adb devices`
  shows exactly one entry in `device` state).
- The device screen is unlocked. The test's `@Before` wakes the device
  and dismisses the keyguard, but a lockscreen password cannot be
  bypassed — ask the user to unlock first if needed.
- Locale: the test matches the mode key by `R.string.a11y_key_mode_to_*`
  resolved against the app resources, so any locale on the device will
  work — but the screenshot *content* inherits the device locale. If
  the device is zh-TW, both locale folders end up with zh-TW shots. Warn
  the user when the device locale does not match what they expect, and
  offer to skip the copy step and stage only the matching locale.

## Workflow

1. **Check the device.** Run `adb devices` and confirm exactly one is
   connected and authorised. If not, stop and ask the user.
2. **Run the Gradle task.**
   ```bash
   ./gradlew :app:screenshotKeyboard
   ```
   This installs `:app:installDebug` + `:app:installDebugAndroidTest`,
   runs the test, pulls the PNGs, and copies them into both fastlane
   locale folders.
3. **Spot-check the results.** Read at least `01_settings.png` and
   `02_ez_keyboard.png` from
   `app/build/reports/screenshots/screenshots/` to confirm they aren't
   blank/black (sleeping screen) or caught mid-animation ("Preparing
   dictionary…" splash still visible). If they look wrong, ask the user
   to wake/unlock the device and re-run — don't blindly commit.
4. **Review the diff.**
   ```bash
   git status
   git diff --stat fastlane/metadata/
   ```
   Expect four modified PNGs per locale directory.
5. **Stop there unless the user asks to commit.** Do not run
   `commit-and-push` yourself — the user usually reviews the new
   screenshots visually in Android Studio's Git tool before committing.

## Failure modes to watch for

- **Black screenshots** → screen was asleep. Wake the device and re-run.
- **"Preparing dictionary…" shown in settings.png** → MainActivity's
  first-launch dictionary seed is still running; the test's 6-second
  sleep is supposed to cover this. Re-run once; if it still repros,
  increase the sleep in `KeyboardScreenshotTest.captureShowcaseScreenshots`.
- **IllegalStateException "Mode key … not found in IME window"** →
  UiAutomation didn't see the IME window's accessibility tree. Most
  common cause is the IME hasn't actually been selected: confirm with
  `adb shell settings get secure default_input_method` and re-run.
- **`adb pull` fails with "No such file or directory"** → the app was
  uninstalled before the pull. That means someone ran
  `connectedAndroidTest` instead of `screenshotKeyboard`; use the
  task.
- **Fastlane locale mismatch** → device locale determines screenshot
  content. If the user wants a true en-US set, have them run
  `adb shell settings put system system_locales en-US` (requires a
  process/device restart to take effect on system chrome), re-run the
  task, and copy only to `en-US/`.
