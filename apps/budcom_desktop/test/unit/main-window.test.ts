import { describe, expect, it, vi } from 'vitest';

import desktopPackage from '../../package.json';

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
