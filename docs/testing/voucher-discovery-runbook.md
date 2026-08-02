# Controlled Voucher Discovery Runbook

Status: Procedure only; this document does not authorize or execute discovery.

Authority:

- `docs/specifications/business-os-voucher-contract-v1.1.md`
- `docs/testing/voucher-discovery-fixture.md`
- The completed and human-approved Voucher discovery manifest

## 1. Purpose and safety boundary

This runbook describes the first controlled Voucher discovery performed by an approved engineer against one
named, synthetic, non-production Tally fixture company. It does not apply to production companies or real
customer data.

Discovery remains read-only from the connector. Fixture mutations needed for identity experiments are
performed manually by the authorized Tally data owner and only when the manifest explicitly approves them.
Stop immediately if the active company, period, operation, approval, output location, or sanitization state
does not exactly match the approved manifest.

## 2. Preconditions

The engineer must complete and record every check before execution.

### 2.1 Required governance approval

- [ ] Exactly one supported `governanceMode` is selected.
- [ ] For `single-authorized-owner`, the owner name, role, UTC approval time, and approval reference are
      complete and non-placeholder.
- [ ] The owner explicitly acknowledges fixture-only use, synthetic data, backup, sanitization, date range,
      expected counts, and execution.
- [ ] The owner accepts responsibility for product, Tally data, accounting, security/privacy, and connector
      engineering review.
- [ ] For `multi-reviewer`, all five role approvals are named, dated, approved, and referenced.
- [ ] Approval covers the exact fixture alias, date range, operation, experiment matrix, evidence location,
      response limit, timeout, sanitization mode, and retention period.
- [ ] Create/edit/cancel/delete authorization is explicit for every planned identity experiment.

Approval cannot be supplied or completed by code.

Single-owner governance is limited to local non-production development. It cannot authorize a production
company or rollout, mark an operation `VERIFIED_SAFE`, bypass sanitization, or weaken bounded date, timeout,
response-size, or output-root controls.

### 2.2 Fixture company

- [ ] The company is explicitly designated non-production.
- [ ] It contains synthetic data only.
- [ ] It is not named in the production-company denylist.
- [ ] The visible company identity matches the manifest exactly.
- [ ] Its Tally build and company period are recorded.
- [ ] Expected voucher fixture keys and aggregate counts are recorded.
- [ ] No unrelated company is open or selected.

### 2.3 Backup

- [ ] The Tally data owner created a backup immediately before experiments.
- [ ] The backup location and timestamp are recorded without committing an absolute private path.
- [ ] A restoration check was completed.
- [ ] The owner confirms that restoration will affect only the fixture.

### 2.4 Manifest and date range

- [ ] The machine-readable manifest passes runtime validation.
- [ ] Every required human approval is complete.
- [ ] Expected records, counts by type, active count, cancelled count, and mutation sequence are complete.
- [ ] `dateFrom` and `dateTo` are explicit ISO dates.
- [ ] Dates match the approved fixture-company period evidence.
- [ ] The range is current financial year to date and does not exceed one financial year.
- [ ] No financial-year start month was silently assumed.
- [ ] No unbounded or default date request is possible.

### 2.5 Sanitization and local evidence controls

- [ ] Sanitization mode is `strict`.
- [ ] The approved output directory is inside the approved local evidence root.
- [ ] The directory is access-controlled and contains no prior unrelated evidence.
- [ ] Maximum response bytes and timeout are positive, explicit, and approved.
- [ ] Raw response content will not be committed.
- [ ] Evidence retention and secure cleanup dates are recorded.

If any precondition is unchecked, the run is **NO-GO**.

## 3. Discovery execution

Only an approved engineer may perform these steps.

1. Record the run identifier, engineer, reviewers, UTC start time, connector commit, Tally build, fixture
   alias, and manifest evidence version.
2. Validate the manifest with the repository-provided runtime validator. Save only its sanitized pass/fail
   result.
