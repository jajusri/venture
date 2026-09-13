# VENTURE Clean Tree and Artifact Hygiene

**Status:** Permanent governance principle
**Relationship to other files:** This defines the repository-wide principle. `VENTURE-CLAUDE-PROMPT-PROCESSING-RULES.md` Step 14 ("Git Discipline") and Step 19 ("Release/Artifact Rules") already define the per-task mechanics — inspect status, isolate intended files, preserve unrelated dirty/untracked state, stage only approved scope, record artifact provenance. This file does not repeat those mechanics; it states the standing principle they exist to serve.

## 1. Permanent principle

**Every completed VENTURE task must leave no unexplained dirty state.**

If a task's final report cannot account for every modified, staged, or
untracked file — why it exists, whether it belongs to this task, and whether
it was intentionally left alone — the task is not actually finished, however
green the tests are.

## 2. The three categories

Every file the repository or a working tree touches falls into exactly one
of these:

- **Intended project truth** — organized, verified, committed. This is the
  goal state for anything meant to persist.
- **Temporary output** — build artifacts, extraction scratch space,
  diagnostic dumps, isolated worktrees. Must be removed after use or live in
  a designated temp/ignored location, never left to accumulate silently in
  the working tree.
- **Unrelated dirty state** — pre-existing uncommitted or untracked work
  belonging to a different, still-in-progress task. Protected: never
  touched, staged, reset, stashed, or absorbed by an unrelated task, and
  always reported separately so the human owner knows it's still there.

## 3. Rules

1. Legitimate project work must not be hidden in `.gitignore` simply to make
   `git status` look clean. If something needs to be ignored, it must
   genuinely be disposable (build output, local machine config, temp
   extraction) — never a way to launder unfinished or unreviewed work out of
   sight.
2. Approved/current design and planning references are active truth and
   belong in the tracked tree (`docs/`), not in an ignored or scratch
   location.
3. Superseded experiments (rejected designs, abandoned approaches, old
   exploration output) must not pollute active references. Move them to an
   explicit archive location or remove them — do not leave them
   indistinguishable from current truth.
4. **Active tree = current truth.** Anything a future session reads from the
   working tree should be trustworthy without needing to check whether it's
   stale.
5. **Archive = useful historical evidence.** Superseded material still has
   value for understanding why a decision was made, but must be clearly
   separated from what's currently authoritative (e.g. by location, by
   explicit "superseded" status in the file itself, or both).
6. **Temp = disposable.** Nothing load-bearing should live only in a temp
   location. If a temp artifact turns out to matter, promote it to active
   truth or archive deliberately — don't leave it as the only copy of
   something important.

## 4. External design archive

`D:\VENTURE-Design-Archive\` is an intentional example of category 2/3
separation done correctly, outside Git entirely:

```
00_README_AND_DECISIONS
01_APPROVED_MASTERS
02_APPROVED_NEXT_SCREENS
03_STITCH_EXPORTS_RAW
04_SUPERSEDED_EXPLORATIONS
05_IMPLEMENTATION_HANDOFF
```

Approved visual references and raw Stitch explorations remain there. The
repository may reference this archive conceptually (see
`docs/design/VENTURE-UI-DESIGN-DECISIONS.md`), but it must never be copied
into Git — Git stays high-signal, and the archive stays the working space
for volume/iteration that doesn't belong in version control.

## 5. Release-worktree hygiene

Isolated release worktrees (`D:/tmp/venture-release-<short-sha>` and similar)
are temporary by construction. They exist to build release evidence from a
clean, dirty-tree-free checkout without disturbing the main working tree's
in-progress state. They are not meant to accumulate indefinitely — an old
worktree whose candidate has been superseded is disposable, not a permanent
archive of that candidate (the git history and the task's final report are
the permanent record).

## 6. Evidence over ceremony

This file is itself subject to `VENTURE-EVIDENCE-OVER-BUREAUCRACY-PRINCIPLE.md`.
Clean-tree discipline exists to prevent lost work, hidden state, and
future-session confusion about what's real — not to create extra process for
its own sake.

**PERMANENT.**
