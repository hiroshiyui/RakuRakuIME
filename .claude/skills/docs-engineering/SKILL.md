---
name: docs-engineering
description: Writing/updating project documentation for RakuRaku IME (README, TODOs, LICENSE notices, AI-assistant guide files). Use when the user asks to update docs or rewrite user-facing text.
argument-hint: task description
---

# Document Engineering

You are performing documentation tasks for RakuRaku IME (輕鬆輸入法).

## Project Documentation Files

| File | Purpose |
|------|---------|
| `README.md` | Project overview, attribution, licensing, setup instructions. |
| `LICENSE` | GPL-3.0-or-later full text for the application. |
| `TODOs.md` | Active feature backlog and completion tracking. |
| `GEMINI.md` | Guidance for the Gemini AI assistant (project primer). |
| `CLAUDE.md` | *(may be added)* Guidance for Claude Code. Follow the same tone as `GEMINI.md` if added. |
| `app/src/main/assets/gpl.txt` | GPLv2 text bundled for the dictionary data. |
| `app/src/main/assets/ezphrase.txt` | 輕鬆資訊 public license text bundled for the dictionary data. |

There is currently no `PRIVACY-POLICY.md`, `NOTICES.md`, or `fastlane/` metadata. If the user asks to add any of those, confirm scope first — they are new artifacts, not updates.

## Attribution Rules

The dictionary data (`ezbig.utf-8.cin`) was produced by:

- **Original Author:** 高衡緒
- **Organization:** 輕鬆資訊企業社 (no longer operational)

Any user-facing doc that describes the dictionary must preserve this attribution and the dual license note (GPLv2 + 《輕鬆資訊「輕鬆輸入法大詞庫」公眾授權書》). See `README.md` for the canonical wording.

## Language

- Primary documentation language is **English**.
- The product name has both an English form (*RakuRaku IME*) and a Traditional Chinese form (*輕鬆輸入法*). When both are relevant (title lines, store copy), include both.
- When Traditional Chinese is requested, use Traditional characters only — never Simplified.
- If the user asks for a Traditional Chinese version of a doc that only exists in English, ask whether they want a parallel file or interleaved bilingual sections before starting.

## Writing Style

- Clear and approachable; technical third person for README and internal docs.
- **Respect upstream contributors.** When describing issues in dependencies or upstream data sources, prefer softened wording — "issue", "behavior", "limitation" over "bug"; "resolved" or "addressed" over "fixed". In Traditional Chinese, prefer 「問題」 over 「錯誤」 or 「bug」 for upstream references.
- When updating licensing / attribution sections, do not reword the legal text of the referenced licenses.

## Dependency & License Notices

If the user asks for a third-party notices document, derive the contents from `gradle/libs.versions.toml` and `app/build.gradle.kts`. Do not invent library licenses — check each dependency's declared license before listing it.

## Task: $ARGUMENTS
