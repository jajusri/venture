# MVP-1 Execution Plan

**Status:** Authoritative execution plan for the first signed VENTURE Android APK  
**Date:** 2026-07-28  
**Scope:** Pilot-ready Android companion (`apps/venture_android`) + supporting Connector/Desktop operations  
**Governing docs:** [NON_NEGOTIABLES.md](NON_NEGOTIABLES.md), [PRODUCT_SOUL.md](PRODUCT_SOUL.md), [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), [ROADMAP.md](ROADMAP.md), [PRODUCTION_VALIDATION.md](PRODUCTION_VALIDATION.md), [DECISIONS.md](DECISIONS.md)

This document does **not** authorize Contact Intelligence, AI, OCR, PDF export, or Share Sheet as MVP-1 blockers unless Product explicitly expands scope. Those remain strategic backlog per ROADMAP / PRODUCT_SOUL until scheduled.

---

# Executive Summary

| Item | Assessment |
| --- | --- |
| **Current completion estimate** | **82%** toward first signed pilot APK |
| **Confidence level** | **Medium–High (≈75%)** for feature completeness; **Medium (≈60%)** for release/sign-off until live validation is recorded |
| **Major blockers** | (1) Production Validation still formally incomplete in docs/ROADMAP; (2) Live device + live Connector E2E not fully closed in `PRODUCTION_VALIDATION.md`; (3) No release signing / `1.0.0` packaging pipeline yet; (4) Emulator/instrumentation environment instability (API 37) |
| **Release readiness** | **Not ready** for Version 1.0.0 signed APK or pilot deployment |

**What is already true**

- Android companion **feature foundation for MVP-1 companion workflows is implemented**: Server Config, Company/Session, Dashboard, Ledgers, Stock Items, Vouchers (+ details), Universal Search, manual Sync (ledgers/stock), Diagnostics, Settings/theme.
- Automated Android gates (`assembleDebug`, `testDebugUnitTest`, androidTest compile) have passed in recent validation windows.
- Connector is a viable local companion hub (read-only toward Tally); Desktop can operate Connector lifecycle for pilots.
- Health DTO compatibility fix for live Connector 0.3.1 is on `main` (`75fa44f`).

**What is not yet true**

- Formal Production Validation closeout in ROADMAP / PRODUCTION_VALIDATION evidence.
- Stable instrumented test green on a supported API level.
- Release variant, signing, version bump, ProGuard/R8 policy, pilot runbook, and signed artifact storage.
- End-to-end pilot proof on a physical device with a business company dataset.

---

# Remaining Features

## Critical (must ship before signed 1.0.0 pilot)

| Feature | Purpose | Dependencies | Complexity | Risk | User value |
| --- | --- | --- | --- | --- | --- |
| **Close Production Validation** | Prove companion works against real Connector + real device | Running Connector, online device/API≤36 AVD preferred, update docs | M | High (environment) | Trust — without this, release is speculative |
| **Live E2E defect burn-down** | Fix only defects found in live/instrumented runs | Validation evidence | S–L | Medium | Correctness |
| **Release packaging** | `release` build, signing config, versionName `1.0.0` | Keystore decision, Gradle release config | M | Medium | Installable pilot APK |
| **Release network/security policy** | Cleartext/TLS posture appropriate for pilot (loopback / LAN policy documented) | Connector bind mode, network security config | S–M | High if wrong | Safe connectivity |
| **Pilot Connector baseline** | Pin/document Connector version + health/ready expectations for pilots | Connector release notes | S | Medium | Reproducible support |
| **Privacy-safe logging for release** | Ensure release builds do not log accounting payloads | BuildConfig flags, constitution | S | High if missed | NON_NEGOTIABLE privacy |

## Important (should ship with pilot or immediately after first signed APK)

| Feature | Purpose | Dependencies | Complexity | Risk | User value |
| --- | --- | --- | --- | --- | --- |
| **Instrumented test green on stable AVD** | Regression safety net | API 36 (or physical device) | M | Medium (env) | Prevents silent UI breakage |
| **Manual QA script + evidence pack** | Repeatable pilot acceptance | PRODUCTION_VALIDATION checklist | S | Low | Ops confidence |
| **Desktop ops readiness for pilot** | Start/stop Connector, diagnostics export for support | Desktop hardening already partial | M | Medium | Pilot supportability |
| **Honest empty/offline UX review** | Ensure deferred Room cache does not imply false offline completeness | ADR-004, DECISIONS | S | Medium | Trust |
| **Connector voucher data prerequisite docs** | Pilots understand vouchers need promoted snapshots | Connector voucher foundation flags | S | Medium | Avoid “empty vouchers = bug” |
| **ROADMAP / PRODUCTION_VALIDATION sync** | Docs match reality | Validation closeout | S | Low | AI/human governance |

