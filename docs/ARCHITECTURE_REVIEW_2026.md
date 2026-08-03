# Architecture Review 2026

**Date:** 2026-07-28  
**Author stance:** Principal Software Architect  
**Code changes:** None — review document only  
**Governing documents:** [NON_NEGOTIABLES.md](NON_NEGOTIABLES.md), [PRODUCT_SOUL.md](PRODUCT_SOUL.md), [VISION_2030.md](VISION_2030.md), [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), [ROADMAP.md](ROADMAP.md), [DECISIONS.md](DECISIONS.md)

Related earlier Android-only review: [ARCHITECTURE_REVIEW.md](ARCHITECTURE_REVIEW.md) (2026-07-27). This document is the **whole-repository** assessment against the completed documentation foundation.

---

# Executive Summary

| Scorecard | Score | Notes |
| --- | --- | --- |
| **Overall project health** | **78%** | Strong companion posture and safety culture; product surfaces and monorepo cohesion still uneven |
| **Architecture maturity** | **76%** | Clear layering in Connector and Android; Desktop solid; shared platform story incomplete |
| **Maintainability** | **72%** | Good feature slices and tests; parallel clients, dual mobile stacks, tracked `dist/` hurt |
| **Scalability** | **68%** | Excellent solo / single-PC; SME viable; enterprise LAN/auth/HA not yet designed |
| **Documentation maturity** | **90%** | Governing set is now excellent; some operational docs lag (ROADMAP still shows Production Validation incomplete) |
| **Technical debt** | **Medium** | Intentional safety complexity + accidental duplication across clients and stacks |

**One-line verdict:** The repository is a credible foundation for an ERP companion Business OS, not yet the 2030 platform — but the **direction and non-negotiables are encoded well enough that continued disciplined execution can get there**.

---

## Repository map (evidence)

| Path | Role |
| --- | --- |
| `apps/budcom_android/` | Primary BUDCO companion (Kotlin, Compose, Clean Architecture) |
| `apps/budcom_desktop/` | Electron shell: Connector lifecycle + operational UI |
| `apps/budcom_mobile/` | Flutter track (early; Melos workspace) |
| `connector/budcom_connector/` | Local read-only Tally companion service (Node) |
| `shared/packages/budcom_core`, `budcom_contracts` | Dart domain/contracts — **not** consumed by Android/Desktop |
| `docs/` | Product + engineering governance |
| `tests/` | Cross-cutting contract / architecture tests |

---

# Module evaluations

Each module answers the ten review questions against the governing documents.

---

## 1. Android (`apps/budcom_android`)

### Evidence

- Feature-first slices under `feature/{serverconfig,company,dashboard,masterdata,voucher,search,sync,diagnostics,settings}` with `data` / `domain` / `presentation`.
- Networking: Retrofit + dynamic base URL interceptor; Connector as contract source of truth ([PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md)).
- Completed companion surface per [ROADMAP.md](ROADMAP.md): Dashboard through Settings; Production Validation recorded as live health fix on `main` (`75fa44f`).
- Prior remediation of high coupling smells documented in [ARCHITECTURE_REVIEW.md](ARCHITECTURE_REVIEW.md) (operational mode, hydrator port, dashboard ports).

### Answers

