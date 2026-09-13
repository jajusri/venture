# Venture Execution Plan — 2026-09-13

**Supersedes** `docs/MVP1_EXECUTION_PLAN.md` (2026-07-28) as the active plan — that document predates
Catalogue, Transaction Mode, and Vartalap entirely. Built from `VENTURE-GROUND-TRUTH-2026-09-13.md`.

**How this project runs from here** (replaces the ChatGPT-architect/Claude-builder split and its
ceremony — see chat for the full reasoning): Claude does both architecture and implementation.
Automated tests stay mandatory for every change — that's the real quality/speed lever, not
process. This plan and the ground-truth doc are the source of truth, updated when something
material changes, not after every task. No mandatory multi-document sync, no 13-point defect
write-ups, no Brainstorm-1/2 gate for routine work. Claude proceeds autonomously through
well-specified work and stops only for genuine product/policy decisions — marked **DECISION**
below.

---

## Phase 0 — Validation & Hygiene Closeout

Small, mostly mechanical. Goal: know the real current state with certainty, and clear the
release blockers that have been sitting untouched.

1. Re-run Connector/Desktop full suites on Windows (real result, not the Linux-sandbox run) —
   confirm whether the 4 Connector / 31 Desktop failures I saw are purely platform-path artifacts.
2. Run Android `testProdDebugUnitTest` + `lintProdDebug` on Windows, get a current count (last
   known: 1,317/1,317, pre-Catalogue/Transaction Mode/Vartalap).
3. Bump Android `versionCode`/`versionName` to reflect what's actually shipped — currently frozen
   at `continuity.28` despite three major features landing since.
4. Confirm whether backend (Trust/Relay) tests run in `.github/workflows/` CI; wire them in if not.
5. **DECISION** — Android `applicationId`: not yet chosen, flagged in the ledger as an effectively
   permanent choice once published to Play Store. Needs picking now, not deferred.
6. **DECISION** — Release signing: no Android keystore or Windows code-signing certificate exists
   anywhere in the repo/environment (verified). I can generate the Android keystore; a Windows
   code-signing cert has to be purchased from a CA (DigiCert, Sectigo, etc.) — that's on you.
7. **DECISION** — TD-009 (trusted-LAN connector access doesn't require the device-auth path that
   already exists): given 1M-user ambitions, should this become mandatory rather than opt-in?
8. TD-022 (unbounded manual voucher date-range query): pick a sane max span (e.g. 1 year) so this
   can actually close instead of sitting as an accepted limitation indefinitely.

## Phase 1 — Automation

As literally scoped in the existing docs: **WorkManager background auto-sync** — running the
already-built Adaptive Sync frequency logic without the app open, rather than only on foreground
refresh. Also in scope per Product Soul's Automation pillar, smaller: Smart Notifications (sync
completed/failed, connector unavailable) — cheap to add once background work exists.

1. Design: WorkManager periodic + expedited work, battery/network constraints (Doze-aware),
   reuse the existing Adaptive Sync engine rather than duplicating scheduling logic.
2. Implement, with tests for the constraint/retry/backoff logic specifically (not just the sync
   call itself, which is already tested).
3. Smart notifications: sync completed/failed, connector unavailable — bounded, no spam, matches
   the Non-Negotiables' "never surprise the user."

## Phase 2 — Complete & Expand

Deliberately left open here — this is where we pick up again once Phase 0/1 are done, since it's
a product-priority call, not an engineering one. Known candidates already documented but not
built: Seller Inbox UI (Vartalap's receiving side has backend/domain support already, no screen
yet), Contact Intelligence (blocked pillar, now that Production Validation-equivalent work is
underway), Home Insights dashboard (no content spec exists yet), Referral Tree. We'll pick the
order when we get here.

## Phase 3 — 1M-User Hardening (two tracks, run independently)

### Track A — Per-install (Android / Connector / Desktop)

Already architecturally sound for this (offline-first, capability-based, no shared load between
installs). Remaining work is about surviving scale in *aggregate*, not shared infrastructure:

- Room migration safety: 22 migrations exist now — audit that upgrading from every prior installed
  version still works, not just the latest few.
- Play Store readiness: signing (Phase 0), data-safety form, privacy policy, staged rollout plan.
- Fleet-visible, privacy-safe crash/ANR reporting — right now Diagnostics is pull-based (a user
  screenshots it); at 1M installs you need push-based aggregate visibility without logging
  accounting payloads (the Non-Negotiable that must hold regardless).
- Re-verify the performance budgets already written in
  `docs/engineering/VENTURE-NON-FUNCTIONAL-REQUIREMENTS-AND-BUDGETS.md` still hold under
  Catalogue/Transaction Mode's larger data volumes.

### Track B — Central backend (Trust/Relay)

This is genuinely new work — the one part of VENTURE that is shared infrastructure, so it's the one
outage or breach that would hit every business at once.

- Containerize (Dockerfile/docker-compose) — none exists yet.
- **DECISION** — hosting target: which cloud/provider? Do you have an existing account anywhere
  (AWS/GCP/Azure/Fly.io/Render/Railway), or is this a from-scratch choice?
- Wire backend tests into CI (ties to Phase 0.4).
- Load-test Relay's backpressure/mailbox paths under realistic concurrent-business volume, not
  just correctness (tests currently prove correctness, not capacity).
- Observability: structured logging is already there (pino); needs metrics + uptime alerting.
- Secrets management: currently `.env`-based; needs a real secrets store before any real
  deployment (Postgres credentials, signing keys).
- Backup/disaster-recovery plan for Postgres — this is now the one place VENTURE has data that
  isn't locally recoverable per-business.
- Revisit TD-009 policy decision (Phase 0.7) here too, since it's the same boundary from the
  other side.

---

## Immediate next step

Phase 0 items 1–4 and 8 I can move on right away. Items 5–7 (and Track B's hosting choice, when
we get to Phase 3) need your call first — flagged in chat.
