# Sharing the AI with internal testers (without exposing the key)

> **Project Draugr — Architecture / Ops**
>
> *Your API key must never live on a machine you don't control. This is how testers get the
> AI-enabled experience anyway.*

**Status:** Decision record + build plan for when we hand AI-enabled builds to internal testers.
Nothing here is built yet — today, testers run with `DRAUGR_AI_ENABLED=false` (full game on
deterministic prose). See [three-ai-integration.md](three-ai-integration.md) and [SECURITY.md](../../SECURITY.md).

---

## The rule this whole document exists to satisfy

**Never put your Anthropic key on a machine you don't control.** A key that ships to a tester can be
read from disk/memory, leaks, and bills *your* account with no cap you can enforce from their side.
Obfuscation is not protection. So "just enable AI in the build we send" is off the table.

## Why it isn't trivial today — the architecture constraint

Draugr currently runs **fully local per player**: the launcher starts *that player's* Spring backend +
Postgres + frontend, and the backend calls Anthropic directly. So on a tester's machine, the thing
that needs the key is *their* backend — and we won't put the key there. Two independent problems
follow, and the options below solve them separately:

1. **Where does the key live** so the tester can make AI calls without holding it? (the security problem)
2. **Does the world need to be shared** or stay one-per-tester? (an architecture problem that only some
   options drag in)

---

## Options, worst-fit to best-fit

### Option A — Each tester brings their own key

Each tester creates their own Anthropic key and sets it locally (via `Set-DraugrApiKey.ps1`). Your key
never leaves your machine; their usage bills their account.

- **Good for:** one or two technical testers who don't mind creating a key and paying cents.
- **Bad for:** non-technical testers (a brother/friend), or when *you* want to fund and control the
  testing spend. Doesn't scale and pushes billing onto them.
- **Effort:** zero — already supported.

### Option B — **AI proxy** (recommended for internal testing)

Stand up a **small server you control** that holds your key and speaks the Anthropic Messages API.
Each tester still runs the whole game locally (own backend + world — *no multiplayer work needed*), but
their backend points its AI calls at **your proxy** instead of `api.anthropic.com`. The proxy injects
your key server-side and forwards to Anthropic. Testers get AI-on with **no key on their machines**.

```
tester's local backend ──(per-tester token)──▶  YOUR AI proxy (holds the real key)  ──▶  Anthropic
        (no key)                                  rate-limit · spend cap · revoke · log
```

- **Why it's the best fit:** it solves problem 1 (key stays on your server) *without* touching problem 2
  (each tester keeps their own local world). It rides the existing `LanguageModel` seam — the change is
  a base-URL/token swap, not new game architecture.
- **You keep control:** issue each tester a **proxy token** (not the Anthropic key); rate-limit and cap
  spend at the proxy; revoke a single tester without rotating your key; log usage per tester.
- **Effort:** low–moderate — a thin always-on web service + a small client config addition (below).
- **Cost:** one small VM / serverless function; you fund the Anthropic spend (bounded by the proxy).

### Option C — Fully hosted backend (the launch path, not for early testing)

Host the entire backend + Postgres on your infrastructure; testers run only the **frontend**, pointed at
your server (`VITE_DRAUGR_API_URL=https://your-server`). The key is server-side and the client is a thin
UI.

- **Why not yet:** the game is **one living chronicle per world**. A single shared backend means every
  tester shares one world and one chronicle — they'd collide. Option C therefore requires **per-user
  world/session isolation** first (the multiplayer/multi-tenant step), plus auth, hosting Postgres, and
  a deploy pipeline. That's a milestone, not a testing convenience.
- **When:** this is the eventual public/launch architecture. Do it when you're building multiplayer, not
  to unblock a friend this week.

### Recommendation

| Situation | Use |
|---|---|
| 1–2 technical testers, right now | **Option A** (own key) |
| A handful of non-technical internal testers, you fund the spend | **Option B** (AI proxy) — recommended |
| Public / broader launch, shared or persistent worlds | **Option C** (fully hosted) — its own milestone |

Start at A if you only need yourself + one dev; move to **B** the moment you hand it to non-technical
testers. C comes with multiplayer, later.

---

## Build checklist for Option B (the AI proxy)

1. **Proxy service** (any stack; a ~100-line Node/Python/Go service, or a serverless function):
   - Accepts requests from tester backends, authenticated by a **per-tester bearer token** you issue.
   - Injects your real `ANTHROPIC_API_KEY` (from the host's env / a cloud secret — e.g. GitHub Actions →
     deploy, or the platform's secret manager) and forwards to `POST /v1/messages`.
   - **Guardrails:** per-token rate limit; a global + per-token monthly spend cap; request logging keyed by
     token; an allowlist of models (so a token can't request a pricier model than you intend — this is also
     where per-tier model selection could live). Reject anything else.
   - Never returns or logs the real key.
2. **Two shapes are possible — prefer the first:**
   - **Base-URL passthrough:** the proxy implements the Anthropic Messages wire format, so the client just
     overrides the SDK base URL and sends the per-tester token as its "key". Smallest client change.
   - **Custom endpoint:** the proxy exposes its own `/narrate` etc.; needs a `ProxyLanguageModel` client impl.
     More work; only if you want to reshape requests.
3. **Client config addition (small, planned — not built yet):** add `draugr.ai.base-url`
   (`DRAUGR_AI_BASE_URL`) to `AiProperties` and pass it to `AnthropicOkHttpClient.builder().baseUrl(...)` in
   `AnthropicLanguageModel`. Then a tester build ships:
   - `DRAUGR_AI_ENABLED=true`
   - `DRAUGR_AI_BASE_URL=https://your-proxy`
   - `DRAUGR_AI_API_KEY=<that tester's proxy token>`  ← not your Anthropic key
   The `LanguageModel` seam and per-agent model config already exist; this is the only code delta.
4. **Distribute** the tester build with the proxy URL + that tester's token baked into their encrypted
   local config (the token is low-value and revocable — unlike the Anthropic key). Rotate/revoke per tester
   at the proxy.

## Operational guardrails (all options)

- **Console spend limit** on the Anthropic account regardless of option — the last-resort cap.
- **Rotate/revoke** at the first sign of misuse: the Anthropic key (Option A/your proxy) or a tester's proxy
  token (Option B) — revoking a token never disrupts other testers.
- **Log usage per identity** (per key in A, per token in B) so an anomaly is attributable.
- **Never** email/paste a key or token in plaintext; deliver via the encrypted-at-rest mechanism
  ([SECURITY.md](../../SECURITY.md)).

## What would change this doc

- Draugr moving to per-user isolated worlds (makes Option C viable and probably preferred).
- Anthropic shipping a first-class "hand a scoped, capped sub-credential to a third party" primitive — that
  would shrink the Option B proxy to a credential hand-off.
