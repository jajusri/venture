# VENTURE Android — Production Validation

**Scope:** Production Validation milestone (`apps/venture_android`)  
**Status:** **Partial pass** — clean-install active-company hydration from Connector session **PASS** (2026-07-28); broader E2E checklist still incomplete

---

## Clean-install validation — active company hydration (2026-07-28)

**Test date:** 2026-07-28 (~22:55 IST)  
**Purpose:** Verify a brand-new Android installation restores the active company from Connector session without manual company selection.  
**Result:** **PASS**

### Environment

| Item | Value |
| --- | --- |
| Host OS | Windows 10.0.26200 |
| Device | Physical **CPH2707** (OPPO), serial `3C15CB00C6Z00000` |
| Android version | **16** (API **36**) |
| Application | `0.1.0` debug (`com.jajusri.venture.debug`) |
| Install method | `adb uninstall` (confirmed package removed) → `:app:installDebug` |
| Connector | Live on host `127.0.0.1:8080`, version **0.3.1** |
| Reachability | `adb reverse tcp:8080 tcp:8080`; validation build used temporary `BuildConfig.CONNECTOR_BASE_URL=http://127.0.0.1:8080/` for physical+reverse only, then **reverted** to `http://10.0.2.2:8080/` |
| Pre-existing Connector session | `selectedCompany`: **Venture-Test-01** (`venture-test-01`), `connectionStatus`: `connected` |
| Manual company selection | **None** |

### Procedure

1. Uninstalled `com.jajusri.venture.debug` (clean app data).  
2. Built and installed latest debug APK from current source (including session-hydrate fix).  
3. Confirmed Connector `GET /session` still had Venture-Test-01.  
4. Cold-started `MainActivity` with no UI interaction for company selection.  
5. Dumped UI hierarchy after init (~10–12s).

### Observed UI (uiautomator)

| Check | Result |
| --- | --- |
| Connector discovered / health | **PASS** — Connection: **Connected**; last health `2026-07-28 22:55:48` |
| Configured URL | `http://127.0.0.1:8080/` |
| Session restored | **PASS** — Session: **Valid** |
| Active company auto-restored | **PASS** — Selected company: **Venture-Test-01 (venture-test-01)** |
| “No company selected” | **Not shown** |
| “Setup Required” | **Not shown** |
| Manual intervention for company | **Not required** |

Banner showed **Partial operational data available** because live Connector **0.3.1** returns **`GET /ready` → 404** (readiness Unknown). That is separate from company hydration and does not block company restore.

### Connector evidence (host)

- `GET /health` → 200; service message includes `Selected company: Venture-Test-01`; `connectorVersion`: `0.3.1`  
- `GET /session` → 200; `session.selectedCompany.name` = `Venture-Test-01`  
- `POST /session/validate` previously confirmed SUCCESS for same company  
- `GET /ready` → 404 on this Connector build

### Artifacts

Captured under host temp `venture_validation_clean/` (UI dump `final.xml`, logcat, connector JSON snapshots).

### Confirmation

**Active company is restored automatically from Connector session on clean install** when the Connector is reachable and already has a selected company. Aligns with decision *Connector Session is the authoritative source for active company state* (`docs/DECISIONS.md`, 2026-07-28).

---

## Earlier incomplete run (2026-07-27)

**Git tip at start:** `be0b960`  
**Status then:** Incomplete — automated gates passed; no usable device/Connector in that window.

| Item | Value (2026-07-27) |
| --- | --- |
| Physical device | None |
| Emulator | Attempted; remained `adb offline` |
| Connector | Unreachable on `:8080` |

Automated gates at that time: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin` — PASS.

---

## Related defect fixed before this clean-install pass

| Defect | Fix |
| --- | --- |
| Fresh Android ignored Connector session company; showed empty local cache as “no company” | `CompanyRepositoryImpl` hydrates from `GET /session` when local cache blank; `RefreshDashboardUseCase` restores before reading local id |

---

## Remaining gaps (not blockers for company-hydrate claim)

1. Full MVP-1 checklist (master data, vouchers, sync, diagnostics, lifecycle matrix) not re-run end-to-end in this pass.  
2. Live `GET /ready` 404 on Connector 0.3.1 → readiness Unknown / partial banner.  
3. Emulator API 36.1 still unreliable on this host; physical + `adb reverse` used for this pass.  
4. Default `BuildConfig` remains emulator `10.0.2.2` after validation (physical pilots must configure URL or use reverse + saved URL).

---

## Release confidence (company hydration slice)

**90%** for “clean install restores Connector-selected company when Connector is reachable.”  
Overall Production Validation milestone: still **partial** until remaining checklist areas are closed.

---

## Final verdict (this pass)

**CLEAN-INSTALL ACTIVE COMPANY HYDRATION — PASS**

Broader Production Validation milestone remains open for remaining E2E areas.
