# Diagnostics Module — Stage Update

**Module:** Desktop Diagnostics  
**Milestone:** 4D  
**Last updated:** 2026-07-23  
**Path:** `apps/budcom_desktop/src/application/diagnostics-service.ts`

---

## Current status

**Completed — Live Validated**

---

## Capabilities

- Diagnostics snapshot aggregation (versions, OS, lifecycle, session summary)
- Copy diagnostics summary to clipboard
- Export sanitized JSON bundle to `{userData}/diagnostics-exports/`
- Recent lifecycle events and errors in UI
- Health check action
- Open logs folder
- Clear nonessential logs (with confirmation)

---

## Bundle contents

Included: sanitized config, versions, health, lifecycle state, recent logs, session summary, allowed env vars.

Excluded: passwords, tokens, licence secrets, Tally XML, ledger/voucher data, personal/financial records, arbitrary env vars.

---

## Test evidence

Diagnostics export and redaction covered in `test/unit/milestone-4d.test.ts`

Live evidence: `docs/diagnostics/m4d-live-validation.json`

---

## Production readiness

**L4 — Live Validated**
