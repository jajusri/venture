# BUDCOM Ground-Truth Snapshot — 2026-09-13

**Produced by:** Claude, working directly against the `venture` repo clone (this project's
Claude-only fork of Budcom) — by reading git history and running live test suites, not by
trusting prior status documents. The two prior canonical documents
(`BUDCOM-CURRENT-DEVELOPMENT-STATUS.md`, `BUDCOM-DEVELOPMENT-LEDGER.md`) both stop at **Phase 71
(2026-08-25)** and do not mention any of the work described below — they are stale as of this
snapshot. `BUDCOM-GROUND-TRUTH-PROJECT-STATE-AUDIT.md` (2026-08-23) is the prior snapshot this
one supersedes.

**Repo/branch:** `main`, in sync with `origin/main` (0 ahead, 0 behind). This branch is the old
Budcom repo's `integration/final-mobile-desktop` branch, renamed — it is materially ahead of what
that repo's own `main` branch had as of the last audit.

---

## 1. What shipped since the last audit (192 commits, Aug 23 → Sep 13)

| Area | Commits | What it is |
|---|---|---|
| Transaction Mode | 35 | Buyer-side ordering: cart/composer, Estimate vs Purchase Order, WhatsApp share, Buy Again/Reorder, seller inbox |
| Trust service | 28 | New central identity/authority service — business device registration, credential issuance, key rotation, membership approval |
| Android | 22 | Feature wiring, theming, pairing UX, hardening across the above |
| Relay service | 19 | New central store-and-forward message delivery — mailboxes, backpressure, replay guards, acknowledgements |
| Vartalap (cross-cutting) | 17 | The transport/envelope layer connecting Trust+Relay to Android (device signing, envelope contracts) |
| Controlled-pilot tooling | 16 | Real two-phone pilot harness/orchestration scripts (PowerShell) |
| Catalogue | 14 | **Closed out to completion** (see below) |
| Tooling/dev-experience | 9 | Environment doctor, deterministic dev launch scripts |
| Desktop | 6 | Theme refresh, pairing shortcut, lifecycle fixes |
| Connector | 3 | Gated Stock Item fields, health timestamp |

**Catalogue (MVP-1.4) is complete**, not a remaining task. Declared done 2026-08-25 after live
verification against a second real company's data; two defects found live (TD-050, TD-051) were
fixed (TD-050) or documented as a non-blocking evidence gap (TD-051). Company isolation and
price-state semantics independently re-confirmed at closure.

**Vartalap is real, working, live-tested infrastructure**, not a design sketch — verified on two
physical phones with real orders and a real WhatsApp share flow, three genuine defects found and
fixed live in that session (missing FileProvider path entry, a data-loss-on-failed-share bug, a
mislabeled button).