| # | Question | Assessment |
| --- | --- | --- |
| 1 | Aligns with mission? | **Yes.** Intelligent companion that reduces friction over ERP data — not an ERP replacement. |
| 2 | Violates NON_NEGOTIABLES? | **No material violations.** UX principles (reduce clicks/waiting) are explicit in UI state models; AI not yet present (consistent with “assist later”). |
| 3 | Unnecessary complexity? | **Low–medium.** Clean Architecture is appropriate; leftover `*Package.kt` markers and unused Room/WorkManager scaffolding add noise ([ARCHITECTURE_REVIEW.md](ARCHITECTURE_REVIEW.md)). |
| 4 | Duplicate responsibilities? | **Partial.** Per-feature error mapping and similar remote patterns; Connector HTTP clients duplicated vs Desktop/TS (monorepo-wide). |
| 5 | Separate better? | Dashboard already narrowed to ports; further extract shared UI error mapping and optional Gradle feature modules **after** ports stabilize ([REPOSITORY_EVOLUTION.md](REPOSITORY_EVOLUTION.md)). |
| 6 | Naming reflects domain? | **Mostly.** Domain language (ledger, voucher, session, sync) is clear; product brand dual-spellings (BUDCO / Budcom / BudCom) remain. |
| 7 | Solo → SME → enterprise? | **Solo/SME yes** (URL config, company session, browsers). **Enterprise partial** — no multi-user auth story on device; depends on Connector LAN/auth maturity. |
| 8 | ERP as SoR? | **Yes.** Read/browse/sync-from-Connector; no Tally write path in Android. |
| 9 | Future AI? | **Structurally ready** for assistant features over existing search/diagnostics ports; no AI SDK coupling yet (good). |
| 10 | Multi-device without redesign? | **Domain ports help**, but Kotlin/Compose UI is Android-bound by design ([PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md)). Reuse is **ideas and contracts**, not binary UI. Acceptable if OpenAPI/shared contracts become the portable core. |

---

## 2. Connector (`connector/budcom_connector`)

### Evidence

- Layering: `api/` → `services/` → `erp/` ports → `tally/` adapter → `storage/sqlite/` ([register-services.ts](../connector/budcom_connector/src/bootstrap/register-services.ts), architecture boundary tests).
- Multi-layer read-only toward Tally: capabilities, forbidden mutation XML, approved EXPORT-only gateway, `HealthReport.readOnly: true`.
- Company isolation via `company_id` on domain tables; WAL + `BEGIN IMMEDIATE` transactions.
- Voucher: extraction + snapshot repository + `GET /api/v1/vouchers*` present; public voucher sync not customer-operational (`voucher-foundation` flags); stub `/companies/.../vouchers` still 501.
- Dual mental models: live M3 master extraction vs synced SQLite masters.
- Uncommitted WIP for voucher 0.4.0 observed in working tree during mid-2026 validation (large untracked voucher tree) — treat as **active product line**, not abandoned experiment.

### Answers

| # | Question | Assessment |
| --- | --- | --- |
| 1 | Aligns with mission? | **Strongly yes.** Local companion that strengthens ERP use without replacing Tally. |
| 2 | Violates NON_NEGOTIABLES? | **No.** “Strengthen not dependency,” respect ERP ecosystem, integrate rather than replace — embodied in read-only egress. |
| 3 | Unnecessary complexity? | **Mostly necessary** for reliability/safety. Accidental: parallel ledger/stock sync shapes; placeholder stubs (licensing/scheduler) alongside real sync. |
| 4 | Duplicate responsibilities? | Live masters vs synced caches; dual voucher HTTP surfaces (real `/api/v1` vs stub company path). |
| 5 | Separate better? | Keep ERP ports; collapse duplicate sync engines into shared sync kernel over time; remove or finish stubs deliberately. |
| 6 | Naming reflects domain? | **Yes** (Budcom, ERP-neutral ports, Tally confined to adapter). |
| 7 | Solo → SME → enterprise? | **Solo excellent; SME good** on one box; **enterprise weak** today (no auth, single-writer SQLite, in-memory voucher paging, loopback-first networking). |
| 8 | ERP as SoR? | **Yes — hard guarantee.** |
| 9 | Future AI? | Local structured APIs + diagnostics are good AI *data* substrates; no AI runtime in Connector (correct separation). |
| 10 | Multi-device? | **Yes as hub.** Desktop/Android/future web can share Connector HTTP; this is the right multi-device expansion pivot. |

---

## 3. Desktop (`apps/budcom_desktop`)

### Evidence

- Electron layering: renderer → allowlisted preload IPC → main → application HTTP/lifecycle ([ipc-allowlist.ts](../apps/budcom_desktop/src/application/ipc-allowlist.ts)).
- Security posture: context isolation, no Node in renderer, CSP — aligns with production-hardening rules.
- Owns Connector process lifecycle (spawn/manage) — role Android does not play.
- Feature parity gap vs Android: no voucher browser/details IPC/UI observed; Android already consumes vouchers.
- `apps/budcom_desktop/dist/` gitignored yet historically tracked (~69 files) — maintainability hazard.
- Root README layout historically under-emphasized Desktop (table focuses Android/Flutter).

