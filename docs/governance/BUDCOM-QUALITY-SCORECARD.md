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
| Connectivity/recovery | 2 | Automated fixes exist for TD-013–TD-017; physical confirmation is the weakest link in the whole assessment — TD-017's own physical retest is logged "BLOCKED/INCOMPLETE," and the one broad physical pass that did occur (continuity.13) did not exercise TD-017's actual failure mode (endpoint address change) or TD-013's (Connector process restart). |
| Performance/resource efficiency | 3 | No demonstrated performance problem. TD-022/TD-023 are explicitly recorded, non-blocking, P3, "do not action without build/physical evidence" observations, not proven issues. |
| UX clarity | — **(judgment)** | Needs an actual person exercising the pilot workflows end to end; not scorable from source inspection. One known gap either way: private-storage mid-session loss shows a generic "Disconnected" state rather than a storage-specific one (TD-025). |
| Error/loading/empty-state honesty | 3 | TD-014's bounded dashboard recovery is tested (10/10 deterministic runs). TD-025 (storage-loss UX) is a real, open gap in this dimension. |
| Security/trust/privacy | 3 | Tally access is enforced read-only at the capability layer (`EXPORT`-only allowlist, `IMPORT`/`EXECUTE`/`CREATE`/`ALTER` blocked); diagnostics sanitizers verified (TD-010); TD-024 (wrong-drive validation gap) found and fixed this session. Trust/pairing behavior (TD-016/017/019) is automated-sound but physically underconfirmed on real hardware, which matters for a trust dimension specifically. |
| Automated test evidence | 5 | 157+66+~102 test files / 2,712+ tests passing at HEAD; ESLint/tsc/Android lint all clean; verified fresh this session, not taken on faith from prior reports. |
| Physical validation | 2 | **Updated 2026-08-16.** Raised from 1: a current-version Desktop (`0.4.12`) + Android (`continuity.15`) build has now been physically installed and exercised end-to-end for the Voucher sync workflow against real ESTIMATION Tally data, including recovering from a real found-and-fixed defect (TD-001, `amount-sign-conflict`) with Connector-side evidence (snapshot promotion, zero incomplete records) inspected directly, not taken on report alone — see `docs/planning/BUDCOM-MVP-1-CONTROLLED-PILOT-CLOSURE-STATUS.md` §28. Capped at 2, not higher: only one workflow (Voucher sync) on one company has been confirmed; Session 2's remaining items (combined "Sync all," repeated refresh, Ledger statement, voucher-type coverage, PDF generation, WhatsApp share) and all of Sessions 1/3/4/5 are still outstanding. `budcom_archives` has no evidence newer than 2026-08-09 and is now superseded by this session's direct evidence. |
| Upgrade/release readiness | 2 | **Updated 2026-08-16.** Current controlled-pilot candidate: Desktop `0.4.12` (`BudcomDesktop-0.4.12-x64-setup.exe`, SHA-256 `f646aa5bf28...`, commit `99da6ab`) and Android `continuity.15`/versionCode 16 debug APK (SHA-256 `7a67db1...`, commit `8808e76`, unchanged across this whole TD-001 investigation). Desktop 0.4.12 has now been installed on real hardware and the Voucher sync path physically confirmed (see Physical validation row). Still capped at 2, not higher: Android has no signed-release path at all (no keystore in this environment); the remaining Session 2 items and Sessions 1/3/4/5 haven't run; only one of several core workflows has real-hardware confirmation so far. |

## Mandatory gates

Regardless of total score, a milestone/release cannot be considered ready if any applicable critical dimension has a blocking 0/1 in:

- accounting/data integrity;
- security/trust;
- migration/persistence;
- core functional correctness.

**None of the four mandatory-gate dimensions are scored 0/1 above** — so this scorecard's
own hard-gate rule does not, by itself, block release. The NOT READY verdict in the
closure document comes from **Physical validation (1)** and **Upgrade/release readiness (1)**
instead: this project's own release ladder and this hardening pass's explicit brief both
treat real physical/device evidence as a precondition for "controlled pilot ready," even
though this document's mandatory-gate list doesn't name those two dimensions explicitly.

## Release summary

**Overall classification:** NO-GO (for controlled-pilot distribution) — code-quality gates pass; physical-evidence gates are partial.
**Strongest evidence:** Automated test evidence (5) — 2,700+ tests, clean across all three components, verified fresh against current HEAD, not inherited from older reports.
**Weakest dimension:** Physical validation (2, updated 2026-08-16) — one core workflow (Voucher sync, including recovery from a real found-and-fixed defect) is now physically confirmed on Desktop `0.4.12` / Android `continuity.15`; the rest of the human-check matrix is still outstanding.
**Required correction before release:** Complete the remaining Session 2 items and Sessions 1/3/4/5 in `docs/planning/BUDCOM-MVP-1-CONTROLLED-PILOT-CLOSURE-STATUS.md` §11 against Desktop `0.4.12` / Android `continuity.15` (Windows install/restart/reconnect, remaining Android workflows, private-storage recovery). Re-score this document once that evidence lands.
**Accepted limitations:** Private-storage hot-removal mid-write atomicity not fully guaranteed (documented); TD-022/TD-023 performance observations (P3, non-blocking); TD-025 storage-loss UX gap (P2, open, fails closed/safely).
