# RakuRaku IME TODOs

Outstanding work for RakuRaku IME. Completed items have been cleaned
out — see `git log` for the full history of what's landed.

## Feature Roadmap

- [x] **User Phrase Manager** — landed as a separate `user_phrases`
      table (v4 migration), an "always rank first" merge into
      `KeyboardViewModel.updateCandidates` / `updateNextCharPredictions`,
      Compose `UserPhraseManagerScreen` with add/delete CRUD, search,
      and CSV backup/restore (`UserPhraseCsv`). Strict EZ-keystroke
      validation on entry and restore via `CinParser.validKeystrokeChars`.

- [ ] **User Phrase Manager — follow-ups**
    - Inline edit (today the row is delete-only; recreate to "edit").
    - Frequency-of-use tracking on user-phrase commits — currently
      `selectCandidate(...)` only bumps the bundled `dictionary.frequency`,
      not the user-phrase row. With the always-rank-first policy this
      is cosmetic, but useful for ordering among multiple user phrases
      sharing a prefix.
    - Phrase-level keystroke awareness in suggestions: `EzKeystrokeLookup`
      currently builds char-by-char concatenations and ignores the EZ
      table's phrase-level abbreviation rules (radical-root selection,
      hybrid sequences — see
      <https://github.com/hiroshiyui/EzIM_Tables_Project/blob/main/CLAUDE.md>).
      For a phrase already in the corpus this would surface the canonical
      keystrokes; for true user-novel phrases the char-by-char form is
      always valid anyway, so this is a polish item, not a correctness one.

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
        - **Input validation on import (treat the file as
          untrusted):** users can pick any file via SAF, including a
          crafted or corrupted one, so the importer must defend
          against it end-to-end:
            - Cap the accepted file size before reading (e.g.
              50 MiB) so a malicious file can't force the app to
              allocate unbounded memory or fill the device.
            - Cap per-row string lengths (keystroke ≤ ~16 chars,
              phrase text ≤ ~64 chars, frequency within `Int`
              range and non-negative); reject rows that exceed
              them.
            - Validate keystroke characters against the EZ root
              set — the same validator the CRUD UI uses — and
              reject rows with stray bytes, control chars, or
              unexpected Unicode categories in the phrase text.
            - Sniff for zip bombs / nested archives if the export
              format is zipped; stream-decompress with a hard
              limit on total uncompressed bytes rather than
              buffering the whole archive.
            - Use parameterised inserts (Room DAO only, no raw SQL
              string concatenation with imported data).
            - Parse JSON / proto with a strict decoder — reject
              unknown fields on a schema version the app doesn't
              understand, rather than silently dropping them.
            - Surface the first validation error to the user and
              abort the whole import; never apply a partial
              restore that could leave the DB in a surprising
              state.
            - Never eval / reflect / load classes from imported
              content (obvious, but worth stating: this is a
              plain-data format).
        - Settings UI: two buttons under a new "Backup" section,
          next to the existing "Re-import Dictionary" / "Reset
          Learning" actions.
