# VENTURE Claude Prompt Processing Rules

**Status:** LOCKED operating rules  
**Applies from:** MVP-1.1 onward  
**Purpose:** Define exactly how Claude must process every substantial VENTURE prompt before, during, and after execution.  
**Governance:** This file operates under `POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md`.

---

## 1. Core Rule

Claude must not treat a VENTURE prompt as a simple instruction to start coding immediately.

Every substantial prompt must be processed through a controlled sequence:

**Understand → Inspect → Clarify → Confirm Scope → Execute → Checkpoint if Needed → Test → Mini-Harden → Report → Stop**

The purpose is to reduce vague routes, wasted context, debugging loops, accidental scope expansion, and half-completed work being lost.

---

## 2. Step 1 — Parse the Prompt Before Acting

Claude must first identify:

- requested product outcome;
- explicit scope;
- explicit exclusions;
- acceptance criteria;
- architectural constraints;
- security/trust constraints;
- persistence/sync requirements;
- physical-test requirements;
- likely affected modules/layers;
- forbidden actions;
- stop conditions;
- whether the task is implementation, diagnosis, test-only, build-only, physical validation, release, or documentation work.

Do not begin source modification until task class and boundaries are clear.

---

## 3. Step 2 — Inspect Existing Project State

Before implementation, inspect only what is needed to answer:

- What already exists?
- What can be reused?
- What is already proven?
- Which architecture governs this area?
- Which tests/contracts already cover it?
- Which status/report files matter?
- Is the requested behavior already implemented?
- Are there dirty/uncommitted files that must be preserved?
- Is current HEAD/build/provenance relevant?

Prefer targeted inspection over repeated whole-repo rediscovery.

Use connectors/MCP/persistent project context where available instead of asking the user to paste information Claude can already access.

---

## 4. Step 3 — Initial Clarification Gate

For major prompts, Claude must ask a short set of high-value questions **before substantive coding** if any material ambiguity remains.

Questions should be limited to matters that can materially alter product outcome or architecture, such as:

- UX behavior;
- authoritative data source;
- online/offline semantics;
- persistence;
- sync;
- migration behavior;
- edge cases;
- acceptance criteria;
- security/trust implications;
- scope boundary.

Claude must not:

- ask what is already explicitly locked;
- re-open settled architecture without evidence;
- ask trivial implementation preferences;
- ask broad generic questions;
- use clarification as an excuse to avoid execution.

If no consequential ambiguity exists, Claude should state that clarification is not required and proceed.

---

## 5. Step 4 — Brainstorm 2 When Needed

If Claude asks material clarification questions, it must wait for the user/ChatGPT response before substantive implementation.

After clarification, Claude should restate the final execution boundary concisely and proceed.

Do not continue on assumptions when the answer could materially change the result.

### Complete Clarification Gate

When Claude asks multiple consequential clarification questions, the clarification gate remains OPEN until EVERY material question has been answered or explicitly resolved.

If the user answers only some of the questions (e.g. only Q1 of Q1/Q2/Q3, or Q1 and Q3 but not Q2):

- record and preserve every answered question;
- keep every unanswered question explicitly unresolved;
- ask ONLY the unresolved question(s) again;
- do not begin substantive implementation.

None of the following count as resolving an unanswered consequential question:

- silence;
- omission;
- an incomplete response;
- "okay";
- "yes" where it does not unambiguously answer the specific question;
- answering only another question in the set;
- failure to mention the question;
- continuation of conversation.

A consequential question is CLOSED only when:

1. the user explicitly answers it; or
2. the user explicitly delegates that decision to Claude; or
3. the user explicitly authorizes Claude's stated recommendation/default; or
4. new repository/evidence makes the question objectively irrelevant — Claude must explicitly state why it became irrelevant.

Before substantive implementation begins after a clarification round, Claude must account for the full clarification set:

- if every material question is resolved, state: **"Clarification gate: CLOSED — all material questions resolved."**
- if any remain unresolved, state: **"Clarification gate: OPEN — unresolved questions: Qx, Qy."** and ask only those questions.

Do not repeatedly ask an already-resolved question unless new evidence materially invalidates the earlier answer.

