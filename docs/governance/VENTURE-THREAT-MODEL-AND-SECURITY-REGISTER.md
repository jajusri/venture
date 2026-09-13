# VENTURE Threat Model and Security Register

**Status:** Initial locked framework; update continuously with evidence  
**Purpose:** Define VENTURE assets, trust boundaries, attack surfaces, security assumptions, threats, mitigations, residual risks, and verification evidence.

## 1. Security principle

Security decisions must be evidence-driven and proportional to risk. VENTURE must fail closed where trust cannot be established, preserve user control over consequential operations, and avoid silently weakening security for convenience.

## 2. Critical assets

At minimum protect:

- Tally-synchronized accounting data
- Ledger/Voucher/Stock data
- VENTURE local databases
- Connector identity
- TLS/private key material
- trusted-device credentials
- pairing/session credentials
- company selection and trust state
- removable/private-storage business payloads
- future Vartalap messages and attachments
- Business Profile identity data
- exported XML/PDF documents
- release artifacts and provenance

## 3. Trust boundaries

### Windows Desktop / Connector
Trusted local application boundary.

### Tally
Authoritative accounting system. VENTURE reads approved data and does not directly mutate Tally under current governance.

### Android
Trusted paired client after secure identity verification.

### Local network
Transport medium, not inherently trusted. Identity must not depend on IP address alone.

### Removable storage
Physical-location boundary, not an encryption guarantee.

### Future cloud/AI providers
External trust boundary. No provider becomes authoritative for core accounting/business state.

## 4. Threat categories

Track at minimum:

- unauthorized LAN client
- stolen/lost Android device
- copied Connector identity
- replayed/forged pairing attempt
- endpoint impersonation
- stale/changed DHCP IP
- malicious or accidental local process access
- database theft
- removable-drive loss
- wrong/lookalike USB
- hot-removal corruption
- downgrade/rollback vulnerability
- installer tampering
- dependency/supply-chain compromise
- logs leaking business data
- diagnostics leaking secrets
- insecure exports
- future AI data exfiltration
- privilege escalation
- malformed Tally/XML input
- denial of service / repeated requests

## 5. Security register

| ID | Threat | Asset | Likelihood | Impact | Existing Mitigation | Evidence | Residual Risk | Status |
|---|---|---|---|---|---|---|---|---|
| SEC-001 | Wrong Connector endpoint / IP reuse | Android trust | Medium | High | Connector identity + fingerprint/trust continuity | Physical reconnect tests | DHCP change still needs deferred physical exercise | Open evidence |
| SEC-002 | Unauthorized device pairing | Connector data | Medium | High | one-time sessions, device credentials, bounded attempts, trusted identity | Unit/integration tests | Physical abuse test later | Mitigated / verify |
| SEC-003 | Lost removable storage | Business DB | Medium | High | user-controlled physical location, vault identity checks | Code/tests | DB plaintext on removable media | Accepted MVP limitation |
| SEC-004 | Wrong USB / lookalike media | Business DB | Medium | High | stable vaultId marker, fail-closed behavior | Automated tests | duplicate copied vault markers not fully resolved | Accepted limitation |
| SEC-005 | Sensitive log leakage | Business payload | Low | High | structured/sanitized logging | audit performed | future features may add new log paths | Monitor |
| SEC-006 | Direct unauthorized Tally mutation | Accounting truth | Low | Critical | current architecture is read + user-initiated XML import only | architecture/code review | future scope changes require explicit approval | Locked |
| SEC-007 | Release artifact tampering | Installed app | Low | High | SHA-256, signer/provenance checks, clean build worktree | release pipeline | code signing maturity to improve for broader release | Open |
| SEC-008 | AI provider misuse of business data | Business/privacy | Future | High | AI not dependency; provider abstraction planned | architecture rule | requires future privacy design before AI integration | Deferred |

## 6. Required security review triggers

Re-run threat review when adding:

- cloud sync
- public internet access
- messaging
- remote notifications
- new authentication model
- new pairing mechanism
- direct write-back capability
- external AI
- public profiles/catalogues
- multi-company/multi-user authorization
- encryption/key-management changes
- new installer privilege
- external APIs/webhooks

## 7. Security acceptance requirements

No release should claim security readiness unless applicable evidence includes:

- trust/pairing continuity
- fail-closed behavior
- credential protection
- company isolation
- no sensitive log leakage
- safe migration/upgrade
- dependency audit
- artifact provenance
- physical abuse-path testing where relevant

## 8. Residual-risk rule

Do not hide residual risk behind phrases like “secure” or “private.” State the exact protection and exact limitation.

**LIVING SECURITY REGISTER — EVIDENCE OVER ASSUMPTION.**
