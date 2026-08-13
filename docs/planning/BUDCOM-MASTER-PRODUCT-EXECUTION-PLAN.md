# BUDCOM Master Product Execution Plan

**Status:** INITIAL FRAMEWORK --- empirical calibration pending\
**Governance:** `POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md`

## 1. Purpose

This file converts the BUDCOM roadmap into an executable development
program. It answers what to build, sequence/dependencies, acceptance and
hardening gates, Claude-capacity allocation, expected calendar time,
monetary cost, and which plan/agent strategy gives the best
whole-project time-versus-cost result.

Numerical forecasts must be calibrated primarily from actual BUDCOM
prompts and accepted outcomes, not generic estimates.

## 2. Release boundary

### MVP-1 / MVP-1.0.x

Reserved for defects, essential missed MVP-1 behavior, bounded
micro-polish, screen/layout optimization, colors/visual consistency,
button behavior, interaction clarity, and performance/regression fixes.
No new feature/module expansion.

Tracked in `BUDCOM-MVP-1-0-X-REFINEMENT-REGISTER.md`, which holds the
improve-vs-increase classification rule, physical-device findings,
connectivity/state-correctness hardening, UI-polish items, and freeze
criteria for this boundary. This plan does not duplicate that content.

### Locked post-MVP sequence

1.  **MVP-1.1 --- Connect with Universal Party Identity**
2.  **MVP-1.2 --- Relationship Timeline, Issue History, Dincharya & OI**
3.  **MVP-1.3 --- Business Profile**
4.  **MVP-1.4 --- Catalogue**
5.  **Post-1.4 --- integrated hardening, real-world usage pause,
    evidence review, then MVP-2 authorization**

## 3. Cross-roadmap architecture

-   ChatGPT remains architectural authority.
-   Layering: Infra → Data → Business → UI.
-   Android remains lightweight and local-first.
-   Room/local persistence should provide authoritative display where
    appropriate; remote sync remains separate.
-   Sync should be incremental/idempotent where practical.
-   Queries should be indexed/bounded/paged where needed.
-   Desktop remains deliberately lightweight and exposes honest
    refresh/sync freshness.
-   Connector identity must not depend on fixed laptop IP.
-   Pairing/security/trust changes require explicit approval.
-   BUDCOM does not directly write to Tally; user-initiated compatible
    XML export/import remains the boundary unless explicitly changed.
-   Evidence before expansion.
-   OI before premature generative-AI claims.
-   Avoid premature CRM/public marketplace/social-feed expansion.

## 4. Execution-unit classes

-   **S --- Small:** narrow, low-risk, likely sub-session.
-   **M --- Medium:** coherent feature slice, multiple layers, usually
    one planned session plus mini-hardening.
-   **L --- Large:** cross-layer persistence/sync/UX work, likely
    multiple sessions.
-   **H --- High Risk:** architecture, security/trust, pairing/auth,
    migration/data integrity, installer/release, connectivity
    resilience, or broad integration.

## 5. Standard milestone record

For each significant milestone record:

**Product:** requirement, desired outcome, UX, exclusions,
dependencies.\
**Technical:** architecture/data/sync/migration/security impact,
expected layers, risk.\
**Acceptance:** automated, physical, visual, offline/restart/update and
performance evidence.\
**Claude:** size class, expected/actual sessions, model, connectors/MCP,
Brainstorm 2, interventions, retries, hardening.\
**Usage:** session/weekly consumption, time-to-limit, wait, wall time,
paid overflow.\
**Outcome:** PASS/PARTIAL/FAIL, defects, deferred items, commits,
release implication.

## 6. Transition sequence

### Transition 0 --- Freeze MVP-1

Complete physical acceptance, essential MVP-1.0.x corrections, bounded
polish, regression evidence and explicit freeze.

### Transition 1 --- Claude automation infrastructure

Set up VS Code integration, connectors/MCP, persistent context,
permissions, approval gates, notifications, autonomous
tests/builds/linters, bounded debugging, and reporting.

Validate with a controlled task.

### Calibration milestones 1--3

