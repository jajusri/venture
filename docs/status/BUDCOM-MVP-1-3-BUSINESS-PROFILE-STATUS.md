# BUDCOM MVP-1.3 — Business Profile — Status

**Status:** Part A complete — acceptance gate PASSED. Part B complete — acceptance gate PASSED.
Part C (integrated hardening, freeze) complete — **MVP-1.3 COMPLETE / FROZEN.**
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

---

# Part B — MVP-1.3-B: Profile Presentation & Sharing Foundation

**Starting HEAD:** same working tree as Part A's own final commits (`6731d38`, `ae54647`,
`52ac893`, `3f08c56`), continued in the same session with no intervening state change.

## B1. Scope actually implemented

Exactly this task's own MVP-1.3-B line item:

1. Logo picker UI — `ActivityResultContracts.PickVisualMedia()` (the system Photo Picker; no
   runtime permission needed on any supported Android version) wired via a one-shot
   `BusinessProfileEffect.RequestLogoPick` (the established `MutableSharedFlow<Effect>
   (extraBufferCapacity = 1)` pattern already used by `PartyXmlExportViewModel`), since launching
   an `ActivityResultLauncher` is an Activity-scoped action a ViewModel cannot perform directly.
2. Logo display — `BitmapFactory.decodeFile` off the main thread (the same pattern already
   established by `PdfPreviewScreen`'s page rendering; no Coil/Glide dependency exists in this
   codebase and none was added), converted to `ImageBitmap` for Compose's `Image`.
3. Replace/Remove logo actions, wired through A's already-built (but previously unused-by-UI)
   `UpdateBusinessProfileLogoUseCase`/`ClearBusinessProfileLogoUseCase`/
   `ResolveBusinessProfileLogoFileUseCase` (the last one newly added this session, see B2).
4. Presentation polish: the logo now appears prominently above the trading name in both view and
   edit modes; a busy indicator during logo updates; plain-language failure messages for every
   `BusinessProfileLogoFailureReason`.
5. The internal "sharing foundation" data boundary — `BusinessProfileShareSnapshot` (B7).
6. A genuine cross-company data-race defect found and fixed during this session's own hardening
   review, not shipped and discovered later (B5).
7. Full test coverage per this task's own test-strategy requirements (B6).
8. Mini-hardening review (B5) and full regression (B8).

**Explicitly not built** (C scope or beyond, confirmed untouched): any actual Share/Export
action for the business-card snapshot (B7's own boundary), image cropping/editing (no locked
document asks for one, and this codebase has no cropping-UI precedent to reuse), any Catalogue or
Vartalap code, any public/cloud transport.

## B2. New repository surface — `resolveLogoFile`

`BusinessProfileRepository.resolveLogoFile(logoAssetPath: String?): File?` — a thin delegation to
`BusinessProfileLogoStore.resolveLogoFile`, added because A's UI never needed to *read* a logo
file, only store one. Re-validates via the same path-traversal-defended containment check on every
read (A7), even though the path originates from this app's own database — so a corrupted/tampered
row can never resolve outside the logo directory. Exposed via a new
`ResolveBusinessProfileLogoFileUseCase`. The ViewModel resolves and caches the `File` in
`BusinessProfileUiState.logoFile` whenever the profile loads or the logo changes, so the Compose
layer only ever decodes an already-validated `File`, never a raw path string.

## B3. UI — logo picker, display, and controls

`BusinessProfileLogo` (new private composable, shared by both view and edit content): a 72dp
circular avatar; decodes off `Dispatchers.IO` inside a `LaunchedEffect(logoFile)`, falling back to
a plain placeholder icon (`Icons.Filled.AccountCircle` — confirmed present in the core
`material-icons-core` jar this codebase already depends on; `Icons.Filled.Business` was tried
first and confirmed **absent** from that jar via direct inspection, so a different, verified icon
was used instead) when there is no logo or decoding fails — `BitmapFactory.decodeFile` returns
`null` on a missing/corrupt file rather than throwing, so this path can never crash.

