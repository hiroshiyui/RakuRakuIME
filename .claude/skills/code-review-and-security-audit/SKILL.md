---
name: code-review-and-security-audit
description: Review code for quality, correctness, and security vulnerabilities. Use when the user asks to review code, audit for security issues, or check for bugs and anti-patterns.
argument-hint: file path, component name, or scope of review
---

# Code Review and Security Audit

You are performing code review and security auditing for RakuRaku IME (輕鬆輸入法).

## Stack Reminders

- Kotlin + Jetpack Compose + Material 3 for UI.
- Room (with KSP) + FTS4 + a pre-packaged database asset for the dictionary.
- Coroutines / `suspend` with `Dispatchers.IO` for parsing and DB work.
- DataStore Preferences for user settings.
- No JNI, no C/C++, no native libraries — if a review surfaces native concerns, it is out of scope for this project.

## Review Checklist

### Code Quality

- Null safety: prefer null-safe operators; avoid `!!` unless justified.
- Coroutines: suspend functions stay on the correct dispatcher (`Dispatchers.IO` for disk / DB / asset work); UI state updates happen on the main thread or via Compose state (thread-safe).
- Room: transactions wrap multi-statement writes; FTS4 queries use the correct MATCH syntax; indexes align with the actual query shapes.
- Compose: state is hoisted where it needs to be; `remember`/`rememberSaveable`/`LaunchedEffect` keys are correct; avoid leaking `Context` into long-lived state.
- Resource management: `InputStream` / `Reader` / `Cursor` closed with `.use { }` or `try/finally`.
- Error handling: appropriate use of try/catch at boundaries (asset I/O, DB init); no silently swallowed exceptions.
- No dead code, unused imports, or redundant logic.

### Code Smells

- Long methods, large composables, deep nesting.
- Duplicated logic across key handlers / layout code — candidate for extraction.
- Magic numbers or strings where a named constant, enum, or sealed class would read better.
- Primitive obsession where a domain type would be clearer (e.g., keystroke, character).
- Long parameter lists — group into a data class.
- Mutable shared state — prefer immutable data and local state; flag unnecessary `var` or global mutable collections.

### Refactoring Suggestions

- Extract method / extract composable when a block has a distinct responsibility.
- Replace conditional chains with sealed classes or enums where it aids clarity.
- Use Kotlin idioms: scope functions, destructuring, extension functions, `buildList` / `buildString`.
- Lift repeated Compose logic into reusable composables (e.g., `SettingsSwitch`).
- Reduce coupling: prefer passing narrowly-typed dependencies over full `Context` or database handles when practical.
- Improve testability: flag code that is hard to unit test (hidden singletons, direct asset access) and suggest seams.

### Android / IME-Specific Security

- **Keystroke privacy**: this app is an IME and processes all user input. Review for any log statements, crash reports, analytics, or persistence that could leak user keystrokes, dictionary lookups, or clipboard data.
- **SharedPreferences / DataStore**: no sensitive data stored; nothing world-readable.
- **Intent handling**: validate intent extras; do not re-dispatch untrusted intents.
- **Exported components**: check `AndroidManifest.xml` — activities / services / receivers should not be inadvertently exported; the IME service itself must be exported but only with the proper `android.permission.BIND_INPUT_METHOD` guard.
- **ProGuard/R8**: if/when enabled, verify rules don't strip security-critical code.
- **WebView**: not currently used; flag any introduction.

### Data & Dictionary Integrity

- Asset parsing (`CinParser`): validate line format; avoid crashes on malformed input.
- Room migrations: schema changes must ship a migration or an intentional `fallbackToDestructiveMigration`; destructive fallback drops user frequency data — call that out.
- Pre-packaged DB asset: ensure it stays in sync with the `.cin` asset; the asset-hash sync in `CinParser` is the guardrail.

### General Security

- No hardcoded secrets, API keys, or credentials.
- No command injection via `Runtime.exec()` or `ProcessBuilder`.
- No path traversal in file operations.
- No insecure RNG for security-sensitive operations.
- Dependencies: check for known vulnerabilities in `gradle/libs.versions.toml`.

## Output Format

Report findings using this structure:

### Critical / High
Security vulnerabilities, crashes, data loss risks.

### Medium
Logic bugs, thread safety concerns, concrete code smells.

### Low / Informational
Style, readability, minor optimizations.

For each finding, include:
- **File and line number** (e.g., `CinParser.kt:42`)
- **Description** of the issue
- **Impact** — what could go wrong
- **Recommendation** — how to fix it, with code if helpful

## How to Run

Without arguments, review recently changed files:
```bash
git diff --name-only HEAD~5
```

With a scope (file, directory, or component), focus on that area.

For a full audit, systematically review:
1. Data layer (`app/src/main/java/org/ghostsinthelab/app/rakurakuime/data/`)
2. IME service and input handling
3. Compose UI layer (`ui/`, `MainActivity`)
4. Configuration (`AndroidManifest.xml`, `app/build.gradle.kts`)
5. Dependencies (`gradle/libs.versions.toml`)

## Task: $ARGUMENTS