## Nice to Have (explicitly not MVP-1 blockers)

| Feature | Purpose | Dependencies | Complexity | Risk | User value |
| --- | --- | --- | --- | --- | --- |
| Contact Intelligence Foundation | Phone book ↔ ledger | Production Validation complete (ROADMAP) | L | Medium | High long-term; not required for signed pilot of current surface |
| Ledger/Stock detail screens | Deeper master drill-in | Product decision | M | Low | Convenience |
| Durable Room cache | Stronger offline | Product policy | L | Medium | Offline value |
| Public voucher sync HTTP + Android UX | Fill voucher snapshots from Tally via product API | Connector foundation | L | High | Unlocks voucher content |
| PDF generation / Share Sheet | Root README aspirational MVP wording | Contracts + UX | L | Medium | Sharing workflows (soul pillar — backlog) |
| AI Assistant / Document Intelligence | Soul pillars | Contracts | XL | High | Future |
| WorkManager auto-sync | Automation | Product decision | M | Medium | Convenience |
| Flutter track parity | Alternate client | Explicit product decision | XL | High | None for Android pilot |

---

# Android

## Screen / surface inventory

| Screen / surface | Status | Notes |
| --- | --- | --- |
| Server Configuration | **COMPLETE** / **NEEDS HARDENING** | Live URL + health/ready; harden release networking; re-validate live |
| Company Selection | **COMPLETE** / **NEEDS HARDENING** | Live company discovery/select; re-validate live |
| Session validation (actions on Dashboard) | **COMPLETE** / **NEEDS HARDENING** | Re-validate live |
| Dashboard (Home) | **COMPLETE** / **NEEDS HARDENING** | Operational home; live summary pass required |
| Master Data Hub | **COMPLETE** | Navigation hub |
| Ledger Browser | **COMPLETE** / **NEEDS HARDENING** | List/search/pagination; no detail screen (deferred) |
| Stock Item Browser | **COMPLETE** / **NEEDS HARDENING** | Same pattern |
| Voucher Browser | **COMPLETE** / **NEEDS HARDENING** | Requires company + date range; empty without snapshots |
| Voucher Details | **COMPLETE** / **NEEDS HARDENING** | Read-only public contract |
| Universal Search | **COMPLETE** / **NEEDS HARDENING** | Grouped search; voucher window disclosed |
| Sync | **COMPLETE** / **NEEDS HARDENING** | Ledgers + Stock Items only; no public voucher sync |
| Diagnostics | **COMPLETE** / **NEEDS HARDENING** | Previously flagged as needing live pass |
| Settings (+ theme) | **COMPLETE** / **NEEDS HARDENING** | Theme persistence; About; deep links to config/company/sync/diagnostics |
| Splash / first-run dedicated splash | **NOT STARTED** (optional) | App launches MainActivity/nav; not a separate product splash |
| Ledger Detail | **NOT STARTED** | Deferred |
| Stock Item Detail | **NOT STARTED** | Deferred |
| Contact Intelligence UI | **NOT STARTED** | Blocked by ROADMAP until Validation complete; not MVP-1 APK blocker in this plan |
| PDF / Share destinations | **NOT STARTED** | Backlog |
| Auth / login | **NOT STARTED** | Out of scope for loopback pilot |

**Legend**

- **COMPLETE** = implemented vertical slice in repo  
- **NEEDS HARDENING** = must pass live QA + release packaging scrutiny  
- **NOT STARTED** = no product UI yet  

---

# Connector

## Remaining API work (for Android MVP-1 pilot)

| Item | Priority | Notes |
| --- | --- | --- |
| Document exact pilot API set | Critical | `/health`, `/ready` (or honest absence), `/session`, `/companies`, `/ledgers`, `/stock-items`, `/sync/*`, `/diagnostics/connection`, `/api/v1/vouchers*` |
| Align `/ready` with live builds | Important | Some live 0.3.1 builds returned 404; Android treats readiness optional — document pilot expectation |
| Remove or finish stub voucher company routes | Important | Avoid dual API confusion (`/api/v1/vouchers` vs `/companies/.../vouchers` 501) |
| Public voucher sync HTTP | Nice / Important for voucher-rich pilots | Today `customerOperational: false` — pilots need snapshot strategy (ops sync or accept empty) |
| Auth for non-loopback | Nice for LAN pilots | Loopback + Desktop-managed Connector is acceptable for first pilot |

