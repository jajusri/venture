# Controlled Voucher Discovery Fixture Authorization

Status: Human approval required; discovery is not authorized by this document alone.

## Fixture and governance boundary

The fixture must be a designated non-production Tally company containing synthetic data only. Record its
alias, period, Tally build, backup location, and responsible owner before discovery.

Select exactly one governance mode:

- `single-authorized-owner` for local, synthetic, non-production development
- `multi-reviewer` when the five independent review functions are available

### Single-authorized-owner mode

The authorized owner accepts responsibility for product ownership, Tally data ownership, accounting review,
security/privacy review, and connector engineering review. The manifest must record:

- Name and project role
- Dated approval and auditable local approval reference
- Fixture-only acknowledgment
- Synthetic-data confirmation
- Backup confirmation
- Sanitization approval
- Date-range approval
- Expected-count approval
- Explicit execution acknowledgment

This mode cannot authorize a production company or rollout, mark an operation `VERIFIED_SAFE`, bypass
sanitization, or bypass bounded date, timeout, response-size, and output-root controls. It is invalid outside
local non-production development.

### Multi-reviewer mode

The existing five roles remain supported: product owner, Tally data owner, accounting reviewer,
security/privacy reviewer, and connector engineering owner. Every approval must be named, dated, approved,
and carry a reference.

Code cannot complete, infer, or substitute owner or reviewer approval.

Create, edit, cancel, and delete experiments are permitted only when individually listed in the approved
manifest and performed manually inside the named fixture. Production companies, customer data, credentials,
imports, automated mutation requests, and unlisted experiments are prohibited.

## Backup, verification, and cleanup

The Tally data owner must create and verify a restorable backup before experiments. Record expected counts
before each mutation sequence. Reviewers manually compare Tally-visible records and aggregate counts before
and after each experiment; disagreement stops discovery.

After review, restore or reconcile the fixture to its approved baseline, verify counts and backup usability,
securely remove prohibited raw captures, and record cleanup approval. Do not delete customer or unrelated
user data.

## Minimum fixture voucher matrix

The fixture owner must replace placeholders manually. They are not accounting values.

| Key | Type | Date | Number | Party | Reference | Narration | Ledger entries | Inventory | Cancelled | Amount | Mutation experiment | Requirement | Private in evidence |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `SALES_BASE` | Sales | `[date]` | `[number]` | `[authoritative party]` | `[reference]` | `[text]` | `[expected structure]` | `[expected]` | No | `[authoritative behavior]` | edit mutable fields | Mandatory | all business values |
| `PURCHASE_BASE` | Purchase | `[date]` | `[number]` | `[authoritative party]` | `[reference]` | `[text]` | `[expected structure]` | `[expected]` | No | `[authoritative behavior]` | edit then restore | Mandatory | all business values |
| `RECEIPT_BASE` | Receipt | `[date]` | `[number]` | `[review behavior]` | `[reference]` | `[text]` | `[expected structure]` | none expected | No | `[authoritative behavior]` | edit reference | Mandatory | all business values |
| `PAYMENT_BASE` | Payment | `[date]` | `[number]` | `[review behavior]` | `[reference]` | `[text]` | `[expected structure]` | none expected | No | `[authoritative behavior]` | edit narration | Mandatory | all business values |
| `JOURNAL_NO_PARTY` | Journal | `[date]` | `[number]` | no meaningful single party expected | `[reference]` | `[text]` | `[expected structure]` | none expected | No | `[adapter decision]` | edit ledger allocation | Mandatory | all business values |
| `CONTRA_BASE` | Contra | `[date]` | `[number]` | `[review behavior]` | `[reference]` | `[text]` | `[expected structure]` | none expected | No | `[adapter decision]` | edit then restore | Mandatory | all business values |
| `INVENTORY_BASE` | `[candidate type]` | `[date]` | `[number]` | `[review behavior]` | `[reference]` | `[text]` | `[expected structure]` | `[expected structure]` | No | `[adapter decision]` | edit quantity then restore | Conditional on inventory evidence scope | all business values |
| `CANCELLED_BASE` | `[candidate type]` | `[date]` | `[number]` | `[review behavior]` | `[reference]` | `[text]` | `[expected structure]` | `[expected]` | Yes | `[adapter decision]` | create, cancel, observe, restore | Mandatory | all business values |
| `NO_REFERENCE` | `[candidate type]` | `[date]` | `[number]` | `[review behavior]` | absent | `[text]` | `[expected structure]` | `[expected]` | No | `[adapter decision]` | none | Mandatory | all business values |
| `NO_NARRATION` | `[candidate type]` | `[date]` | `[number]` | `[review behavior]` | `[reference]` | absent | `[expected structure]` | `[expected]` | No | `[adapter decision]` | none | Mandatory | all business values |
| `BLANK_NUMBER` | `[permitting type]` | `[date]` | absent if permitted | `[review behavior]` | `[reference]` | `[text]` | `[expected structure]` | `[expected]` | No | `[adapter decision]` | none | Conditional on source behavior | all business values |
| `UNICODE_TEXT` | `[candidate type]` | `[date]` | `[number]` | `[Unicode placeholder]` | `[Unicode placeholder]` | `[Unicode placeholder]` | `[expected structure]` | `[expected]` | No | `[adapter decision]` | edit Unicode then restore | Mandatory | all business values |
| `MAX_BOUNDARY_TEXT` | `[candidate type]` | `[date]` | `[boundary placeholder]` | `[boundary placeholder]` | `[boundary placeholder]` | `[candidate-limit text]` | `[expected structure]` | `[expected]` | No | `[adapter decision]` | boundary validation only | Mandatory | all business values |
| `IDENTITY_TWIN_A` | `[same candidate type]` | `[date]` | `[similar value]` | `[similar value]` | `[similar value]` | `[similar value]` | `[expected structure]` | `[expected]` | No | `[similar behavior]` | resemble twin B after edit | Mandatory | business values and source identity |
| `IDENTITY_TWIN_B` | `[same candidate type]` | `[date]` | `[similar value]` | `[similar value]` | `[similar value]` | `[similar value]` | `[expected structure]` | `[expected]` | No | `[similar behavior]` | remain distinct | Mandatory | business values and source identity |

This matrix proposes candidates; it does not declare any type supported.

## Manifest, sanitization, evidence, and retention

The approved manifest records the exact fixture alias, explicit date range, expected records and counts,
mutation sequence, sanitization state, and approvals.

Future approved runs may produce sanitized request shape and structural XML, a field inventory, identity
experiment table, completeness observations, count reconciliation, response-size measurements, parse
duration, memory observations, cancellation behavior, privacy review, and an approval decision.

Schemas, placeholder manifests, sanitized structures, aggregate counts, bounded measurements, and approved
decisions may be committed. Raw XML, credentials, production/customer names, unredacted narration, business
values, source identities, local paths, and sensitive monetary values remain access-controlled and local,
then are deleted after the approved retention period.

## Exit procedure

Stop on company or count mismatch, incomplete approval, unexpected type, truncation, timeout, structural
drift, privacy failure, unsafe output path, or any production-data indication. Restore/reconcile the fixture,
verify backup usability, remove prohibited artifacts, and record closure.
