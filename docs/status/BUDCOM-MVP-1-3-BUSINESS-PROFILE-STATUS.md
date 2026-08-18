# BUDCOM MVP-1.3 — Business Profile — Status

**Status:** Part A complete — acceptance gate PASSED. Continuing automatically to Part B per this
task's own explicit instruction.
**Companion documents:** `docs/status/BUDCOM-CURRENT-DEVELOPMENT-STATUS.md` (concise current-state
checkpoint), `docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` (chronological record),
`docs/architecture/BUDCOM-MVP-1-3-BUSINESS-PROFILE-ARCHITECTURE.md` (planning/recovery review this
milestone builds on), `docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` PDL-019 (the five locked
product decisions this entire milestone builds on).

---

# Part A — MVP-1.3-A: Business Identity Foundation

**Starting HEAD:** `3166d3388d79e17828fb991b93a1f42336f99834` (`docs: MVP-1.3 Business Profile
planning/recovery review`), working tree clean. Recovered state by direct inspection rather than
trusting the prior session's own report: re-read `git log -1`, `DatabaseConstants.VERSION`
(confirmed `9`, unchanged since MVP-1.2), confirmed zero `feature/businessprofile` code existed
anywhere in the repository before this session (direct search), and confirmed the architecture
doc's own §5 open-decisions list before writing any code.

## A1. Product decisions locked (session prerequisite, before implementation)

The five decisions this task's own governing prompt supplied were recorded permanently as
`docs/governance/BUDCOM-PRODUCT-DECISION-LOG.md` **PDL-019**, mirroring the format of PDL-014
through PDL-018:

1. Business Profile is scoped **per selected Tally `companyId`**, never installation-global.
2. Initial field set: Business/Trading Name, Legal Name, Address (line/city/state/PIN), Phone,
   Email, GSTIN, Website, Logo, Short Business Description. No arbitrary Tally field import, no
   catalogue/product fields, no social-feed/marketplace fields.
3. Business Profile is 100% BUDCOM-owned data for MVP-1.3 — no `PartyFieldProvenance`, no Tally
   round-trip, no Connector mutation endpoint.
4. Logo storage: app-private storage (not the existing Private USB Storage mechanism), behind a
   small `BusinessProfileLogoStore` abstraction so the mechanism can change later; must validate
   file type, enforce a size limit, avoid path traversal and arbitrary external file exposure,
   never log sensitive paths/content, and handle a missing/corrupt logo gracefully.
5. No visitor-facing Resources view in MVP-1.3 — owner-side only.

The architecture doc's §3 and §5 headings were updated with a `RESOLVED 2026-08-18 — PDL-019.`
banner, preserving the original open-question reasoning below each banner as historical record
(not rewritten).

## A2. Scope actually implemented

Exactly this task's own MVP-1.3-A line item (Business Identity Foundation):

1. `MIGRATION_9_10` (Room schema v9→v10) — one new table, `business_profile`.
2. `BusinessProfileEntity`/`BusinessProfileDao`/`BusinessProfileMappers` (data layer).
3. `BusinessProfile`/`BusinessProfileDraft` (domain), `BusinessProfileRepository` (interface) +
   `BusinessProfileRepositoryImpl`, four use cases (Get/Save/UpdateLogo/ClearLogo).
