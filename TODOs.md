# RakuRaku IME TODOs

Outstanding work for RakuRaku IME. Completed items have been cleaned
out — see `git log` for the full history of what's landed.

## Input & Logic
- [ ] **Physical Keyboard Support:** Logic to handle typing when a Bluetooth or USB keyboard is attached.

## System & Settings
- [ ] **Accessibility (TalkBack):** Add comprehensive `semantics` labels for candidates, roots, and function keys.

## Code Review & Security Audit (2026-04-19)

Remaining Low/Informational items from the 2026-04-19 audit pass.
(H-tier, M-tier, and L1/L2/L4/L5 have already landed.)

### Low / Informational

- [ ] **L8 — `fallbackToDestructiveMigration` = silent data loss on schema bump**
    - **File:** `data/ImeDatabase.kt:41-42`.
    - **Status:** Documented in `CLAUDE.md` and accepted as a
      tradeoff. When a future schema version is needed, decide then
      whether to write an actual `Migration` (preserving learned
      frequencies) or continue with destructive migration (users
      reimport on upgrade).