**Asking the question is not enough — the material answer must actually be received or explicitly resolved.**

---

## 6. Step 5 — Establish a Baseline

Before changing code, establish the relevant baseline:

- current HEAD;
- git status;
- relevant tests;
- current version/schema;
- artifact/build identity where relevant;
- current known failure evidence;
- current API/contract;
- current data/storage state where relevant.

For bugs, identify the earliest proven failing boundary before changing code whenever practical.

For release/build tasks, use a clean isolated worktree where instructed.

---

## 7. Step 6 — Prefer the Minimum Correct Change

Implement the smallest change that reaches the approved outcome **without weakening long-term architecture**.

Rules:

- reuse proven architecture;
- avoid parallel systems;
- avoid duplicated business logic;
- do not invent new network paths where local-first architecture already exists;
- do not infer accounting/security data that is not authoritative;
- do not silently broaden scope;
- do not redesign unrelated modules while fixing a narrow defect.

If the minimum fix would violate an architectural invariant, stop and report instead of bypassing the invariant.

---

## 8. Step 7 — Execute With Maximum Practical Autonomy

Within approved scope Claude may autonomously:

- modify source;
- add/update tests;
- run builds;
- run linters/type checks;
- run audits;
- diagnose failures;
- repair defects caused by its changes;
- rerun relevant tests;
- update status/docs;
- prepare coherent commits.

Claude should not ask permission for routine actions already authorized by the prompt.

Human/ChatGPT approval remains required for:

- architecture changes;
- product-scope expansion;
- security/trust-boundary changes;
- destructive data operations;
- destructive migrations;
- release/deployment/install decisions;
- remote pushes where approval is required;
- other explicitly restricted actions.

---

## 9. Step 8 — Mid-Execution Clarification & Recovery Protocol

Claude is allowed to ask consequential questions **during execution** if new evidence creates ambiguity that could not reasonably have been resolved at the start.

Claude must not continue down a vague route merely because work has already begun.

### Trigger conditions

Use mid-execution clarification when:

- new evidence exposes a product/UX ambiguity;
- architecture/data authority is unclear;
- two plausible implementation paths have materially different consequences;
- a new security/trust issue appears;
- acceptance criteria become contradictory;
- the current route requires scope expansion;
- a second evidence-based repair attempt at the same boundary has failed;
- Claude is genuinely stuck and further work would become speculative.

### Required checkpoint behavior

Before asking:

1. finish only the current safe/atomic operation;
2. preserve all valid completed work;
3. do not revert good work merely because clarification is needed;
4. do not continue speculative edits;
5. capture a concise checkpoint containing:
   - original objective;
   - work completed;
   - files changed;
   - tests passed/failed;
   - current git state;
   - exact newly discovered ambiguity/blocker;
   - alternatives, if known;
   - Claude's recommended option and why;
   - exact question requiring a decision;
   - precise resume point.

### Checkpoint commit rule

If partial work is coherent, safe, and the governing prompt permits commits, Claude may create a clearly named checkpoint commit.

If commits are not authorized, leave the worktree intact and report its exact state.

Never create a misleading "final" commit for incomplete work.

### Resume rule

After the user/ChatGPT answers:

- resume from the checkpoint;
- do not restart the entire prompt;
- do not rediscover already-inspected context unnecessarily;
- revalidate only the affected boundary;
- continue the original execution plan.

This protocol exists specifically to prevent loss of a half-processed prompt and unnecessary Claude-usage waste.

### Complete Clarification Gate applies to checkpoints too

If a checkpoint asks multiple consequential questions and the user answers only some of them, the Step 4 Complete Clarification Gate governs here as well:

- preserve all valid completed work;
- preserve every answered decision;
- keep every unanswered decision explicitly unresolved;
- re-ask only the unresolved question(s);
- do not cross the consequential decision boundary those questions gate until they are resolved.

Safe independent work that cannot prejudice any unresolved decision may continue only if the existing rules in this document already permit it (see Step 7). Do not treat "safe independent work" as a loophole to implement around an unanswered consequential question.

See Step 4's Complete Clarification Gate for the full OPEN/CLOSED protocol and question-closure criteria — the same rule governs both the initial gate and every mid-execution checkpoint.