Edit mode gained an "Add Logo"/"Replace Logo" button (label depends on whether a logo already
exists) plus a "Remove Logo" button shown only when a logo exists, plus a small
`CircularProgressIndicator` while `isUpdatingLogo` is true (both buttons disabled during this
window, preventing a double-submit race). View mode shows the same logo read-only, above the
trading name headline.

## B4. ViewModel — new events, plain-language failure messages

Three new `BusinessProfileEvent`s: `ChangeLogoTapped` (emits the `RequestLogoPick` effect),
`LogoPicked(uri)` (calls `updateBusinessProfileLogo`; `null` result — no profile saved yet — shows
"Save the Business Profile before adding a logo."; `Failure` maps each
`BusinessProfileLogoFailureReason` to a specific, honest, non-technical message, e.g. "That image
is too large (max 5 MB)." for `FileTooLarge`), `ClearLogoTapped`.

## B5. Self-caught defect — a cross-company data race in async save/logo operations

**Found during this session's own hardening review, before any external report:** `save()`,
`updateLogo()`, and `clearLogo()` each launch a `viewModelScope.launch { ... }` coroutine that ends
by unconditionally calling `_uiState.update { it.copy(...) }` with the operation's result. If the
user switched companies *while* one of these was still in flight (e.g., tapped Save, then
immediately switched to a different company before the write finished), the `init` block's
company-change collector would correctly load the new company's fresh state — but once the
stale save/logo operation for the *previous* company finally completed, its `_uiState.update`
call would unconditionally overwrite whatever was currently showing, silently corrupting the new
company's on-screen state with the old company's result. This is exactly the class of defect this
task's own §10 company-switching acceptance criteria exist to catch, just in the async-completion
path rather than the synchronous-load path (which `loadJob?.cancel()` already guarded correctly).

**Fix:** a new `updateIfStillOnCompany(requestedCompanyId) { ... }` helper wraps every async
completion (`save()`'s success/failure branches, `updateLogo()`'s three outcome branches,
`clearLogo()`'s completion) — the state update is applied only if `state.companyId` still equals
the company the operation was actually performed for; otherwise it is silently discarded (the
underlying write itself still completes and persists correctly — the guard only prevents the
*stale UI update*, never the data). Locked in by two dedicated JVM tests using a controllable
`CompletableDeferred` gate in the fake repository to force the exact interleaving: `a save that
completes after switching companies never overwrites the new company's state`, `a logo update that
completes after switching companies never overwrites the new company's state`. Both assert the new
company's state survives untouched and separately assert the stale write still landed correctly on
disk for the original company — the fix silences the *symptom* without silently dropping the
*write*.

**No other genuine defect found** during this session's review of the new B code (logo
picker/display/store wiring, share-snapshot mapper).

## B6. Tests

- **New JVM tests (11):** `BusinessProfileShareSnapshotTest` (new file, 3 — full field mapping
  with the companyId/timestamps/raw-path exclusion verified by the type shape itself, a fully
  blank address produces `null` not an empty string of commas, a partial address omits only the
  missing components); `BusinessProfileViewModelTest` — 7 new (change-logo-tapped emits the
  effect, picking a logo before any profile exists shows the correct notice and never calls the
  repository, picking a logo for an existing profile resolves and stores the file, a rejected logo
  shows its specific reason and never touches the stored path, clearing a logo removes it and
  shows a confirmation, plus the two B5 race-condition regression tests);
  `BusinessProfileRepositoryImplTest` — 1 new (`resolveLogoFile` delegates to the logo store and
  returns `null` for a blank path).
- **New instrumented tests (4), run and passing on device `10BF44124K000E3`:**
  `BusinessProfileScreenTest` — edit mode with no logo shows only "Add Logo" and emits
  `ChangeLogoTapped`; edit mode with an existing logo shows "Replace"/"Remove" and renders the
  actual decoded image (a real tiny PNG fixture written to the test app's cache dir, proving the
  `BitmapFactory.decodeFile` path genuinely works end-to-end, not just that the button exists);
  the busy indicator shows and disables the change button while `isUpdatingLogo`; view mode shows
  the logo above the trading name.

## B7. The internal "sharing foundation" data boundary

Per PDL-019's own conceptual chain (Business Profile → future Catalogue → future Vartalap) and
this task's explicit "internal data boundary only, no public transport" requirement:
`BusinessProfile.toShareSnapshot(): BusinessProfileShareSnapshot` — a pure, side-effect-free
mapping to a plain "business card" shape (trading name, legal name, one formatted address string,
phone, email, GSTIN, website, description). Deliberately excludes `companyId`, `createdAt`/
`updatedAt`, and the raw `logoAssetPath` file-system path — not by convention but by the type's
own shape, so a future caller cannot accidentally leak an internal path or identifier through this
surface.

**Deliberately not built, and not a gap:** no Android `Intent`, no `Intent.ACTION_SEND`, no file
export, no network call, no UI button anywhere invokes this type. It exists purely as the stable
data shape a later, explicitly-authorized Vartalap milestone can build a real share feature on top
of — building the actual share mechanism now would be premature integration into product territory
this milestone's own governing decisions (PDL-019 §5) explicitly reserve for later. Covered by
3 pure-function JVM tests (B6); no instrumented coverage needed since nothing here touches Android
APIs.

## B8. Mini-hardening review

Cross-checked against this task's own checklist, focused on what's new in B:

- **Company isolation:** unaffected by B's changes at the data layer (logo storage/resolution were
  already company-keyed in A); the one genuine new risk (B5) was found and fixed.