### Answers

| # | Question | Assessment |
| --- | --- | --- |
| 1 | Aligns with mission? | **Yes** as operator shell for Connector + operational visibility. |
| 2 | Violates NON_NEGOTIABLES? | **No**, if lifecycle remains assistive (run Connector) rather than lock-in SaaS. |
| 3 | Unnecessary complexity? | Electron + IPC allowlist is justified; large renderer `app.ts` growth is a maintainability risk. |
| 4 | Duplicate responsibilities? | HTTP client/DTO duplication vs Android; screenshots/validation scripts adjacent to product code. |
| 5 | Separate better? | Extract renderer modules; consider shared OpenAPI-generated TS client later. |
| 6 | Naming? | Budcom / BUDCO dual branding; no Tradon. |
| 7 | Scale? | Fits solo/SME on Windows next to Tally; not a multi-tenant enterprise console yet. |
| 8 | ERP as SoR? | **Yes** (talks to Connector, does not write Tally). |
| 9 | Future AI? | Could host AI UX later; keep AI out of main-process privileges. |
| 10 | Multi-device? | Desktop is one surface of VISION_2030; IPC-bound UI is not portable — contracts via Connector are. |

---

## 4. Shared packages & secondary mobile

### Evidence

- `shared/packages/budcom_core` and `budcom_contracts` are **Dart**, Melos-tied to Flutter (`apps/budcom_mobile`).
- Android/Desktop **do not** consume these packages; they re-implement DTOs/clients in Kotlin/TS.
- Flutter remote path remains early (health-oriented) while Android is the production companion track ([README.md](../README.md), [ROADMAP.md](ROADMAP.md)).

### Answers

| # | Question | Assessment |
| --- | --- | --- |
| 1 | Aligns with mission? | **Intentional platform seeds**, but currently **misaligned with active delivery** (Kotlin-first companion). Risk of two “truths.” |
| 2 | Violates NON_NEGOTIABLES? | Not inherently — but dual stacks can create **dependency** on maintaining both without user benefit. |
| 3 | Unnecessary complexity? | **Yes, today** — two mobile strategies without a published multi-platform milestone. |
| 4 | Duplicate responsibilities? | Domain concepts duplicated across Dart core and Kotlin domain models. |
| 5 | Separate better? | Decide: (A) Flutter becomes experimental/archive until scheduled, or (B) generate contracts from OpenAPI into all languages. |
| 6 | Naming? | Budcom packages clear. |
| 7 | Scale? | N/A until adopted; does not help Android enterprise scale today. |
| 8 | ERP as SoR? | Contracts assume companion model — fine. |
| 9 | Future AI? | Shared domain events could help later — unused by primary app. |
| 10 | Multi-device? | Dart shared packages **could** help Flutter multi-surface; they do **not** help Compose/Electron without a different sharing strategy (OpenAPI/schema). |

---

## 5. Documentation (`docs/`)

### Evidence

- Governing set complete: NON_NEGOTIABLES, PRODUCT_SOUL (Who We Serve, Design Philosophy), VISION_2030, PROJECT_CONSTITUTION, ROADMAP, DECISIONS.
- Rich ADRs, milestones, stage-updates, OpenAPI, Android contracts, reliability rules.
- Lag: [ROADMAP.md](ROADMAP.md) still marks Production Validation “in progress” while Android health validation fix is on `main` and product declared validation complete — **docs drift**.
- Connector voucher ops/specs appear as untracked or parallel docs in working trees — governance for Connector track is strong when committed.

### Answers

| # | Question | Assessment |
| --- | --- | --- |
| 1 | Mission alignment? | **Excellent.** Soul + non-negotiables + 2030 vision encode inclusion and anti-dependency. |
| 2 | Violations? | None in governing set. |
| 3 | Complexity? | Appropriate for AI-assisted development; volume is high but navigable via README index. |
| 4–5 | Duplication / separation? | Some overlap soul ↔ non-negotiables ↔ vision (intentional reinforcement). Android vs platform ROADMAP scopes should stay explicit. |
| 6 | Naming? | BUDCO consistent in governing docs. |
| 7–10 | Scale / SoR / AI / multi-device? | Vision documents explicitly cover these; constitution correctly fences current Android stack. |