**Critical architectural fact:** `backend/README.md` states plainly that Trust and Relay are
"**separately deployable BUDCOM central services... not part of the LAN/Tally Connector**."
Unlike the Connector (which runs locally per business, next to that business's own Tally), Trust
and Relay are meant to be hosted once, centrally, Postgres-backed, and shared across every
business's app. This is the one piece of BUDCOM's architecture that is genuinely multi-tenant
shared infrastructure — everything else (Connector, Android, Desktop) remains local-per-install.

## 2. Current versions

| Component | Version | Note |
|---|---|---|
| Android | versionCode 29 / `0.1.1-continuity.28` | **Stale** — unchanged despite Catalogue + Transaction Mode + Vartalap all shipping since it was set |
| Room DB schema | version 22 | Was version 10 at the last audit — 12 migrations landed since (Catalogue, Transaction Mode, pairing/trust caching) |
| Connector | 0.4.6 | |
| Desktop | 0.4.20 | |
| Backend (Trust/Relay) | 0.1.0 | New service, pre-1.0 |

## 3. Test health (run fresh, in a Linux cloud sandbox — not trusted from docs)

| Component | Result | Notes |
|---|---|---|
| Backend (Trust/Relay) | **286 passed, 10 skipped, 0 failed** (46 files) | Skipped tests are the live-Postgres integration suite (needs a real DB, not run here) |
| Connector | **1,494 passed, 4 failed** (164 files) | All 4 failures look environment-specific, not product defects: 2 are worker-thread tests expecting a prior `tsc` build step that wasn't run here; 1 is a Windows-path test (`C:\imports\...`) that only makes sense on Windows, run here on Linux. **Needs re-confirmation on Windows before trusting either way.** |
| Desktop | **710 passed, 31 failed** (69 files) | Same pattern — the failures shown are Windows path/Electron-mock assumptions (`C:\Program Files\Budcom Desktop\...`, `app.setPath` override) that don't hold on Linux. **Needs re-confirmation on Windows.** |
| Android | **Not run** | No Android SDK available in this cloud sandbox (Google's SDK repository is not on the allowed network list here). Last known figure (pre-audit): 1,317/1,317 unit tests, 0 lint issues — needs a fresh run on the Windows machine to confirm against current code. |

**Action item:** run `npm test` for Connector and Desktop on the actual Windows machine (or in a
Windows CI runner) to get a true pass/fail count. The cloud sandbox is fast for everything else
but cannot validate Windows-specific paths.

## 4. Open technical debt (of 51 tracked items, only 3 are open)

| ID | What | Why it's still open |
|---|---|---|
| TD-009 | Connector's trusted-LAN bind mode doesn't *require* the authenticated pairing path that already exists (`secureMobilePairingEnabled`) | Deliberately left as an operator opt-in — tightening it to mandatory needs a product decision, not just a code change |
| TD-022 | Manual voucher date-range refresh (typed dates, not the automatic sync path) has no maximum-span validation, unlike the automatic path which is already bounded | Needs a product decision on a sane max span |
| TD-027 | Tally Voucher Export can transiently exclude vouchers Tally's own UI already shows | Recorded observation, not a code defect, no action taken by design |

All three are policy/product decisions, not blocked engineering work — a healthy sign for code
quality. TD-009 is the one most relevant to scaling past a single pilot: right now, secure device
auth on the LAN connector is optional per install.

## 5. Backend (Trust/Relay) production-readiness gap, relevant to 1M-user hardening

Real, tested, clean-architecture code (domain/application/http/persistence layers, migration
tooling exists). What's missing before this can actually carry load for many businesses:

- No containerization or deployment config found (no Dockerfile, no docker-compose, no IaC) — it has never actually been deployed anywhere outside dev/pilot.
- Protections (rate limiting, abuse handling) exist for Trust (`trust-protections.ts`) but nothing equivalent was found for Relay specifically — worth confirming Relay's backpressure tests (`relay-backpressure.test.ts`, passing) actually cover production-scale abuse, not just correctness.
- No CI workflow currently runs the backend's own test suite (the three `.github/workflows/*.yml` files are Android/release-focused — needs confirming whether backend is wired in).
- Never load-tested; no observability/monitoring story yet (this matters more here than anywhere else in the codebase, since it's the one shared service a Trust/Relay outage would affect every business at once, unlike a Connector outage which only affects one business).

## 6. Recommendation

This snapshot replaces the Aug 23 audit as the basis for planning. Given it, the earlier agreed
sequencing still holds:

1. **Validation/hygiene closeout** — re-run Connector/Desktop/Android tests on Windows for a true
   count; bump Android version numbers to match actual shipped content; confirm whether backend
   tests run in CI.
2. **Automation** — as literally scoped in existing docs, this is WorkManager background
   auto-sync (Adaptive Sync's frequency logic already exists; the "run it without the app open"
   piece doesn't yet). Worth confirming this is still what's meant, now that Catalogue turned out
   to already be done.
3. **1M-user hardening** — two genuinely different tracks: (a) per-install robustness for
   Android/Connector/Desktop, which the existing architecture already supports well; (b) real
   backend engineering for Trust/Relay (containerize, deploy, load-test, monitor, decide on
   TD-009) since that's the one piece any outage or breach hits every business at once.
