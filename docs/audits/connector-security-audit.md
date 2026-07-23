# Security Audit

## Threat model summary

Local-trust connector on LAN; primary threats: malicious local process, malformed Tally XML, crafted JSON migration files, DoS via API.

## Confirmed findings

| Finding | Severity | Disposition |
|---------|----------|-------------|
| Default bind host | Medium | **Resolved TD-008** — defaults to `127.0.0.1`; LAN mode requires explicit config + acknowledgement |
| LAN mode without auth | Medium | **Open TD-009** — not claimed secure; firewall/acknowledgement only |
| SQL injection | — | **Clean** — parameterized queries + sort allowlist |
| Migration path traversal | Low | **Fixed** — basename validation |
| Sync run IDOR | Low | **Fixed** — company-scoped lookup |
| Backup path leak in API | Low | **Fixed** — returns filename only |
| Desktop sync timeout/retry | Medium | **Fixed** — 600s timeout, no retry |
| Connector logs unredacted | Low | Accepted — audit XML redaction exists; general logger lacks redaction layer |
| IPC allowlist | — | **Clean** for exposed channels |

## Correctly implemented

- Tally egress XML validator, policy engine, mandatory safe-mode limits
- Read-only middleware toward Tally
- Error handler — no stack traces to clients
- Electron contextBridge + IPC allowlist
- Session gating for ledger operations

## Dependency audit

`npm audit --omit=dev`: **0 vulnerabilities**
