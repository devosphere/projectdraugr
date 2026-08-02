# Security — the Anthropic API key

The AI layer (three-AI integration) needs an Anthropic API key. This is how it is kept safe.

## The key is never in the repo

- It is read **only from the environment** (`DRAUGR_AI_API_KEY` / `ANTHROPIC_API_KEY` in `application.yml`). Nothing is hardcoded.
- The encrypted key file (`.secrets/anthropic.key`) and the legacy `secrets.local.ps1` are **gitignored**. A `git clone` therefore contains **zero secrets** — sharing the repo never leaks the key.
- The code never logs the key (startup logs only `key present: true/false`).

## Local at-rest encryption (Windows DPAPI)

Set the key once:

```
powershell -ExecutionPolicy Bypass -File scripts\Set-DraugrApiKey.ps1
```

You paste the key at a hidden prompt; the script encrypts it with **Windows DPAPI (CurrentUser)** and writes the ciphertext to `.secrets\anthropic.key`. That blob is decryptable **only by your Windows user on this machine** — copy it to another PC, or open it as a different user, and you get undecryptable ciphertext.

At launch, `Start-Draugr.ps1` decrypts it **in memory only**, sets it as an in-process env var the backend inherits, and never writes the plaintext to disk nor persists it as a user/system environment variable. If the file is missing or can't be decrypted, the game runs with the AI layer **off** — never a crash.

- **Rotate:** re-run `Set-DraugrApiKey.ps1`.
- **Disable / remove:** delete `.secrets\anthropic.key`.

### What this protects against — and what it doesn't

Protects against someone **accessing your directory**, copying the file, browsing a backup/zip, or logging in as a **different** Windows user — they get ciphertext. It does **not** protect against someone using your own **unlocked, logged-in** Windows session, or malware running **as you** — those could run the same decrypt. No local at-rest scheme can, because the key must be decryptable by you to be usable. Backstops for that: set a **spend limit** in the Anthropic Console, and **rotate/revoke** the key there anytime.

## Sharing the game for internal testing

- **`git clone` is safe** — no key travels with it. Testers run the game with AI **off** and get the full, correct game on deterministic prose (the AI is a pure upgrade layer; only the AI-refined flourishes and AI summaries are absent).
- If you **zip your working folder** instead of cloning, **exclude `.secrets/`** (and `secrets.local.ps1`) from the archive.
- **Never put your key on a machine you don't control.** For testers to have the AI experience, either they use **their own** key, or you host the backend on a **server you control** (key server-side / a CI/deploy secret such as GitHub Secrets) and testers point a client at it. GitHub Secrets is for that CI/deploy path — it cannot feed a locally-run backend.
