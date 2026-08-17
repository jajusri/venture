# BUDCOM Quality Scorecard

**Purpose:** Give every major milestone/release a consistent quality review instead of relying only on a single PASS/FAIL label.

**MVP-1 controlled-pilot first pass — 2026-08-15.** This was a blank template until this
assessment. The scores below are a first, evidence-backed draft filled in from repository
inspection and a fresh automated-test run against `main` @ `e2d3eac`/`6fc6689`
(see `docs/planning/BUDCOM-MVP-1-CONTROLLED-PILOT-CLOSURE-STATUS.md` for the full
gate-by-gate detail behind each row). Rows marked **(judgment)** score a dimension this
document's own purpose reserves for product/business judgment, not code inspection —
treat those as placeholders pending an actual human review, not a real score.

Score each dimension:

- 0 = unproven / unacceptable
- 1 = major gaps
- 2 = usable but weak
- 3 = acceptable
- 4 = strong
- 5 = excellent / well proven

| Dimension | Score 0–5 | Evidence / Notes |
|---|---:|---|
| Functional correctness | 3 | 2,712 automated tests passing across Connector/Desktop/Android, clean lint/typecheck/build. Physical confirmation of the P0 connectivity/pairing/sync paths is largely missing or ambiguous (see closure doc §3) — automated correctness is proven, real-world correctness is not yet. |
| Accounting/data integrity | 3 | Ledger identity (TD-011), SQLite `PRAGMA integrity_check`, decimal-money handling, offline-XML validation all implemented and tested. No demonstrated data-corruption path. Known, explicitly documented limitation: private-storage hot-removal mid-write atomicity is not fully guaranteed. |
| Persistence/migration safety | 3 | JSON→SQLite migration tested; schema-version downgrade/future-schema guard in place; backup/recovery documented. Private-storage vault identity is filesystem-only, not bound inside the DB itself (documented gap, not tested). **Updated 2026-08-16:** the private-storage fail-closed/no-fallback/recovery mechanism was physically pull-and-reinsert tested end-to-end (byte-identical SHA-256 hashes before/after) — see Physical validation row. Two new, non-blocking gaps found and recorded the same day: no Standard→Private migration/warning flow (TD-033, P2) and unreliable stale-marker adoption on switch (TD-034, P3) — neither is exercised by a pilot that configures Private mode once and never switches. Score unchanged at 3: the new evidence strengthens confidence in the fail-closed guarantee specifically, but doesn't newly address the pre-existing filesystem-only-identity gap. |
| Offline/local-first behavior | 4 | Android local-first Ledger/Voucher persistence implemented, tested, and physically confirmed to survive an in-place app update (continuity.13, 2026-08-13). |
| Connectivity/recovery | 4 | **Updated 2026-08-16 (evidence reconciliation, closure doc §45).** Raised from 2: every individual link in the TD-013/017/029/030/031/032 chain now has direct physical evidence of correct real-device behavior — Desktop rebind (TD-029), IPv4-only mDNS (TD-030), securePort advertisement (TD-031 Connector-side) and multicast-lock acquisition (TD-032) were each **directly witnessed live** (not inferred), correlating OS-level state (`dumpsys wifi`, `ip maddr`, live `bonjour-service` browses) with the actual retest in progress. TD-032's differential test (unicast <0.1s, multicast zero services, lock held and group joined throughout) cleanly isolated the one remaining hotspot-leg failure to the test hotspot's own multicast-forwarding behavior — an accepted environmental limitation, not a code defect. TD-013 (restart-recovery) is now physically confirmed via a real Desktop/Connector restart with automatic recovery, no re-pair. Not scored 5: the full end-to-end TD-017 chain has never been directly witnessed via a genuine two-router address change (only via the now-known-unsuitable hotspot) — classified combined-evidence (B), not directly-witnessed (A), for that one specific composite scenario. Non-blocking for a pilot restricted to one stable router LAN (see closure doc §46). |
| Performance/resource efficiency | 3 | No demonstrated performance problem. TD-022/TD-023 are explicitly recorded, non-blocking, P3, "do not action without build/physical evidence" observations, not proven issues. |
| UX clarity | 4 | **Updated 2026-08-16 (final human validation session).** Raised from an unscored judgment placeholder: Session 2's full Android core-workflow set (sync, Ledger Browser, Voucher Browser + type coverage, Voucher/Ledger PDF generation, WhatsApp share/save) physically confirmed PASS end to end. TD-028's in-app PDF Preview — the reserved final validation stage — is now **physically confirmed PASS** (Preview, visual content review, Save, and WhatsApp Share all passed) and CLOSED. The private-storage screens were also physically exercised (storage-unavailable screen with Retry/Locate/Exit shown correctly on USB removal). Not scored 5: TD-025's pre-existing gap remains — a *live mid-session* storage loss (app still running) would still show a generic "Disconnected" rather than the storage-specific screen; this session's physical test used the clean stop/restart pattern instead, and the gap is explicitly accepted for pilot under a no-USB-removal-while-running operating constraint, not fixed. |
| Error/loading/empty-state honesty | 3 | TD-014's bounded dashboard recovery is tested (10/10 deterministic runs). TD-025 (storage-loss UX) is a real, open gap in this dimension. |
| Security/trust/privacy | 3 | Tally access is enforced read-only at the capability layer (`EXPORT`-only allowlist, `IMPORT`/`EXECUTE`/`CREATE`/`ALTER` blocked); diagnostics sanitizers verified (TD-010); TD-024 (wrong-drive validation gap) found and fixed this session. Trust/pairing behavior (TD-016/017/019) is automated-sound but physically underconfirmed on real hardware, which matters for a trust dimension specifically. |
| Automated test evidence | 5 | 157+66+~102 test files / 2,712+ tests passing at HEAD; ESLint/tsc/Android lint all clean; verified fresh this session, not taken on faith from prior reports. |
| Physical validation | 5 | **Updated 2026-08-16 (closure reconciliation, §45-46).** Raised from 3: Session 2 (Sync All, Ledger/Voucher browsing, type coverage, PDF, share — §28/§32/§34), Session 3 items Q/R (connectivity resilience, combined TD-029-032 chain, each link directly witnessed live — §45), Session 4 (Desktop/Connector restart, Android restart — §43), and Session 5 (USB private-storage pull/reinsert with byte-identical SHA-256 continuity directly filesystem-witnessed, plus PDF final visual review — §44) are now all physically confirmed. The two scenarios never literally, unattended, end-to-end witnessed — a genuine two-router TD-017 address change, and a live mid-write USB pull (item Y) — are both honestly classified as combined-evidence/accepted-environmental-limitation rather than claimed as directly proven, and are non-blocking under the pilot's own operating constraints (single stable router LAN; no USB removal while running). Session 1 (Desktop install/upgrade identity continuity specifically) was not separately re-itemized this pass but is subsumed by the restart-resilience and USB evidence, which exercise the same startup/config-resolution code paths. |
| Upgrade/release readiness | 4 | **Updated 2026-08-16 (closure reconciliation, §46).** Raised from 2: Desktop `0.4.15` / Connector `0.4.6` and Android `continuity.19`/versionCode 20 are approved for controlled-pilot GO — both SHA-256 hashes (`321c0946...`, `33f4eef3...`) independently re-verified against the artifacts on disk during this reconciliation, not taken on faith. Sessions 2, 3 (Q/R), 4, and 5 all physically confirmed (Physical validation row). Not scored 5: Android has no signed-release path at all (debug-signed only, a longstanding unchanged environment limitation); Windows-restart-with-auto-start (item U) was not separately itemized this pass. |

