# VENTURE Backup, Recovery and Disaster Recovery Specification

**Status:** Required production-governance framework  
**Purpose:** Define how VENTURE protects continuity when devices, disks, databases, removable media, installations, or operating systems fail.

## 1. Recovery principle

The product must have a known recovery path before broad deployment. Recovery must preserve accounting/business data and trust state wherever possible and must not rely on improvised reinstall/reset procedures.

## 2. Failure scenarios to design for

- Windows laptop failure
- SSD/HDD failure
- accidental VENTURE uninstall
- Windows reinstall
- corrupted SQLite database
- incomplete WAL state
- accidental data deletion
- lost private-storage USB
- damaged private-storage USB
- wrong USB inserted
- Connector identity loss
- Android phone replacement
- Android app corruption
- failed Room migration
- interrupted installer upgrade
- interrupted historical sync
- user restores an older backup
- Tally company restored from backup with changed internal state

## 3. Data classes

Classify recovery requirements separately for:

### Reconstructible from Tally
- synced ledgers
- vouchers
- stock items
- other accounting data where full authoritative history remains available

### VENTURE-originated and not trivially reconstructible
- enriched contact data
- tags
- prospects
- notes
- Dincharya actions
- relationship history
- catalogue drafts
- published metadata
- future Vartalap messages
- local settings not stored elsewhere

### Security/trust state
- Connector identity
- device credentials
- trusted fingerprints
- revocation state
- pairing continuity

These classes may require different backup policies.

## 4. Minimum backup strategy

Before wider release define:

- what is backed up;
- where it is backed up;
- frequency;
- retention;
- encryption status;
- integrity verification;
- user-visible recovery workflow;
- restore authorization;
- schema compatibility.

## 5. Private removable storage

Private Storage must have an explicit backup story.

Current MVP fact:
- physical-location control is provided;
- business DB remains plaintext on the removable medium;
- hot-removal mid-write atomicity is not fully guaranteed;
- Standard→Private migration is not yet implemented.

Future recovery design should include safe copy/verify/restore procedures and clear handling of lost vault identity.

## 6. Recovery objectives

Define later from measured product needs:

- **RPO (Recovery Point Objective):** maximum acceptable data loss.
- **RTO (Recovery Time Objective):** maximum acceptable recovery time.

Do not invent aggressive targets before product usage is measured.

## 7. Restore validation

A successful restore must prove, as applicable:

- database opens;
- schema version accepted;
- company identity correct;
- counts reconcile;
- known Voucher/Ledger opens;
- VENTURE-originated data survives;
- pairing/trust is either preserved or safely re-established;
- no duplicate data after subsequent sync;
- stale backup does not overwrite newer authoritative state silently.

## 8. Release requirement

Before broad release, maintain:

- current backup procedure;
- restore procedure;
- test evidence;
- known limitations;
- rollback compatibility statement;
- last successful recovery drill date.

## 9. Disaster recovery drill

Periodically perform a controlled drill:

1. capture known baseline;
2. create verified backup;
3. simulate loss in isolated environment;
4. restore;
5. verify data/trust;
6. sync again;
7. confirm no duplication/corruption;
8. document elapsed recovery time and defects.

**NO BROAD PRODUCTION CLAIM WITHOUT A TESTED RESTORE PATH.**
