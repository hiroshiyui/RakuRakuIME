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

- [x] **User Phrase Manager — follow-ups**
    - Inline edit: each row carries an "Edit" button backed by a
      shared `UserPhraseFormDialog` and
      `UserPhraseDao.updateById` (`UPDATE OR IGNORE` against the
      unique `(character, keystroke)` index, so collisions surface
      a friendly toast).
    - Frequency-of-use tracking: `user_phrases.frequency` added in
      v4→v5 migration; `selectCandidate(...)` bumps both DAOs
      unconditionally — the UPDATE on whichever table doesn't own
      the row is a no-op, so the ViewModel doesn't need to know
      which table the candidate came from. User-phrase candidate
      queries now sort by `frequency DESC, created_at DESC`.
    - Phrase-level keystroke awareness: `EzKeystrokeLookup` now
      prepends corpus-known phrase keystrokes (via
      `DictionaryDao.keystrokesForPhrase`, which doesn't filter to
      single-char rows) before the per-char fallback. For
      user-novel phrases the char-by-char rule still runs as
      before.

- [x] **Backup / Restore manager** — landed. Gzipped-JSON archive
      (`*.rkbak.gz`) with `schema`/`applicationId`/`createdAt` header,
      `userPhrases` (full rows + learned `frequency` + `createdAt`),
      and `dictionaryFrequencies` (only the dictionary rows with
      `frequency > 0` — the static corpus comes back from the bundled
      asset DB on the next install). `BackupArchive` does all I/O via
      `OutputStream` / `InputStream` so SAF owns the file lifetime.

      Import enforces every constraint listed below (strict
      top-level + nested decode, 50 MiB uncompressed cap to defeat
      gzip bombs, per-row length and Unicode-control rejection,
      EZ keystroke validation via the same `CinParser` validator
      the CRUD UI uses, parameterised Room DAO inserts, no
      reflection / class loading). Errors surface the first
      offending row to the user and abort the whole import.

      Restore merges into the existing DB: user phrases via
      `OR IGNORE` against the unique `(character, keystroke)`
      index, dictionary frequencies via `incrementFrequencyExactBy`
      (delta-add) so a partial restore doesn't clobber recent
      learning. UI lives under a new "Backup & Restore" section in
      Settings next to "Re-import Dictionary" / "Reset Learning".

      Full design retained below for posterity in case the format
      ever needs a v2:
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
