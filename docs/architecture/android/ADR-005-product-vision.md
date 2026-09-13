# ADR-005: Product Vision

**Series:** Android companion (`apps/venture_android`)  
**Status:** Accepted  
**Date:** 2026-07-27  
**Related:** [`PRODUCT_SOUL.md`](../../PRODUCT_SOUL.md)

## Context

Architecture without product intent drifts into CRUD shells and dashboard-only tools. VENTURE’s purpose is broader: an intelligent operational companion for ERP users. This ADR records that product vision as an architectural constraint so technical work remains aligned.

## Decision

Treat [`PRODUCT_SOUL.md`](../../PRODUCT_SOUL.md) as the strategic product reference for Android companion work. Engineering may schedule pillars through [`ROADMAP.md`](../../ROADMAP.md); it may not delete or ignore the vision because a pillar is unscheduled.

## Summary of the soul

### Intelligence-first

Think **with** the user: suggestions, remembered preferences, duplicate detection, next-action guidance, mistake identification. Do not hide irreversible behaviour behind opaque automation.

### Android-first

Prefer platform capabilities when they reduce effort: Contacts, Camera, Notifications, Files, Share Sheet, Biometrics, Background Work, Offline Storage.

### Reduce user effort

Every unnecessary tap, screen, or repeated entry is a defect. Features that do not reduce effort must be questioned before implementation.

### Business companion philosophy

- The ERP (via Connector) remains the system of record.
- VENTURE helps people use that information.
- VENTURE is not another ERP, not a Tally replacement, not reporting-only, not dashboard-only, not a CRUD application.

### Innovation pillars (strategic, not Version 1 commitments)

Contact Intelligence, Phone Book ↔ Ledger integration, Universal Search, AI Assistant, Document Intelligence, Share Sheet integration, Smart Dashboard, Smart Notifications, Offline Intelligence, Automation, Diagnostics.

## Consequences

1. Milestone designs should cite which soul pillars they advance — even partially.
2. AI agents must answer the five constitution questions in the product soul before implementing.
3. Technical cleverness that increases user effort is out of policy.
4. Roadmap order still controls sequencing; soul pillars do not authorize invented Connector APIs.
