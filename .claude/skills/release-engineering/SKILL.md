---
name: release-engineering
description: Release engineering tasks for RakuRaku IME — version bumping, building release APKs, tagging, and creating GitHub releases. Use when the user asks to prepare a release, bump version, tag, or build for distribution.
argument-hint: task description
---

# Release Engineering

You are performing release engineering tasks for RakuRaku IME (輕鬆輸入法).

> **Status:** the project is pre-1.0 (`versionCode = 1`, `versionName = "1.0"` at the time this skill was written). There is no established signing pipeline, no fastlane/F-Droid metadata, and no automated version-bump Gradle task. Treat the workflow below as a baseline; if the user has introduced new tooling since, confirm which steps still apply.

## Version Scheme

- **versionName**: semver-style `MAJOR.MINOR.PATCH` (e.g. `1.0.1`).
- **versionCode**: monotonically increasing integer.
- Both are defined in `app/build.gradle.kts` under `defaultConfig`.

Bump manually by editing `app/build.gradle.kts`. No `bumpPatchVersion` Gradle task exists in this repo — do not invoke one.

## Release Process (baseline)

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
5. **Build signed APKs** — signing requires a keystore passphrase. **Do not run the signed build automatically.** Prompt the user to build via Android Studio (Build > Generate Signed APK) or with their local signing config, then wait for confirmation.
6. **Create release commit** — subject: `Release <versionName>`.
7. **Tag the release** — lightweight tag matching `versionName` (e.g. `1.0.1`), no `v` prefix:
   ```bash
   git tag <versionName>
   ```
8. **Push** commit and tag only after the user confirms:
   ```bash
   git push origin main
   git push origin <versionName>
   ```

## GitHub Release

Create the GitHub Release with `gh` once signed APKs are ready. Do **not** rely solely on `--generate-notes`; write a short human-readable summary first, then append the auto-generated commit list.

```bash
gh release create <versionName> \
  --title "<versionName>" \
  --notes "$(cat <<'EOF'
<short human-readable summary>

**Full Changelog**: https://github.com/hiroshiyui/RakuRakuIME/compare/<previous-tag>...<versionName>
EOF
)" \
  <apk-files...>
```

Attach whichever APKs the user produced (typically a release APK; include debug only if the user asks).

## Build Commands Reference

```bash
./gradlew :app:assembleDebug                # Debug build
./gradlew :app:assembleRelease              # Release build (check minify/shrink settings in app/build.gradle.kts)
./gradlew :app:lint
./gradlew :app:testDebugUnitTest            # Unit tests (if present)
./gradlew :app:connectedDebugAndroidTest    # Instrumented tests (requires a connected device/emulator)
./gradlew :app:clean
```

## Important Reminders

- Single remote: `origin` → `git@github.com:hiroshiyui/RakuRakuIME.git`. No mirror.
- Always confirm with the user before pushing commits or tags or creating GitHub releases.
- The release APK requires a signing config the user maintains outside version control.
- If the user introduces fastlane/F-Droid metadata or a signing automation later, update this skill accordingly.

## Task: $ARGUMENTS
