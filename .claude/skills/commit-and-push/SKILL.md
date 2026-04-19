---
name: commit-and-push
description: Commit code changes and push via Git. Use when the user asks to commit, push, or save their work to the repository.
argument-hint: commit message or description of changes
---

# Commit and Push

You are committing and pushing code changes for RakuRaku IME (輕鬆輸入法).

## Commit Message Convention

The project's history is mixed — some commits use a conventional-style prefix (`docs:`, `perf:`, `fix:`), others use a plain imperative subject (`Add …`, `Fix …`, `Update …`, `Remove …`, `Implement …`). When in doubt, match the style of nearby recent commits (`git log --oneline -20`).

- **Subject line**: imperative mood, concise (≲ 72 chars).
- **Body** (optional): explain *why*, not just *what*; wrap at ~72 chars. Use bullet points for multi-part changes, following the style of `ccdaf29`.
- **Trailers**: current history does not use `Signed-off-by` or `Co-Authored-By` trailers. Do not add them unless the user explicitly asks.

### Subject Line Patterns (existing examples)

- `Add …` — new feature or file.
- `Fix …` — bug fix.
- `Update …` — dependency or content update.
- `Remove …` — deletion of code or files.
- `Implement …` — larger new capability.
- `docs: …`, `perf: …`, `fix: …` — conventional-prefix variants used in recent commits.

## Workflow

1. **Review changes** — `git status` and `git diff` to understand what will be committed.
2. **Stage files** — add specific files by name rather than `git add -A`. Do not stage:
   - Secrets / credentials (e.g., `local.properties`, signing keys).
   - Build artifacts (`app/build/`, `.gradle/`, `.idea/` where gitignored).
3. **Commit** — pass the message via HEREDOC for reliable formatting:
   ```bash
   git commit -m "$(cat <<'EOF'
   Subject line here

   Optional body explaining why.
   EOF
   )"
   ```
4. **Push** — push to `origin/main` immediately after the commits land.
   Do not pause to ask for confirmation first; the user's standing
   preference for this repo is "just push". Still refuse a force-push
   to `main` unless explicitly requested.

## Remotes & Branches

- Single remote: `origin` → `git@github.com:hiroshiyui/RakuRakuIME.git`.
- Main branch: `main`.
- Never force-push to `main` without explicit user approval.

## Task: $ARGUMENTS
