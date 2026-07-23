# Tally Ledgers Module — Stage Update (5A)

## Scope

Extended Tally ledger extraction and domain mapping for Milestone 5A.

## Changes

- `mapLedger()` enriched with GUID, AlterID, GST, mailing, contact, status
- `mapTallyLedgerToDomain()` maps raw XML nodes inside adapter boundary
- Ledger sync consumes normalized extraction output — no XML above adapter

## Parser Safety

Strict validation via existing Tally safety stack (request guard, XML validator, read gateway). Collection validation adds duplicate/parent/circular checks.

## Status

Complete for 5A ledger sync foundation.
