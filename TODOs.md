# RakuRaku IME TODOs

Outstanding work for RakuRaku IME. Completed items have been cleaned
out — see `git log` for the full history of what's landed.

## Code Review & Security Audit (2026-04-19)

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

## Feature Roadmap

- [ ] **User Phrase Manager**
    - **What:** a settings screen that lets the user add, edit, and
      delete their own phrases — keystroke → phrase text pairs that
      live alongside the bundled dictionary and feed the same
      candidate lookup. When the user types (or pastes) the phrase
      text, the screen suggests a ranked list of candidate root
      keystrokes derived from EZ's composing rules so the common
      case is one tap, not manual keystroke entry.
    - **Why:** the bundled `ezbig.utf-8.cin` is read-only for end
      users; the only personalisation today is learned frequency.
      Letting users add their own phrases turns RakuRaku into a
      practical daily-driver IME for names, jargon, and phrases that
      aren't in the shipped corpus. Auto-suggesting roots is what
      makes the UX tolerable: hand-deriving a 4-character phrase's
      root sequence is slow and error-prone even for fluent EZ
      users.
    - **Design sketch:**
        - Separate Room entity (e.g. `UserPhrase`) with the same
          `(keystroke, text)` shape as `Dictionary` plus a
          user-origin flag; DAO merges user phrases into
          `getCharacters(keystroke)` / `getCharactersByPrefix(prefix)`
          so the candidate bar surfaces them transparently.
        - Root-suggestion engine: per character in the phrase,
          enumerate the possible roots by cross-referencing the
          bundled `Dictionary` table (every row where `text ==
          <char>` gives one or more keystrokes that resolve to that
          char). Combine across characters per EZ's phrase-composing
          rules described in
          <https://github.com/hiroshiyui/EzIM_Tables_Project/blob/main/CLAUDE.md>
          (hybrid keystroke sequences, radical-root selection,
          single-char vs. multi-char rules). Rank suggestions by
          total learned frequency so the user's usual picks surface
          first.
        - CRUD UI in `MainActivity`, one entry per row with phrase
          text + keystroke fields; the keystroke field offers the
          suggestion chips inline. Validate keystroke characters
          against the EZ root set on manual entry too.
        - UI / interaction reference: Guileless Bopomofo's User
          Phrase Manager at
          <https://github.com/hiroshiyui/GuilelessBopomofo> —
          matches the "add / edit / delete user phrases" shape we
          want, so read its screen + Room wiring before designing
          ours from scratch. (Guileless is Bopomofo-based so the
          root-derivation logic doesn't transfer, but the CRUD flow
          and list-vs-editor layout do.)
        - Schema bump → requires the L8 migration work above.

- [ ] **Backup / Restore manager**
    - **What:** export the full dictionary state (bundled entries +
      user phrases + learned frequencies) to a file the user can save
      off-device, and import a previously-exported file to restore
      that state on a new install or another device.
    - **Why:** learned frequencies are the single most valuable piece
      of per-user state this IME accumulates; reinstall / phone swap
      currently loses it. User phrases (once the feature above
      lands) have the same property.
    - **Design sketch:**
        - Export format: a single file (zipped JSON or a Room DB
          snapshot) with a version header so future schema changes
          stay importable. Include bundled + user + frequency so
          restore is self-contained and doesn't need the exporter
          and importer to be on the same build.
        - Store to app-private or `Downloads/` via SAF
          (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`) so no
          extra runtime permissions are needed on API 26+.
        - Importer should validate the header, refuse mismatched
          `applicationId`, merge into the existing DB (don't silently
          wipe), and surface per-row counts in the result toast.
        - Settings UI: two buttons under a new "Backup" section,
          next to the existing "Re-import Dictionary" / "Reset
          Learning" actions.
