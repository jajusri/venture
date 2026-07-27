# Product Soul

> "We are not building another ERP mobile app.
> We are building the smartest companion for businesses that use an ERP."

**Status:** Authoritative product manifesto  
**Working name:** BUDCO

BUDCO is **not** merely an ERP mobile client.

It is an intelligent Android business companion that removes friction between people, phone capabilities, business workflows, the BudCom Connector, and the ERP system of record.

Engineering rules: [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md)  
Milestones: [ROADMAP.md](ROADMAP.md)  
Decisions: [DECISIONS.md](DECISIONS.md)

---

## Why BUDCO exists

Businesses already have ERP systems. What they often lack is a fast, intelligent, mobile-first experience.

Users should not think about databases, ledgers, vouchers, or synchronization. They should get work done.

BUDCO removes friction between people and business systems. Every feature must reduce effort. If it does not, question it before implementation.

---

## Mission

Build the most intelligent and reliable Android companion for ERP users — not the largest, not the most complicated: the most useful.

---

## Long-term vision

BUDCO becomes the application business users open first. The ERP remains the system of record in the background. The ERP stores information; BUDCO helps people use it.

---

## Product philosophy

### Simplicity
Simple beats clever. One tap beats five. Automatic beats manual. Prediction beats repeated entry.

### Reliability and trust
Accounting software must earn trust. Actions must be predictable, traceable, explainable, and recoverable. Never surprise the user.

### Intelligence-first design
Think **with** the user, not for the user: suggest likely ledgers, remember preferences, detect duplicate work, recommend next actions, identify common mistakes.

### Android-first design
Use Contacts, Camera, Notifications, Files, Share Sheet, Biometrics, Background Work, and Offline Storage when they reduce effort.

### Respect for user time
Every unnecessary tap, screen, or repeated entry is a bug.

### Offline-aware behavior
Offline must never mean useless: show cached data, mark stale information, explain unavailable actions, preserve safe work, retry intelligently.

### Traceable and recoverable actions
Users and support engineers must understand what happened and how to recover.

---

## Innovation pillars

Strategic goals — intentionally separate from Version 1 milestones. Never delete them.

### 1. Phone Book ↔ Ledger intelligence
- Match Android contacts with ERP ledgers
- Suggest creating ledgers for unmatched contacts
- Search contacts and ledgers together
- Detect likely duplicates
- Open related ledger information from contact-oriented workflows
- Future caller-context capabilities, subject to Android permissions and platform restrictions

### 2. Universal Search
Search Companies, Ledgers, Customers, Suppliers, Stock Items, Vouchers, Contacts, and Documents.

### 3. AI Assistant
Natural-language search, plain-language error explanations, suggested ledger and voucher matches, guided business workflows.

### 4. Document Intelligence
Camera capture for bills, receipts, invoices, and supporting documents; future OCR, extraction, duplicate detection, and entity suggestions.

### 5. Android Share integration
Receive PDFs, images, text, and files; share invoices, vouchers, statements, and documents through Android-supported workflows.

### 6. Smart Dashboard
Operational state, sync state, pending work, failures, and useful business signals.

### 7. Smart Notifications
Connector unavailable, sync completed or failed, action required — never spam the user.

### 8. Offline Intelligence
Show cached data, clearly mark stale information, explain unavailable actions, preserve safe work, retry intelligently.

### 9. Automation
Background refresh, scheduled sync, smart retry, validation, cache maintenance.

### 10. Diagnostics
A support engineer should understand app condition within one minute: Health, Readiness, Session, Versions, API history, Network state, Exportable diagnostics.

---

## What BUDCO is not

Not another ERP, not a Tally replacement, not reporting-only, not dashboard-only, not a CRUD application. It is an intelligent operational companion.

---

## Permanent feature test

Before implementing any feature, answer:

1. Does it reduce user effort?
2. Can Android already provide relevant context?
3. Can the Connector already provide the data?
4. Can the workflow become smarter without becoming confusing?
5. Does it move BUDCO toward being an intelligent business companion?

If the answer is no, reconsider.

---

## Final standard

People should eventually say:

> "It just knows what I need."