---

# Strengths

1. **ERP-as-SoR is real, not aspirational** — Connector egress controls and Android/Desktop read paths prove it.
2. **Governing documentation foundation** — rare clarity for AI and humans (NON_NEGOTIABLES through DECISIONS).
3. **Android feature architecture** — vertical slices, ports, use cases, Compose state discipline after Dashboard remediation.
4. **Connector reliability culture** — atomic SQLite patterns, architecture boundary tests, privacy-minded diagnostics rules.
5. **Desktop security baseline** — allowlisted IPC, context isolation, sandbox.
6. **Inclusion encoded** — Who We Serve / Inclusion non-negotiable prevent “enterprise-only” drift.
7. **Honest feature freeze signals** — voucher `customerOperational: false`, readiness composition flags — prefer honesty over fake completeness.

---

# Weaknesses

1. **No shared typed Connector client** across Kotlin / TypeScript / Dart — drift risk as APIs evolve.
2. **Dual mobile strategies** (Kotlin production + Flutter/Melos shared packages) without a single active product narrative.
3. **API surface inconsistency** on Connector (e.g. voucher `/api/v1` vs stub company routes; live masters vs sync caches).
4. **Enterprise scalability gap** — auth, multi-user LAN, SQLite single-writer, in-memory voucher query paths.
5. **Desktop ↔ Android feature parity gap** (vouchers, search depth).
6. **Tracked generated Desktop `dist/`** despite `.gitignore`.
7. **Documentation drift** — ROADMAP Production Validation status vs declared completion / commit history.
8. **Large uncommitted Connector voucher WIP** (when present) increases merge and review risk.

---

# Immediate improvements

1. Align [ROADMAP.md](ROADMAP.md) and [PRODUCTION_VALIDATION.md](PRODUCTION_VALIDATION.md) with completed Android validation reality (docs-only).
2. Stop tracking `apps/budcom_desktop/dist/` (`git rm -r --cached`) while keeping ignore rules.
3. Publish or shelve Flutter/`shared` Dart packages explicitly in README (active vs experimental).
4. Finish or remove Connector stub voucher routes to one public story.
5. Prefer OpenAPI (`docs/openapi/connector-v1.yaml`) as the cross-client contract gate in CI.
6. Keep Desktop voucher work on a deliberate parity milestone — do not silently diverge.

---

# Future improvements

1. Shared sync kernel for ledger/stock (and later voucher) in Connector.
2. AuthN/Z for non-loopback Connector exposure (VISION_2030 connected ecosystem).
3. Pushdown pagination/search for large voucher books (enterprise scale-down *and* scale-up).
4. AI assistant as a **client of Connector + on-device context**, never as a bypass of ERP SoR ([NON_NEGOTIABLES.md](NON_NEGOTIABLES.md) §5).
5. Multi-device: keep domain logic behind HTTP contracts; avoid rewriting Connector per surface.
6. Contact Intelligence / Document Intelligence per PRODUCT_SOUL pillars — scheduled only when contracts exist.

---

# Potential risks

| Risk | Impact | Likelihood |
| --- | --- | --- |
| Contract drift across three client languages | High | Medium |
| Accidental Tally write if safety layers regress | Critical | Low (if tests stay mandatory) |
| SQLite + multi-company concurrency under heavy sync | High | Medium as SME grows |
| LAN exposure without auth | Critical | Medium if operators bind beyond loopback |
| Flutter/Kotlin dual roadmap confusion | Medium | Medium |
| Docs claiming completion while ROADMAP lags | Medium | High (already observed) |
| Desktop `dist/` commits polluting reviews | Low–Medium | High |

---

# Recommended refactors

| Refactor | Priority | Notes |
| --- | --- | --- |
| Untrack Desktop `dist/` | High | Hygiene |
| Unify Connector public voucher API story | High | Remove stub or implement |
| OpenAPI → generated/verified clients | Medium | Multi-device enabler |
| Shared remote error mapping on Android | Medium | Maintainability |
| Extract Desktop renderer modules | Medium | File size / clarity |
| Abstract Connector sync engine | Medium | Reduce ledger/stock clone |
| Declare Flutter track status | Medium | Mission focus |
| Remove Android `*Package.kt` markers | Low | Per evolution doc |