---

## 10. Step 9 — Loop Prevention

When something fails:

1. capture exact evidence;
2. identify earliest failing boundary;
3. distinguish product defect from environment/tooling artifact;
4. form one bounded hypothesis;
5. test it;
6. make one evidence-based correction;
7. rerun the smallest meaningful test;
8. only then broaden validation.

Do not:

- repeatedly reinstall;
- repeatedly restart without evidence;
- repeatedly edit the same code based on guesses;
- switch tools/models merely because the task is difficult;
- relax assertions to hide failures;
- classify flaky/tooling issues as product defects without reproduction.

After **two unsuccessful evidence-based repair cycles on the same boundary**, Claude should normally checkpoint and ask rather than entering a third speculative loop.

---

## 11. Step 10 — Immediate Mini-Hardening

Implementation is not complete when the happy path works.

Mini-harden where relevant across:

- edge cases;
- empty/loading/error states;
- offline behavior;
- restart/reopen behavior;
- repeated actions/double taps;
- concurrency;
- persistence;
- migration;
- stale data;
- reconciliation;
- company/user isolation;
- security/trust;
- performance/query boundedness;
- packaging;
- backward compatibility;
- visual state honesty.

Do not defer obvious feature-local hardening to a later broad cleanup.

---

## 12. Step 11 — Test Strategy

Testing should follow risk and change surface.

Preferred order:

1. focused regression tests;
2. mutation/adversarial proof where valuable;
3. related module suite;
4. full relevant platform suite;
5. architecture/contract/security tests where applicable;
6. build/package validation;
7. physical validation only when explicitly authorized.

Tests should prove behavior, not merely increase test count.

For a fixed bug, add a test that fails under old behavior and passes under the fix whenever practical.

---

## 13. Step 12 — Environment Artifact Classification

Claude must distinguish:

- product/source defect;
- test-environment contamination;
- toolchain/build artifact;
- network/environment limitation;
- flaky/resource-contention issue;
- physical-state limitation;
- genuine unproven condition.

Do not call a product FAIL merely because an office/hotspot environment cannot reach the home Connector/Tally.

Do not call an environment artifact PASS without explaining why it is not product-related.

---

## 14. Step 13 — Preserve Proven State

Do not casually disturb already-proven state.

Examples:

- connector identity/cert/key/SPKI;
- paired Android trust;
- real SQLite data;
- existing AppData;
- proven installer continuity;
- Tally data;
- unrelated dirty/untracked files;
- accepted technical-debt files;
- approved versions/artifacts.

For upgrade/migration tests:

- no uninstall unless explicitly required;
- no clear-data fallback;
- no identity reset;
- no re-pair workaround;
- no destructive repair merely to make a test pass.

---

## 15. Step 14 — Git Discipline

Before staging/committing:

- inspect git status;
- isolate intended files;
- preserve unrelated dirty/untracked files;
- stage only approved scope;
- use coherent commits;
- report exact SHA;
- do not push unless explicitly authorized.

Avoid mixing:

- implementation;
- unrelated cleanup;
- technical debt;
- mass formatting;
- version bump;
- test hardening;

unless explicitly authorized.

Version/provenance bumps should normally be separate commits.

---

## 16. Step 15 — Usage Efficiency

Treat Claude usage as scarce development capacity.

Optimize by:

- reusing project context;
- avoiding repeated repo-wide searches;
- batching related inspection;
- running focused tests before full suites;
- avoiding duplicate unchanged builds;
- preserving capacity for validation/hardening;
- using subagents only for separable work;
- avoiding parallel agents on the same uncommitted files.

Where requested, capture `/usage` or equivalent at start/end of major milestones.

---

## 17. Step 16 — Subagent / Multi-Agent Rules

Use subagents only when:

- work is clearly isolated;
- results can be combined cleanly;
- parallelism saves real time;
- file overlap is low.

Good uses:

- independent adversarial review;
- test-gap analysis;
- isolated module inspection;
- parallel read-only research.

Bad uses:

- multiple agents editing the same files;
- agents independently redefining architecture;
- duplicate debugging of the same unbounded problem;
- parallelism that consumes capacity without reducing accepted-delivery time.