Use 2--3 representative real post-MVP milestones both to deliver value
and to measure the workflow. Capture real time, usage, loops,
intervention, connector savings, hardening effort and accepted outcome.

Then calibrate this file's whole-project model.

## 7. MVP-1.1 --- Connect

Primary objective: trusted party/contact layer around existing
accounting identity without becoming a generic CRM.

Planned groups:

-   Universal Party Identity;
-   Tally/BUDCOM ledger contacts;
-   Customers and Prospects;
-   hierarchical tags;
-   search every ledger by phone number;
-   last-synced Tally balance;
-   Call / BUDCOM message / WhatsApp actions;
-   View Ledger / View Vouchers;
-   BUDCOM enrichment → Tally-compatible XML → user import → re-sync
    confirmation;
-   green confirmed vs black pending field semantics;
-   notes/activity and voucher linkage.

Mini-harden coherent slices; integrated hardening before MVP-1.1
release.

## 8. MVP-1.2 --- Relationship Timeline, Issue History, Dincharya & OI

Primary objective: convert party history into actionable operational
memory.

-   Relationship Timeline: disputes, delivery, commitments and relevant
    interactions.
-   Issue History: preserve business context without generic-CRM sprawl.
-   Dincharya: action-focused follow-ups, promised-payment checks,
    callbacks, pending contact completion, pending Tally XML
    confirmation and later appropriate catalogue follow-ups.
-   Missed-but-actionable items should not become an endless overdue
    list.
-   OI positioning: **"We are not AI. This is OI --- programmed to help
    you."**
-   Prefer deterministic, explainable operational helpers.

Mini-hardening per slice + integrated MVP-1.2 hardening.

## 9. MVP-1.3 --- Business Profile

Primary objective: establish trusted business identity before broader
communication/catalogue exposure.

Detailed scope comes from Brainstorm 1. Do not allow premature
marketplace/social-network expansion.

Locked visual/ownership shape: `docs/design/BUDCOM-UI-DESIGN-DECISIONS.md`
§7 (Business Profile Ecosystem) — one business identity, one catalogue, one
asset library, multiple permission-controlled views; Connect and Vartalap
consume this data, they do not own it.

## 10. MVP-1.4 --- Catalogue

Primary objective: robust catalogue on stable product identity.

Current direction:

-   master catalogue table;
-   stable product/SKU IDs;
-   Excel import/export;
-   image filenames aligned with SKU IDs;
-   DB-stored linkage;
-   branch/draft/review/publish workflow;
-   approved image organization/brand-replacement workflow.

Catalogue is part of the same Business Profile Ecosystem ownership boundary
as MVP-1.3 — see `docs/design/BUDCOM-UI-DESIGN-DECISIONS.md` §7. It does not
fork a second identity/asset model.

After MVP-1.4: full integrated hardening → real-world usage pause →
evidence review.

## 11. Empirical Claude calibration

  ------------------------------------------------------------------------------------------------------------------------------------------------
  Milestone     Complexity   Model     Wall   Turns   Session   Weekly   Limit           User   Retry/Loop   Test/Build   Mini-Hardening Outcome
                                       Time           Usage Δ  Usage Δ    Wait   Intervention                                            
  ------------- ------------ ------- ------ ------- --------- -------- ------- -------------- ------------ ------------ ---------------- ---------
  Calibration 1 TBD          TBD        TBD     TBD       TBD      TBD     TBD            TBD          TBD          TBD              TBD TBD

  Calibration 2 TBD          TBD        TBD     TBD       TBD      TBD     TBD            TBD          TBD          TBD              TBD TBD

  Calibration 3 TBD          TBD        TBD     TBD       TBD      TBD     TBD            TBD          TBD          TBD              TBD TBD
  ------------------------------------------------------------------------------------------------------------------------------------------------

Do not invent missing precision.

## 12. Whole-project scenarios

### A --- Optimized Pro

Project productive sessions, five-hour constraints, weekly-cap delay,
calendar duration, subscription/overflow cost and user involvement.

### B --- Max 5x

Use the applicable 5x capacity tier as an input, but only remove/shrink
bottlenecks that measured Pro data shows are capacity-related. Do not
multiply total speed by five.

