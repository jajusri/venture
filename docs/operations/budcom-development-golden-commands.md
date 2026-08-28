# BUDCOM Development — Golden Commands

Agents must prefer these canonical scripts over improvised shell sequences.

## Doctor (read-only environment check)

```powershell
.\scripts\budcom-doctor.ps1
```

## Desktop

**Status:**

```powershell
.\scripts\budcom-desktop.ps1 -Status
```

**Build, launch, verify:**

```powershell
.\scripts\budcom-desktop.ps1 -Build -Launch -Verify
```

**Restart:**

```powershell
.\scripts\budcom-desktop.ps1 -Restart -Verify
```

See also: [desktop-development-launch.md](./desktop-development-launch.md)

## Android

**Status (requires device serial when multiple devices connected):**

```powershell
.\scripts\budcom-android.ps1 -Status -DeviceSerial <SERIAL>
```

**Deploy (physical validation package = ProdDebug):**

```powershell
.\scripts\budcom-android.ps1 `
  -DeviceSerial <SERIAL> `
  -Variant ProdDebug `
  -Build -Install -Launch -Verify
```

### Android variants

| Variant | Package | Use |
|---------|---------|-----|
| `ProdDebug` | `com.budcom.android.debug` | Physical validation, primary dev install |
| `DevDebug` | `com.budcom.android.dev.debug` | Isolated DEV app (coexists with ProdDebug) |
| `ProdRelease` | `com.budcom.android` | Release-shaped prod package |
| `DevRelease` | `com.budcom.android.dev` | Release-shaped dev package |

The Android script refuses silent downgrades, verifies APK package/version after install, never wipes app data, and requires a **clean worktree** plus `last-build.json` from the current HEAD before `-Install`.

## Physical validation evidence (separate)

Buying-cycle screenshot/log helpers remain in:

```powershell
.\scripts\validation\android-physical-buying-cycle.ps1 -StatusOnly -DeviceSerial <SERIAL>
```

## Environment defaults (this machine)

| Item | Path |
|------|------|
| Android JDK | `C:\Program Files\Android\Android Studio\jbr` |
| Gradle flags | `--no-daemon --no-parallel --max-workers=1` |
| Desktop user data | `%APPDATA%\@budcom\desktop` |
