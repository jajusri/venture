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
| Persistence/migration safety | 3 | JSON→SQLite migration tested; schema-version downgrade/future-schema guard in place; backup/recovery documented. Private-storage vault identity is filesystem-only, not bound inside the DB itself (documented gap, not tested). |
| Offline/local-first behavior | 4 | Android local-first Ledger/Voucher persistence implemented, tested, and physically confirmed to survive an in-place app update (continuity.13, 2026-08-13). |
| Connectivity/recovery | 2 | **Updated 2026-08-16 (round 5 of the same Session 3 item R retest chain).** Automated fixes exist for TD-013–TD-017 plus TD-029, TD-030, TD-031, and TD-032 (see TD-032's registry entry for the full chain — mDNS never advertised the secure port, then the wrong port was verified, then the device never even received multicast traffic without a held `WifiManager.MulticastLock`). **TD-032's fix was then independently proven correctly deployed and running on the actual physical retest device** (`dumpsys wifi` showed the app's own UID as an active multicast-lock owner exactly when `ip maddr` showed the mDNS group joined) — yet the retest still failed. A differential test (unicast TCP connects in <0.1s; multicast discovery finds nothing across multiple full windows) isolated the remaining gap to the test hotspot's (OnePlus Nord 5) own multicast-forwarding behavior between its Wi-Fi clients — a real, independently-documented characteristic of many phone-based hotspots, not a BUDCOM defect. No further code fix was made this round. Full suites clean (Connector 1,419/1,419, Android debug+release 1,021/1,021 each, 0 lint issues). Still capped at 2, not higher: the combined fix has not yet had a valid physical retest — the phone hotspot test environment itself is now known to be unsuitable for the mDNS-dependent leg specifically, so confirmation requires a real Wi-Fi router; recorded PENDING a retest on suitable hardware, not yet run. TD-013's restart-recovery scenario is still unconfirmed. |
| Performance/resource efficiency | 3 | No demonstrated performance problem. TD-022/TD-023 are explicitly recorded, non-blocking, P3, "do not action without build/physical evidence" observations, not proven issues. |
| UX clarity | — **(judgment)** | **Updated 2026-08-16 (autonomous hardening run).** Session 2's full Android core-workflow set (sync, Ledger Browser, Voucher Browser + type coverage, Voucher/Ledger PDF generation, WhatsApp share/save) has now been physically exercised end to end and passed. TD-028 (PDF could not be previewed before WhatsApp/share) — originally deferred, then approved for implementation now by explicit user decision — is **implemented and automated-validated**; in-app Preview now gates the Voucher bottom-sheet PDF action and the Ledger Advanced-Options "Preview PDF" button, on a shared `core/pdf/` screen, zero new dependency. Visual/physical confirmation reserved for the final human validation session, last, as requested — not yet scored as confirmed UX. Pre-existing gap unchanged: private-storage mid-session loss shows a generic "Disconnected" state rather than a storage-specific one (TD-025). |
| Error/loading/empty-state honesty | 3 | TD-014's bounded dashboard recovery is tested (10/10 deterministic runs). TD-025 (storage-loss UX) is a real, open gap in this dimension. |
| Security/trust/privacy | 3 | Tally access is enforced read-only at the capability layer (`EXPORT`-only allowlist, `IMPORT`/`EXECUTE`/`CREATE`/`ALTER` blocked); diagnostics sanitizers verified (TD-010); TD-024 (wrong-drive validation gap) found and fixed this session. Trust/pairing behavior (TD-016/017/019) is automated-sound but physically underconfirmed on real hardware, which matters for a trust dimension specifically. |
| Automated test evidence | 5 | 157+66+~102 test files / 2,712+ tests passing at HEAD; ESLint/tsc/Android lint all clean; verified fresh this session, not taken on faith from prior reports. |
| Physical validation | 3 | **Updated 2026-08-16 (Session 2 complete).** Raised from 2: Session 2's entire Android core-workflow matrix is now physically confirmed on real hardware against real ESTIMATION Tally data — Sync All, repeated refresh/freshness, Ledger Browser + statement, Voucher Browser + type coverage (all 8 required types, verified both programmatically and visually), Voucher PDF, Ledger PDF, and WhatsApp share/save — including recovering from a real found-and-fixed defect (TD-001, `amount-sign-conflict`) and two real Tally-side count/timing observations investigated to ground truth (TD-026, TD-027), all with Connector-side evidence inspected directly via read-only queries, not taken on report alone. See `docs/planning/BUDCOM-MVP-1-CONTROLLED-PILOT-CLOSURE-STATUS.md` §28, §32, §34. Capped at 3, not higher: only Session 2's scope (one company, Android core workflows) is confirmed; Desktop-side install/restart/reconnect (Session 1), connectivity resilience (Session 3), restart resilience (Session 4), and private-storage recovery (Session 5) remain entirely unstarted. |
| Upgrade/release readiness | 2 | **Updated 2026-08-16 (diagnostic-logging candidate, round 5).** Desktop 0.4.12 physically confirmed Session 2 in full (see Physical validation row). Current candidates: Desktop `0.4.15` / Connector `0.4.6` (unchanged since TD-031, closure doc §35-§40) SHA-256 `321c0946ff2fc263a5558bf55cc34886f7358bbf5a7eb98da674d5a6144899b5`, and Android `continuity.19`/versionCode 20 debug APK (durable diagnostic logging only, no behavior change, closure doc §42), SHA-256 `33f4eef37d5d32f746e81d564ac36efa992aeb74bb6e698f7a2af0e786420c0c` — Android-only this round; Desktop/Connector genuinely unchanged. Still capped at 2, not higher: Android has no signed-release path at all (no keystore in this environment); the new candidates have not been physically retested on suitable hardware yet (the phone-hotspot test environment is now known unsuitable for the mDNS-dependent leg — a real router is required); Sessions 1/4/5 haven't run, and Session 3 is paused pending that retest. |

## Mandatory gates

Regardless of total score, a milestone/release cannot be considered ready if any applicable critical dimension has a blocking 0/1 in:

- accounting/data integrity;
- security/trust;
- migration/persistence;
- core functional correctness.

**None of the four mandatory-gate dimensions are scored 0/1 above** — so this scorecard's
own hard-gate rule does not, by itself, block release. The NOT READY verdict in the
closure document comes from **Physical validation (3, partial — Session 2 of 5)** and
**Upgrade/release readiness (2)** instead: this project's own release ladder and this
hardening pass's explicit brief both treat real physical/device evidence as a
precondition for "controlled pilot ready," even
though this document's mandatory-gate list doesn't name those two dimensions explicitly.

## Release summary

**Overall classification:** NO-GO (for controlled-pilot distribution) — code-quality gates pass; physical-evidence gates are partial (Session 2 of 5 complete).
**Strongest evidence:** Automated test evidence (5) — 2,700+ tests, clean across all three components, verified fresh against current HEAD, not inherited from older reports.
**Weakest dimension:** Physical validation (3, updated 2026-08-16) — Session 2's full Android core-workflow matrix is now physically confirmed on Desktop `0.4.12` / Android `continuity.15`, including one real defect found-and-fixed (TD-001) and two Tally-side observations investigated to ground truth (TD-026/TD-027); Sessions 1, 3, 4, 5 (Desktop install/restart, connectivity resilience, restart resilience, private-storage recovery) remain entirely unstarted.
**Required correction before release:** Physically retest the combined TD-029+TD-030+TD-031+TD-032 fix (Session 3 item R — real network-address change, full A→B→A cycle both directions) against Desktop `0.4.15` / Android `continuity.18`, then complete the rest of Session 3 plus Sessions 1, 4, 5 in `docs/planning/BUDCOM-MVP-1-CONTROLLED-PILOT-CLOSURE-STATUS.md` §11. Re-score this document once that evidence lands.
**Accepted limitations:** Private-storage hot-removal mid-write atomicity not fully guaranteed (documented); TD-022/TD-023 performance observations (P3, non-blocking); TD-025 storage-loss UX gap (P2, open, fails closed/safely); TD-026 out-of-window Voucher carry-forward has no re-verification against Tally (P2, deferred); TD-027 Tally Export-interface can temporarily lag its own UI/reports for very recently entered vouchers (P2, observational, not a BUDCOM defect); TD-028 PDF preview implemented and automated-validated 2026-08-16 (superseding the earlier deferral by explicit user decision), physical/visual confirmation reserved for the final human session, last, as requested; TD-029 Desktop network-rebind fix, TD-030 mDNS over-advertisement fix, TD-031 mDNS missing-secure-port fix, and TD-032 missing Android MulticastLock fix (together the full reason TD-017 had never once physically succeeded) all implemented and automated-validated, physical retest of the combined scenario still pending (P0, this is the current release blocker); a separate, unresolved (not proven) ambiguity about possible Desktop dashboard-UI staleness after a late superseded company-discovery failure was recorded during this investigation and deferred to a dedicated observation pass (closure doc §40.5); a separate, confirmed non-BUDCOM environmental factor (this OEM test device's Wi-Fi silently roaming between networks on its own mid-test) was observed and documented so it isn't mistaken for a product defect in a future retest (closure doc §41.3).
