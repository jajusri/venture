# Budcom Coding Standards

Version: 1.0  
Status: Permanent  
Priority: High  
Applies to: All Budcom repositories, modules, services, connectors, apps, scripts, tests, and documentation

---

## 1. Purpose

These standards exist to keep Budcom:

- Reliable
- Secure
- Maintainable
- Testable
- Modular
- Scalable
- Easy to understand
- Safe to change
- Ready for future technologies and ERP integrations

Code quality is not cosmetic.

Code quality directly affects:

- Customer trust
- Data safety
- Product stability
- Development speed
- Debugging time
- Long-term cost

---

## 2. Core Engineering Principles

All code must follow these principles:

1. Clarity before cleverness
2. Correctness before speed
3. Safety before convenience
4. Simplicity before abstraction
5. Explicit behaviour before hidden magic
6. Composition before inheritance
7. Small modules before large files
8. Strong contracts before assumptions
9. Fail closed before fail open
10. Tests before confidence

Do not introduce complexity unless it solves a real, current architectural problem.

---

## 3. Scope Discipline

Every task must remain within its approved scope.

Do not:

- Add unrelated features
- Refactor unrelated modules
- Change public contracts without approval
- Introduce speculative architecture
- Mix several milestones in one changeset
- Expand requirements silently
- Add temporary hacks that become permanent

When a new requirement appears during implementation:

1. Record it
2. Classify it
3. Place it in the appropriate future milestone
4. Continue the current approved scope

---

## 4. Naming Standards

Names must reveal intent.

### Files

Use lowercase kebab-case:

```text
tally-request-guard.ts
company-discovery-service.ts
xml-response-parser.ts