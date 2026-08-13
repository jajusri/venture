# BUDCOM Post-MVP-1 Development Modus Operandi

**Status:** LOCKED project governance\
**Applies from:** MVP-1.1 onward\
**Product Owner:** User\
**Architectural authority / final technical approver:** ChatGPT\
**Primary implementation engine:** Claude with maximum practical
autonomy inside approved boundaries

## 1. Purpose

This document defines the permanent BUDCOM development method after
MVP-1. The objective is to maximize accepted, hardened product outcome
per unit of time, human involvement, and Claude usage while preventing
vague implementation, repetitive debugging loops, context waste,
architectural drift, and uncontrolled scope expansion.

## 2. Permanent roles

### Product Owner

The user's primary work is feature/product planning, business
priorities, Brainstorm 1/2 decisions, visual/product acceptance, and
consequential approvals---not implementation babysitting.

### ChatGPT

ChatGPT remains architectural authority, technical planner, scope/risk
governor, Claude workflow designer, prompt optimizer,
acceptance/hardening planner, and final technical approver. Every Claude
plan must account for context/usage limits, five-hour windows, weekly
limits, connectors/MCP, task size, risk, loop prevention, expected
outcome, test reserve, and approval boundaries.

### Claude

Within approved scope Claude may autonomously inspect the repo,
implement, run tests/builds/linters/audits, diagnose and repair within
scope, mini-harden, update status/docs, prepare commits, and produce
evidence-based reports. Claude is not the architectural authority.

## 3. Mandatory development cycle

### Brainstorm 1 --- User + ChatGPT

Before implementation define the problem, desired final outcome,
UX/workflow, data authority, online/offline behavior, persistence, edge
cases, invariants, scope, exclusions, acceptance criteria, risks, and
physical/visual evidence.

### Reverse-engineer from the outcome

ChatGPT plans backward:

**Final outcome → acceptance evidence → invariants → architecture/data
needs → implementation surface → tests → mini-hardening → stop
conditions.**

### Claude clarification gate

Claude first asks a bounded set of consequential unresolved questions.
It must not re-ask locked matters, ask trivial questions it can safely
resolve, or begin substantive implementation while material ambiguity
remains.

### Brainstorm 2 --- User + ChatGPT

Resolve Claude's meaningful questions and issue a clarified execution
brief.

### Autonomous execution

Claude then inspects, establishes baseline, implements, tests,
diagnoses, repairs within scope, mini-hardens, reruns regression gates,
prepares commits/evidence, reports, and stops at approval boundaries.

### Product/technical review

The user reviews product outcome. ChatGPT reviews technical evidence and
approves architecture/quality.

## 4. Claude automation before MVP-1.1

Before substantive MVP-1.1 feature work, configure and validate:

-   VS Code Claude integration;
-   appropriate connectors/MCP;
-   repository/context access;
-   persistent project instructions;
-   permission strategy;
-   approval boundaries;
-   notifications/popups for genuine permission needs;
-   task-completion notification;
-   autonomous build/test/lint workflow;
-   bounded debugging;
-   documentation/status and commit/report conventions.

Validate the automation on a small controlled task first.

## 5. Explicit approval gates

Claude must stop for approval before consequential architecture/scope
changes, security/trust-boundary changes, authentication/pairing
changes, destructive data/migration actions, irreversible operations,
release/version cuts, production installs/deployments, remote pushes
where approval is required, or actions that could break proven
continuity.

## 6. MVP-1.0.x boundary

MVP-1.0.x is only for defects, essential missed MVP-1 behavior, and
bounded high-value micro-polish such as screen optimization, layout,
colors, button behavior, interaction clarity, performance, and
regressions. No new feature/module expansion. Genuine post-MVP features
begin at MVP-1.1.

## 7. Five-hour-session optimization

Treat Claude windows as planned development blocks. Complete Brainstorm
1, outcome definition, architecture decisions, acceptance criteria,
exclusions, and prompt preparation before consuming scarce Claude
capacity wherever practical.

Size work deliberately as sub-session, one-session,
multi-session/high-risk, mini-hardening, or integrated-hardening/release
work. Preserve capacity for validation instead of spending the entire
allowance on implementation.

