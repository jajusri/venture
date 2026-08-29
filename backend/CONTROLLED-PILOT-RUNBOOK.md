Controlled-Pilot Runbook — Trust + Relay

Factual, current-state operational guide for the next controlled-pilot phase. Nothing here claims a
capability that has not actually been exercised in this repository — where something is unproven,
that is stated explicitly rather than implied.

## A. Prerequisites

- Node.js 24.x (`node -v` — this package's `engines` field requires `>=24 <25`).
- A reachable PostgreSQL instance. Not bundled, not automated by this runbook — bring your own
  (local install, or any reachable instance). Nothing in this repository starts PostgreSQL for you.
- `backend/` dependencies installed: `npm install` inside `backend/`.
- Run `.\scripts\budcom-services.ps1 -Doctor` first, always. It reports exactly what is missing —
  missing database, missing dependencies, occupied ports — rather than a generic failure once you
  try to start something.

## B. Start PostgreSQL / services

PostgreSQL itself is outside this runbook's scope — start whatever local Postgres you have, then:

```
copy backend\.env.example backend\.env    # then edit BUDCOM_TRUST_DATABASE_URL for real
.\scripts\budcom-services.ps1 -Start -Verify
```

This starts Trust (`npm run dev`, port 8080 by default) and Relay (`npm run dev:relay`, port 8082 by
default — deliberately not 8080, which the Desktop/Connector's own debug build already assumes), then
hits both `/health` endpoints. Trust runs its own migrations on every boot (idempotent — safe to
restart); Relay also runs them defensively so launch order never matters.

Individually: `-StartTrust` / `-StartRelay`. The tool refuses to start a service whose configured
port is already occupied by something it didn't start itself, rather than silently binding a second
listener or killing whatever is there.

## C. Verify Trust

`.\scripts\budcom-services.ps1 -Verify` checks `GET http://<host>:<port>/health` on both services.
Trust's health check today only confirms the HTTP server itself is answering — it does not ping the
database. A Trust process can report healthy while genuinely unable to reach Postgres for anything
beyond its own boot-time migration run (which would already have crashed the process if it failed —
so a running Trust process **has** proven database connectivity at least once, at startup).

## D. Verify Relay

Same `/health` shape. Relay's own real authority verification (`PilotAuthorityVerifier`) depends on
reaching Trust's `/v1/trust/issuers/:issuerId/verification-keys` route — if Trust is down or
`BUDCOM_TRUST_BASE_URL` is misconfigured, Relay's `/health` still reports OK (it only checks its own
process), but every real submission/mailbox/ack call will fail closed (see the automated test
`backend/test/controlled-pilot-integration.test.ts`'s own "Trust unavailable" case — it fails
closed with a 500, not a graceful 503; distinguishing "Trust is down" from "credential is invalid" at
the HTTP-status level is a real, small, unimplemented refinement, not a security gap).

## E. Provision TEST integration identities

```
cd backend
npm run provision:dev -- create-business --actor actor-a --verification-id verified-a --name "Pilot Business A" --intent create-a-1
npm run provision:dev -- grant-scope --membership <membershipId-from-above> --scope manage_memberships,approve_memberships,register_devices,revoke_devices,issue_credentials,send_orders,receive_orders
npm run provision:dev -- register-device --business <businessId> --actor actor-a --membership <membershipId> --device device-a-1
npm run provision:dev -- issue-credential --business <businessId> --membership <membershipId> --device device-a-1 --device-key-path .local/device-device-a-1-key.pem --scope send_orders --intent issue-a-1 --out .local/credential-a.json
npm run provision:dev -- status --membership <membershipId> --business <businessId> --device device-a-1
```

Repeat for Business B (grant `receive_orders` instead of/in addition to `send_orders` depending on
its role). This tool:
- calls Trust's real application services in-process (no HTTP provisioning route exists anywhere in
  this backend — see `backend/services/trust/src/app.ts`, which only ever registers `GET /health`
  and the verification-keys route);
- refuses to run unless every runtime-environment signal that is actually set (`BUDCOM_RUNTIME_ENV`,
  `NODE_ENV`) explicitly normalizes to `development` or `test` — a positive allowlist, not merely
  "is not production" (hardened 2026-08-29; see section K). **An unset environment is now refused,
  not silently allowed** — export `BUDCOM_RUNTIME_ENV=development` (as `.env.example` already shows)
  before running this CLI;
