import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it, vi } from 'vitest';

import desktopPackage from '../../package.json';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** Extracts the `registerIpcHandler('channel', async (...) => { <body> });` body text via brace counting. */
function extractIpcHandlerBody(source: string, channel: string): string {
  const registrationStart = source.indexOf(`registerIpcHandler('${channel}'`);
  if (registrationStart === -1) {
    throw new Error(`registerIpcHandler('${channel}', ...) not found in main.ts`);
  }
  const bodyStart = source.indexOf('{', source.indexOf('=>', registrationStart));
  let depth = 0;
  for (let i = bodyStart; i < source.length; i += 1) {
    if (source[i] === '{') depth += 1;
    if (source[i] === '}') {
      depth -= 1;
      if (depth === 0) {
        return source.slice(bodyStart, i + 1);
      }
    }
  }
  throw new Error(`Unbalanced braces while extracting handler body for '${channel}'`);
}

vi.mock('electron', () => {
  class BrowserWindow {
    public id = 1;
    public webPreferences: unknown;
    public webContents = {
      on: vi.fn(),
      once: vi.fn((_event: string, callback: () => void) => {
        callback();
      }),
      setWindowOpenHandler: vi.fn(),
      reload: vi.fn().mockResolvedValue(undefined),
      send: vi.fn(),
    };
    constructor(options: { webPreferences?: unknown }) {
      this.webPreferences = options.webPreferences;
    }
    loadFile = vi.fn().mockResolvedValue(undefined);
    show = vi.fn();
    isDestroyed = vi.fn(() => false);
    once = vi.fn((_event: string, callback: () => void) => {
      callback();
    });
    on = vi.fn();
  }
  return {
    app: {
      whenReady: vi.fn().mockResolvedValue(undefined),
      on: vi.fn(),
      quit: vi.fn(),
      isPackaged: false,
      getPath: vi.fn(() => '/tmp/budcom-test-userdata'),
      getVersion: vi.fn(() => desktopPackage.version),
    },
    BrowserWindow,
    ipcMain: { handle: vi.fn() },
    shell: {
      openPath: vi.fn().mockResolvedValue(''),
    },
  };
});

describe('main window creation', () => {
  it('creates a sandboxed browser window with preload', async () => {
    const { createMainWindow } = await import('../../src/main/main.js');
    const window = createMainWindow();
    expect(window).toBeDefined();
    expect(window.loadFile).toHaveBeenCalled();
    expect((window as unknown as { webPreferences: { contextIsolation: boolean } }).webPreferences).toMatchObject({
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    });
  });
});

describe('application startup', () => {
  it('registers ipc handlers on bootstrap', async () => {
    const electron = await import('electron');
    const { bootstrapApp } = await import('../../src/main/main.js');
    bootstrapApp();
    expect(electron.ipcMain.handle).toHaveBeenCalledWith('desktop:get-dashboard', expect.any(Function));
    expect(electron.ipcMain.handle).toHaveBeenCalledWith('desktop:get-logs', expect.any(Function));
    expect(electron.ipcMain.handle).toHaveBeenCalledWith('desktop:get-lifecycle-status', expect.any(Function));
    expect(electron.ipcMain.handle).toHaveBeenCalledWith('desktop:start-connector', expect.any(Function));
    expect(electron.ipcMain.handle).toHaveBeenCalledWith('desktop:get-settings', expect.any(Function));
    expect(electron.ipcMain.handle).toHaveBeenCalledWith('desktop:get-diagnostics', expect.any(Function));
    expect(electron.ipcMain.handle).toHaveBeenCalledWith('desktop:save-settings', expect.any(Function));
  });
});

describe('manual lifecycle-command routing (structural — no coordinator bypass)', () => {
  // 16. IPC handlers do not call lifecycle mutation methods directly in trusted-LAN mode.
  //
  // This is a source-shape check rather than a runtime-behavior check: main.ts constructs its
  // real ConnectorLifecycleService/PowerShellRouteQuerier/NetworkChangeWatcher at module scope
  // with no seam to invoke a single IPC handler in isolation without booting the whole app. The
  // coordinator's manual-command *behavior* (start/stop/restart, blocking, races, staleness) is
  // proven deterministically in trusted-lan-rebind-coordinator.test.ts against real fakes — this
  // test only proves the wiring wasn't quietly reverted to the direct-mutation bypass that
  // required this fix in the first place.
  const mainTsSource = readFileSync(
    path.join(__dirname, '../../src/main/main.ts'),
    'utf-8',
  );

  it.each([
    ['desktop:start-connector', 'manualStart', 'ensureConnectorRunning'],
    ['desktop:stop-connector', 'manualStop', 'stopConnector'],
    ['desktop:restart-connector', 'manualRestart', 'restartConnector'],
  ] as const)('%s routes trusted-LAN mode through trustedLanCoordinator.%s(), not lifecycleService.%s() directly', (channel, coordinatorMethod, directMethod) => {
    const body = extractIpcHandlerBody(mainTsSource, channel);

    expect(body).toContain("connectorBindMode === 'trusted-lan'");
    expect(body).toContain(`trustedLanCoordinator.${coordinatorMethod}(`);

    const trustedLanBranchEnd = body.indexOf('}', body.indexOf(`trustedLanCoordinator.${coordinatorMethod}(`));
    const directCallIndex = body.indexOf(`lifecycleService.${directMethod}(`);

    // The direct ConnectorLifecycleService call may still exist for local-only mode, but it must
    // appear only after the trusted-lan branch's closing brace — never unconditionally reachable
    // before the eligibility-gated coordinator path.
    if (directCallIndex !== -1) {
      expect(directCallIndex).toBeGreaterThan(trustedLanBranchEnd);
    }
  });
});