4. `BusinessProfileLogoStore` interface + `AndroidBusinessProfileLogoStore` — the full logo
   storage *infrastructure* (validation, size cap, path-traversal defense), even though A's own
   UI does not yet expose a logo picker (B's scope) — because `logoAssetPath` is a real column in
   A's own schema and the repository's `updateLogo`/`clearLogo` contract needed a real
   implementation to be genuinely tested, not stubbed.
5. `BusinessProfileViewModel`/`BusinessProfileUiState`/`BusinessProfileScreen` — a functional,
   basic, whole-entity view/edit screen (no logo picker UI yet — explicit A/B split, see A3).
6. Dashboard integration: `Routes.BUSINESS_PROFILE`, a fifth `HomePrimaryEntryRow`, honest
   empty/setup state.
7. Full test coverage per this task's own §14 test-strategy requirements.
8. Mini-hardening: full JVM debug+release regression, both lints (report-XML-verified), all three
   assembles, full instrumented suite on a connected authorized device, plus a genuine
   investigate-before-accept resolution of two new instrumented failures (A9).
9. This Durable Development Record.

**Explicitly not built** (B/C scope, confirmed untouched): logo picker/display UI, full
presentation-hierarchy/accessibility polish beyond basics, the internal sharing-foundation data
boundary, any public/cloud transport, Catalogue, Vartalap, visitor-facing Resources view. Verified:
zero files under `apps/budcom_desktop` or the Connector changed; zero `AndroidManifest.xml`
change; zero network/Connector import anywhere in `feature/businessprofile` (`grep`-verified, see
A9); zero `Log`/`Timber` call anywhere in the new feature (`grep`-verified).

## A3. Design decision — why A ships full logo-storage infrastructure but no picker UI

The architecture doc's own sub-milestone framing (§14) splits "Business Identity Foundation" (A)
from "Profile Presentation & Sharing Foundation" (B). Read literally, "foundation" means the data
layer must be real and fully testable — including the logo `Uri` → validated file round-trip,
since `logoAssetPath` is a genuine schema column A introduces. Building only a stub
`BusinessProfileLogoStore` in A and the real implementation in B would have meant either (a) A's
schema ships a column with no working writer, untestable until B, or (b) B silently becomes a
second "finish the data layer" milestone instead of purely presentation. Implementing the storage
abstraction fully in A — validated, tested, hardened — while deliberately deferring only the
*picker/display Compose UI* to B keeps each milestone's diff honest: A is "the entity + its
storage are real and correct," B is "a person can actually see and change them well."

## A4. Data model

### `business_profile` — new table

| Column | Type | Nullable | Purpose |
|---|---|---|---|
| `companyId` | TEXT | NOT NULL, **PK** | Company scope — sole primary key, since this is a singleton per company (PDL-019 §1). |
| `tradingName` | TEXT | NOT NULL | The only required field. |
| `legalName` | TEXT | NULL | |
| `addressLine1` / `addressCity` / `addressState` / `addressPincode` | TEXT | NULL each | |
| `phone` | TEXT | NULL | Free text, matching this codebase's own convention (no format enforcement at save time). |
| `phoneNormalized` | TEXT | NULL | Computed via the existing `PhoneNumberNormalizer.normalizeForSearch` — reused, not duplicated. |
| `email` / `gstin` / `website` / `description` | TEXT | NULL each | |
| `logoAssetPath` | TEXT | NULL | App-private absolute path, resolved/validated exclusively through `BusinessProfileLogoStore` — never trusted as a raw string elsewhere. |
| `createdAt` / `updatedAt` | INTEGER | NOT NULL each | Epoch millis via `TimeProvider` (never `System.currentTimeMillis()` directly). |

Deliberately **no** `PartyFieldProvenanceEntity`-style tracking (PDL-019 §3) and **no** Room
`ForeignKey` (matching every other company-scoped table in this codebase's own natural-key
convention).

## A5. Migration

`DatabaseModule.MIGRATION_9_10` — one `CREATE TABLE IF NOT EXISTS business_profile (...)`
statement. Verified byte-for-byte against Room's own KSP-generated
`app/schemas/com.budcom.android.core.database.AppDatabase/10.json` after a real build — every
column name, SQL type, `notNull` flag, and the single-column primary key match exactly (direct
`grep` comparison of the hand-written SQL against the generated `createSql`, the same "verify
before trusting" discipline applied to every migration since `MIGRATION_5_6`).

`AppDatabaseMigrationTest.migrate9To10_preservesExistingRowsAndAddsBusinessProfileTableOnly` — a
real `MigrationTestHelper` run against the actual production `MIGRATION_9_10` object, starting
from a populated version-9 database (a company + a party row). Proves: pre-existing rows survive
untouched; the new table's full column set matches via `PRAGMA table_info`; the table starts
genuinely empty (`COUNT(*) = 0`, not merely "no error"); a post-migration insert/read round-trip
works. **Run and passing on real hardware** (device `10BF44124K000E3`), not just JVM-simulated.

## A6. Repository / use-case API

`BusinessProfileRepository`: `getProfile(companyId): BusinessProfile?`,
`saveProfile(companyId, draft): BusinessProfile`,
`updateLogo(companyId, sourceUri): BusinessProfileLogoResult?` (the outer nullable specifically
means "no profile row exists yet for this company" — attempting to attach a logo before any
profile has ever been saved is a distinct, explicitly representable case, not silently
auto-creating a blank profile), `clearLogo(companyId)`.

`BusinessProfileRepositoryImpl.saveProfile` preserves the existing row's `createdAt` and
`logoAssetPath` across re-saves (a text-field edit must never silently drop an already-attached
logo), and computes `phoneNormalized` via the existing, reused `PhoneNumberNormalizer` rather than
introducing a second normalization routine.

## A7. Logo storage — `AndroidBusinessProfileLogoStore`

Design details, each a direct answer to this task's own §9 safety requirements:

- **File-type validation:** `context.contentResolver.getType(sourceUri)` checked against an
  allowlist map (`image/jpeg`, `image/png`, `image/webp` only) — an unrecognized/unsupported MIME
  type is rejected before any byte is copied (`BusinessProfileLogoFailureReason.UnsupportedFileType`).
- **Size limit:** a bounded streaming copy (`copyBounded`, 8 KB buffer) aborts and deletes the
  partial file the moment 5 MB (`MAX_LOGO_BYTES`) is exceeded — the source is never fully buffered
  in memory, and an oversized file is never left on disk.
- **Path traversal:** the persisted filename stem is derived from `companyId` through the same
  allowlist-regex sanitizer (`[^A-Za-z0-9._-]+`) this codebase already established for XML-export
  filenames (`PartyXmlExportCoordinator.sanitizedPartyXmlExportFilename`) — reused, not
  reinvented. Because the sanitizer strips every path-separator character (`/`, `\`) and the
  result is used as a single filename component (never joined as a multi-segment path), no
  traversal is structurally possible even in adversarial cases (verified by reasoning through
  `companyId` values like `"../../etc/passwd"` — separators become `-`, and trim-set-only leading
  runs of `.`/`-`/`_` are trimmed away). `resolveLogoFile` adds a second, independent layer: a
  `canonicalFile.parentFile == directory.canonicalFile` containment check on every read, so even a
  corrupted/tampered persisted path can never resolve outside the logo directory.
- **No arbitrary external exposure:** logos live under app-private `context.filesDir` — never
  passed through `FileProvider`, never exposed to another app, never uploaded anywhere (grep-
  verified zero network import in the whole feature).
- **Missing/corrupt logo:** `resolveLogoFile` returns `null` for a missing file, a non-file path,
  or a path that fails the containment check — callers (B's future display code) receive a clean
  `null`, never an exception.
- **No sensitive logging:** zero `Log`/`Timber` call anywhere in the storage class (grep-verified).
- **Orphan cleanup:** `clearOtherExtensions` deletes any stale other-extension file for the same
  company stem after a successful write, so replacing a `.png` logo with a `.jpg` one doesn't leave
  an orphaned file behind.

## A8. UI — a single whole-entity editor, not per-field dialogs

`BusinessProfileScreen` uses one inline view/edit toggle over the entire form (11 fields, one
Save/Cancel pair) rather than Party Detail's per-field `EditField` dialog pattern — a deliberate
choice, documented in-code: unlike Party Detail (a multi-section screen where fields are one part
alongside contacts/tags/notes), this entire screen *is* one entity's editor, so one Save action is
simpler for first-time setup than eleven repeated dialog interactions, while staying "simple, no
wizard" per this task's own explicit boundary.

**Self-caught defect, fixed before any test was run:** the first draft's `CancelEditTapped`
handler reverted to `state.form` — but `form` already held the user's live, in-progress edits by
that point (it's mutated directly as the user types), so "Cancel" would have silently kept
whatever the user had just half-typed instead of truly reverting to the last-saved values. Fixed
by introducing a separate `savedForm` field on `BusinessProfileUiState`, distinct from the live
`form` — `load()`/`save()` set both to the same freshly-loaded/saved value; `EditTapped` and
`CancelEditTapped` both reset `form = savedForm`. The
`cancelling an edit reverts to the last-saved values, discarding unsaved changes` JVM test locks
this in.

Logo display/picker UI is explicitly **not** built in A (in-code doc comment marks this as B
scope) — A's screen shows and edits only the eleven text fields.

## A9. Instrumented-test investigation — a genuine finding, resolved at the test level

The first full `connectedDebugAndroidTest` run this session showed **14** failures, not the
expected 12 — two new ones, both in the new `BusinessProfileScreenTest`
(`cancelInEditModeEmitsCancelEditTapped`, `editFormEmitsFieldChangedEventsAndSaveTapped`). Per this
task's own "investigate before accepting" discipline, these were **not** assumed to be more
instances of the known device-viewport baseline without evidence:

1. Re-read `BusinessProfileScreen.kt`'s Cancel/Save button wiring directly — both correctly call
   `onEvent(...)`; no production defect visible.
2. Re-ran only `BusinessProfileScreenTest` in isolation, twice — both times reproduced the
   identical 2 failures (not flaky/non-reproducible), ruling out simple run-to-run noise.
3. Compared against an existing, already-established screen using the identical
   static-state + `performTextInput` test idiom (`ProspectCreateScreenTest.
   typingTheNameFieldEmitsDisplayNameChanged`, same `Scaffold` + `verticalScroll` structure) — ran
   it in isolation on the same device: **7/7 passing**, ruling out generic device/IME text-input
   flakiness as the cause.
4. Root-caused directly: `BusinessProfileEditForm` stacks 11 empty `OutlinedTextField`s before the
   Save/Cancel button pair inside one `verticalScroll` column — on this specific physical device's
   viewport, that pushes the buttons below the fold at initial (unscrolled) position.
   `performClick()` does not pre-check visibility the way `assertIsDisplayed()` does, so the touch
   coordinate silently fails to land rather than throwing — the same underlying
   off-screen-widget-on-this-device phenomenon as the already-documented 12-failure baseline
   (`DashboardScreenTest` et al.), just manifesting as a silent no-op instead of a hard assertion
   failure.

**Fix applied at the test level** (not production code, since a real user naturally scrolls before
tapping a button they can see is below the fold): added `.performScrollTo()` before `.performClick()`
on both `business_profile_save` and `business_profile_cancel` in `BusinessProfileScreenTest.kt`.
Re-ran in isolation: **8/8 passing**. Re-ran the full 341-test suite: exactly **12** failures,
byte-for-byte the known baseline set, **zero overlap** with any `businessprofile`/`AppDatabase`/
`Dashboard` file this session touched.

This is recorded here rather than silently folded into "known flakiness," per the Durable
Development Record rule — a real, reproducible discrepancy was found, root-caused with direct
comparative evidence (not assumed), and fixed at the correct layer (test, not production).

## A10. Company isolation — adversarial evidence

Two-layer discipline, matching MVP-1.2's own precedent exactly:

- **DAO** (`BusinessProfileDaoTest`): `profilesAreCompanyIsolatedEvenWithIdenticalTradingNames
  PhonesAndGstin` — two companies given **identical** trading name, phone, and GSTIN; proves they
  are genuinely independent rows (mutating one never affects the other), not a shared row that
  happens to look the same. `aProfileMissingForOneCompanyDoesNotLeakAnotherCompanysProfile` — a
  company with no profile of its own never sees another company's. `logoBelongingToOneCompanyNever
  AppearsForAnother` — a logo path attached to one company is never visible to a sibling.
- **Repository** (`BusinessProfileRepositoryImplTest`): `profiles are company isolated end to end
  through the repository, even with identical content` — the same adversarial case proven again
  through the public repository API, not just the raw DAO.
- **ViewModel** (`BusinessProfileViewModelTest`): `switching companies loads a fresh profile and
  discards any unsaved edit, never mixing data` — the explicit adversarial script from this task's
  own §10 (edit company A, switch to B before saving, verify B's own data loads and A's unsaved
  edit is gone) — proven at the UI-state layer via the same
  `companySession.observeSelectedCompanyId().distinctUntilChanged()` reload pattern already used by
  `ConnectViewModel`/`DincharyaViewModel`.

## A11. Mini-hardening review

Performed explicitly before declaring the milestone complete, cross-checked against this task's
own §15 checklist:

- **Company leakage / stale selected-company state:** A10 above; every company change cancels the
  in-flight `loadJob` and resets to a fresh `BusinessProfileUiState` before loading the new
  company's data — no stale merge possible.
- **Accidental global profile / duplicate profile creation:** structurally impossible —
  `companyId` is the sole primary key, `upsert` uses `OnConflictStrategy.REPLACE`.
- **Incorrect profile after company switch:** `load()` sets `form`/`savedForm` atomically from the
  freshly-fetched profile — never a partial/mixed state.
- **Save race:** the Save button is `enabled = !state.isSaving`, preventing a double-submit tap.
- **Unsaved changes / Cancel correctness:** A8's `savedForm` fix, locked in by test.
- **Long names/addresses/descriptions:** `longTradingNameAndDescriptionRenderWithoutCrashing`
  instrumented test (headline uses `maxLines`/ellipsis; other fields wrap naturally, no crash).
- **Malformed optional values:** no format validation, by design (matches this codebase's own
  established convention for phone/email/etc. free-text fields).
- **Missing/deleted/invalid/oversized logo:** handled entirely at the `BusinessProfileLogoStore`
  layer (A7) — not yet reachable from the UI since the picker is B scope, but the underlying
  contract is real and tested now, not stubbed.
- **Inaccessible controls / colour-only state:** the notice dialog conveys its message as text, not
  colour alone; every interactive element is a labelled `Button`/`OutlinedButton`/`OutlinedTextField`
  — the same self-describing pattern already established (and previously audited) elsewhere in this
  codebase; no icon-only or unlabeled control introduced.
- **Misleading empty states:** "no company selected" (`MasterDataUiError.Message`) and "no profile
  saved yet" (`business_profile_empty`) are two distinct, honestly-worded states, never conflated.
- **False affordances:** none found — Edit/Save/Cancel are all fully wired and functional.
- **Accidental Tally write / network call / Desktop/Connector/catalogue/public-exposure surface:**
  grep-verified zero network/Connector import anywhere in `feature/businessprofile`; zero
  `AndroidManifest.xml` change; zero file under `apps/budcom_desktop` touched.
- **Sensitive logging:** grep-verified zero `Log`/`Timber` call anywhere in the new feature.
- **Path traversal / unsafe URI handling:** A7 above — two independent defensive layers on write
  and read respectively; `contentResolver.openInputStream` failures are caught and mapped to an
  honest `UnreadableSource` failure, never an uncaught exception.
- **Repository/architecture boundaries:** Room → Domain → Repository → Use Case → ViewModel →
  Compose layering preserved exactly; no shortcut/bypass introduced.

**Defects found and fixed:** one — the `CancelEditTapped` reversion bug (A8), caught during this
session's own code review before any test was run, not by an external report. One genuine
instrumented-test gap (A9), root-caused and fixed at the test level.

**No other genuine defect found.**

## A12. Tests

- **New JVM tests (17):** `BusinessProfileRepositoryImplTest` — 9 (null-when-unsaved, create sets
  `createdAt == updatedAt`, re-save preserves `createdAt`/advances `updatedAt`, save never touches
  an existing `logoAssetPath`, `updateLogo` returns `null` with no profile yet, `updateLogo`
  persists the store's returned path, `updateLogo` leaves the row untouched on store failure,
  `clearLogo` removes the reference and calls the store, company isolation end-to-end);
  `BusinessProfileViewModelTest` — 8 (no-company error state, no-profile empty state (not an
  error), load populates both `form` and `savedForm`, save-without-trading-name shows a notice and
  never calls the repository, save-with-trading-name persists and exits edit mode, Cancel reverts
  to last-saved values, company-switch discards unsaved edits and never mixes data, a repository
  save failure surfaces a notice without crashing).
- **New instrumented tests (18), run and passing on device `10BF44124K000E3`:**
  `AppDatabaseMigrationTest.migrate9To10_...` (1, real production migration object, see A5);
  `BusinessProfileDaoTest` (new file, 7 — null-when-none, round-trip, replace-not-duplicate,
  logo-only update preserves other fields, plus the three company-isolation adversarial cases in
  A10); `BusinessProfileScreenTest` (new file, 8 — loading, error+retry, empty+setup, populated
  view with optional-field-omission, edit-form field/save events, Cancel event, long-content
  rendering, notice dialog show/dismiss — see A9 for the `performScrollTo` fix);
  `DashboardScreenTest.businessProfilePrimaryEntryIsShownAndNavigable` (1, new — Dashboard entry
  wiring) and `DashboardViewModelTest`'s existing navigation-events test extended with
  `OpenBusinessProfile`/`BusinessProfile` (not a new test, a mechanical extension of an existing
  one, not counted separately above).

## A13. Full regression results

- `testDebugUnitTest` / `testReleaseUnitTest`: **1,255/1,255 passing** both (1,238 prior +17 new
  this session).
- `lintDebug` / `lintRelease`: **0 errors** both (confirmed via report XML `severity="Error"`
  count, not just the console summary).
- `assembleDebug`, `assembleRelease`, `assembleDebugAndroidTest`: all `BUILD SUCCESSFUL`.
- `connectedDebugAndroidTest` on device `10BF44124K000E3` (`I2407`/`I2407i`): **329/341 passing**.
  The 12 failures are the exact same pre-existing device-viewport-artifact class documented since
  MVP-1.1-B (`DashboardScreenTest` ×2, `DiagnosticsScreenTest`, `LedgerStatementScreenTest` ×2,
  `SecurePairingScreenTest`, `ServerConfigScreenTest` ×2, `SettingsScreenTest` ×2, `SyncScreenTest`,
  `VoucherDetailsScreenTest`) — **zero overlap** with any `businessprofile`/`AppDatabase`/
  `Dashboard` file this session touched. See A9 for the genuine two-failure discrepancy found,
  investigated, and resolved before reaching this clean baseline.

## A14. Version / artifacts

**Version deliberately not bumped this session.** Per this task's own instruction to defer the
single coherent version bump to the final freeze (Part C) and this codebase's own established
precedent (every prior milestone's A/B/C sub-parts left `versionName`/`versionCode` unchanged,
bumping only at the integrated-hardening freeze step): `versionName`/`versionCode` remain
`0.1.1-continuity.27`/`28`, unchanged from the MVP-1.2 freeze. No APK artifact was produced for
distribution this session (only the standard `assembleDebug`/`assembleRelease` regression-gate
builds, not preserved as named release candidates).

## A15. Accepted limitations (explicit)

- No logo picker/display UI yet — the storage layer is real and tested (A7), but nothing in A's
  screen lets a user actually attach a logo. Reserved for Part B.
- No format validation on phone/email/GSTIN/website/pincode — by design, matching this codebase's
  established free-text convention; not planned to change without an explicit product decision.
- The 12 pre-existing device-viewport instrumented failures remain accepted, re-confirmed with
  zero overlap and zero new failures this session (A13).

## A16. Exact NEXT TASK

MVP-1.3-A's acceptance gate is fully green: compiles; JVM regression clean both variants; both
lints 0 errors; all three assembles green; instrumented suite at the exact known-12 baseline with
zero overlap (after a genuinely investigated and resolved 2-failure discrepancy, A9);
company-isolation tests pass with dedicated adversarial evidence at three layers; zero P0/P1
defect; zero security/data-integrity issue; zero architecture contradiction; zero accidental scope
expansion (logo UI deliberately deferred to B, not silently included).

**MVP-1.3-A ACCEPTANCE GATE PASSED — AUTOMATIC CONTINUATION TO MVP-1.3-B AUTHORIZED**, per this
task's own explicit "continue A → B → C automatically when the preceding gate passes" instruction.