- persists to a local, gitignored JSON file (`.local/trust-dev-state.json` by default) — **not**
  the same Postgres store Trust's own `main.ts` would use in a real deployment (see the final
  report's stated persistence boundary). Wiring this CLI to the real Postgres stores
  (`postgres-authority-write-store.ts`, already written and SQL-shape tested) is a small, clearly
  scoped follow-up once a real pilot Postgres is available to validate against.
- prints only non-secret identifiers and status — never private key material.

## F. Run the backend proof

```
cd backend
npx vitest run test/controlled-pilot-integration.test.ts
```

This is the actual evidence for "does the real Trust+Relay backend work end to end" — it is a
Vitest test, not a manual script, because it needs to construct two real signed identities and run
real HTTP round trips deterministically; running it IS running the proof. See that file's own doc
comment for exactly what is real (Trust's application services, real ECDSA signing/verification, a
real bound Trust HTTP server, Relay's real route/application/repository layer) versus what is an
explicit, isolated, declared boundary (Postgres persistence — this sandboxed dev environment has
none reachable; the authenticated-envelope wire format — a dev-test-only placeholder, not the real
Android<->Relay contract, which does not exist in this backend yet).

## G. What remains before real Phone A / Phone B provisioning

This is the honest gap list — see the final report's ANDROID PHONE-A/PHONE-B READINESS section for
full detail:

1. **No Trust HTTP client exists in Android at all.** Nothing in `apps/budcom_android` calls any
   `/v1/trust/...` route. Device registration and credential issuance from a real phone are
   architecturally undefined on the Android side today.
2. **Every credential/verification-key binding in Android is a hardcoded stub returning `null`**
   (`TransactionModule.kt`'s `RelayTransportModule`) — `RelayCredentialSource`,
   `CommercialTrustCredentialSource`, `IssuerVerificationKeyCache`, `AuthorityEpochCache`. The real
   submit path is a guaranteed dead end today, by design, not by accident (the module's own comment
   frames the eventual swap as "a one-line change... later").
3. **Relay base URL is build-config-only** (`BuildConfig.RELAY_DEFAULT_BASE_URL`, empty in every
   variant today) — no runtime settings screen sets it, unlike the Connector's own `feature/
   serverconfig`, which is Connector-specific and does not cover Relay.
4. **Content-version mismatch — FIXED (2026-08-29, relay-authority-repair package)**: Relay's domain
   validation now requires `commercialContentVersion == 3` (`ORDER_SNAPSHOT_CONTENT_VERSION` in
   `services/relay/src/domain/relay.ts`), matching Android's real
   `OrderVersionSnapshot.CURRENT_CONTRACT_VERSION`. v2 submissions are explicitly rejected (not
   silently accepted, not upgraded) — see `relay-domain.test.ts`'s explicit v2/v3/unknown-version
   tests and `controlled-pilot-integration.test.ts`'s end-to-end equivalents. Relay does not parse
   or reconstruct the commercial content (it remains opaque bytes-plus-metadata to Relay), so this
   fix does not require Relay to understand v3's `buyerBusinessId`/`sellerBusinessId` fields — it
   only changes which version number this gate accepts as current. No v2-to-v3 role fabrication was
   introduced; nothing on the Android side (`feature/transaction/**`) was touched.
5. Building a real Android Trust client + credential storage + a Relay-URL settings surface is
   **not** a "narrow operational wiring change" — it is new identity/credential-storage/authority-
   establishment work, which this package's own mandate says to stop at rather than build. See
   PRODUCTION PROVISIONING BLOCKER in the final report.

## H. Shutdown / recovery

```
.\scripts\budcom-services.ps1 -Stop
```

Stops only processes this tool itself started and is still tracking (matched by PID **and** the
process's own recorded start time, so a coincidentally-reused PID is never mistaken for a service
this tool launched). Never touches a process it did not start. If a service was started some other
way (e.g. directly via `npm run dev`), stop it yourself the same way you started it.

Crash recovery: both `main.ts` entrypoints handle `SIGINT`/`SIGTERM` for a clean Fastify shutdown
(closing the database pool via the `onClose` hook). There is no supervisor/auto-restart — if a
service crashes, `.\scripts\budcom-services.ps1 -Status` will show it as not running; restart with
`-StartTrust`/`-StartRelay`.

## I. Log locations

- Service stdout/stderr: `%TEMP%\budcom-services\trust.log` / `trust.log.err`, and the `relay.*`
  equivalents. Rewritten on every `-Start*` call (not appended) — copy out anything you need before
  restarting.
- Fastify's own structured request logs go to each service's stdout log above (pino JSON lines).
- PID/start-time tracking (used by `-Status`/`-Stop`, not meant for manual reading):
  `%TEMP%\budcom-services\trust.pid` / `relay.pid`.

## J. Common failure explanations

| Symptom | Likely cause | Check |
|---|---|---|
| `BUDCOM_TRUST_DATABASE_URL is required` (Trust exits immediately) | Env var not set | `-Doctor` |
| Trust/Relay exits within ~1-2s of `-Start*` | Database unreachable, or malformed connection string | The printed `--- last error output ---` tail, or the full `.log.err` file |
| `-StartTrust`/`-StartRelay` refuses immediately with "port already in use" | Something else (often BUDCOM Desktop's own bundled server on 8080) already owns that port | `-Doctor`'s port section; change `BUDCOM_TRUST_PORT`/`BUDCOM_RELAY_PORT` |
| Relay accepts everything unconditionally / rejects everything unconditionally | Relay was launched with a verifier other than `PilotAuthorityVerifier` (only `main.ts`'s real wiring uses it — a script that calls `buildRelayService` directly with hand-built fakes, as the pre-existing tests do, will behave however those fakes say) | Confirm you started Relay via `npm run dev:relay` / `-StartRelay`, not a bespoke script |
| Credential rejected with no obvious reason | Membership scope doesn't include the capability being exercised (`send_orders` to submit, `receive_orders` to fetch/ack) -- `CreateBusiness`'s own default grant does **not** include `receive_orders` | `provision:dev status`, then `grant-scope` if needed |
| `dev-provision.ts` refuses to run | `BUDCOM_RUNTIME_ENV`/`NODE_ENV` is `production`, **or neither is set at all** | This is intentional (dev-only guard, fail-closed positive allowlist — section K); explicitly `export BUDCOM_RUNTIME_ENV=development`, don't just unset it |
| Fetch/Ack return 400 `authenticatedRequest is required` | Caller sent only plaintext identifiers (`recipientBusinessId`/`recipientActorId`/`recipientDeviceId`) with no signed possession proof | This is intentional (section K) — every Fetch/Ack call must include a base64 `authenticatedRequest` built per `devtools/pilot-envelope.ts`'s `buildAuthenticatedRelayRequest` |
| Fetch/Ack return 403 despite a credential that "looks" valid | The credential's claims no longer match CURRENT Trust authority (membership/device authority epoch advanced, device rotated/revoked since issuance) even though the credential's own signature and expiry are still fine | Re-issue a fresh credential against current Trust state (`provision:dev issue-credential`) rather than reusing an old one |

## K. Security contract (relay-authority-repair, 2026-08-29)

An independent audit (Codex) of the previous `fcbc04c` state found two authority-bypass defects in
Relay's mailbox Fetch and Acknowledgement endpoints. Both are fixed; this section states the
resulting contract plainly so it doesn't quietly regress.

**Identifiers are not authority.** `businessId`/`actorId`/`deviceId`/`membershipId` in a request body
are claims, not proof. The previous `checkBearerAuthority()` granted Fetch/Ack authority from these
plaintext identifiers alone (an active DB row matching the claimed identity was enough) — deleted.
Every Fetch and Acknowledgement call now requires **both**:
1. A Trust-issued, currently-valid credential (signed by Trust's issuer key, unexpired, and —
   critically — whose claims still match CURRENT Trust authority state for that business/membership/
   device: actor, membership, device key id/version/fingerprint, and authority epoch all checked
   field-by-field, not just "the live rows still look active"). An older credential does not regain
   validity just because current rows later return to a superficially compatible state.
2. A device signature (ECDSA P-256/SHA-256) proving possession of that credential's registered
   device private key, over a canonical payload binding protocol version, action
   (`mailbox_fetch`/`acknowledge`), business/actor/membership/device/device-key identity, a request
   id, a timestamp, and the operation's own target and parameters (mailbox id + cursor + limit for
   Fetch; envelope id + receivedAt for Ack). Changing any one of those fields after signing
   invalidates the request. **The device's private key never leaves the device** — only its public
   key and fingerprint are ever registered with Trust.

Presenting identifiers with no `authenticatedRequest` returns 400. Presenting a well-formed,
validly-signed request for the wrong business/actor/device, or with a stale/rotated/revoked
credential, returns 403 — see `test/controlled-pilot-integration.test.ts`'s adversarial cases for the
exact matrix (identifier-only, wrong recipient, tampered signature, wrong signing key, expired
credential, revoked device, stale authority epoch, device key rotation).

**The CURRENT device key is the highest active-status key version, never whichever version a
presented credential happens to claim** (round 2, 2026-08-29 — Codex re-certification BLOCKER 1). A
device identity `(businessId, deviceId)` can have more than one registered key-version row (e.g.
after a legitimate key rotation); historical rows below the highest active version remain stored for
audit/history but are never authoritative once a higher version exists — this reuses the exact
`ORDER BY device_key_version DESC LIMIT 1 WHERE status = 'active'` selection rule
`AuthorityRepository.findActiveDevice()` already used elsewhere in Trust, rather than inventing a
second definition of "current". A credential still bound to a superseded version fails once a newer
version becomes current, even with a valid signature, unexpired lifetime, and the old row still
physically active — see the `controlled-pilot-integration.test.ts` device-key-rotation sequence
(T0 pre-rotation credential succeeds → T1 new key registered → T2 old credential fails, including with
the NEW key's own private key → T3 a fresh credential for the new key succeeds).

**A concurrent duplicate write is idempotent only when the winner is actually equivalent to what this
call attempted** (round 2, 2026-08-29 — Codex re-certification BLOCKERS 2 and 3). Two concurrent
identical device registrations, or two concurrent identical business-creation calls for the same
intent, safely converge on one stored row and both callers see the same result. But if a race lands a
*different* registration or a *different* initial membership at the same key (conflicting actor,
membership, device key id, fingerprint, public key, status, or scope), that now fails closed with a
deterministic domain error (`Conflicting device key registration` / `Conflicting business creation`)
instead of the previous behavior of silently returning whichever row won as if it were this caller's
own. See `test/postgres-authority-write-store.test.ts`'s lettered test matrices for both stores.

**Replay protection is separate from acknowledgement idempotency.** A valid signed request cannot
become reusable bearer authority: each `(businessId, deviceId, requestId)` is consumed exactly once
within a bounded clock-tolerance window (`RelayReplayGuard`, in-memory locally / Postgres-backed in
`main.ts`'s real wiring, migration v6 `relay_authenticated_request_nonce` — additive, non-destructive,
indexed by expiry for bounded cleanup). This is deliberately independent from
`RecordRelayAcknowledgement`'s own business-level idempotency (retrying an acknowledgement for the
same envelope returns the same result without double-recording delivery) — a legitimate retry mints a
fresh request id/nonce per transmission; only a literal replay of the exact same signed bytes is
rejected. Expired nonce rows are cleaned up both per-device (on that device's own next request) and,
as of round 2 (2026-08-29 — Codex's small replay-cleanup finding), via a small bounded/indexed global
sweep on every request, so a device that goes permanently inactive doesn't leave its expired rows in
the table forever without needing a separate scheduler.

**Current commercial content version is v3** (see section G item 4) — Relay treats the content itself
as opaque bytes, so this is a version-number gate only, not role-handling logic.

**Dev provisioning is non-production only**, enforced by a positive allowlist that fails closed on an
unset or ambiguous environment (see section E and the `dev-provision.ts` row in section J) — not
merely "refuses when it happens to see the literal word production."

**What this section does not claim:** LIVE POSTGRES PROOF: NOT PROVEN. No live PostgreSQL was
reachable in this sandboxed development environment (round 1 or round 2), so the Postgres-backed
replay guard, authority-snapshot reader, and write stores (including this round's current-device-key
selection and concurrent-winner equivalence checks) are all SQL-shape/real-repository-semantics
tested against stateful fakes that model real INSERT/SELECT/DELETE/ON CONFLICT behavior, not proven
against a real database. No physical Android device has exercised any of this (Android has no Trust
HTTP client at all yet — see section G items 1-2). `test/controlled-pilot-integration.test.ts` is real
Trust + real Relay + real ECDSA signing + real HTTP round trips end to end, against file-backed/
in-memory persistence standing in for Postgres.
