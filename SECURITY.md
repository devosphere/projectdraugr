# Security — the Anthropic API key

The AI layer (three-AI integration) needs an Anthropic API key. This is how it is kept safe.

## The key is never in the repo

- It is read **only from the environment** (`DRAUGR_AI_API_KEY` / `ANTHROPIC_API_KEY` in `application.yml`). Nothing is hardcoded.
- The encrypted key file (`.secrets/anthropic.key`) and the legacy `secrets.local.ps1` are **gitignored**. A `git clone` therefore contains **zero secrets** — sharing the repo never leaks the key.
- The code never logs the key (startup logs only `key present: true/false`).

## Local at-rest encryption

Set the key once:

```
powershell -ExecutionPolicy Bypass -File scripts\Set-DraugrApiKey.ps1
```

You paste the key at a hidden prompt, then choose the protection:

- **With a password (recommended)** — stored as `DPAPI(AES-256(key))`. Unreadable without **both** your
  Windows account on this machine **and** the password. The password is **never stored** (only its
  SHA-256-derived AES key is used, in memory). You type it at each launch to enable AI.
- **Without a password** — DPAPI only, bound to your Windows user + machine. Auto-enables at launch, no prompt.

Either way the ciphertext lands in `.secrets\anthropic.key` (gitignored). At launch, `Start-Draugr.ps1`
decrypts it **in memory only** (prompting for the password if the file is password-protected), sets it as
an in-process env var the backend inherits, and never writes plaintext to disk nor persists an environment
variable. Missing / wrong password / different user or machine → the game runs with the AI layer **off**,
never a crash.

- **Rotate / change password:** re-run `Set-DraugrApiKey.ps1`.
- **Disable / remove:** delete `.secrets\anthropic.key`.

### What this protects against — and what it doesn't

- **DPAPI (both modes)** blocks someone **accessing your directory**, copying the file, browsing a backup/zip,
  or logging in as a **different** Windows user — they get ciphertext.
- **The password (recommended mode) additionally** blocks someone using your **own unlocked, logged-in**
  Windows session — the file stays encrypted until the password is entered, and it lives only in your head.

The remaining risk is a keylogger / malware running **as you** while you type the password (it could capture
it), which no local scheme can fully prevent. Backstops: a **spend limit** in the Anthropic Console, and
**rotate/revoke** the key there anytime. A **guard script that only prompts for a password is not protection**
— the file could be opened directly without running it; encryption (above) is why the password actually gates
access.

## Sharing the game for internal testing

- **`git clone` is safe** — no key travels with it. Testers run the game with AI **off** and get the full, correct game on deterministic prose (the AI is a pure upgrade layer; only the AI-refined flourishes and AI summaries are absent).
- If you **zip your working folder** instead of cloning, **exclude `.secrets/`** (and `secrets.local.ps1`) from the archive.
- **Never put your key on a machine you don't control.** For testers to have the AI experience, either they use **their own** key, or you route their AI calls through a small proxy/server you control (key server-side) and hand each tester a revocable token — not your Anthropic key. The full plan (options, recommendation, build checklist) is in [docs/architecture/sharing-the-ai-with-testers.md](docs/architecture/sharing-the-ai-with-testers.md). GitHub Secrets is for that server/CI path — it cannot feed a locally-run backend.
