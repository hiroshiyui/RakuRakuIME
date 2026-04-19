# RakuRaku IME TODOs

This list tracks the missing features and planned improvements for RakuRaku IME, modeled after professional IME standards (like JustArray and Gboard).

## Input & Logic
- [x] **Frequency-Based Sorting:** Dynamically move frequently used characters to the front of the candidate list based on user history.
- [x] **Auto-Select:** Automatically commit a character if it is the only possible match for a sequence.
- [x] **English Word Prediction:** Suggest English words as you type in English/Shifted mode.
- [ ] **Physical Keyboard Support:** Logic to handle typing when a Bluetooth or USB keyboard is attached.
- [ ] **Pro Shift Key:** Implement "one-shot" shift (resets after one key) and "Caps Lock" (double-tap).

## User Interface (UI)
- [x] **Advanced Symbol Panels:** Categorized symbol pages (Punctuation, Math, Arrows, Emoticons) instead of a single row.
- [x] **Key Alternates:** Show a popup of related characters (e.g., accented vowels) on long-press.
- [x] **Adjustable Keyboard Height:** Allow users to scale the keyboard height in settings.
- [x] **Dynamic Material You Theming:** support wallpaper-derived colors on Android 12+.
- [x] **Split Keyboard Layout:** A specialized layout for landscape orientation or tablets.

## System & Settings
- [x] **Settings Screen:** A dedicated UI in `MainActivity` to manage preferences.
    - [x] Vibration intensity/toggle.
    - [x] Dictionary management (re-import/reset).
    - [x] "About" section with full attribution for **高衡緒** and **輕鬆資訊企業社**.
- [ ] **Accessibility (TalkBack):** Add comprehensive `semantics` labels for candidates, roots, and function keys.

## Technical Debt / Performance
- [x] **Database Optimization:** Ensure Room queries for prefix matching remain performant as usage history grows.
- [x] **Initialization UX:** Add a progress bar for the first-run dictionary import.

## Code Review & Security Audit (2026-04-19)

Findings from the `/code-review-and-security-audit` pass on 2026-04-19.
Items are grouped by severity; each carries the file/line reference,
observed issue, user-visible impact, and the fix direction.

### High

- [ ] **H1 — Vibration preferences silently ignored + coroutine leak per keystroke**
    - **File:** `ime/RakuRakuImeService.kt:68-84`.
    - **Issue:** `handleKeyPress()` calls the extension
      `Flow<T>.stateIn(scope, SharingStarted.Eagerly, initial).value` on
      every key press. `stateIn` returns a StateFlow whose `.value` is
      the `initialValue` until its first upstream emission arrives
      asynchronously; reading immediately always returns `true` / `0.5f`.
      A fresh `Eagerly`-started collector is also spawned per keystroke
      and bound to `lifecycleOwner.lifecycleScope`, so one typing
      session accumulates hundreds of zombie collectors on the DataStore
      flow.
    - **Impact:** The user's Vibration Enabled toggle and intensity
      slider in Settings have no real effect — vibration is always on
      at 0.5 amplitude. Memory/CPU grow with typing.
    - **Fix:** Collect once in `onCreate()` into member `StateFlow`s
      (`vibrationEnabled`, `vibrationIntensity`) using
      `SharingStarted.Eagerly`, then read `.value` per key press.
      Remove the per-call extension.

- [ ] **H2 — Resource leak in `CinParser.parseAndPopulate`**
    - **File:** `data/CinParser.kt:73-114`.
    - **Issue:** `inputStream` / `reader` are opened outside a
      `use { }` / try-finally block. The closing `inputStream.close()`
      at the bottom is unreachable if `withTransaction { … }` throws
      (malformed line, DB insert error, cancellation).
    - **Impact:** File descriptor leak on any parse failure; repeated
      failed syncs could exhaust FDs on low-resource devices.
    - **Fix:** Wrap the whole block in
      `context.assets.open(ASSET_NAME).bufferedReader().use { reader -> database.withTransaction { … } }`
      and delete the trailing manual `close()`.