3. Confirm that the selected governance mode is unambiguous and its owner or reviewer approvals are complete.
4. Confirm the candidate operation identifier exactly matches the approved discovery candidate.
5. Confirm its classification is `EXPERIMENTAL_DISABLED`, rollout is `disabled`, and production-gateway
   execution remains denied. If it is `VERIFIED_SAFE`, production-enabled, or operationally registered,
   stop and open a security review.
6. Confirm the active company exactly matches the approved fixture alias and is absent from the
   production-company denylist.
7. Confirm explicit dates match the manifest and remain within the approved company period and maximum.
8. Resolve and verify the output directory is contained inside the approved local evidence root.
9. Confirm strict sanitization, maximum response bytes, timeout, and reviewer acknowledgment.
10. Have the authorized owner or security/privacy reviewer confirm the final inputs without exposing
    business values in chat, tickets, or logs.
11. Execute the separately reviewed discovery harness using only those exact inputs. Do not improvise
    command flags, change the operation, retry automatically, or bypass a failed check.
12. If the harness reports mismatch, timeout, truncation, cancellation, structural drift, excessive size,
    or sanitization failure, stop. Preserve only allowed failure metadata.
13. Capture the permitted evidence artifacts listed below.
14. Record UTC completion time and whether the run completed, failed, or was cancelled.
15. Do not promote the operation, implement extraction, or alter production policy during the session.

This runbook intentionally does not define a runnable command. The command and harness implementation require
a separate reviewed change and explicit authorization.

## 4. Evidence capture and review

### 4.1 Permitted artifacts

- Sanitized request shape
- Sanitized structural XML fixture
- Field inventory
- Identity experiment table using fixture keys
- Completeness observations
- Count reconciliation
- Response-size measurements
- Parse duration
- Memory observations
- Cancellation behavior
- Privacy review
- Gate decision

### 4.2 Review questions

Reviewers must record evidence rather than conclusions based on assumptions:

- Which fields and child structures were observed?
- Is each field consistently present, conditionally present, absent, or ambiguous?
- Which candidate source value appears stable through approved mutations?
- How is cancellation represented before and after reopening?
- Are ledger entries present, ordered, and structurally distinguishable?
- When are inventory entries present, absent, or inapplicable?
- Is any voucher-level amount authoritative, or is its meaning ambiguous?
- Are debit/credit semantics defined at the voucher or child-entry level?
- What objective signal proves the response is complete?
- What are response bytes, parse duration, and observed peak memory?

## 5. Business OS contract field inventory

Create one row per candidate voucher type and field. Do not merge conflicting observations.

Allowed classifications:

- `Observed` — structurally present and evidenced
- `Missing` — expected for the experiment but absent
- `Optional` — legitimately conditional or absent under the contract
- `Unsupported` — cannot satisfy the adapter contract
- `Ambiguous` — present but meaning or authority is not proven

| Screen/structure | Contract field | Type/fixture key | Classification | Evidence reference | Reviewer notes |
|---|---|---|---|---|---|
| Shared | `voucherId` candidate | `[type/key]` | `[Observed/Missing/Optional/Unsupported/Ambiguous]` | `[sanitized reference]` | `[notes]` |
| Shared | `identityVersion` strategy | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Shared | `date` | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Shared | `voucherType` | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Shared | `voucherNumber` | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Shared | `partyName` | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Shared | `amount` | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Shared | amount `side` | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Shared | `status` | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Shared | `dataQuality` inputs | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| List | `referenceNumber` | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| List | `narrationPreview` | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Details | `effectiveDate` | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Details | `narration` | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Ledger entry | `lineNumber`/order | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Ledger entry | `ledgerName` | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Ledger entry | `amount` and side | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Ledger entry | reference fields | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Inventory entry | `lineNumber`/order | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Inventory entry | `itemName` | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Inventory entry | quantity/unit/rate | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |
| Inventory entry | `amount` and side | `[type/key]` | `[classification]` | `[reference]` | `[notes]` |

