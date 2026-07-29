# Voucher controlled-rollout operator runbook

Use this runbook together with
`voucher-controlled-rollout-policy.md` and
`voucher-controlled-rollout-checklist.md`. It never authorizes execution by itself.

## Before execution

1. Record the connector artifact version and immutable source/build identifier.
2. Confirm SQLite schema version `11` and that migrations completed successfully.
3. Stop writes and create a complete database backup through the existing approved
   backup behavior. Record its path, timestamp, size, and successful read probe.
4. Confirm the exact company appears as enabled in the approved allowlist.
5. Confirm exactly one company is requested.
6. Confirm the inclusive period is valid and within both the one-day initial policy
   and the 366-day connector maximum.
7. Confirm Tally availability without submitting a Voucher synchronization.
8. Confirm there is no active in-process run and no unexpired
   `voucher_sync_reservations` row for the company.
9. Confirm the registered operation remains `Export / Collection`, object type
   `Voucher`, with no automatic retry.
10. Confirm limits remain request 65,536 bytes, response 1,048,576 bytes, and
    timeout 30 seconds. Confirm XML limits retain release defaults.
11. Confirm no scheduler, startup, background, API, or IPC invocation exists.
12. Confirm the privacy-safe monitoring destination and responsible observer.
13. Rehearse the stop, request-lock watchdog, and process restart procedures.
14. Complete the pre-run checklist and obtain explicit execution authorization.

## During execution

Observe, in order:

1. Synchronization requested.
2. Company reservation acquired.
3. Extraction started.
4. Transport completed within byte and timeout limits.
5. Parser completed with `records` or valid `empty`; stop on `partial` or `failure`.
6. Candidate, accepted, and rejected counts reported; stop if any rejection occurs
   or candidate accounting does not reconcile.
7. Staging started and Voucher, ledger, inventory, and allocation counts reported.
8. Repository completion and validation succeeded.
9. Promotion completed and the active pointer changed atomically.
10. Synchronization completed and the reservation released.

Stop on an unexpected or unclassified failure. Do not submit another request.

## After execution

1. Record the immutable synchronization outcome and parser status.
2. Confirm candidate equals accepted plus rejected, and rejected is zero.
3. Reconcile extracted and stored Voucher, ledger, inventory, and allocation counts.
4. Confirm repository completion and promotion succeeded.
5. Confirm the active pointer identifies the new snapshot for only the requested
   company.
6. Query the new active snapshot and verify aggregate counts without recording
   Voucher contents.
7. Confirm the previous snapshot remains queryable and immutable.
8. Query another company, where approved test evidence exists, to confirm its active
   pointer did not change.
9. Confirm no raw XML or prohibited business content was persisted in logs,
   diagnostics, evidence, or checklist records.
10. Record request/response byte counts, phase durations, aggregate counts,
    notification failures, reservation conflicts, and failure classification.
11. Obtain operator and reviewer sign-off before any later run.

## Failure handling

1. Stop and do not retry automatically.
2. Record only privacy-safe identifiers, counts, timings, and classifications.
3. Confirm the previous active pointer remains selected.
4. Confirm failed or staging snapshots are hidden from active queries.
5. Confirm the reservation was released. If the process crashed, record its
   `expires_at` and do not rerun before expiry and explicit approval.
6. Preserve failed or disputed snapshots and the database for diagnosis.
7. Escalate a verified defect with reproducible, sanitized evidence.
8. If the request-audit watchdog triggers, stop the process and use the restart
   procedure below.
9. A retry is a new controlled run and requires a new checklist and approval.

## Monitoring and diagnostics

Record these privacy-safe lifecycle events:

- requested
- reservation acquired and released
- extraction started
- transport completed
- parser completed
- staging started
- repository validation completed
- promotion completed
- synchronization completed or failed

Record:

- total synchronization and individual transport, parsing, mapping, staging,
  completion, and promotion durations
- request and response bytes
- candidate, accepted, rejected, ledger, inventory, and allocation counts
- notification failure, reservation conflict, and stale snapshot counts
- failure classification

Alert and stop for:

- rejected records or a count mismatch
- parser partial/failure
- promotion or active-pointer failure
- unexpected reservation conflicts
- a retained request lock
- stale staging snapshots
- notification failures
- synchronization duration outside the approved envelope
- repeated process restarts

## Privacy restrictions

Never record or persist:

