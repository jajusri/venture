# BUDCOM AI Engineering Learnings

**Status:** Living empirical knowledge base  
**Purpose:** Preserve what the BUDCOM team has actually learned about AI tools, debugging methods, validation strategies, workflow failures, and successful recovery patterns so future work can reuse proven approaches instead of rediscovering them.  
**Important:** This file records experience and evidence. It is not a rigid tool-ranking document and does not override the locked governance files.

---

## 1. Why this file exists

BUDCOM development has already produced valuable practical knowledge that goes beyond architecture and product decisions.

We have learned:

- which AI tools perform well on which classes of work;
- which workflows burn usage quickly;
- which tools are methodical vs fast;
- which tools are best used for implementation vs review;
- how physical testing can expose defects that static analysis misses;
- how a second model can unlock a new line of thinking;
- how repeated debugging loops begin;
- how to preserve working state when an investigation becomes ambiguous;
- how environment problems can masquerade as product defects;
- how package-level verification can catch problems that source tests miss.

These lessons should remain available when the team gets stuck later.

---

## 2. How to use this document

When a future BUDCOM task becomes difficult, first ask:

1. Have we faced a structurally similar problem before?
2. Which tool or method actually helped?
3. Which approach wasted time or usage?
4. Was the earlier issue code, architecture, environment, packaging, physical state, or product ambiguity?
5. What evidence resolved the problem?
6. Which tool should lead this task now?
7. Which tool should only review or provide a second opinion?

Do not blindly reuse an old tool choice. Re-evaluate against current models, limits, repository state, and task risk.

---

## 3. Confidence labels

Use these when adding future entries:

- **High confidence** — repeated real BUDCOM evidence
- **Medium confidence** — one or two strong examples
- **Low confidence** — plausible observation, not yet repeated
- **Historical** — may no longer reflect current model/tool capability

---

# 4. Tool Learnings

## AIL-001 — Claude is strong on complex, narrow, architecture-sensitive work

**Situation:** Security, connectivity, release-hardening, data-integrity, multi-file fixes, difficult debugging, and architecture-sensitive implementation.

**Observed outcome:** Claude tended to work more slowly and methodically, but repeatedly produced accurate narrow fixes and avoided many of the repetitive debugging loops experienced elsewhere.

**What we learned:**
- Claude is especially valuable when correctness and disciplined scope matter more than raw implementation speed.
- It is well suited to high-risk hardening, architecture-sensitive corrections, integration stabilization, security review, and final production verification.
- It works best when the prompt gives precise boundaries, acceptance criteria, and stop conditions.
- Claude should be encouraged to ask consequential questions before and during execution rather than guess.

**When to use again:**
- high-risk fixes;
- release hardening;
- complex repository-wide reasoning;
- migration/data-integrity work;
- security/trust issues;
- connectivity resilience;
- final verification.

**When not to overuse:**
- trivial boilerplate;
- very small mechanical edits where a faster tool is enough;
- broad exploratory work with unclear scope.

**Confidence:** High

---

## AIL-002 — Cursor is effective for fast routine implementation

**Situation:** Scaffolding, routine feature implementation, mechanical edits, fast multi-file work, and local iteration.

**Observed outcome:** Cursor was able to move quickly and perform multiple implementation tasks efficiently when scope was clear.

**What we learned:**
- Cursor is valuable when speed matters and the architecture is already decided.
- It is best used for foundation/routine implementation that can later be independently reviewed.
- It should not be the sole authority for high-risk architecture, release-hardening, security, connectivity, or data-integrity decisions.

**When to use again:**
- routine implementation;
- scaffolding;
- repetitive code changes;
- low-risk UI/data plumbing;
- tightly scoped tasks.

**When not to rely on it alone:**
- security/trust boundary work;
- release-hardening;
- architecture changes;
- complex connectivity;
- migrations/data-integrity;
- final production verification.

**Confidence:** High

---

## AIL-003 — Codex is valuable for deep investigation but can exhaust usage quickly

**Situation:** Deep code investigation, validation, debugging, review, and difficult technical analysis.

**Observed outcome:** Codex could provide strong investigation/validation value, but practical limits were exhausted quickly during heavy use.

**What we learned:**
- Codex should be reserved for work where its depth materially changes the outcome.
- Do not spend Codex capacity on routine implementation that Claude/Cursor can handle.
- Use it strategically for independent verification, hard technical investigation, or second-opinion analysis.
- Avoid running Codex and another agent against the same uncommitted files.

**When to use again:**
- deep validation;
- independent code review;
- difficult root-cause investigation;
- hard technical questions where a different reasoning path adds value.

**When not to use:**
- routine edits;
- long repetitive implementation;
- tasks already well-covered by Claude;
- situations where limits would be spent without distinct value.

**Confidence:** High

---

## AIL-004 — Gemini can be valuable as a problem-reframing / second-opinion tool

