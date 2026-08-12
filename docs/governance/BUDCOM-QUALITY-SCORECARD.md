# BUDCOM Quality Scorecard

**Purpose:** Give every major milestone/release a consistent quality review instead of relying only on a single PASS/FAIL label.

Score each dimension:

- 0 = unproven / unacceptable
- 1 = major gaps
- 2 = usable but weak
- 3 = acceptable
- 4 = strong
- 5 = excellent / well proven

| Dimension | Score 0–5 | Evidence / Notes |
|---|---:|---|
| Functional correctness |  |  |
| Accounting/data integrity |  |  |
| Persistence/migration safety |  |  |
| Offline/local-first behavior |  |  |
| Connectivity/recovery |  |  |
| Performance/resource efficiency |  |  |
| UX clarity |  |  |
| Error/loading/empty-state honesty |  |  |
| Security/trust/privacy |  |  |
| Automated test evidence |  |  |
| Physical validation |  |  |
| Upgrade/release readiness |  |  |

## Mandatory gates

Regardless of total score, a milestone/release cannot be considered ready if any applicable critical dimension has a blocking 0/1 in:

- accounting/data integrity;
- security/trust;
- migration/persistence;
- core functional correctness.

## Release summary

**Overall classification:** PASS / PARTIAL / FAIL / READY / NO-GO  
**Strongest evidence:**  
**Weakest dimension:**  
**Required correction before release:**  
**Accepted limitations:**  
