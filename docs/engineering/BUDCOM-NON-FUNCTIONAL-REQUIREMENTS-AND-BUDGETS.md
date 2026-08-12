# BUDCOM Non-Functional Requirements and Engineering Budgets

**Status:** Initial framework; numerical budgets must be calibrated with measurement  
**Purpose:** Convert BUDCOM's lightweight/fast/reliable philosophy into measurable engineering constraints.

## 1. Permanent product principle

BUDCOM must be exceptionally lightweight, responsive, resource-efficient, resilient, and unobtrusive across Android and Windows. Competing products with equivalent functionality should not easily be materially lighter or faster.

## 2. Budget categories

Track explicit budgets for:

- Android cold start
- Android warm start
- Desktop startup
- Connector startup
- reconnect time
- local screen render latency
- Room/SQLite query latency
- sync latency
- historical reconciliation throughput
- PDF generation
- memory usage
- idle CPU
- active CPU
- Android battery impact
- Windows idle background impact
- APK size
- installer size
- DB growth
- log growth
- network usage
- crash-free operation
- retry/recovery time

## 3. Initial target philosophy

Until enough measurements exist, use these directional rules:

- local/common operations should feel immediate;
- ordinary Room/SQLite reads should target sub-second completion;
- UI should not wait on network when local authoritative data exists;
- idle Desktop/Connector CPU should be near-zero in ordinary conditions;
- background work should be bounded;
- no unbounded full-table/full-history processing on common screens;
- no N+1 network design for company-wide accounting history;
- no hidden polling at unnecessarily high frequency;
- APK/installer growth requires explanation when material.

## 4. Performance evidence

For significant features record, where relevant:

- baseline before change;
- after-change measurement;
- test device/hardware;
- dataset size;
- cold vs warm behavior;
- median and worst meaningful observation;
- query plan/index use;
- regression threshold.

Avoid fragile microbenchmarks that do not represent user experience.

## 5. Resource budgets

### Android
Measure:
- memory after launch
- memory under large lists
- background battery behavior
- DB size
- startup time
- scrolling responsiveness
- offline query performance

### Desktop/Connector
Measure:
- idle memory
- idle CPU
- startup time
- reconnect time
- child-process behavior
- log/database growth
- effect on Tally responsiveness

## 6. Dataset-scale testing

Performance tests should eventually include:

- small business dataset
- realistic Waterland-scale dataset
- multi-year accounting history
- large ledger count
- high voucher count
- large catalogue
- many images
- prolonged usage

## 7. Reliability budgets

Track:

- crash rate
- failed sync rate
- reconnect failure rate
- duplicate/phantom record rate
- migration failure rate
- corrupt/recovery incidents
- mean time to recover from Tally/network outage

Exact SLOs should be set after controlled-pilot evidence exists.

## 8. Regression rule

A feature that materially violates a locked performance/resource budget must not ship merely because functionality is correct.

Performance is part of product correctness for BUDCOM.

## 9. Calibration plan

During Controlled Pilot:

1. capture real baseline measurements;
2. set numerical budgets from observed good behavior;
3. identify outliers;
4. define release thresholds;
5. update this document with measured targets.

## 10. Scale discipline

**Design irreversible decisions as though BUDCOM may one day serve a
billion users. Build reversible implementations only for the scale that
evidence justifies today.**

- Avoid structural ceilings in identities, contracts, data ownership,
  migrations, trust boundaries, and modularity.
- Do NOT prematurely build billion-user infrastructure — no premature
  cloud/microservice/global-scale mandate.
- Scale infrastructure only when evidence demands it.
- Prefer evolution over future rewrites.

Internal shorthand: **billion-user engineering discipline, current-user
infrastructure, evidence-driven scaling.**

This does not claim current infrastructure supports billion-user scale.
It governs how irreversible decisions (identity, contracts, schema,
trust boundaries) are shaped so they do not need a future rewrite, while
actual implementation scale stays matched to real evidence — never
ahead of it.

**MEASURE FIRST, THEN LOCK NUMBERS — BUT NEVER ABANDON THE LIGHTWEIGHT PRINCIPLE.**