## Validation still required

1. Host `GET /health` + documented readiness behavior on the **pilot Connector version**.  
2. Company discovery + session bind for a real Tally company.  
3. Ledger + stock sync → list/search on Android.  
4. Voucher list/details against a **promoted** snapshot (or documented empty).  
5. Diagnostics allowlist fields match privacy rules.  
6. Restart recovery: interrupted sync does not corrupt checkpoints.  

## Performance work

| Item | Priority | Notes |
| --- | --- | --- |
| Bound voucher list memory path | Important if large books | In-memory filter/page risk on large snapshots |
| Sync duration honesty | Important | No fabricated progress % |
| Companies endpoint latency | Important | Observed slow/timeout under load — set client timeouts expectations |
| Tally single-flight / rate limits | Keep | Do not “optimize” by weakening safety |

---

# Desktop

## Remaining operational work (pilot support)

| Item | Priority | Notes |
| --- | --- | --- |
| Pilot runbook: start Connector, confirm health, point Android | Critical | Operator steps |
| Privacy-safe diagnostics export for support | Important | Partial gaps called out in reliability docs |
| Stop tracking `dist/` in git | Important hygiene | Already gitignored but historically tracked |
| Feature parity with Android vouchers | Nice | Not required for Android-signed APK |
| Packaged Desktop installer for pilots | Important if pilots are non-dev | Optional if engineers operate Connector via npm/node |

Desktop is a **pilot enabler**, not the signed APK deliverable.

---

# Testing

## Unit

| Status | Action |
| --- | --- |
| Strong on Android (`testDebugUnitTest` historically ~150 passing) | Keep green on every hardening change |
| Connector unit/integration heavy | Run before pilot Connector pin |
| Do not weaken assertions to pass | Constitution / NON_NEGOTIABLES |

## Integration

| Status | Action |
| --- | --- |
| Connector sync/reservation/architecture boundary tests exist | Required before pilot Connector tag |
| Cross-repo OpenAPI drift checks | Important — treat Connector implementation as SoT |

## Live Connector

| Status | Action |
| --- | --- |
| Partially done in later validation sessions; **docs still Incomplete** | Re-run and **update PRODUCTION_VALIDATION.md** to PASS/FAIL per checklist |
| Device: prefer physical or API ≤36 AVD | Avoid known API 37 Compose InputManager failures as release gate alone |

## Manual QA

Execute PRODUCTION_VALIDATION checklist sections A–I + lifecycle:

- Startup, Dashboard, Masters, Vouchers, Search, Sync, Diagnostics, Settings  
- Rotation, bg/fg, offline/reconnect, company switch, theme persistence  

## Regression

| Gate | When |
| --- | --- |
| `:app:assembleRelease` (once configured) | Before signed artifact |
| `:app:testDebugUnitTest` | Every change |
| `:app:connectedDebugAndroidTest` on stable device | Before pilot freeze |
| Smoke on signed APK (not only debug) | Before pilot handoff |

---

# Production Readiness

| Area | MVP-1 expectation | Status |
| --- | --- | --- |
| **Authentication** | Not required for loopback Desktop-managed Connector pilot; LAN auth deferred | Deferred with documented threat model |
| **Recovery** | Honest errors + retry; Connector sync atomicity | Mostly present; prove live |
| **Offline** | Offline-aware banners; no fake Room cache | Present by decision; must stay honest |
| **Diagnostics** | In-app Diagnostics + privacy-safe fields | Implemented; live verify |
| **Logging** | No production accounting payload logs | Enforce in release BuildConfig |
| **Performance** | Acceptable list scroll/search on pilot dataset | Needs live measurement |
| **Security** | Read-only Tally; cleartext only where explicitly allowed for local Connector; release signing | Signing not done; network policy needs release review |
| **Accessibility** | Basic Material3 semantics; content descriptions on key actions | Partial; spot-check TalkBack on Dashboard/Settings |

---

# Release Checklist

## Before Version 1.0.0

- [ ] PRODUCTION_VALIDATION.md updated with live PASS evidence (device + Connector versions)  
- [ ] ROADMAP Production Validation marked complete  
- [ ] All Critical remaining features closed or explicitly waived in DECISIONS  
- [ ] `versionName` / `versionCode` set for release (`1.0.0` / appropriate code)  
- [ ] Release signing keystore procedure documented (EV optional per reliability decision)  
- [ ] `assembleRelease` succeeds  
- [ ] Network security / cleartext policy reviewed for pilot topology  
- [ ] Release logging flags verified (no OkHttp body logging of business data)  
- [ ] Pilot Connector version pinned + health contract notes  
- [ ] Known limitations published (no voucher sync API; no contact AI; no PDF/share)  

