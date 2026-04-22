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
