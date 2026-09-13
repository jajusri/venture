# Android Settings Foundation

**Date:** 2026-07-27  
**Scope:** `apps/venture_android` Settings Foundation  
**Purpose:** Document settings architecture, DataStore usage, available preferences, navigation, and persistence.

---

## Principle

Settings exposes only **implemented** configuration and navigation into existing screens.

- No placeholder toggles
- No disabled future options
- No invented preferences that do nothing

---

## Existing configuration inventory (inspected)

| Source | Store / location | Settings treatment |
| --- | --- | --- |
| Connector base URL | DataStore `connector_config` (`ConnectorBaseUrlLocalDataSource`) | Display + navigate to Server Configuration (no second editor) |
| Selected company id | DataStore `company_selection` | Display + navigate to Company Selection |
| Theme | **New** DataStore `app_settings` key `theme_preference` | System / Light / Dark; applies immediately |
| App identity | `BuildConfig` + package name | About section only (no build date invented) |
| Sync summary | `ObserveSyncStatusPort` (in-process) | Display + navigate to Sync |
| Connector version | `GET /health` → `connectorVersion` via `ConnectorStatusPort` | About when probe succeeds; else “Not available” |
| Network logging / Timber | BuildConfig / debug-only | Not user-configurable; no developer menu |

---

## Architecture

```text
Compose (SettingsScreen)
  → SettingsViewModel
    → ObserveSettingsSnapshotUseCase / SetThemePreferenceUseCase / RefreshSettingsConnectorFactsUseCase
      → ThemePreferencesRepository → ThemePreferencesStore (DataStore app_settings)
      → ConnectorStatusPort / CompanySessionPort / ObserveSyncStatusPort / ApplicationIdentityPort
```

Compose never accesses DataStore directly.

---

## Available settings

| Category | Items | Behavior |
| --- | --- | --- |
| Connection | Base URL, online/offline | Navigate to Server Configuration |
| Appearance | Theme System / Light / Dark | Persist + apply without restart |
| Company | Current company | Navigate to Company Selection |
| Synchronization | Sync status summary | Navigate to Sync |
| Diagnostics | Entry | Navigate to Diagnostics |
| About | App name, package, version name/code, build type, Connector version when known | Refresh Connector facts |

---

## Theme persistence

- DataStore file: `app_settings`
- Key: `theme_preference`
- Values: `system` (default), `light`, `dark`
- Unrecognized stored values surface as a configuration error (not silently ignored)
- `MainActivity` observes `ThemePreferencesRepository` and passes `darkTheme` into `VentureTheme`

---

## Navigation

Dashboard → Settings → Server Configuration | Company | Sync | Diagnostics

Does not duplicate those screens.

---

## Non-goals

- Export logs, clear cache, restart Connector
- Search defaults / sync interval preferences (not implemented)
- Developer hidden menus
- WorkManager / analytics / crash reporting settings