Also record Summary evidence for total count, counts by type, incomplete count, cancelled count, and
`lastSynchronizedAt` semantics. Summary monetary aggregation is excluded.

## 6. Stable identity experiment

Use fixture keys in retained evidence. Do not log or commit source identity or business values.

### 6.1 Baseline create

1. The authorized Tally data owner manually creates the approved fixture record.
2. Capture the sanitized structural observation and candidate identity.

Expected: a non-mutable, company-scoped identity candidate exists and the identity strategy version is
recorded.

Pass: identity is present, unique, reproducible, and distinct from mutable business fields.

Fail: identity is absent, derived from number/date/type/party/reference/amount, or collides.

### 6.2 Edit mutable content

Perform each approved edit separately: general edit, renumber, and narration edit. Observe after each step.

Expected: stable identity and `identityVersion` remain unchanged; content changes are visible.

Pass: the same identity resolves to the edited record without a duplicate.

Fail: identity changes, disappears, collides, or both old and new records appear.

### 6.3 Cancel

Manually cancel the fixture voucher and observe it again.

Expected: identity and version remain stable; cancellation maps authoritatively to `Cancelled`.

Pass: one record retains its identity and changes cancellation state.

Fail: cancellation deletes the record unexpectedly, creates a duplicate, changes identity, or remains
ambiguous.

### 6.4 Restore

Restore the cancelled voucher only if the fixture approval permits it.

Expected: identity and version remain stable; status returns authoritatively to `Active`.

Pass: the same record returns to active without duplication.

Fail: identity changes, a second record appears, or status cannot be determined.

### 6.5 Restart and reopen

Restart Tally, reopen only the approved fixture company, and repeat the bounded observation.

Expected: every surviving fixture record retains its identity and identity version.

Pass: identities, counts, and relationships reconcile exactly.

Fail: identity depends on process lifetime, open-company order, mutable data, or an unstable session value.

Any duplicate stable identity with different content is an immediate identity-gate failure.

## 7. Completeness experiment

No experiment in this phase may delete or replace a live Voucher cache. Observations use isolated discovery
output only.

| Condition | How to establish it | Required outcome |
|---|---|---|
| Complete export | Transport success; approved limits respected; company/range confirmed; structure valid; expected count reconciliation exact; no duplicate conflict; explicit complete-export evidence present | Mark the discovery observation complete; do not infer operational promotion |
| Partial export | Deliberately bounded fixture test or structural/count mismatch recognized by the reviewed harness | Classify partial and retain any previous cache unchanged |
| Empty export | Use an approved fixture period whose expected count is explicitly zero | Accept as evidence only when company/range and complete-zero proof reconcile |
| Timeout | Use the approved bounded timeout test without automatic retries | Record sanitized timeout metadata; retain prior cache |
| Cancellation | Cancel the reviewed harness at an approved point | Record cancelled state and real processed counts; retain prior cache |
| Truncation | Apply an approved response limit below the known fixture response size or use a sanitized truncated fixture offline | Detect truncation as failure; never treat parser success alone as complete |

Unexpected empty results, partial content, timeout, cancellation, truncation, structural drift, or count
mismatch must never authorize deletion reconciliation.

## 8. Financial semantics review

The accounting reviewer evaluates each candidate voucher type independently.

### Voucher-level amount

- Identify whether the source exposes a single authoritative voucher-level amount.
- Compare it with the visible fixture voucher without committing its value.
- Do not create a total by summing ledger or inventory entries.
- Mark absent or conflicting semantics `Ambiguous` or `Unsupported`.

### Debit/credit semantics

- Determine whether side applies authoritatively to the voucher header, individual child entries, or neither.
- Record signed-versus-absolute behavior and reversal/cancellation behavior.
- Do not expose a header side until its business meaning is proven.

### Child-entry ordering