- **Save/logo race:** B5 above — the dominant finding this part.
- **Missing/corrupt logo:** `BitmapFactory.decodeFile` returning `null` is handled by falling back
  to the placeholder icon, verified by the instrumented "no logo" test and reasoned through for the
  corrupt-file case (same code path, same fallback).
- **Oversized/wrong-type logo:** already defended at the storage layer (A7); B's new failure
  messages surface each specific reason to the user rather than a generic "something went wrong."
- **Accessibility:** the logo `Box` carries a `contentDescription` via `Modifier.semantics {}`
  (the child `Image` is marked `contentDescription = null` to avoid double-announcing the same
  element — the established pattern for a decorative-inside-a-described-container composable);
  every button remains a labelled `OutlinedButton`/`Button` with visible text, matching this
  codebase's established self-describing-control convention; plain hardcoded content-description
  strings match the existing `PdfPreviewScreen`/`Icon` precedent in this codebase (not
  string-resourced — confirmed this is the established pattern, not an oversight).
- **Sensitive logging:** grep-verified zero `Log`/`Timber` call anywhere in the new B code.
- **No accidental Tally/network/Connector/public-exposure surface:** grep-verified zero new
  network/Connector import; the Photo Picker is a local, OS-level, offline mechanism — no
  permission dialog, no network call, no data leaves the device.
- **No accidental scope expansion:** the share-snapshot type has zero UI entry point anywhere
  (B7) — verified by grep, no composable references `toShareSnapshot`.

**Defects found and fixed:** one — the cross-company async-completion race (B5), caught during
this session's own review before any test was run against it, not by an external report.

## B9. Full regression results

- `testDebugUnitTest` / `testReleaseUnitTest`: **1,266/1,266 passing** both (1,255 prior + 11 new
  this session).
