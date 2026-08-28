# BUDCOM Desktop — canonical development launch

Use the repository launcher script. Do **not** improvise manual `npm start`, raw
`electron .`, or generic `taskkill` commands unless this script is broken and you
are debugging the script itself.

## Commands

**Status (read-only):**

```powershell
.\scripts\budcom-desktop.ps1 -Status
```

**Build, start, and verify:**

```powershell
.\scripts\budcom-desktop.ps1 -Build -Launch -Verify
```

**Restart (repo-scoped stop + relaunch + verify):**

```powershell
.\scripts\budcom-desktop.ps1 -Restart -Verify
```

## What the script uses

| Item | Value |
|------|--------|
| Project | `apps/budcom_desktop` |
| Build | `npm run build` (TypeScript compile + asset copy) |
| Dev launch | `node_modules/electron/dist/electron.exe .` (detached) |
| Package | `@budcom/desktop` (`com.budcom.desktop`) |
| User data | `%APPDATA%\@budcom\desktop` |
| Connector default port | `8080` (overridden by `desktop-config.json` when present) |

See also: [budcom-development-golden-commands.md](./budcom-development-golden-commands.md)

## Notes

- Clears `ELECTRON_RUN_AS_NODE` for the child process (required for a real GUI window).
- Identifies BUDCOM processes by workspace path / `budcom_desktop` markers — never kills unrelated Node/Electron apps.
- Launcher logs: `%TEMP%\budcom-desktop-launcher\`
- Desktop app logs: `%APPDATA%\@budcom\desktop\logs\budcom-desktop.log`
