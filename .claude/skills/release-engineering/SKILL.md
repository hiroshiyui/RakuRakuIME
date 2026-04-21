---
name: release-engineering
description: Release engineering tasks for RakuRaku IME — version bumping, building release APKs, tagging, and creating GitHub releases. Use when the user asks to prepare a release, bump version, tag, or build for distribution.
argument-hint: task description
---

# Release Engineering

You are performing release engineering tasks for RakuRaku IME (輕鬆輸入法).

## Version Scheme

- **versionName**: semver-style `MAJOR.MINOR.PATCH` (e.g. `1.0.4`).
- **versionCode**: monotonically increasing integer.
- Both are defined in `app/build.gradle.kts` under `defaultConfig`.

Bump manually by editing `app/build.gradle.kts`. No `bumpPatchVersion` Gradle task exists in this repo — do not invoke one.

## Release Process

1. **Ensure all changes are committed** on `main`.
2. **Bump version** — edit `app/build.gradle.kts`:
   - Increment `versionCode` by 1.
   - Update `versionName` per the change's scope (patch / minor / major).
3. **Update docs if needed** — `README.md`, `TODOs.md`.
4. **Run verification** before tagging:
   ```bash
   ./gradlew :app:lint
   ./gradlew :app:testDebugUnitTest        # if unit tests exist
   ./gradlew :app:assembleDebug
   ./gradlew :app:assembleRelease          # release build (minification per build.gradle.kts)
   ```
5. **Build signed APKs** — signing requires a keystore passphrase. **Do not run the signed build automatically.** Prompt the user to build via Android Studio (Build → Generate Signed APK) or with their local signing config, then wait for confirmation. The artifacts land in `app/release/*.apk` and `app/debug/*.apk`, with matching `.asc` signature files.
6. **Create release commit** — subject: `Release <versionName>`.
7. **Tag the release** — lightweight tag matching `versionName` (e.g. `1.0.4`), no `v` prefix:
   ```bash
   git tag <versionName>
   ```
8. **Push** commit and tag after the user confirms (user's standing preference: push without pausing for confirmation when a release is fully prepared):
   ```bash
   git push origin main
   git push origin <versionName>
   ```

## GitHub Release

Create the GitHub Release with `gh` once signed APKs are ready. Write a short human-readable summary; do not rely solely on `--generate-notes`.

```bash
gh release create <versionName> \
  --title "<versionName>" \
  --notes "$(cat <<'EOF'
<short human-readable summary>

**Full Changelog**: https://github.com/hiroshiyui/RakuRakuIME/compare/<previous-tag>...<versionName>
EOF
)" \
  app/release/org.ghostsinthelab.app.rakurakuime-<versionName>-release.apk \
  app/release/org.ghostsinthelab.app.rakurakuime-<versionName>-release.apk.asc \
  app/debug/org.ghostsinthelab.app.rakurakuime-<versionName>-debug.apk \
  app/debug/org.ghostsinthelab.app.rakurakuime-<versionName>-debug.apk.asc
```

Attach both release and debug APKs plus their `.asc` signatures (established pattern from `1.0.3` / `1.0.4`).

## F-Droid Metadata

After cutting a GitHub release, update F-Droid metadata. Two parallel files must stay in sync:

1. **In-repo reference copy:** `fdroid.yml` (at the repo root).
2. **F-Droid's `fdroiddata` repo:** `/home/yhh/MyProjects/fdroiddata/metadata/org.ghostsinthelab.app.rakurakuime.yml`.

For each file, update:
- The latest `Builds:` entry — set `versionName`, `versionCode`, and `commit` to the new release (the `commit` field references the tag name).
- `CurrentVersion:` and `CurrentVersionCode:`.
- The "Tag/commit references below assume release …" comment at the top of the in-repo `fdroid.yml` (not present in the `fdroiddata` copy).

The changes in `/home/yhh/MyProjects/fdroiddata/` live in a separate git repo — remind the user to commit and push that one themselves; this skill's `commit-and-push` only covers the RakuRakuIME repo.

## Fastlane Metadata

Fastlane per-locale changelogs must be refreshed for every versionCode that ships publicly:

- `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
- `fastlane/metadata/android/zh-TW/changelogs/<versionCode>.txt`

Notes:
- **Filename is `<versionCode>.txt`**, not `<versionName>.txt`. So `1.0.4` (versionCode 6) lands at `6.txt`.
- Keep each changelog to the diffs *since the previous F-Droid-published versionCode*, not since the prior internal bump — if versionCodes 2–5 were never published, the new changelog should cover everything since the last published one.
- Write both locales. The zh-TW copy should use 台式漢語 (not 繁體中文) phrasing.
- Screenshots under `fastlane/metadata/android/{en-US,zh-TW}/images/phoneScreenshots/` are updated by the separate `/take-and-update-screenshots` skill — don't regenerate them here unless the UI changed visibly in this release.

## Build Commands Reference

```bash
./gradlew :app:assembleDebug                # Debug build
./gradlew :app:assembleRelease              # Release build
./gradlew :app:lint
./gradlew :app:testDebugUnitTest            # Unit tests (if present)
./gradlew :app:connectedDebugAndroidTest    # Instrumented tests (needs device/emulator)
./gradlew :app:clean
```

## Important Reminders

- Single remote: `origin` → `git@github.com:hiroshiyui/RakuRakuIME.git`. No mirror.
- The release APK requires a signing config the user maintains outside version control.
- Force-push to `main` is never done without explicit user authorisation.
- Release sequence is always: bump → verify → sign → commit → tag → push → `gh release create` → update `fdroid.yml` + `fdroiddata/…` → add `changelogs/<versionCode>.txt`.

## Task: $ARGUMENTS