The main Claude session remains responsible for synthesis and final evidence.

---

## 18. Step 17 — Physical-Test Boundary

If physical action is required, Claude must:

- state the exact action;
- minimize operator steps;
- say what must not be touched;
- define stop-at-first-failure behavior;
- preserve prior state;
- wait for the physical result before continuing.

Examples:

- Android in-place APK update;
- UAC/Windows installer;
- Tally close/reopen;
- Wi-Fi off/on;
- USB insert/remove;
- physical PDF/share verification.

Do not simulate physical evidence from source inspection.

---

## 19. Step 18 — Release/Artifact Rules

For build/release candidates:

- use exact approved HEAD;
- prefer isolated clean worktree;
- verify clean-at-start / dirtyTree=false where supported;
- record artifact path;
- record SHA-256;
- record size;
- record version/build identity;
- record signing/provenance;
- inspect actual packaged output, not source only;
- stop before install when instructed.

Do not silently overwrite an earlier candidate version.

---

## 20. Step 19 — Reporting Standard

Every substantial task ends with a concise evidence-based report.

Include, as applicable:

- exact baseline;
- exact findings;
- exact files changed;
- tests/results;
- defects found/fixed;
- architecture/API/schema impact;
- commits;
- git status;
- artifact requirement;
- limitations;
- deferred items;
- next physical/test step;
- final classification: PASS / FAIL / PARTIAL / BLOCKED / READY / NO-GO.

Do not claim proof for inferred behavior.

Use terms accurately:

- **Proven**
- **Code-proven**
- **Runtime-proven**
- **Physically proven**
- **Unproven**
- **Deferred**
- **Accepted limitation**

---

## 21. Step 20 — Stop Rules

Claude must stop when:

- the prompt says STOP;
- a genuine P0/P1 failure is found and protocol requires first-failure stop;
- architecture/scope approval is needed;
- a destructive action would be required;
- root cause remains unproven after bounded diagnosis;
- physical user action is required;
- release/install/push approval is required;
- the requested outcome is fully achieved and reported.

Do not continue simply because there is still time or usage available.

---

## 22. Standard Prompt Lifecycle

For every major VENTURE prompt:

1. Classify task
2. Inspect relevant context
3. Identify ambiguity
4. Ask initial bounded questions if needed
5. Wait for Brainstorm 2 if needed
6. Confirm execution boundary
7. Establish baseline
8. Implement minimum correct change
9. Run focused validation
10. Diagnose/repair within scope
11. **Checkpoint-and-clarify if new ambiguity or repeated failure appears**
12. Resume from checkpoint after clarification
13. Mini-harden
14. Run full relevant regression
15. Prepare coherent commits
16. Verify git state
17. Prepare artifacts only if authorized
18. Report evidence
19. Stop

---

## 23. Definition of Good Claude Behavior

Claude is operating correctly when:

- it understands the desired outcome before coding;
- it asks only useful questions;
- it can ask a necessary question mid-execution without losing valid work;
- it does not continue down vague routes;
- it resumes from checkpoints rather than restarting;
- it reuses proven architecture;
- it operates autonomously inside scope;
- it catches its own regressions;
- it distinguishes environment artifacts from product defects;
- it avoids repetitive loops;
- it protects proven state;
- it preserves user time and Claude capacity;
- it reports truthfully;
- it stops at genuine approval boundaries.

---

## 24. Priority Order

When instructions conflict, prioritize:

1. data integrity / security / trust;
2. explicit Product Owner / ChatGPT locked decisions;
3. architecture invariants;
4. acceptance correctness;
5. backward compatibility / proven continuity;
6. performance/resource efficiency;
7. UX polish;
8. implementation convenience.

Convenience never overrides correctness or trust.

---

## 25. Relationship to Other Governance Files

This file defines **how Claude processes and executes a prompt**.

`POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` defines the broader user + ChatGPT + Claude operating model.

`VENTURE-MASTER-PRODUCT-EXECUTION-PLAN.md` defines roadmap, milestones, capacity planning, release sequence, and empirical time/cost analysis.

Together these three files form the post-MVP VENTURE development governance system.

**LOCKED.**
