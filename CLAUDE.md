# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

RakuRaku IME — an Android Input Method Editor for the EZ Input Method (輕鬆輸入法).
Kotlin + Jetpack Compose + Room (FTS4) + DataStore, **minSdk 26**, single-activity + single-service app.

For the EZ Input Method's input-semantics domain knowledge (radical roots, phrase composing rules, hybrid keystroke sequences, selection logic), read **`GEMINI.md`** first — don't duplicate that content here or in new docs.

## Common commands

```bash
./gradlew :app:compileDebugKotlin      # fastest feedback loop
./gradlew :app:assembleDebug           # full debug APK (packages assets/resources)
./gradlew :app:assembleRelease         # release APK (minify disabled currently)
./gradlew :app:lint                    # Android lint
./gradlew :app:testDebugUnitTest       # unit tests (src/test)
./gradlew :app:connectedDebugAndroidTest  # instrumented tests (needs device/emulator)
./gradlew clean
```

Run a single instrumented test:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=\
  org.ghostsinthelab.app.rakurakuime.DictionaryTest#someMethod
```

There is no `testReleaseUnitTest` pipeline wired up; stick with `debug`.

## Architecture

Packages under `app/src/main/java/org/ghostsinthelab/app/rakurakuime/`:

- **`data/`** — persistence + bundled-corpus loaders.
- **`ime/`** — IME service + Compose-lifecycle bridge.
- **`ui/`** — Compose screens, keyboard composables, theme.
- **`util/`** — small helpers (e.g. `HapticHelper`).

### IME service ↔ Compose bridge

`RakuRakuImeService` is an `InputMethodService` hosting a `ComposeView`. Because an `InputMethodService` is not a `LifecycleOwner` / `ViewModelStoreOwner` / `SavedStateRegistryOwner`, the service owns a custom **`ImeLifecycleOwner`** that implements all three and is attached to the decor view before `ComposeView.setContent`. This is why a `KeyboardViewModel` can be obtained via `ViewModelProvider` inside the service. Touching this wiring without understanding the three-owner attachment will break Compose initialisation or leak view models across IME sessions.

`onCreateInputView()` re-builds the Compose tree — theme/keyboard changes don't hot-swap mid-session; the keyboard re-composes next time it is brought up.

### State — `KeyboardViewModel`

The keyboard is state-first. A single `AndroidViewModel` owns every state flow the UI reads:

- `inputMode: StateFlow<InputMode>` — one of `EZ | NUMBER | ENGLISH | EMOJI`. `FunctionRow`'s mode key cycles through them. `updateEditorInfo(...)` auto-switches to `NUMBER` for numeric `EditorInfo.inputType`.
- EZ composing state: `composingText` (in-progress keystroke sequence), `preEditBuffer` (already-selected Chinese characters still in composing region), paginated `pagedCandidates` / `hasPrevPage` / `hasNextPage`.
- English composing state: `englishBuffer` + `englishCandidates`, fed by `EnglishDictionary.prefixLookup`. `setInputMode(mode)` clears the non-active buffer; selecting an English candidate commits `<word> ` and capitalises the first letter if the buffer's first char was uppercase.
- `emojiCategory: StateFlow<Int>` — index into `EmojiDictionary.categories(context)`.
- `themeMode: Flow<ThemeMode>` — surfaced so `RakuRakuImeService` can feed it into `RakuRakuIMETheme` alongside `MainActivity`.

The `onUpdateComposingText` callback is wired by the service to `InputConnection.setComposingText`; the ViewModel doesn't hold an `InputConnection` reference itself.

### Data layer

- **EZ dictionary — Room + FTS4 + pre-packaged DB.** `ImeDatabase.createFromAsset("databases/ime_database.db")` seeds the DB on first launch; prefix lookup hits the `dictionary_fts` virtual table. Bumping the Room `@Database(version = …)` triggers `fallbackToDestructiveMigration(dropAllTables = true)` — the packaged asset is **not** re-seeded on a version bump, so users will see an asset-backed re-import via `CinParser.syncWithAsset`. Plan schema bumps accordingly (or regenerate the packaged DB).
- **CIN asset sync.** `CinParser.syncWithAsset(context, db)` compares a SHA-256 of `assets/ezbig.utf-8.cin` against a value stored in SharedPreferences. On first launch the pre-packaged DB is trusted and the hash is simply recorded; on later launches, a changed hash clears the table and re-imports. `forceReimport` is the manual path (Settings → Re-import Dictionary).
- **English prediction.** `EnglishDictionary` lazily parses `assets/google_10k_english.txt` (Google 10K, MIT, attributed in README) into an in-memory `EnglishTrie` backed by `LinkedHashMap` so DFS yields candidates in insertion (frequency) order. No persisted learned frequencies — tradeoff accepted to avoid a Room schema bump.
- **Emoji picker.** `EmojiDictionary` parses `assets/emoji.json` (MeaninglessKeyboard, GPL-3.0, attributed in README) via `org.json` at runtime; eight hard-coded categories, cached behind a `Mutex`.
- **User preferences.** `UserPreferences` wraps a single Preferences `DataStore` (`user_preferences`). `ThemeMode` (defined in `ui.theme`) is persisted as a string preference; UserPreferences imports it from the theme package, not vice-versa.

### Theme

`RakuRakuIMETheme(themeMode, darkTheme, content)` in `ui/theme/Theme.kt`. `ThemeMode.DYNAMIC` uses Material You on Android 12+ (`DynamicColorAvailable`) and falls back to Solarized on older devices; `ThemeMode.SOLARIZED` always uses the hard-coded Solarized schemes. `KeyboardTheme` is a `staticCompositionLocalOf`-backed derivation of `MaterialTheme.colorScheme` that only keyboard composables read — don't add theme tokens directly into individual key composables.

### Key input handling — `KeyButton`

Custom pointer-input loop (not `detectTapGestures`) because it must support tap, swipe-up, and long-press-alternates-drag. The commit rule is: fire `onClick` / `onAlternateSelected` only when the loop ends with `released && !gestureHandled`. Cancellation paths that clear `released`: the scrollable ancestor consuming the pointer (`change.isConsumed`), or the finger dragging > `swipeThresholdPx` downward or sideways. See the MK reference comment in the file.

## Assets bundled into the APK

- `ezbig.utf-8.cin` — EZ dictionary source (高衡緒 / 輕鬆資訊企業社, GPLv2 + EZ public licence).
- `databases/ime_database.db` — pre-packaged Room DB built from the CIN.
- `google_10k_english.txt`, `emoji.json` — English prediction + emoji categories (see README attributions).
- `gpl.txt`, `ezphrase.txt`, `LICENSE_EZBIG.md` — license texts shipped for compliance.

When touching attribution-bearing assets, update both `README.md` and the matching in-app references.

## Repo conventions

- **Task tracking.** Mark completed items in `TODOs.md` immediately on completion (mandate inherited from `GEMINI.md`).
- **Commit messages.** History is mixed: plain imperative subjects (`Add …`, `Fix …`, `Update …`) and conventional prefixes (`docs:`, `perf:`, `fix:`) both appear. Match the style of nearby recent commits. No `Signed-off-by` or `Co-Authored-By` trailers in this repo's history — don't add them unless the user asks.
- **Branch + remotes.** `main` only; single `origin` on GitHub.
- **Push policy.** The user's standing preference is "commit-and-push pushes to origin/main immediately after committing" — do not pause for confirmation. Force-push to `main` still requires explicit authorisation.
- **Skills.** `.claude/skills/` contains repo-tailored skills (`commit-and-push`, `code-review-and-security-audit`, `docs-engineering`, `release-engineering`). Invoke them via `/` commands; settings-local files stay out via `.claude/.gitignore`.
- **Licensing.** App is GPL-3.0-or-later; bundled dictionary data carries its own licences (`LICENSE_EZBIG.md`). When adding third-party data, confirm licence compatibility with GPLv3 before redistribution.