## 8. Usage-efficiency rules

Prefer coherent batching, persistent/reusable context, selective
connectors/MCP, evidence-driven debugging, bounded investigations,
explicit stop conditions, and regression tests that prove the exact
failure.

Avoid repeated repo rediscovery, vague exploratory coding, unnecessary
micro-prompts, unlimited debugging loops, duplicate reviews without
distinct purpose, unnecessary model escalation, multiple agents
editing the same uncommitted work, and spending premium Claude capacity on
open-ended visual/UI exploration. Resolve visual direction and screen
iteration through ChatGPT and the external design archive first
(`docs/design/BUDCOM-UI-DESIGN-DECISIONS.md`); Claude implements the
accepted presentation layer, it does not generate/iterate the design.

## 9. Empirical measurement

For the first 2--3 successful automated milestones, and major milestones
thereafter where useful, record:

-   milestone/prompt and complexity class;
-   model;
-   start/end and wall-clock time;
-   Claude turns;
-   session allowance before/after when visible;
-   weekly allowance before/after when visible;
-   time-to-limit and reset waiting;
-   retries/loops and model switches;
-   connectors/MCP/tools;
-   user interventions;
-   build/test time;
-   mini-hardening time;
-   autonomous completion;
-   final accepted outcome;
-   paid overflow/API cost.

Capture `/usage` or equivalent at useful boundaries where available.

Key derived metrics: accepted work per weekly allowance, accepted work
per productive session, user time per accepted milestone, retry
overhead, and cost per accepted hardened milestone.

## 10. Pro / Max planning

Working planning baseline, subject to current official-plan
revalidation:

-   Pro = 1x baseline capacity;
-   Max 5x = applicable 5x usage-capacity tier;
-   Max 20x = applicable 20x usage-capacity tier.

**Capacity is not speed.** Never assume 5x capacity means 5x faster
development or 20x means 20x faster.

Separate productive execution, session-limit waiting, weekly-limit
waiting, user intervention, debugging/retries, test/build time,
mini-hardening, integrated hardening, and release work.

## 11. Cost-versus-time model

After representative real BUDCOM runs compare Pro, Max 5x, Max 20x,
paid-overflow/hybrid options, and justified single-/multi-agent
workflows using the same scope and quality bar.

Primary measures:

1.  **Total time to accepted outcome** --- including limit waits,
    retries, user intervention, testing and hardening.
2.  **Total monetary cost** --- subscription plus
    API/overflow/tooling/agent costs.

Core question: **How much additional money saves how much real BUDCOM
development time?**

Max 5x is the likely practical ceiling initially; higher capacity must
earn its place through measured ROI.

## 12. Multi-agent rule

Use multiple agents only when work can be cleanly separated and
parallelism materially reduces accepted-delivery time. Good candidates
include implementation vs independent adversarial review or
non-overlapping modules. Avoid same-file concurrent editing, duplicate
investigation, and execution agents independently changing architecture.

## 13. Hardening/release rhythm

**Feature → mini-hardening → next feature → mini-hardening → coherent
batch → integrated hardening → real-world validation → release.**

Evidence before expansion.

## 14. Two permanent planning documents

This file governs **how BUDCOM is developed**.

After 2--3 successful automated milestones, calibrate and maintain the
separate `BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md`, which governs **what
is built, in what sequence, and how Claude capacity/time/cost are
allocated**.

## 15. Success condition

This modus operandi is succeeding when requirements are clear before
coding, Claude rarely follows vague routes, routine implementation is
autonomous, debugging loops are bounded, project context is reused
efficiently, mini-hardening is normal, releases have evidence, limits
are planned rather than discovered mid-task, upgrades are justified by
ROI, and the Product Owner spends substantially more time deciding what
BUDCOM should become than supervising implementation.

## 16. Correct path by design

Repository structure, APIs, schemas, helpers, tests, release scripts,
prompts, and governance should naturally guide future developers and AI
agents toward the approved architecture. If doing the wrong thing is
easier than doing the right thing, improve the system rather than
relying on discipline alone to compensate.

**LOCKED.**
