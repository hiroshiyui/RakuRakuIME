# RakuRaku IME TODOs

Outstanding work for RakuRaku IME. Completed items have been cleaned
out — see `git log` for the full history of what's landed.

## Code Review & Security Audit (2026-04-19)

Remaining Low/Informational items from the 2026-04-19 audit pass.
(H-tier, M-tier, and L1/L2/L4/L5 have already landed.)

### Low / Informational

- [ ] **L8 — Write a real Migration at the next schema bump**
    - **Status:** Infrastructure in place. `exportSchema = true` on
      `@Database`; KSP writes schema JSONs to `app/schemas/` via
      the `room.schemaLocation` arg in `app/build.gradle.kts`; the
      v2 baseline (`2.json`) is committed.
    - **What's left:** when the first schema change lands (v3+),
      add either `@AutoMigration(from = 2, to = 3)` for additive
      changes, or write a manual `Migration(2, 3)` for anything
      non-trivial, plus a `MigrationTestHelper`-based unit test.
      `fallbackToDestructiveMigration` stays as a safety net but
      must not be the primary upgrade path (it wipes learned
      frequencies).
    - **FTS4 gotcha:** Room's auto-migration doesn't always produce
      correct SQL for the `dictionary_fts` virtual table. If you
      touch `dictionary` columns that feed the FTS index, the
      migration may need a manual
      `DROP TABLE dictionary_fts; CREATE VIRTUAL TABLE … USING fts4(…);
      INSERT INTO dictionary_fts(dictionary_fts) VALUES('rebuild');`
      step.

## Code Review & Security Audit (2026-04-22)

Findings from the audit pass on the auto-mode-switch / pre-edit buffer
changes (commits `5cce2df` → `bcf47fe`).

### Critical / High

- [x] **H1 — Password fields must not receive English word prediction**
    - **Landed:** introduced an `asciiOnly` StateFlow on
      `KeyboardViewModel` driven by a new companion
      `isAsciiOnlyFor(inputType)` that returns true only for the three
      password variations. `KeyboardScreen` routes English letter-key
      clicks through direct `commitText` when `asciiOnly` is set — the
      composing buffer, prediction trie, and candidate bar never see a
      password prefix. `CandidateBar` also defensively renders an
      empty list in English + asciiOnly. Email / web-email / URI
      still use ENGLISH with prediction. Coverage added via
      `InputModeSelectionTest` (7 new cases on `isAsciiOnlyFor`).
    - **Follow-up (landed):** `isAsciiOnlyFor` now also honors
      `TYPE_TEXT_FLAG_NO_SUGGESTIONS` (on `inputType`) and
      `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` (on `imeOptions`),
      both treated as app-declared opt-out signals independent of the
      text variation. Covered by `InputModeSelectionTest` cases
      `noSuggestionsFlag_isAsciiOnly`,
      `imeNoPersonalizedLearning_isAsciiOnly`,
      `imeNoPersonalizedLearning_overridesEmail`, and a negative case
      for unrelated `IME_ACTION_*` bits.

### Medium

- [ ] **M1 — `appendToPreEdit` leaves `_isSelecting` + candidates stale**
    - **Where:** `KeyboardViewModel.kt:411-416`.
    - **Issue:** if the user has entered candidate-selection mode
      (space pressed once, `_isSelecting = true`, candidate bar
      showing) and then long-presses a key to pick a punctuation
      alternate, `appendToPreEdit` mutates `_preEditBuffer` but leaves
      `_isSelecting` and `_candidates` untouched. The next digit
      press is still interpreted by `KeyboardScreen.kt:152` as
      "select candidate N" instead of committing the digit.
    - **Fix:** set `_isSelecting.value = false` inside
      `appendToPreEdit` (punctuation breaks the selection flow).
    - **Test:** extend `KeyboardViewModelTest` with an
      "appendToPreEdit_exitsSelectionMode" case.

- [ ] **M2 — `onToggleMode` flush path is a binary `if` over 4 modes**
    - **Where:** `KeyboardScreen.kt:468-483`.
    - **Issue:** readability — `if (inputMode == ENGLISH) … else …`
      reads as if only two modes exist, yet NUMBER and EMOJI also
      route through the `else` (safely, because both buffers are
      empty there). A `when (inputMode)` makes the intent explicit.
    - **Fix:** rewrite as `when (inputMode) { ENGLISH -> …; EZ -> …;
      NUMBER, EMOJI -> "" }` and skip the `commitText` call entirely
      for NUMBER/EMOJI to avoid the no-op `clearComposing()` → empty
      `onUpdateComposingText` round-trip.

### Low / Informational

- [ ] **L1 — Move `companion object` to the end of `KeyboardViewModel`**
    - **Where:** `KeyboardViewModel.kt:289-316`. Currently wedged
      between `updateEditorInfo` and `onKeyPress`; Kotlin convention
      puts companion objects at the top or bottom of the class.

- [ ] **L2 — Screenshot coverage for the shifted-label keycaps**
    - **Where:** `KeyboardScreen.kt:275-282`, English layout.
    - Add a `KeyboardScreenshotTest` frame that includes a few symbol
      keys (`1`, `;`, `[`) so the top-left shifted / bottom-right
      default placement is pinned down against future `KeyButton`
      changes to `ezRoot` alignment defaults.

- [ ] **L3 — Strengthen `setInputMode_nonEz_clearsPreEdit`**
    - **Where:**
      `app/src/androidTest/.../KeyboardViewModelTest.kt:100-110`.
    - Also assert `viewModel.candidates.value.isEmpty()` and
      `viewModel.isSelecting.value == false`; that catches the M1
      `_isSelecting` leak if it's ever reintroduced via this path.
