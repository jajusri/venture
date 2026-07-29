# Voucher controlled-rollout policy

**Status:** Preparation complete; execution remains disabled until the first-run acceptance gate is approved
**Scope:** Limited, operator-initiated production Voucher synchronization
**Owner:** Assigned rollout operator and approving release authority

## Approved boundaries

| Control | Policy |
|---|---|
| Companies | Only companies listed in the separately approved allowlist. The repository does not supply a default production company. |
| Companies per invocation | Exactly one |
| Initial date range | One calendar day, inclusive |
| Maximum date range | 366 calendar days, inclusive; expanding beyond one day requires a separately recorded approval |
| Invocation | Manual and attended |
| Concurrency | One operator-initiated Voucher synchronization at a time; same-company database reservation must also be acquired |
| Scheduling | Prohibited |
| Background or startup execution | Prohibited |
| Automatic retry or resume | Prohibited |
| Tally operation | Read-only `Export / Collection`, embedded object type `Voucher` |

The company allowlist is an operational approval record. It must contain the exact
Tally company name and an approval reference, but no Voucher or business content.
An empty or missing allowlist denies every production run.

| Approved company | Approval reference | Approved by | Approved at | Enabled |
|---|---|---|---|---|
| _No company approved_ |  |  |  | No |

## Fixed connector limits

Do not increase these limits during controlled rollout:

- Request: 65,536 UTF-8 bytes
- Response: 1,048,576 UTF-8 bytes
- Transport timeout: 30 seconds
- XML parser depth, node, and text limits: current release defaults
- Request contract: the registered production Voucher operation only
- Retry count: zero

## Authorization

Every invocation requires:

1. An assigned operator.
2. A different person or formally designated authority approving the company,
   inclusive period, connector version, and database target.
3. A completed checklist with evidence references and timestamps.
4. Explicit execution approval recorded after pre-run checks.
5. A new approval after every stopped or failed run. Prior approval never
   authorizes a retry.

## Mandatory stop policy

Stop before contacting Tally, or terminate the current attempt without retry, if:

- The company is absent from the allowlist or more than one company is requested.
- The date range is invalid, longer than the approved range, or longer than 366
  inclusive days.
- The request is not the registered `Export / Collection` Voucher contract.
- The company reservation cannot be acquired.
- A request, response, timeout, or XML parser limit is reached.
- Parser status is `partial` or `failure`.
- Candidate count does not equal accepted plus rejected count.
- Any record is rejected or any fatal validation issue exists.
- Extracted Voucher or child counts do not equal stored counts.
- Foreign-key, ownership, company-isolation, snapshot-completion, promotion, or
  active-pointer verification fails.
- Raw XML or prohibited business content is found in persistence, logs, evidence,
  or the checklist.
- A request-audit failure may have retained the in-process request lock.
- An unclassified error occurs.

A stopped run is not resumed. Preserve privacy-safe diagnostics, verify that the
previous active snapshot remains selected, and obtain a new execution approval.

## Escalation

Escalate immediately to the approving release authority when:

- Any mandatory stop condition occurs.
- A defect appears reproducible.
- Reservations conflict more often than the planned single-run workflow permits.
- A staging snapshot remains stale or a reservation survives beyond its expected
  release.
- Notification failures repeat.
- The process must be restarted.
- Database integrity, company isolation, or active-pointer state is uncertain.

The escalation record may contain identifiers, counts, timings, classifications,
versions, and sanitized error codes. It must not contain raw XML or business data.

## Operational disablement

Disable controlled rollout immediately by one or more of:

1. Do not authorize or invoke another run.
2. Stop the connector process.
3. Remove the company from the approved allowlist.
4. Set the rollout approval record to disabled.

There is no scheduler or remote Voucher execution route to disable. Do not add one.

## Rollback boundaries

- **Operational disablement:** prevents another invocation; it does not change data.
- **Application rollback:** deploy a previously approved connector artifact only
  when its schema compatibility has been verified.
- **Database restoration:** stop the connector, preserve the disputed database,
  restore a verified complete backup, restart, and verify schema and active queries.
- **Active-pointer rollback:** perform only through an existing, separately approved
  repository behavior. No ad-hoc SQL is authorized by this policy.

Never mutate a promoted snapshot. Preserve failed or disputed snapshots for
diagnosis, record the reason, and verify active queries and company isolation after
any approved recovery.

## Rollout phases

### Phase 0 — Dry operational rehearsal

- Do not contact Tally.
- Use a temporary database.
- Rehearse this policy, the runbook, checklist, monitoring, watchdog, stop, and
  disable procedures.

### Phase 1 — First approved company

- One allowlisted company and one approved one-day period.
- One manual invocation.
- Complete reconciliation and review before authorizing another run.

### Phase 2 — Repeated bounded runs

- The same approved company.
- Separately approved bounded periods, one run at a time.
- Review durations, counts, failures, notification results, and database growth
  after each run.

### Phase 3 — Additional approved companies

- Add one company per allowlist approval.
- Require a successful review after every addition.
- Preserve bounded, attended, non-retrying operation.

This policy does not authorize unrestricted rollout.
