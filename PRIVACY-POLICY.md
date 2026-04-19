# Privacy Policy

RakuRaku IME (新修輕鬆輸入法) is a Traditional Chinese input method for
Android. This policy describes what the app does — and does not do — with
data on your device.

## What the app does NOT do

- It does **not** send your keystrokes anywhere.
- It does **not** make any network connections.
- It does **not** include analytics, crash reporting, or advertising SDKs.
- It does **not** request or access the clipboard, contacts, microphone,
  camera, accounts, SMS, or your location.

## What the app stores locally

Everything stays on your device.

- **Dictionary** — the EZ Input Method character and phrase database,
  shipped pre-populated inside the app. User-specific frequency counts
  reflecting which candidates you selected are saved in the same on-device
  Room database so that common choices float to the top next time. You
  can clear these via *Settings → Dictionary Management → Reset Learning
  (Frequencies)*.
- **Preferences** — theme selection, keypress-vibration toggle and
  intensity, keyboard-height scale, split-landscape layout toggle. Stored
  via Android DataStore, scoped to the app's private directory.
- **Dictionary-asset fingerprint** — a SHA-256 hash of the bundled
  `ezbig.utf-8.cin` file, stored in private SharedPreferences. It is used
  to detect when an app update ships an updated dictionary so the
  on-device copy can be refreshed automatically.

## Permissions

The app declares a single permission:

- **`android.permission.VIBRATE`** — for optional haptic feedback on key
  presses. You can disable this at any time from *Settings → Keypress
  Vibration*.

## Third-party code and data bundled with the app

The app ships with the following resources, none of which access the
network or perform tracking:

- **EZ dictionary data** (`ezbig.utf-8.cin`), originally produced by
  高衡緒 / 輕鬆資訊企業社, distributed under GPLv2 and the 《輕鬆資訊
  「輕鬆輸入法大詞庫」公眾授權書》.
- **English word list** (`google_10k_english.txt`) — Josh Kaufman's
  Google 10000 English corpus, MIT License.
- **Emoji layout** (`emoji.json`) — adapted from the MeaninglessKeyboard
  project, GPL-3.0.

All of the above are compiled into the APK and read locally.

## Data transfer

No user data is transmitted off-device by this app.

## Children

The app does not knowingly collect personal data from anyone, and as it
collects no personal data at all, it does not treat children's data
differently from anyone else's.

## Contact

Questions about this policy can be opened as issues on the project
repository:
<https://github.com/hiroshiyui/RakuRakuIME/issues>

## Changes

This policy may be updated alongside app releases. Material changes will
be noted in the project changelog; the full revision history is visible
in this document's git log.