### C --- Max 20x

Evaluate only if Max-5x-equivalent capacity would still materially delay
the roadmap or justified parallel agents create enough useful demand.

### D --- Hybrid / paid overflow

Compare a lower subscription plus occasional paid overflow against
permanent higher tiers.

### E --- Multi-agent

Measure added cost/usage, truly parallel work, merge/review overhead,
conflict risk and accepted-time saving.

## 13. Whole-project cost/time model

  -------------------------------------------------------------------------------------------------------------------------------------------
  Strategy        Fixed   Variable   Productive   Session   Weekly   User   Hardening/Release     Calendar     Total Time Saved   Incremental
                   Cost       Cost     Dev Time      Wait     Wait   Time                       Completion   Project     vs Pro Cost per Time
                                                                                                                Cost                    Saved
  ------------- ------- ---------- ------------ --------- -------- ------ ------------------- ------------ --------- ---------- -------------
  Optimized Pro     TBD        TBD          TBD       TBD      TBD    TBD                 TBD          TBD       TBD   baseline      baseline

  Max 5x            TBD        TBD          TBD       TBD      TBD    TBD                 TBD          TBD       TBD        TBD           TBD

  Max 20x           TBD        TBD          TBD       TBD      TBD    TBD                 TBD          TBD       TBD        TBD           TBD

  Hybrid/PAYG       TBD        TBD          TBD       TBD      TBD    TBD                 TBD          TBD       TBD        TBD           TBD

  Multi-agent       TBD        TBD          TBD       TBD      TBD    TBD                 TBD          TBD       TBD        TBD           TBD
  -------------------------------------------------------------------------------------------------------------------------------------------

Use ranges when uncertainty is material.

## 14. Upgrade rules

**Stay on Pro** when limits rarely interrupt coherent work and upgrades
save little calendar time.

**Consider Max 5x** when Pro repeatedly stops useful autonomous
milestones or weekly/session waiting materially extends delivery and
measured time saved justifies the cost.

**Consider Max 20x** only when Max-5x-equivalent capacity remains a
material bottleneck or valuable parallelism produces positive
whole-project ROI.

Do not upgrade merely to compensate for poor planning.

## 15. Better-agent / multi-agent evaluation

Run controlled comparisons only where a measurable advantage is
plausible. Compare same-scope accepted outcome under optimized single
agent vs implementation+independent reviewer, parallel non-overlapping
modules, or higher-capability models for high-risk work.

Measure total accepted-delivery time, usage, cost, rework, defects
caught and coordination overhead.

## 16. Forecast updating

After each significant milestone:

1.  append actual execution data;
2.  compare forecast vs actual;
3.  update task-size assumptions;
4.  update session/weekly consumption;
5.  update retry/intervention assumptions;
6.  update remaining calendar forecast;
7.  update plan ROI.

Real observations progressively replace estimates.

## 17. Release philosophy

**Brainstorm → clarified outcome → autonomous implementation →
mini-hardening → acceptance → next slice.**

After a coherent batch:

**integrated hardening → real-world validation → release.**

## 18. Immediate post-MVP actions

1.  Freeze MVP-1.
2.  Commit/adopt the governance document.
3.  Configure Claude
    automation/connectors/MCP/permissions/notifications.
4.  Validate automation on a controlled task.
5.  Brainstorm first MVP-1.1 slice.
6.  Reverse-engineer acceptance outcome.
7.  Issue optimized master prompt.
8.  Run Claude clarification.
9.  Conduct Brainstorm 2.
10. Execute autonomously.
11. Mini-harden.
12. Capture usage/time/outcome.
13. Repeat for 2--3 representative milestones.
14. Populate empirical capacity and whole-project ROI sections.
15. Decide whether Pro remains optimal or Max 5x is justified.
16. Continue roadmap using measured execution planning.

## 19. Status

The roadmap/governance are meaningful now. Numerical Claude capacity,
whole-project duration and plan ROI remain deliberately unfilled until
actual BUDCOM runs provide evidence.

**INITIAL FRAMEWORK LOCKED --- EMPIRICAL CALIBRATION PENDING.**