- Raw XML
- Voucher narration or references
- Party, ledger, or stock-item names
- Amounts
- Personal data
- Any other Voucher or business content

Company names belong only in the restricted allowlist and execution authorization,
not general metrics or shared diagnostics. Use approved opaque identifiers where
monitoring needs company correlation.

## Database safety procedure

### Backup before first use

1. Stop the connector or otherwise prevent database writes.
2. Use the existing approved backup behavior to create a complete copy.
3. Verify that the backup exists, is non-empty, and opens read-only.
4. Run `PRAGMA integrity_check` against the backup and require `ok`.
5. Record path, timestamp, size, and verification result without recording data.

Do not copy only the main database while it is being written in WAL mode.

### Schema verification

On the target or an equivalent temporary database, confirm:

- Schema version is `10`.
- `PRAGMA foreign_keys` returns `1`.
- `PRAGMA journal_mode` returns `wal`.
- `PRAGMA busy_timeout` returns `5000`.
- Snapshot, active-pointer, Voucher child, allocation, and
  `voucher_sync_reservations` tables exist.
- Snapshot immutability triggers and Voucher identity indexes exist.
- `PRAGMA foreign_key_check` returns no rows.
- `PRAGMA integrity_check` returns `ok`.

The connector configures foreign keys, WAL, and the busy timeout on each managed
connection. Verification must use temporary databases when testing migrations.

### Lifecycle and recovery

- Readers query through `voucher_active_snapshots`; staging and failed snapshots
  must remain hidden.
- Stale staging snapshots are recovered by the synchronization lifecycle.
- Expired reservations are removed transactionally on the next acquisition.
- Query a historical snapshot only through existing repository query behavior and
  do not update its rows.
- Monitor database, WAL, backup, and historical-snapshot size after each controlled
  run.

### Restore

1. Disable invocation and stop the connector.
2. Preserve and rename the disputed database for diagnosis.
3. Restore the verified complete backup using the approved storage procedure.
4. Start the connector and require successful schema compatibility checks.
5. Repeat integrity, foreign-key, active-pointer, and company-isolation checks.
6. Record approval before permitting another run.

Do not use destructive migration tests or ad-hoc repair SQL on a production database.

## Request-lock watchdog procedure

This is a temporary operational mitigation; it does not correct the underlying
request-audit lock-release defect.

1. Start the watchdog when manual synchronization is invoked.
2. Expect reservation acquisition or a classified conflict promptly. If no lifecycle
   event advances for 35 seconds—five seconds beyond the configured transport
   timeout—declare the run stalled.
3. Inspect privacy-safe connector diagnostics for an audit subsystem exception,
   missing request success/failure audit event, or a later request waiting behind the
   process-local guard.
4. Stop further operator invocation immediately. Do not test the lock with another
   production request.
5. Terminate the connector gracefully. If it does not exit within the approved
   process shutdown window, use the normal process supervisor termination procedure.
6. Before restarting, query the repository and confirm the active snapshot pointer
   is unchanged unless a completed promotion was already recorded.
7. Record whether a company reservation remains. If present, record its expiry and
   wait for expiry; do not delete it manually.
8. Restart the same approved connector artifact and confirm health, schema version,
   active snapshot, and absence or expiry of the reservation.
9. Escalate the audit failure. Obtain a new checklist and explicit approval before
   another run.

## Disablement procedure

1. Withhold manual invocation and execution approval.
2. Mark rollout disabled in the approval record.
3. Remove affected companies from the allowlist.
4. Stop the connector if a run or request may still be active.
5. Record who disabled rollout, when, why, and the last verified active snapshot.

Do not add a scheduler, remote switch, API, or IPC route.

## Rollback procedure

1. Disable rollout and stop the connector.
2. Preserve the disputed snapshot and privacy-safe diagnostics.
3. Confirm the scope and company before any recovery.
4. Prefer operational disablement when data remains internally consistent.
5. Use database restoration only from a verified backup and only with the connector
   stopped.
6. Use active-pointer rollback only if an existing repository operation has been
   separately reviewed and approved. This runbook does not authorize direct SQL.
7. Use application rollback only when the older artifact accepts the current schema;
   otherwise restore its matching database backup.
8. After recovery, verify active query behavior, historical immutability, and
   isolation for every affected company.
9. Record the rollback reason and obtain approval before another synchronization.
