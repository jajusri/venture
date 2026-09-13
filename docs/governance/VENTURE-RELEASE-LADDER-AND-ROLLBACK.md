# VENTURE Release Ladder and Rollback Discipline

**Status:** Permanent release governance

## Release ladder

1. Development milestone
2. Mini-hardened milestone
3. Integrated candidate
4. Physical acceptance candidate
5. Controlled Pilot
6. Pilot hardening patch if required
7. Limited Release
8. Wider release only after evidence

## Every candidate must record

- exact git HEAD
- version name/code
- artifact filename
- SHA-256
- byte size
- signing/provenance
- schema version
- build mode
- source tree clean-at-start
- known-good previous candidate
- known limitations
- required migration path
- rollback considerations

## Rollback rules

Never improvise rollback.

Before release, know:

- whether app binary can be downgraded safely;
- whether Room/SQLite schema is backward compatible;
- whether an older Connector can read newer data;
- whether identity/trust state survives;
- whether user data requires backup/export;
- whether uninstall would destroy required state.

If downgrade is unsafe, rollback means:

- stop rollout;
- preserve data;
- fix forward;
- issue a replacement candidate.

## Upgrade acceptance

For important upgrades verify:

- in-place update;
- no uninstall;
- no data clear;
- company selection preserved;
- pairing/trust preserved;
- local cache preserved;
- migrations non-destructive;
- known cached records survive;
- old and new core workflows function.

## Release stop conditions

Stop rollout for:

- data loss/corruption;
- accounting correctness defect;
- security/trust regression;
- migration failure;
- unrecoverable startup/connectivity defect;
- silent storage fallback;
- widespread crash/blocker.

## Evidence over confidence

A release is not safe because automated tests are green alone. Required physical acceptance must also be completed where applicable.