- [ ] **H3 — Stale `currentInputConnection` snapshot passed into Compose**
    - **File:** `ime/RakuRakuImeService.kt:101-103` (and the call sites
      in `ui/keyboard/KeyboardScreen.kt` that call `.commitText(…)`).
    - **Issue:** `setContent` captures `currentInputConnection` as a
      Compose parameter. Android calls `onCreateInputView` once per
      enable-cycle, not per `onStartInputView`; when the user switches
      text fields mid-session, the service's `currentInputConnection`
      property updates but the snapshot held by the retained Compose
      tree does not.
    - **Impact:** Key taps (including candidate selection) can commit
      text to the previous input field instead of the one the user is
      now focused on.
    - **Fix:** Change the `KeyboardScreen` signature to take a
      `() -> InputConnection?` provider and invoke it per callback, or
      read `currentInputConnection` dynamically inside the service's
      `onKeyPress` bridge. All call sites must switch from
      `currentInputConnection?.commitText(…)` to `inputConnection()?.commitText(…)`.

### Medium

- [ ] **M1 — Unguarded JSON parse in `EmojiDictionary.parse`**
    - **File:** `data/EmojiDictionary.kt:62-77`.
    - **Issue:** `root.getJSONObject("layouts")`, inner
      `.getJSONArray("rows")`, `.getJSONObject(i)` all throw
      `JSONException` on an unexpected schema. The asset is bundled so
      this shouldn't fire in practice, but a corrupt APK, partial
      extraction, or future schema edit crashes the IME's first entry
      into EMOJI mode with no fallback.
    - **Impact:** Crash → IME surface becomes unusable until mode is
      switched back; no degraded experience.
    - **Fix:** Wrap the whole `parse()` body in
      `runCatching { … }.getOrElse { emptyList() }`, or switch to
      `optJSONObject` / `optJSONArray` and return `emptyList()` on the
      unexpected-schema path. Log the exception in debug builds.

- [ ] **M2 — Hash/parse ordering leaves a small redundant-work window on crash**
    - **File:** `data/CinParser.kt:34-56`.
    - **Issue:** Order is `clearAll()` → `parseAndPopulate()` →
      `prefs.edit { putString(KEY_ASSET_HASH, currentHash) }`. If the
      process dies after the parse commits but before the SharedPrefs
      write flushes, the next launch sees a populated DB whose stored
      hash is the *previous* value and re-parses unnecessarily.
      (The earlier "TOCTOU on the asset file" concern does not apply —
      assets are read-only APK entries at runtime.)
    - **Impact:** Wasted ~10-30 s reimport on the user's next launch
      after a rare crash. Not data loss.
    - **Fix:** Compute the hash once up-front, write it before `clearAll()`
      behind a "sync in progress" flag; on launch, if the flag is set,
      force a reimport. Or simply swap the order — write hash first,
      delete it on a parse failure.

- [ ] **M3 — `DictionaryDao.incrementFrequency` compound WHERE hides intent**
    - **File:** `data/DictionaryDao.kt:50-51`.
    - **Issue:** The clause
      `(keystroke = :exactKeystroke OR (keystroke LIKE :prefix || '%' AND :exactKeystroke = ''))`
      requires the caller to remember that passing an empty
      `exactKeystroke` switches the query mode to prefix match.
      Error-prone and the `OR` likely bypasses the `keystroke` index.
    - **Impact:** Subtle caller bugs; suboptimal query plan on large
      dictionaries.
    - **Fix:** Split into `incrementFrequencyExact(character, keystroke)`
      and `incrementFrequencyByPrefix(character, prefix)`. Update the
      two call sites in `KeyboardViewModel.selectCandidate`.

- [ ] **M4 — FTS4 MATCH accepts any caller-supplied pattern**
    - **File:** `data/DictionaryDao.kt:31-39`.
    - **Issue:** `dictionary_fts.keystroke MATCH :query` is today only
      called with `"$keystroke*"` from internal state, but there is no
      validation. If anything routes external / user-typed text through
      this DAO in future, malformed MATCH syntax (unbalanced quotes,
      invalid operators) throws `SQLiteException`.
    - **Impact:** Latent crash if the DAO's usage expands.
    - **Fix:** Either sanitize/escape the MATCH pattern in a helper,
      or wrap the DAO call sites in try/catch returning an empty list
      and a debug log on malformed input. Document the expected format
      in the DAO KDoc.