- `lintDebug` / `lintRelease`: **0 errors** both (confirmed via report XML `severity="Error"`
  count). Both lint runs took unusually long this session (the first genuinely-cold, non-cached
  full lint analysis since MVP-1.2's freeze) — traced via a JVM thread dump to a known, external,
  Windows-specific performance characteristic of the JetBrains Kotlin Analysis API's
  `GlobalSearchScope` union-scope containment check (slow `WindowsPathParser` path normalization
  called once per library root per file analyzed), not a code defect or a genuine hang — confirmed
  by the thread dump showing continuous, legitimate CPU-bound work (not a deadlock/blocked state)
  before the run completed successfully with 0 errors.
- `assembleDebug`, `assembleDebugAndroidTest`: `BUILD SUCCESSFUL` (unchanged from A, no new
  compilation needed for these two).
- `assembleRelease`: `BUILD SUCCESSFUL` in 3m17s (R8-minified, confirming the Hilt DI graph for
  the new `BusinessProfileModule` additions resolves correctly under minification — same
  discipline as A/MVP-1.2-E's own precedent). Also hit the JIT-compilation pathology (B9's own
  lint note) on the first attempt — the same `-XX:TieredStopAtLevel=1` workaround resolved it.
- `connectedDebugAndroidTest` (`BusinessProfileScreenTest` only, isolated): **12/12 passing** on
  device `10BF44124K000E3` — the 8 tests from A plus 4 new logo-UI tests, all clean on the first
  attempt with no off-screen/scroll issue this time (the new logo controls sit above the
  already-scroll-fixed Save/Cancel buttons, not below any new content that would push them further
  down).

## B10. Accepted limitations (explicit)

- No image cropping/editing tool — deliberate (B1), matches this task's own "no locked document
  asks for one" boundary.
- The share-snapshot type has no consumer yet — deliberate (B7), the correct scope boundary for
  this milestone.
- All Part A accepted limitations (§A15) remain unchanged and still apply, minus the "no logo
  picker/display UI yet" item, which B resolves.

## B11. ACCEPTANCE GATE RESULT

Every mandatory gate item verified with evidence above: compiles; JVM regression clean both
variants (1,266/1,266); both lints 0 errors; instrumented logo-UI tests 12/12 clean;
company-isolation preserved and a genuine async-race defect found and fixed with dedicated
regression tests (B5); accessibility maintained; zero P0/P1 defect remaining; zero
security/data-integrity issue; zero accidental scope expansion into Catalogue/Vartalap/public
infrastructure (B7's boundary explicitly enforced and tested).

**MVP-1.3-B ACCEPTANCE GATE PASSED — AUTOMATIC CONTINUATION TO MVP-1.3-C AUTHORIZED**, per this
task's own explicit "continue A → B → C automatically when the preceding gate passes" instruction.
All three assembles (including R8-minified `assembleRelease`) confirmed green in the same session
(B9).

## B12. Tooling note — a JIT-compilation pathology encountered and worked around this session

Both `lintDebug`/`lintRelease` and `assembleRelease` (via its `lintVitalAnalyzeRelease`/
`minifyReleaseWithR8` steps) hit an unusually long stall on first attempt this session — up to
15+ minutes with zero visible progress. Investigated with a JVM thread dump (`jcmd <pid>
Thread.print`) rather than assumed to be a hang: found the daemon's C2 JIT compiler thread
spending 70-80%+ of elapsed wall-clock time continuously compiling a single small method inside
the JetBrains Kotlin Analysis API's Lint/UAST bridge
(`KotlinStaticPsiDeclarationFromBinaryModuleProvider::getProperties`) — a known class of JIT
pathology on this JDK/tooling combination, compounded by a genuinely slow
`GlobalSearchScope`-union containment check that calls `WindowsPathParser` once per library root
per analyzed file (Windows-specific path-resolution overhead). **Not a code defect, not a
deadlock** — confirmed by the thread dump showing real, continuous CPU-bound work throughout, and
by every affected build eventually completing successfully with 0 lint errors once given enough
time or once C2 was disabled. **Workaround applied:** `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`
(disables C2, keeps C1) for the specific `lintDebug`/`lintRelease`/`assembleRelease` invocations
that hit this — a session-local environment variable, never written into any tracked project file
(`gradle.properties` was deliberately left unchanged, since this is a local-machine/JDK-version
tooling characteristic, not a project-wide fix). Recorded here so a future session on this same
machine recognizes the symptom immediately rather than re-diagnosing it from scratch.

---

# Part C — MVP-1.3-C: Integrated Hardening, Candidate Build & Freeze

**Starting HEAD:** `cb585fa571a847edc764bb1f3088b2a6d697516d` (`docs: MVP-1.3-B Business Profile
status -- acceptance gate passed`), working tree clean, continued in the same session with no
intervening state change.

## C1. Scope actually implemented

Not a new feature — an integrated audit of Parts A and B working together, the single coherent
MVP-1.3 version bump, final candidate APKs, installation and smoke test on the owner device, full
regression, and this Durable Development Record, exactly matching MVP-1.2-E's own precedent for
what an integrated-hardening-and-freeze part does.

## C2. Integrated hardening review — A+B together

Cross-checked the whole feature (data → domain → repository → storage → ViewModel → Compose) as
one system, not per-part in isolation:

- **Every use case has a real UI caller.** `GetBusinessProfileUseCase`/`SaveBusinessProfileUseCase`
  (A), `UpdateBusinessProfileLogoUseCase`/`ClearBusinessProfileLogoUseCase`/
  `ResolveBusinessProfileLogoFileUseCase` (B) — none exists as dead, unwired capability. (This is
  the exact class of gap MVP-1.2-E's own audit found and fixed for `SetNoteCompletionUseCase`; this
  feature has no equivalent gap.)
- **Company isolation holds end-to-end**, re-verified by direct re-reading of the final ViewModel
  (not re-run of tests alone): every DAO query is `companyId`-scoped; the B5 async-race guard
  (`updateIfStillOnCompany`) covers all three mutating operations (save, logo update, logo clear);
  the `init` block's `loadJob?.cancel()` covers the read path. No new isolation gap found.
- **Reviewed, not fixed — a deliberate, documented UX characteristic:** the logo Add/Replace/Remove
  actions call the repository immediately (not staged into `form`/`savedForm` the way text-field
  edits are), so tapping Cancel after changing a logo does **not** revert the logo, only the text
  fields. Considered whether this is a defect: it is not — staging a logo change would require
  holding the picked `Uri`'s bytes in memory without writing them until Save, then discarding on
  Cancel, a materially more complex flow than this milestone's own "smallest excellent version"
  principle warrants, and the Remove Logo action already gives an immediate, one-tap undo path if a
  user regrets a change. Matches the common "photo changes apply immediately" pattern used by many
  profile-editing UIs. Recorded as an accepted, reviewed design characteristic (C6), not a silently
  missed defect.
- **Reviewed, not fixed — a narrow, low-impact notice-race:** `notice` is one shared field; if a
  user manages to have both a text-field save and a logo update in flight at the same moment (an
  unusual sequence — tap Save, then immediately tap Change Logo and complete the picker before the
  save's disk write finishes), whichever operation's completion handler runs last overwrites the
  other's notice message. The underlying data written by both operations is always correct either
  way (B5's guard already ensures no state corruption) — only a transient confirmation message could
  be superseded. Considered and rejected building a queued-notice system for this: the window is
  narrow, the consequence is cosmetic (a missed toast, not a lost edit or wrong data), and a
  notification queue is disproportionate machinery for a single-user local-only screen. Recorded as
  an accepted limitation (C6).
- **No accidental Tally/network/Desktop/Connector/manifest/public-exposure surface anywhere in A+B
  combined** — re-confirmed by a fresh grep across the whole `feature/businessprofile` tree this
  session (zero network/Connector import, zero `Log`/`Timber` call, zero `AndroidManifest.xml`
  diff, zero file under `apps/budcom_desktop` touched).
- **Migration integrity** — `MIGRATION_9_10` unchanged since A, already byte-for-byte verified
  against Room's generated schema (A5) and proven via a real `MigrationTestHelper` run (A5); B
  introduced zero schema change, `DatabaseConstants.VERSION` remains `10`.

**Defects found this part:** none new. The two items above were reviewed and consciously accepted,
not silently overlooked — recorded explicitly rather than left implicit.

## C3. Version bump — the single coherent MVP-1.3 freeze bump

`apps/budcom_android/app/build.gradle.kts`: `versionCode` `28` → **`29`**, `versionName`
`"0.1.1-continuity.27"` → **`"0.1.1-continuity.28"`**. Performed only now, after A and B's full
regressions were independently green and this part's own fresh full regression (C4) also passed —
matching this task's own explicit "defer to a coherent milestone boundary" instruction and this
codebase's established precedent (every prior MVP-1.1/1.2 sub-part deferred its bump to the
integrated-hardening freeze step).

## C4. Full regression results (post version-bump)

- `testDebugUnitTest` / `testReleaseUnitTest`: **1,266/1,266 passing** both (unchanged from B — no
  new JVM test added in C, a version-only source change does not add tests). Both runs performed
  with full JIT (not the C2-disabled workaround, since these tests don't hit the lint pathology) —
  confirms the `VoucherRepositoryImplTest` timing flake seen once mid-session (B9/C context) does
  **not** reproduce under normal execution, consistent with the pre-existing, already-documented
  flake class from MVP-1.1-D/E and MVP-1.2-A.
- `lintDebug` / `lintRelease`: **0 errors** both (confirmed via report XML `severity="Error"` count
  on the post-bump build, not just the console summary). This was the first genuinely-cold, fully
  non-cached lint analysis since MVP-1.2's freeze (version-bump invalidated the `BuildConfig`-keyed
  cache across the whole module) — see B12 for the JIT-pathology tooling note this triggered and
  how it was worked around (`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`, session-local only).
- `assembleDebug`, `assembleRelease`, `assembleDebugAndroidTest`: all `BUILD SUCCESSFUL` (combined
  run, 6m55s, including R8-minified release — confirming the full Hilt DI graph across A+B resolves
  correctly under minification).
- `connectedDebugAndroidTest` on device `10BF44124K000E3` (`I2407`/`I2407i`): **333/345 passing**.
  The 12 failures are byte-for-byte the same pre-existing device-viewport-artifact class documented
  since MVP-1.1-B (`DashboardScreenTest` ×2, `DiagnosticsScreenTest`, `LedgerStatementScreenTest`
  ×2, `SecurePairingScreenTest`, `ServerConfigScreenTest` ×2, `SettingsScreenTest` ×2,
  `SyncScreenTest`, `VoucherDetailsScreenTest`) — **zero overlap** with any `businessprofile`/
  `AppDatabase`/`Dashboard` file touched this milestone. 345 = 341 (MVP-1.3-A baseline) + 4 new
  logo-UI instrumented tests from B, confirming no test was silently lost or duplicated between
  parts.

## C5. Candidate artifacts

**Debug:** `apps/budcom_android/app/build/outputs/apk/debug/app-debug.apk` — **14,562,380 bytes** —
SHA-256 `601b248fb07da27da2d805ab4ed8c06681b02e3c01eaeab22fc3a2994896902f` — `com.budcom.android.debug`,
versionCode 29, versionName `0.1.1-continuity.28`, debuggable (confirmed via `aapt dump badging` on
the actual built APK, not source config alone).

**Release (unsigned, verification only, never a public-release artifact):**
`apps/budcom_android/app/build/outputs/apk/release/app-release-unsigned.apk` — **2,536,316 bytes** —
SHA-256 `dd68728630fb29f54b38ffb60aa8ca8264339810c9b2a7b9921c54b56be94a67` — `com.budcom.android`
(no debug suffix), same version, not debuggable, unsigned (no signing credentials exist anywhere in
this repository — unchanged blocker, tracked separately in
`docs/planning/BUDCOM-MVP-1-PUBLIC-RELEASE-GATE-MATRIX.md`).

## C6. Accepted limitations (explicit, consolidated for the whole MVP-1.3 milestone)

- No image cropping/editing tool (B1) — deliberate, matches this task's own boundary.
- The share-snapshot type (B7) has no consumer yet — deliberate, the correct MVP-1.3 boundary; a
  real share feature is explicitly reserved for a future, separately-authorized Vartalap milestone.
- Cancelling a text-field edit does not revert an already-applied logo change (C2) — a reviewed,
  accepted, deliberate UX characteristic, not a defect; the Remove Logo action provides an
  immediate one-tap undo.
- A narrow notice-message race exists if a save and a logo update are both in flight simultaneously
  (C2) — cosmetic only (a transient confirmation message can be superseded), never a data-integrity
  issue; the underlying B5 guard already ensures correct state either way.
- No format validation on phone/email/GSTIN/website/pincode (A15) — by design, matches this
  codebase's established free-text convention.
- The 12 pre-existing device-viewport instrumented failures remain accepted, re-confirmed with zero
  overlap and zero new failures this session (C4).
- Public release remains blocked on signing credentials only — unrelated to and unchanged by
  MVP-1.3 (tracked separately, see C5).

## C7. Installation and smoke test on device `10BF44124K000E3`

**Critical sequencing honored per this task's own explicit warning:** `connectedDebugAndroidTest`
(C4) was run **before** this installation step, and install state was verified **after** that run
completed, not merely assumed from before it. Direct check (`adb shell pm list packages
com.budcom.android`) confirmed the app was indeed absent post-test-run — `connectedDebugAndroidTest`'s
own documented side effect (installs the app-under-test + test APK before running, uninstalls both
afterward for test hermeticity), the same behavior already documented in MVP-1.2-D/E. This was not
silently reinstalled without note — it is recorded here exactly as it happened.

**Install performed:** `adb install -r apps/budcom_android/app/build/outputs/apk/debug/app-debug.apk`
→ `Performing Streamed Install / Success`. Confirmed via `adb shell dumpsys package
com.budcom.android.debug`: `versionCode=29`, `versionName=0.1.1-continuity.28`,
`firstInstallTime == lastUpdateTime` (a genuine fresh install).

**Smoke test performed directly on device:** launch succeeded (`monkey -p com.budcom.android.debug
-c android.intent.category.LAUNCHER 1`; `MainActivity` became `mFocusedApp`); rendered the correct,
honest first-run **Secure Pairing** screen (screenshot-confirmed, matching the established dark
theme, no visual defect); zero `FATAL EXCEPTION`/`AndroidRuntime:` logcat entries across the launch,
navigation, and relaunch sequence; Back navigation correctly returned to the home launcher
(`com.android.launcher3` became `mFocusedApp` — root-activity behavior, not a crash, app process
stayed alive); relaunch succeeded cleanly (`MainActivity` regained focus, zero new crash entries).

**Explicitly not exercised on-device:** Dashboard, Business Profile entry/screen, Connect, Dincharya
— this environment has no real paired Tally Connector to complete Secure Pairing against, and per
this task's own instruction no attempt was made to fake or bypass pairing. These surfaces are
verified only through the automated instrumented Compose test suite (C4 — real Room/real Compose on
this same device, synthetic state), a distinct and weaker form of evidence than genuine on-device
navigation, stated as such rather than conflated with it — matching MVP-1.2-E's own precedent
exactly.

## C8. Final freeze gate checklist

- [x] Compiles clean, both variants.
- [x] JVM regression clean, both variants (1,266/1,266).
- [x] Both lints 0 errors (report-XML-verified).
- [x] All three assembles green, including R8-minified release.
- [x] Instrumented suite at the exact known-12 baseline, zero overlap, zero new failures.
- [x] Company isolation holds end-to-end, re-verified this part (C2).
- [x] Zero P0/P1 defect.
- [x] Zero security/data-integrity issue.
- [x] Zero accidental scope expansion (Catalogue/Vartalap/public infrastructure all confirmed
      absent, C2/B7).
- [x] Single coherent version bump performed only after all regressions green (C3).
- [x] Candidate APKs built, hashed, sized (C5).
- [x] Device install state verified **after** the connected-test run, not merely before it (C7).
- [x] Smoke test performed with an honest verified-vs-not-verified boundary (C7).
- [x] This Durable Development Record complete (specialist status doc — this file).

## C9. Git — commits and push

Coherent commits for C's own work (version bump + documentation), following the same
per-concern grouping already used for A and B. **Pushed to `origin/main` at this final freeze
step** — explicitly authorized by this task's own "pushing should occur only at the final freeze
unless explicitly instructed otherwise" instruction, now reached. Exact commit hashes and the
post-push verification are recorded in `docs/status/BUDCOM-CURRENT-DEVELOPMENT-STATUS.md` §2/§2a.

## C10. FINAL RESULT

Every C gate item verified with evidence above (C8). MVP-1.3-A, MVP-1.3-B, and MVP-1.3-C are all
complete, tested, documented, version-bumped, built, installed, and smoke-tested.

**MVP-1.3 COMPLETE / FROZEN.**

Per this task's own final stop condition: **STOP. Do not begin MVP-1.4 implementation. Do not begin
further MVP-1.3 work.**

**EXACT NEXT TASK: MVP-1.4 planning/recovery review** (read-only architecture/gap-analysis review,
matching the MVP-1.3 planning session's own precedent — not implementation), pending Product
Owner/technical review of this MVP-1.3 result.
