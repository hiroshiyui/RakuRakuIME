# Project Knowledge: RakuRaku IME (EZ Input Method)

## Project Mandates
- **Task Tracking:** Always mark a to-do task as done in `TODOs.md` immediately upon completion.

## Dictionary and Assets
- The primary dictionary source is `app/src/main/assets/ezbig.utf-8.cin`.
- This file was converted from the original Big5 `ezbig.cin` using `iconv -c -f BIG5-HKSCS -t UTF-8`.
- Licensing and attribution for the dictionary data (高衡緒 and 輕鬆資訊企業社) are maintained in `app/src/main/assets/LICENSE_EZBIG.md`, `gpl.txt`, and `ezphrase.txt`.
- Static corpus priors for candidate ordering ship as `app/src/main/assets/85rest01.csv` (字頻) and `85rest02.csv` (詞頻), sourced from MOE《八十五年常用語詞調查》via the government open-data platform (data.nat.gov.tw/dataset/45518). Bundled as UTF-8 (transcoded from the upstream Big5 with `iconv -c -f BIG5 -t UTF-8`).
- The pre-packaged Room database `app/src/main/assets/databases/ime_database.db` is rebuilt by the Gradle task `./gradlew :app:buildImeDb` (logic in `buildSrc/src/main/kotlin/ImeDbBuilder.kt`). It reproduces Room's exact v3 schema — including the `dictionary_fts` virtual table, sync triggers, and `room_master_table` identity hash from `app/schemas/.../3.json` — so the asset is byte-compatible with what Room would generate at runtime. Rerun this task after touching the CIN file, the CSV corpora, the Room schema, or `FrequencyCsv.SEED_NUMERATOR`.

## Candidate Ordering and Corpus Weights
- Each `dictionary` row carries two static-prior columns alongside the user-learned `frequency`:
  - `character_weight` — populated only for single-character rows, derived from `85rest01.csv` (字頻總表) rank.
  - `phrase_weight` — populated only for multi-character (phrase) rows, derived from `85rest02.csv` (詞頻總表) rank.
- The seeding formula is reciprocal: `weight = floor(10000 / rank)` (rank 1 → 10 000, rank 1 000 → 10, rank > 10 000 → 0). The earlier `max(0, 100 - rank)` form was discarded because it flattened the long tail and pushed common-but-mid-rank entries (e.g. 輕鬆 at rank 1 424) below alphabetic order.
- `DictionaryDao` orders candidates by `frequency DESC, (character_weight + phrase_weight) DESC`. Net effect: fresh installs surface common characters/phrases on day one without waiting for learned frequency to accumulate, while continued use lets personal frequency override the corpus prior over time.
- `DictionaryDao.nextCharactersAfter(...)` (the post-selection next-character strip) uses `SUM(phrase_weight) DESC` as its tiebreaker after `SUM(frequency) DESC`, so the same prior shapes follow-on character suggestions.

## User Phrase Manager and Backup / Restore
- A separate Room table `user_phrases` (introduced in v4, gained per-row `frequency` in v5) holds user-defined phrases. Each row is `(id, character, keystroke, created_at, frequency)` with a unique index on `(character, keystroke)`.
- User phrases are always merged ahead of corpus candidates for the same keystroke and never written into the bundled `dictionary` table — that lets the asset DB stay byte-identical to what `buildImeDb` produces, and keeps re-import / wipe operations from touching user data.
- `KeyboardViewModel.selectCandidate` bumps the learned `frequency` on **both** DAOs unconditionally. The UPDATE on whichever table doesn't own the committed row is a no-op (the WHERE clause matches nothing), so the ViewModel doesn't need to know which side the candidate came from. User-phrase candidate queries sort by `frequency DESC, created_at DESC`; the always-rank-first merge in the ViewModel still wins over corpus rows so the frequency only orders user phrases among themselves.
- The User Phrase Manager screen offers inline edit through `UserPhraseDao.updateById` (`UPDATE OR IGNORE` so a collision with the unique index becomes a 0-row return surfaced as a friendly toast) — edit-in-place preserves `id`, `created_at`, and learned `frequency` instead of forcing a delete-and-recreate cycle.
- **Backup / Restore manager** (`BackupArchive`). Settings → "Backup & Restore" exports user phrases + learned dictionary frequencies as a gzipped JSON archive (`*.rkbak.gz`), and restores them by merging into the existing DB. Format header: `{schema: 1, applicationId, createdAt}`; payload arrays `userPhrases[]` (full rows incl. `frequency` + `createdAt`) and `dictionaryFrequencies[]` (only rows with `frequency > 0` — the static corpus is shipped in the asset DB, so backing it up would just bloat the archive). The parser treats the picked file as untrusted: 50 MiB uncompressed cap to defeat gzip bombs, strict-decode rejection of unknown top-level *and* nested fields, per-row string length caps, `isISOControl` rejection in phrase text, and EZ keystroke validation via the same `CinParser.validKeystrokeChars` validator the CRUD UI uses. The first validation failure aborts the whole import — partial restores are never applied. Restore-side: user phrases via `OR IGNORE` against the unique `(character, keystroke)` index; dictionary frequencies via `DictionaryDao.incrementFrequencyExactBy` (delta-add) so a partial restore doesn't clobber recent learning. See `SECURITY.md` for the full threat model.

## Technical Understanding of 輕鬆輸入法 (EZ Input Method)

### 1. Shape-Based Radical Philosophy
EZ is a radical-based system that balances stroke order with visual identification. It decomposes characters into fundamental components called "roots" (字根).

### 2. Full-Keyboard Root Mapping
EZ utilizes 48 keys across four rows, including numbers and punctuation, as first-class roots:
- **Number Row (1-0, -, =):** Major radicals (e.g., `2` for `車`, `4` for `言`, `6` for `雨`, `=` for `母`).
- **Letter Rows:** QWERTY mapping for roots (e.g., `q` for `手`, `v` for `女`, `z` for `辶`, `x` for `又`).
- **Punctuation Roots:** Keys like `[` (匚), `]` (】), `;` (寸), `'` (Ｌ), and `/` (ㄙ) are integral to character sequences.

### 3. Keystroke Sequences
Sequences are often hybrid, mixing letters, numbers, and symbols (e.g., `z4` for "這", `v=` for "姆"). This multi-modal approach results in very short codes (typically 1–3 keys) for common characters.

### 4. Phrase Composing Rules
EZ uses highly efficient shorthand logic for phrases (詞彙), which is why the dictionary contains nearly 100,000 entries:
- **The "First Root" Principle:** Phrases typically use the first root of the most significant characters rather than full character sequences.
- **Internal Delimiters:** The `,` (，) and `.` (＼) keys are used as internal delimiters to signal phrase mode and separate component roots (e.g., `z,4` for "這句話").
- **Prefix Shortcuts:** Common prefixes like `''`, `,,`, or `..` are used for idioms and common phrases (e.g., `''33` for "斷斷續續").
- **Positional Encoding:** 2-character words often combine the first root of the first character and the last root of the second character.
- **Visual "Anchors":** Sequences prioritize distinct visual components to aid memory and recognition.

### 5. Selection Logic
Key conflict management is essential because numbers are both roots and selection triggers. 
- **Rule:** Number keys continue the root sequence by default.
- **Selection Mode:** Pressing **Space** triggers "Selection Mode" (if multiple candidates exist), during which number keys act as selection triggers for the candidate bar.
