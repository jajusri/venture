# VENTURE Desktop — canonical development launch

Use the repository launcher script. Do **not** improvise manual `npm start`, raw
`electron .`, or generic `taskkill` commands unless this script is broken and you
are debugging the script itself.

## Commands

**Status (read-only):**

```powershell
.\scripts\venture-desktop.ps1 -Status
```

**Build, start, and verify:**

```powershell
.\scripts\venture-desktop.ps1 -Build -Launch -Verify
```

**Restart (repo-scoped stop + relaunch + verify):**

```powershell
.\scripts\venture-desktop.ps1 -Restart -Verify
```

## What the script uses

| Item | Value |
|------|--------|
| Project | `apps/venture_desktop` |
| Build | `npm run build` (TypeScript compile + asset copy) |
| Dev launch | `node_modules/electron/dist/electron.exe .` (detached) |
| Package | `@venture/desktop` (`com.venture.desktop`) |
| User data | `%APPDATA%\@venture\desktop` |
| Connector default port | `8080` (overridden by `desktop-config.json` when present) |

See also: [venture-development-golden-commands.md](./venture-development-golden-commands.md)

## Notes

- Clears `ELECTRON_RUN_AS_NODE` for the child process (required for a real GUI window).
- Identifies VENTURE processes by workspace path / `venture_desktop` markers — never kills unrelated Node/Electron apps.
- Launcher logs: `%TEMP%\venture-desktop-launcher\`
- Desktop app logs: `%APPDATA%\@venture\desktop\logs\venture-desktop.log`
