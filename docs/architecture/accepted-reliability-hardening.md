# Accepted Reliability and Production-Hardening Decision

**Project:** Venture Business OS — Tally Connector  
**Status:** Accepted with scope controls  
**Decision type:** Architectural reliability and production-readiness guidance

## Decision

The project accepts:

1. Capability-based Tally connection pre-flight checks.
2. Atomic SQLite synchronization with crash-safe recovery.
3. Strict and conservative XML boundary validation.
4. Privacy-safe diagnostic bundles.
5. Windows and Electron production hardening.

## Governing constraints

- These principles do not expand MVP-1.
- The MVP-1 feature freeze remains in force.
- Implementation occurs only in the milestone where the capability is required.
- Critical defects, security issues, architectural faults, and production-readiness gaps may be addressed.
- Repository milestone documents and stage-update files are the source of truth.
- External assessments must not override approved architecture or invent milestone status.
- ERP-neutral ports remain independent from Tally-specific implementation.
- The connector remains read-only toward Tally unless a later approved specification explicitly changes this.

## Interpretation notes

- Pre-flight checks use configuration and capability probing rather than fixed assumptions about process name, port, release, or XML shape.
- WAL mode can support SQLite operation but is not itself a crash-recovery design.
- XML normalization must be deterministic and must never silently alter accounting meaning.
- Diagnostics use an allowlist and exclude identifiable customer, company, voucher, tax, and financial data. **Reliability Step 3 (2026-07-24):** restored on current `main` from stash; desktop export/clipboard/summary and connector diagnostic API fields use explicit allowlist DTOs including a field-selected configuration mapper (`SafeDiagnosticConfigurationV1`); serialized absence and exact-key tests passing (21 desktop + 8 connector unit tests); on-disk logs and non-export UI remain a known partial gap. GUID-first ledger remediation at `dfb4720` is separate scope.
- Company isolation is mandatory, but this decision does not prescribe a database-per-company topology.
- Code signing is a release decision; EV signing must not block MVP progress.
- Obfuscation is not a security boundary.
- Idempotency protects local synchronization, imports, checkpoints, events, and retries; it does not imply creating ledgers in Tally.

## Traceability

Every related implementation must update the relevant stage-update file with implementation status, tests, evidence, defects, risks, limitations, unresolved observations, and production-readiness level.