## Before Signed APK

- [ ] Release keystore available to Release Manager  
- [ ] Signed APK/AAB built and hash recorded  
- [ ] Install + smoke on at least one physical device  
- [ ] ProGuard/R8 (if enabled) mapping archived  
- [ ] Crash-on-launch test with empty app data (first-run Server Config)  

## Before Pilot deployment

- [ ] Pilot customer/company + Tally readiness confirmed  
- [ ] Desktop/Connector ops owner assigned  
- [ ] Support path: Diagnostics screenshots + privacy rules  
- [ ] Rollback plan (previous APK + Connector version)  
- [ ] Success criteria below signed off by Product + Tech Lead  

---

# Recommended Build Order

Each milestone is independently shippable (docs + artifact or evidence pack).

| Order | Milestone | Shippable output | Exit criteria |
| --- | --- | --- | --- |
| **M1** | Production Validation Closeout | Updated `PRODUCTION_VALIDATION.md` + ROADMAP | Live checklist A–I + lifecycle recorded; instrumented suite green on stable device **or** waived with written env limitation |
| **M2** | Pilot Defect Burn-down | Android/Connector fixes **only** for observed defects | Unit gates green; no speculative features |
| **M3** | Release Engineering | Unsigned `assembleRelease` + signing docs | Release variant builds; logging/network policy reviewed |
| **M4** | Signed Artifact | Signed `1.0.0` APK + hash | Install smoke on physical device |
| **M5** | Pilot Ops Pack | Runbook + limitations + Connector pin | Pilot owner can reproduce setup without developers |
| **M6** | Pilot Observability | Support playbook using Diagnostics | One-minute triage path proven |
| **Post-MVP-1** | Contact Intelligence Foundation | Per ROADMAP “Next” | Only after M1 complete (DECISIONS) |

**Do not** insert Contact Intelligence, AI, or PDF between M1–M5 unless Product expands MVP-1 scope in DECISIONS.

---

# Success Criteria

MVP-1 is complete when **all** of the following are true:

1. **Mission fit:** Android companion strengthens ERP use (browse/search/sync/diagnose) without writing to Tally.  
2. **Validation:** Live Production Validation evidence is complete for core workflows on a real device against a pinned Connector.  
3. **Quality:** Unit tests pass; instrumented tests pass on a supported device **or** an explicit, documented environmental waiver exists with compensating manual QA.  
4. **Artifact:** Signed `1.0.0` APK installs and completes first-run → health → company → dashboard → at least one master list → settings theme round-trip.  
5. **Honesty:** Empty voucher/offline/readiness states match Connector reality; no fake capabilities.  
6. **Privacy:** Release builds do not log identifiable accounting payloads.  
7. **Ops:** Pilot runbook exists for Connector + Android URL (`10.0.2.2` vs LAN) and support Diagnostics.  
8. **Governance:** ROADMAP reflects completion; Contact Intelligence remains scheduled after Validation per DECISIONS unless superseded.

---

# Highest-risk areas

1. **Environment-dependent validation** (emulator focus/ANR/API 37 instrumentation) masking or delaying real app defects.  
2. **Connector version skew** (`/ready` missing; health field optionality; voucher snapshots absent).  
3. **Release networking** — cleartext HTTP to local Connector vs hardened release defaults.  
4. **Scope creep** — Contact Intelligence / PDF / Share pulled into “must have for 1.0.0” without contracts.  
5. **Dual mobile narrative** (Flutter shared packages) distracting from Android pilot.  

---

# Recommended next coding task

**Do not start Contact Intelligence.**

**Next coding task (after this plan):**  
Run **M1 Production Validation Closeout** on a **physical device or API ≤36 AVD** with Connector running; fix **only** defects observed; then update `PRODUCTION_VALIDATION.md` + ROADMAP.

If validation is already informally done and only docs lag: the next *engineering* task is **M3 Release Engineering** (release signing + `assembleRelease`), but governance requires Validation docs closed first per DECISIONS.

---

## Completion estimate (summary)

| Slice | Estimate |
| --- | --- |
| Android MVP companion features | ~90% implemented |
| Live validation + formal closeout | ~50–70% (evidence incomplete in canonical docs) |
| Release/signing/pilot ops | ~25% |
| **Overall to signed pilot APK** | **~82%** |

*End of plan. Documentation only — no production code changes.*