---

# Code that should never be rewritten

Treat as **stability assets** — evolve carefully, do not “clean rewrite”:

1. **Tally egress safety stack** — capability model, forbidden mutation XML, approved operation registry, read gateway (`connector/.../tally/`).
2. **SQLite transaction + company-scoped schema discipline** — crash-recovery / checkpoint philosophy.
3. **Android dynamic base URL interceptor + Connector URL validation** — production connectivity correctness.
4. **Desktop IPC allowlist + contextIsolation preload bridge** — security boundary.
5. **Governing docs set** — NON_NEGOTIABLES through DECISIONS (change only with explicit product decision).
6. **Architecture boundary tests** in Connector — living enforcement of layering.

---

# Code that deserves extra tests

1. Cross-company isolation under concurrent sync (Connector).
2. Large-volume voucher list/search (pushdown vs in-memory).
3. Android health/readiness against real Connector version skew (DTO optionality — already started with 0.3.1 regression).
4. Desktop IPC validation failure paths (oversized/malformed payloads).
5. Session company switch resetting Android list/search/sync state (lifecycle).
6. Network policy / bind-host LAN acknowledgment paths.

---

# Code that deserves simplification

1. Android leftover package markers and unused Room/WorkManager wiring until needed.
2. Connector licensing/scheduler stubs if they only clutter health.
3. Parallel master-data “live vs synced” UX copy — make the mental model one sentence in product UI.
4. Desktop monolithic renderer script growth.
5. Duplicate screenshot/validation artifact habits — keep evidence out of source trees.

---

# Honest long-term assessment

> **If this repository continues at the current quality level, can it realistically evolve into the long-term Business OS envisioned in PRODUCT_SOUL and VISION_2030?**

### Answer: **Yes — with conditions.**

**Supporting evidence (why yes):**

- The hardest architectural non-negotiable — **ERP remains System of Record with a strengthening companion** — is already implemented in the Connector and respected by Android/Desktop.
- The product soul (inclusion, scale-down, anti-dependency, AI-as-assist) is now **written into permanent docs**, which is how multi-year AI-assisted teams avoid mission decay.
- Android already demonstrates a serious companion surface (masters, vouchers, search, sync, diagnostics, settings) on Clean Architecture rails.
- Connector port boundaries (`ErpReadPort`, voucher ports) are the correct hinge for multi-ERP and multi-device futures.

**Conditions (what must stay true):**

1. **Do not rewrite the safety/SoR core** for fashion; extend it.
2. **Collapse client contract drift** (OpenAPI as gate) before adding Web/Cloud/AI clients.
3. **Pick one primary mobile companion track** for Version 1 delivery (today: Android) so inclusion-scale UX gets depth, not twin half-products.
4. **Plan enterprise scale as companion scale** (auth, query, ops) — never as “start writing to Tally.”
5. **Keep documentation honest** (ROADMAP ↔ reality) so AI agents do not implement against stale milestone state.
6. **Schedule PRODUCT_SOUL pillars** (contacts, AI, documents) only when Connector contracts exist — constitution already requires this.

**If those conditions fail**, the repo can still ship a good Tally utility — but not the Business OS in VISION_2030.

**If they hold**, the path from today’s local companion to a multi-device, AI-assisted, inclusive Business OS is **architecturally plausible**, not fantasy.

---

## Score rationale (compact)

| Dimension | Why this number |
| --- | --- |
| Health 78% | Strong core; parity/debt/docs-drift prevent “excellent” |
| Architecture maturity 76% | Ports + layers real; platform sharing immature |
| Maintainability 72% | Tests + slices good; duplication + dist + dual mobile |
| Scalability 68% | Solo/SME strong; enterprise companion gaps explicit |
| Documentation 90% | Governing foundation complete; minor operational lag |
| Debt medium | Safety complexity justified; accidental debt manageable |

---

*End of review. No production code or build files were modified to produce this document.*