**Situation:** A difficult problem where the current line of thinking was not producing progress.

**Observed outcome:** Gemini contributed a useful SQL/database idea that opened a new direction.

**What we learned:**
- A different model can be valuable not because it should own implementation, but because it may frame the problem differently.
- When the primary tool is stuck, a targeted second-opinion question can be more valuable than another iteration of the same approach.
- The best use may be idea generation or reframing, followed by architectural review before implementation.

**When to use again:**
- genuine dead ends;
- architecture brainstorming;
- alternate data-model ideas;
- second-opinion diagnosis.

**When not to use:**
- as an uncontrolled parallel implementer;
- to override accepted architecture without review;
- when the existing path is already evidence-driven and working.

**Confidence:** Medium

---

## AIL-005 — ChatGPT is most valuable as continuity, architecture and orchestration layer

**Situation:** Long-running product development using multiple AI tools.

**Observed outcome:** ChatGPT has been most useful for preserving product continuity, turning business needs into architecture, interpreting agent reports, deciding when evidence is sufficient, designing acceptance criteria, and selecting the next tool/workflow.

**What we learned:**
- One architectural authority prevents different agents from pulling the project in incompatible directions.
- Product decisions, agent roles, release boundaries, and evidence standards should remain centralized.
- Prompt design itself is an engineering task and should be optimized from the desired outcome backward.

**When to use again:**
- feature planning;
- architecture;
- Claude prompt design;
- agent selection;
- acceptance criteria;
- release decisions;
- interpreting conflicting findings.

**Confidence:** High

---

# 5. Workflow Learnings

## AIL-006 — Do not let multiple agents modify the same uncommitted work

**Observed risk:** Conflicts, duplicated fixes, unclear provenance, harder review, and wasted context.

**Rule learned:** Complete one agent's coherent work first, then hand it to another agent for review/hardening.

**Confidence:** High

---

## AIL-007 — Physical testing can reveal defects that source/static analysis misses

**Examples:**
- startup/storage overlay behavior;
- connectivity/reconnect;
- install/upgrade continuity;
- actual Android Room migration;
- Tally restart/recovery;
- Wi-Fi recovery.

**What we learned:**
- automated tests are necessary but insufficient;
- packaged/runtime behavior must be tested physically where relevant;
- source-level correctness does not guarantee CSS/package/runtime correctness;
- final acceptance must include the real user environment.

**Confidence:** High

---

## AIL-008 — Package-level inspection matters

**Situation:** A candidate may have correct source but wrong packaged behavior.

**Observed examples:**
- CSS behavior in packaged `app.asar`;
- confirmation that a circuit-breaker fix was actually present in packaged Connector JS;
- build-info/version provenance verification.

**What we learned:**
- verify the actual artifact, not just source HEAD;
- inspect packaged code for critical fixes;
- record hashes, provenance, versions and clean-source evidence.

**Confidence:** High

---

## AIL-009 — Environment problems can masquerade as product defects

**Observed examples:**
- Tally GUI open while port 9000 was not listening;
- office/mobile-hotspot environment not equivalent to the home trusted LAN;
- Gradle/test resource contention causing transient failures;
- shell-specific `tar` behavior breaking packaging.

**What we learned:**
- classify the environment before changing source;
- confirm the earliest failing boundary;
- avoid fixing application code for an external/tooling issue;
- re-run isolated tasks when resource contention is plausible.

**Confidence:** High

---

## AIL-010 — Stop repetitive repair loops early

**Observed problem:** Repeated retries, reinstall/restart cycles, or speculative code edits consume time and AI limits while reducing certainty.

**What we learned:**
- after two evidence-based failed repair cycles on the same boundary, checkpoint;
- preserve valid work;
- ask for clarification/deeper-investigation approval;
- do not keep moving merely because session capacity remains.

**Confidence:** High

---

## AIL-011 — Reverse-engineer prompts from acceptance outcomes

**Observed improvement:** Clearer prompts produce fewer vague routes and better first-pass results.

**Working method:**
1. define final product outcome;
2. define evidence that proves it;
3. define invariants;
4. infer architecture/data requirements;
5. define implementation scope;
6. define tests/hardening;
7. define stop conditions.

**What we learned:** Prompt design is part of architecture, not just communication.

**Confidence:** High

---

## AIL-012 — Product thinking should happen before Claude consumption

**Observed constraint:** Claude usage windows and weekly limits are scarce resources.

**What we learned:**
- do Brainstorm 1 before the Claude session;
- settle product behavior, architecture and acceptance criteria first;
- let Claude spend capacity on repo execution rather than requirement discovery;
- reserve session capacity for validation/hardening.

**Confidence:** High

---

## AIL-013 — Mid-execution questions are better than vague continuation

**Observed need:** Some ambiguity appears only after source inspection or partial implementation.

