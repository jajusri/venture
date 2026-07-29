# Voucher controlled-rollout checklist

Copy this table for each proposed run. Store only privacy-safe evidence references,
aggregate counts, identifiers, versions, and timestamps. Do not include raw XML or
Voucher/business contents.

Allowed status values: **Pass**, **Fail**, **Not applicable**. A blank or failed
mandatory item blocks execution or acceptance.

| Checklist item | Status | Evidence or notes | Operator | Timestamp |
|---|---|---|---|---|
| Pre-run approval recorded |  |  |  |  |
| Connector release artifact and version confirmed |  |  |  |  |
| Exactly one requested company is enabled in the approved allowlist |  |  |  |  |
| Inclusive period is confirmed and within the approved policy |  |  |  |  |
| Database backup created and read verification passed |  |  |  |  |
| SQLite schema version 11 confirmed |  |  |  |  |
| Integrity and foreign-key checks passed |  |  |  |  |
| WAL and 5,000 ms busy timeout confirmed |  |  |  |  |
| Request, response, XML, and timeout limits confirmed unchanged |  |  |  |  |
| Approved read-only Export / Collection Voucher contract confirmed |  |  |  |  |
| No active run or unexpired reservation exists for the company |  |  |  |  |
| No scheduler, background, startup, API, or IPC invocation exists |  |  |  |  |
| Privacy-safe monitoring destination and observer confirmed |  |  |  |  |
| Stop and request-lock watchdog procedures confirmed |  |  |  |  |
| Explicit execution authorization recorded |  |  |  |  |
| Parser completed without partial/failure status |  |  |  |  |
| Candidate equals accepted plus rejected; rejected equals zero |  |  |  |  |
| Extracted and stored Voucher/child counts reconcile |  |  |  |  |
| Repository validation and snapshot completion passed |  |  |  |  |
| Promotion and active-pointer switch confirmed |  |  |  |  |
| New active snapshot aggregate query passed |  |  |  |  |
| Previous snapshot remains queryable and immutable |  |  |  |  |
| Company isolation is unchanged |  |  |  |  |
| No raw XML or prohibited business content was persisted |  |  |  |  |
| Reservation release or recorded expiry confirmed |  |  |  |  |
| Durations and privacy-safe diagnostics recorded |  |  |  |  |
| Post-run operator sign-off obtained |  |  |  |  |
| Independent reviewer sign-off obtained |  |  |  |  |