## Mandatory gates

Regardless of total score, a milestone/release cannot be considered ready if any applicable critical dimension has a blocking 0/1 in:

- accounting/data integrity;
- security/trust;
- migration/persistence;
- core functional correctness.

**None of the four mandatory-gate dimensions are scored 0/1 above** — so this scorecard's
own hard-gate rule does not, by itself, block release. **Updated 2026-08-16:** Physical
validation and Upgrade/release readiness — the two dimensions the prior NOT READY verdict
turned on — are now scored 5 and 4 respectively (see rows above); nothing in this
reconciliation found a mandatory-gate dimension scored 0/1 either.

## Release summary

**Overall classification: GO (for controlled-pilot distribution), 2026-08-16** — code-quality gates pass; physical-evidence gates are now complete for every scenario this pilot's operating constraints depend on. See `docs/planning/BUDCOM-MVP-1-CONTROLLED-PILOT-CLOSURE-STATUS.md` §45-46 for the full evidence reconciliation and decision record.
**Strongest evidence:** Automated test evidence (5) and Physical validation (5, raised from 3) — 2,700+ tests clean across all three components, plus Sessions 2/3/4/5 all physically confirmed, several via direct OS-level correlation (`dumpsys wifi`/`ip maddr`/live mDNS browses/SHA-256 continuity) rather than report alone.
**Formerly weakest dimension, now resolved:** Physical validation. Session 3's connectivity-resilience chain (TD-029/030/031/032) had each individual link directly witnessed live; Session 4 (Desktop/Connector + Android restart) and Session 5 (USB private-storage pull/reinsert, PDF final visual review) both physically confirmed PASS.
**Accepted limitations for pilot operation:** operate on a stable real router LAN, not a phone-based mobile hotspot (TD-017's full chain is combined-evidence, not directly witnessed, specifically because every real address-change attempt used a hotspot now proven unsuitable for the mDNS leg — closure doc §45); do not remove the USB while BUDCOM is running (TD-025, and the never-performed live-mid-write item Y, both become unreachable under this constraint); do not attempt a Standard→Private storage-mode switch on any pilot machine (TD-033/TD-034, P2/P3, no migration flow exists yet — pilot is configured once, fresh, into Private mode); Desktop installer unsigned (SmartScreen expected), Android debug-signed only (no release-signing path in this environment) — both longstanding, unchanged; TD-022/TD-023 performance observations (P3, non-blocking); TD-026/TD-027 Tally-side observational findings (P2, not BUDCOM defects); historical non-authoritative business-data artifacts remain on the audited machine's internal C:/D: drives from development history, explicitly preserved pending deliberate review, not affecting pilot operation.
**Everything else** found during this entire hardening engagement (TD-001, TD-013 through TD-020, TD-024, TD-028 through TD-032) is CLOSED or Fixed-and-physically-confirmed — see the technical-debt registry.

---

## MVP-1 public-release readiness view (2026-08-17)

**Do not reuse the Controlled-Pilot GO above mechanically — public release has different
requirements.** See `docs/planning/BUDCOM-MVP-1-PUBLIC-RELEASE-GATE-MATRIX.md` for the full
gate-by-gate detail this section summarizes.

| Dimension | Score 0–5 | Change from Controlled-Pilot pass | Notes |
|---|---:|---|---|
| Accounting correctness | 3 (unchanged) | — | No accounting-logic changes this pass. |
| Data integrity | 3 → **4** | **Raised.** | TD-033 (guarded storage-mode switch) and TD-034 (fail-closed on unreadable vault marker) resolved and tested — both were real data/trust-safety gaps for a broad public audience unfamiliar with BUDCOM internals. Not 5: TD-025 (live USB-loss UX) remains an open, documented limitation, and private-storage vault identity remains filesystem-only (pre-existing, unchanged). |
| Security/trust | 3 (unchanged) | — | No security-architecture changes this pass; TD-033's fix protects existing trust data from accidental loss but does not change the trust model itself. |
| Installer/upgrade | 4 (Controlled-Pilot) → **BLOCKED (signing)** | **New dimension for public scope.** | Installer/upgrade mechanics themselves are sound (per-machine NSIS, safe legacy-identity migration, `deleteAppDataOnUninstall: false`, Android `adb install -r` upgrade-path continuity previously verified). Scored as blocked, not numerically, because **no Windows code-signing or Android release-signing credentials exist** — see gate matrix §6. This is the dominant reason public release cannot proceed regardless of any other dimension's score. |
| Connectivity | 4 (unchanged) | — | No changes this pass; TD-029–032 chain remains as previously evidenced. |
| Usability | 4 (unchanged) | — | UI/UX polish pass (prior session) already raised this; no regressions found this session. |
| Supportability | 3 (new for public scope) | — | Release notes and a Quick-Start/troubleshooting guide were authored this session (`docs/planning/BUDCOM-MVP-1-RELEASE-NOTES.md`, `docs/planning/BUDCOM-MVP-1-QUICK-START.md`). Not higher: no dedicated rollback tooling exists beyond reinstalling a prior installer, and Android public `applicationId` remains an undecided release blocker (gate matrix §7). |

**Mandatory gates recheck:** none of accounting/data-integrity, security/trust,
migration/persistence, or core functional correctness score 0/1. The public-release verdict is
blocked by **signing availability**, a release-engineering/business gate this scorecard's four
mandatory dimensions do not directly capture — see the gate matrix's summary verdict.

**Public-release verdict: BLOCKED — signing.** Everything reachable without a production signing
identity has been evaluated; TD-033/TD-034 (the two evidence-backed public-facing data-safety gaps
found in the controlled-pilot audit) are now resolved. See the gate matrix for the complete action
list and the two flagged product decisions (Android public applicationId; TD-025's fuller fix)
requiring owner input before the next pass.

---

## Pre-signing technical-debt closure pass (2026-08-17)

A full non-signing gap inventory was built across the entire technical-debt registry (not limited
to a fixed list) and every safe FIX NOW / IMPROVE NOW item was actioned. **No new public-release
blocker was found.** Updates to the dimensions above:

| Dimension | Change | Notes |
|---|---|---|
| Performance/resource efficiency | 3 (unchanged) | TD-023 fixed (connector query memory — no longer loads the full snapshot per paged request, zero behavior regression). TD-022 refined: the automatic background reconciliation walk was already bounded to 30-day windows; a real but narrower gap was found in the *manual* Voucher Browser refresh path (no date-span cap on user-typed ranges) — kept as an accepted limitation, not fixed, since the right cap is a product decision. |
| Error/loading/empty-state honesty | 3 → **4** | TD-025's mid-session private-storage-loss gap fixed — a live loss now correctly shows the purpose-built recovery screen instead of a generic "Disconnected" state, reusing existing tested UI. A narrower, lower-impact sub-item (`desktop:restart-connector` reusing stale config after a drive-letter change) remains open, Post-MVP-1. |
| Security/trust/privacy | 3 (unchanged) | TD-021 investigated further: the gap is architecturally deeper than one call site (would need both a wider response-classification change and a new cross-feature transport→Company dependency) — correctly left for architectural scoping rather than a unilateral cross-cutting change. TD-009 reviewed: still an accurate gap (trusted-LAN bind mode has no per-device authN unless the separate, opt-in secure-pairing subsystem is also enabled) — requiring pairing for trusted-LAN is a security-policy decision needing product sign-off, not an engineering fix. Hygiene: moved test-only privacy sentinels (a real-looking company name/GSTIN) out of shipped Desktop production source; closed a content-level (not just field-name) redaction gap in Desktop's startup-diagnostics log. |
| Accounting/data integrity | 3 (unchanged) | TD-026 re-investigated: the requested out-of-window reconciliation mechanism was found already correctly implemented (`ReconcileVoucherWindowsUseCase`) — this session closed an automated-test evidence gap and corrected stale registry documentation, avoiding an unnecessary/risky sync-architecture change. |
| Supportability | 3 (unchanged) | TD-004 fixed: Desktop Settings' Tally host/port fields previously persisted successfully but had zero effect on the spawned Connector — now forwarded correctly. TD-027 reviewed: still an accurate Tally-side observation, no BUDCOM defect; a prior deliberate decision not to add UI-facing messaging about it was reaffirmed, not reversed, since no new evidence has surfaced. |
| Automated test evidence | 5 (unchanged) | Full four-component regression re-run at session end: Connector 159 files/1,423 tests, lint/build clean; Desktop 68 files/711 tests, `tsc` (3 configs) clean, full build clean; Contract 5/5; Android confirmed via full regression (see current-status checkpoint for exact counts). Two confirmed, unconditional test-infrastructure leaks fixed (Connector temp-SQLite directories via a new `globalSetup`/teardown; 13 Desktop test files' `mkdtemp` scratch directories via `afterEach`/`try-finally`) — both measured net-zero growth after the fix, pre-existing leaked directories left untouched per instruction. |

**Flagged, not acted on:** `docs/diagnostics/m3-stock-items-raw-sample.xml` (~1,500 real-looking
inventory item names, committed 2026-07-22 in `f6f59c4`) was re-confirmed unreachable by any
packaging path (electron-builder file list, release scripts) but was **not** redacted or deleted —
it predates this session, "do not rewrite history" is an explicit constraint, and whether this
data is genuinely sensitive is an owner judgment call, not one this session should make
unilaterally. Flagged here for an explicit decision.

**No change to the BLOCKED — signing verdict above.** This pass found no additional non-signing
defect that would newly block or delay public release once signing exists.