**What we learned:**
- Claude should checkpoint and ask when new consequential ambiguity appears;
- preserve half-completed valid work;
- resume from checkpoint after Brainstorm 2;
- do not restart the prompt from zero.

**Confidence:** High

---

## AIL-014 — Small, narrow fixes are safer than broad rewrites

**Observed successful pattern:** Circuit-breaker recovery and storage overlay fixes were strongest when the exact failing boundary was proven and the fix touched the minimum necessary surface.

**What we learned:**
- prove the first failing boundary;
- prefer minimum correct repair;
- add a regression test;
- mutation-prove where practical;
- avoid unrelated cleanup in the same commit.

**Confidence:** High

---

## AIL-015 — Real user observation should drive late MVP micro-polish

**Observed principle:** Screen optimization, layout, button behavior, colors, spacing and tiny workflow improvements can add substantial value without expanding feature scope.

**What we learned:**
- test the product in real use before polishing;
- capture moments of confusion, extra taps, unclear status or waiting;
- treat these as MVP-1.0.x micro-polish if they improve existing functionality rather than add features.

**Confidence:** High

---

# 6. Debugging / Investigation Heuristics

When stuck, choose the next tool based on the failure class.

## Architecture ambiguity
**Lead:** ChatGPT  
**Second opinion:** Claude / Gemini  
**Goal:** Clarify model before implementation.

## Complex code/data-integrity defect
**Lead:** Claude  
**Independent review:** Codex where limits justify it  
**Goal:** narrow, evidence-driven repair.

## Routine implementation
**Lead:** Claude or Cursor depending on risk/speed  
**Review:** Claude for higher-risk integration.

## Dead-end reasoning
**Lead:** current agent checkpoints  
**Second opinion:** different model (Gemini/Codex/ChatGPT)  
**Goal:** reframe, not create competing uncommitted implementations.

## Package/runtime discrepancy
**Lead:** Claude  
**Method:** inspect packaged artifact + runtime evidence.

## Physical/environment discrepancy
**Lead:** Claude diagnosis + user physical confirmation  
**Method:** separate environment truth from application source.

---

# 7. Tool Selection Matrix

| Task Type | Preferred Lead | Useful Secondary | Avoid |
|---|---|---|---|
| Product/architecture planning | ChatGPT | Claude/Gemini second opinion | Uncoordinated implementation |
| Routine implementation | Claude/Cursor | Claude review | Expensive deep-agent usage |
| Security/trust/data integrity | Claude | ChatGPT approval, Codex review | Fast unreviewed edits |
| Release hardening | Claude | ChatGPT approval | Multiple writers |
| Deep code investigation | Claude/Codex | ChatGPT synthesis | Repetitive parallel debugging |
| Dead-end idea generation | ChatGPT/Gemini | Claude validation | Blind implementation of suggestion |
| Physical acceptance | Claude protocol + user | ChatGPT review | Source-only claims |
| UI micro-polish | Claude/Cursor | user visual acceptance | Architecture expansion |

This matrix is empirical and should evolve with new model/tool evidence.

---

# 8. How to Add a New Learning

Use this template:

## AIL-XXX — <Short title>

**Date:**  
**Situation:**  
**Tool/approach:**  
**What happened:**  
**Evidence:**  
**What we learned:**  
**When to reuse:**  
**When not to reuse:**  
**Confidence:** High / Medium / Low / Historical  
**Supersedes:** if applicable

---

# 9. Rules for Maintaining This File

- Record evidence, not loyalty to a tool.
- Update observations when models materially improve or limits change.
- Do not convert one successful anecdote into a permanent universal rule.
- Preserve failed approaches when they teach something useful.
- Separate model capability from plan/usage-limit economics.
- Prefer BUDCOM-specific evidence over marketing claims.
- If an old learning conflicts with new repeated evidence, mark it superseded rather than silently deleting history.

---

# 10. Relationship to Other BUDCOM Documents

This file records **what the team has learned from experience**.

It complements:

- `POST-MVP-1-DEVELOPMENT-MODUS-OPERANDI.md` — how the team works;
- `BUDCOM-CLAUDE-PROMPT-PROCESSING-RULES.md` — how Claude processes prompts;
- `BUDCOM-MASTER-PRODUCT-EXECUTION-PLAN.md` — what gets built and how capacity/cost are planned;
- `BUDCOM-PRODUCT-DECISION-LOG.md` — consequential product decisions;
- `BUDCOM-DEVELOPMENT-ECONOMICS-LOG.md` — empirical time/usage/cost evidence.

Together, these files preserve both BUDCOM's **rules** and its **experience**.

---

## Final Principle

> When BUDCOM gets stuck, do not begin from zero.  
> First check what this team has already learned.

**LIVING DOCUMENT — EMPIRICAL, NOT DOGMATIC.**
