# Delivery Milestones

Milestones derived from the MVP 1 specification delivery plan (Section 21) and architecture requirements (Section 18).

## Milestone 0 — Architecture Foundation ✅

**Goal:** Repository skeleton, shared domain, connector contract, CI, no business features.

| Deliverable | Status |
|-------------|--------|
| Monorepo layout with Melos | Done |
| `venture_core` domain package | Done |
| `venture_contracts` DTO package | Done |
| OpenAPI connector contract v1 | Done |
| Connector service skeleton + read-only middleware | Done |
| Flutter app layered structure + home shell | Done |
| Feature module stubs | Done |
| Capability and feature-flag framework | Done |
| Domain event bus interface | Done |
| CI workflow | Done |
| Foundation tests | Done |

**Explicitly excluded:** Tally parsing, real pairing, ledger/voucher data, PDF, search, local DB.

---

## Milestone 1 — Connector Proof of Concept

**Goal:** Prove read-only Tally connectivity on a test company.

- Tally adapter (XML/HTTP export)
- Normalization to Venture entities
- `GET /health` reports real Tally reachability
- `GET /companies` returns test company
- Device pairing flow (minimal approval)
- Contract tests against live connector
- Flutter connection setup screen

**Acceptance:** Developer build connects to one test company.

---

## Milestone 2 — Company and Ledger Reading

**Goal:** Accurate ledger data with local cache.

- Implement ledger endpoints
- Local encrypted database + schema v1 migration
- Ledger list and detail screens
- Money/Dr-Cr reconciliation tests against Tally
- Offline cache with freshness labels
- Paged list performance

**Acceptance:** Ledger balances match Tally for test datasets.

---

## Milestone 3 — Vouchers and Search

**Goal:** Find and inspect vouchers quickly.

- Voucher list and detail endpoints + UI
- Universal search index
- Date and type filters
- Search tolerance (partial names, aliases)

**Acceptance:** Voucher detail matches source; search finds ledgers and vouchers.

---

## Milestone 4 — PDF and Sharing

**Goal:** Generate and share business documents.

- PDF engine (ledger statement, voucher copy)
- Android share sheet integration
- Export audit logging
- Diagnostics screen

**Acceptance:** Multi-page PDF renders without clipping; WhatsApp/email share works.

---

## Milestone 5 — Internal Launch Hardening

**Goal:** Daily-use readiness inside Jaju Sanitations.

- Real-data testing and accuracy fixes
- Performance tuning (search < 300 ms cached)
- App upgrade migration tests
- Security tests (pairing, token expiry, capability denial)
- Signed APK, release tagging, reproducible build docs

**Acceptance:** MVP 1 acceptance checklist (spec Section 26) passes.
