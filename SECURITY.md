# Security

This document describes the security posture of RakuRaku IME (輕鬆輸入法)
and the trust model the app applies to user-supplied files. It is aimed at
people who want to understand what guarantees the app makes before they
restore a backup someone else gave them, or before they audit the source
for an F-Droid review.

If you believe you've found a security issue, please open a private
report via GitHub's "Report a vulnerability" workflow on the
[RakuRakuIME repository][repo] rather than filing a public issue.

[repo]: https://github.com/hiroshiyui/RakuRakuIME

## General posture

- **No network access.** The app does not declare `INTERNET` in its
  manifest. Nothing it does — dictionary lookup, learned frequencies,
  user phrases, English prediction — leaves the device.
- **No telemetry or analytics.** No usage reporting, no crash uploads,
  no third-party SDKs that talk to a server.
- **Local-only persistence.** All learned state lives in the app's
  private Room database and DataStore preferences, both under
  `/data/data/<package>/` and unreadable by other apps on a
  non-rooted device.
- **No runtime permissions requested.** The backup / restore feature
  reaches external storage via the Storage Access Framework
  (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`); no `READ_` or
  `WRITE_EXTERNAL_STORAGE` permission is required on API 26+.

## Threat model: Backup / Restore import

The backup / restore manager exports user phrases and learned
dictionary frequencies as a gzipped JSON archive (`*.rkbak.gz`). The
*export* side is trusted — it serialises data the app already owns.

The *import* side treats the picked file as fully untrusted. The user
can pick any file through SAF, including one that was crafted to
exploit the parser, sourced from a chat app, or simply corrupted in
transit. The implementation in `BackupArchive` is built to assume the
worst-case shape of that file and refuse it cleanly.

### Defenses, by attack class

| Concern | Defense |
|---|---|
| **Gzip bomb / pathological compression ratio** — a tiny archive that expands to gigabytes. | Stream-decompress with a hard cap of 50 MiB total uncompressed bytes (`MAX_UNCOMPRESSED_BYTES`). The decoder tips over the moment the limit is exceeded; we never buffer past it. |
| **Memory exhaustion from huge row counts.** | Per-array cap of 1 000 000 rows; archives that exceed it return `Error.TooManyRows`. |
| **Malformed file (not gzip, not JSON, truncated).** | All I/O exceptions and JSON parse errors are caught and surfaced as `Error.InvalidFile`. No partial state is applied. |
| **Cross-app file injection.** | Top-level `applicationId` is checked against the build's own ID. Archives produced by other apps return `Error.WrongApplication`. |
| **Forward-compatible format confusion.** | `schema` is a single integer (currently `1`). Anything else returns `Error.UnsupportedSchema(found)`. |
| **Smuggling extra fields past a future schema.** | Strict decode — unknown keys at the top level *and* inside row objects return `Error.UnknownField(path)`. A future schema must bump `SCHEMA_VERSION` before the parser will tolerate new fields. |
| **Oversized strings (slow regex / OOM downstream).** | Per-row caps: `character` ≤ 100 chars, `keystroke` ≤ 200 chars. Frequencies are bounded into the non-negative `Int` range. |
| **Control / non-printable bytes in phrase text.** | Phrase strings are rejected if any character matches `Char.isISOControl()`. |
| **Stray bytes posing as EZ root keys.** | Keystrokes are validated against `CinParser.validKeystrokeChars(context)` — the exact same root set the in-app "Add Phrase" dialog uses. |
| **SQL injection via imported values.** | All inserts go through parameterised Room DAO methods (`UserPhraseDao.insert`, `DictionaryDao.incrementFrequencyExactBy`). No string concatenation with imported data, no raw SQL. |
| **Partial restore leaving the DB in a surprising state.** | The first validation error aborts the whole import — no rows are applied until validation has succeeded for *every* row. |
| **Code execution via crafted content.** | The format is plain data: gzip + UTF-8 JSON. We never `eval`, reflect, deserialise polymorphic types, or load classes from the archive. |

### What the import deliberately does *not* do

- **No silent overwrite.** Existing user phrases that collide on
  `(character, keystroke)` are kept (the insert is `OR IGNORE`);
  dictionary frequencies are *added* (`+= delta`) rather than
  replaced, so a restore can't wipe out learning the user has done
  since the backup was taken.
- **No file deletion.** The importer never writes outside the app's
  private storage and never removes the SAF-picked source file.
- **No background work.** The whole import is a foreground
  coroutine driven by the user's tap on the confirmation dialog;
  cancelling the activity discards it.

## Reporting issues

For potential security issues, please use the private "Report a
vulnerability" workflow on GitHub rather than the public issue
tracker, so a fix can land before the underlying detail does. Please
include:

- A description of the issue and its impact.
- Steps to reproduce (ideally a sample file for parser issues).
- The app version (`versionName` from Settings → About, or the
  installed APK).

For dictionary / EZ Input Method correctness questions (these are not
security issues), file a regular GitHub issue instead.