### Low / Informational

- [ ] **L1 — Dead code in `HapticHelper.vibrate`**
    - **File:** `util/HapticHelper.kt:42-45`.
    - **Issue:** The `@Suppress("DEPRECATION") vibrator?.vibrate(duration)`
      branch is unreachable because minSdk = 26 (`Build.VERSION_CODES.O`).
    - **Fix:** Drop the `if (SDK_INT >= O)` guard and the else branch;
      keep only `vibrator?.vibrate(VibrationEffect.createOneShot(…))`.

- [ ] **L2 — Unused imports in `RakuRakuImeService`**
    - **File:** `ime/RakuRakuImeService.kt:26, 39, 45`.
    - **Issue:** `isSystemInDarkTheme`, `InputMode`, and the
      `ui.keyboard.*` wildcard are not used.
    - **Fix:** Remove the two explicit imports; narrow the wildcard to
      the single `KeyboardScreen` symbol actually referenced.

- [ ] **L3 — `allowBackup="true"` with empty `backup_rules.xml`**
    - **Files:** `AndroidManifest.xml:8-10`, `res/xml/backup_rules.xml`.
    - **Issue:** All IME local state (Room dictionary with learned
      frequencies, DataStore preferences, the CIN-asset hash in
      SharedPreferences) is pushed to Google's encrypted backup tied
      to the user's account. The PRIVACY-POLICY promises "never leave
      the device", which is strictly only true if cloud backup is
      opted out — backup is user-initiated but still off-device.
    - **Fix:** Decide intentionally:
      (a) Set `android:allowBackup="false"` and remove the rules file; or
      (b) Keep backup but add explicit `include`/`exclude` rules
          (at minimum exclude the Room dictionary if you'd rather not
          ship learned frequencies off-device). Align the
          PRIVACY-POLICY wording with whichever choice.

- [ ] **L4 — Release build ships unminified / unobfuscated**
    - **File:** `app/build.gradle.kts:43-48`.
    - **Issue:** `isMinifyEnabled = false` in the `release` block.
      APK size is larger than necessary; R8 dead-code elimination is
      skipped.
    - **Fix:** Before cutting 1.0, flip to `true`, run a release build,
      and add any necessary keep rules to `proguard-rules.pro`.
      Compose + Room + DataStore all work under R8 but sometimes need
      hints for reflection.

- [ ] **L5 — Stale dependencies**
    - **File:** `gradle/libs.versions.toml`.
    - **Issue:** `composeBom = "2024.09.00"` is roughly 18 months old;
      `room = "2.7.0-alpha11"` is an alpha.
    - **Fix:** Bump Compose BOM to a current release and move Room to
      the latest stable before publishing. Run lint/tests afterwards.

- [ ] **L6 — Silent skipping of malformed asset lines**
    - **Files:** `data/CinParser.kt:53-57, 63-64`;
      `data/EnglishDictionary.kt:45-49`.
    - **Issue:** Malformed / empty lines are silently dropped during
      parsing. A truncated asset produces a silently-short dictionary.
    - **Fix:** After the loop, assert on a minimum entry count (e.g.
      CIN > 90 000, English > 9 000) and throw (or log loudly) if
      under; catches corrupted builds early.

- [ ] **L7 — (Noted, no change needed) `EnglishDictionary` lazy init**
    - **File:** `data/EnglishDictionary.kt:40-57`.
    - **Observation:** The double-checked `trie?.let { return it }`
      fast path outside the `loadMutex` was flagged by the audit agent
      as potentially returning a partial trie. In fact it's safe:
      `trie` is `@Volatile`, and the assignment `trie = it` happens
      via `.also { trie = it }` *after* the `EnglishTrie` is fully
      populated on `Dispatchers.IO`. No change required.

- [ ] **L8 — `fallbackToDestructiveMigration` = silent data loss on schema bump**
    - **File:** `data/ImeDatabase.kt:41-42`.
    - **Status:** Documented in `CLAUDE.md` and accepted as a
      tradeoff. When a future schema version is needed, decide then
      whether to write an actual `Migration` (preserving learned
      frequencies) or continue with destructive migration (users
      reimport on upgrade).