- Compare observed ledger and inventory order with the Tally-visible fixture.
- Repeat after edit, restart, and reopen.
- Determine whether ordering is authoritative and stable; do not invent sorting.

### Inventory applicability

- Determine per voucher type whether inventory structures are required, optional, or inapplicable.
- Absence for a non-inventory type does not imply `Incomplete`.
- Do not infer inventory totals or header amounts.

No financial conclusion is approved solely because a field exists.

## 9. Privacy review

Before anything is committed, the security/privacy reviewer must verify:

- [ ] No raw voucher XML is included.
- [ ] No credentials, production names, customer names, or production identifiers are included.
- [ ] Voucher numbers, parties, references, narrations, amounts, ledger values, inventory values, search text,
      and stable source identities are removed or irreversibly replaced.
- [ ] Fixture aliases and keys cannot identify a customer.
- [ ] Request shapes contain structure only.
- [ ] Error evidence contains sanitized reason codes, not payload excerpts.
- [ ] Paths are repository-relative or generalized.
- [ ] Counts and measurements are approved for disclosure.
- [ ] Sanitized XML remains structurally faithful after redaction.
- [ ] Local raw evidence has an owner, access boundary, retention deadline, and cleanup record.

Privacy failure is a **NO-GO** and blocks Gate I as well as operational approval.

## 10. Acceptance and gate decision

### Gate C — Transactional export captured

- [ ] Approved fixture and bounded request used.
- [ ] Sanitized structural export captured.
- [ ] Company and date range confirmed.
- [ ] Expected counts reconciled.
- [ ] No truncation, timeout, cancellation, or structural drift.
- [ ] Privacy review permits the retained evidence.

### Gate D — Stable identity proven

- [ ] Identity is source-authoritative, stable, unique, and company-scoped.
- [ ] Mutable fallback fields are excluded.
- [ ] Create/edit/renumber/narration/cancel/restore/restart/reopen experiments pass.
- [ ] Similar mutable records remain distinct.
- [ ] No duplicate identity has conflicting content.
- [ ] `identityVersion` is defined.

### Gate E — Supported voucher types approved

- [ ] Each proposed type has its own fixture evidence.
- [ ] Mandatory and conditional fields are classified.
- [ ] Required child structures are proven.
- [ ] Cancellation and completeness behavior are proven.
- [ ] Per-type conformance tests are specified.
- [ ] The approved allowlist contains only proven types.

### Gate F — Financial semantics approved

- [ ] Voucher-level amount authority is decided per type.
- [ ] Signed/absolute and debit/credit semantics are documented.
- [ ] Party behavior is approved per type.
- [ ] Ledger ordering is proven.
- [ ] Inventory applicability and amount behavior are approved.
- [ ] Ambiguous values remain disabled or unavailable.

### Gate G — Complete-export rule proven

- [ ] Complete, partial, empty, timeout, cancellation, and truncation cases are distinguished.
- [ ] Company and range confirmation is reliable.
- [ ] Expected count reconciliation is reliable.
- [ ] Response-limit breach cannot appear successful.
- [ ] Parser success alone cannot prove completeness.
- [ ] Every failure retains the previous snapshot.
- [ ] Deletion reconciliation remains fail-closed.

For each gate, record `PASS`, `FAIL`, or `BLOCKED`, evidence references, remaining action, owner, reviewer,
and decision date. A gate is `PASS` only when every required item is checked and evidence-reviewed.

## 11. Go / No-Go and final outcome

**GO** requires Gates C, D, E, F, and G all to pass, together with the already required performance,
privacy, fixture-authorization, and operational approvals.

Any failed, blocked, ambiguous, missing, mismatched, truncated, timed-out, cancelled, or privacy-unsafe
condition is **NO-GO**.

Only after all required gates pass may operational Voucher extraction implementation begin. Approval does
not automatically authorize storage, synchronization, API, IPC, desktop UI, packaging, publishing, or
production rollout; each remains a separately reviewed implementation stage.
