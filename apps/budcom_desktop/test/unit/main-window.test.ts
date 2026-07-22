import { describe, expect, it, vi } from 'vitest';

vi.mock('electron', () => {
  class BrowserWindow {
    public id = 1;
    public webPreferences: unknown;
    public webContents = {
      on: vi.fn(),
      once: vi.fn((_event: string, callback: () => void) => {
        callback();
      }),
    };
    constructor(options: { webPreferences?: unknown }) {
      this.webPreferences = options.webPreferences;
    }
    loadFile = vi.fn().mockResolvedValue(undefined);
    show = vi.fn();
    once = vi.fn((_event: string, callback: () => void) => {
      callback();
    });
  }
  return {
    app: {
      whenReady: vi.fn().mockResolvedValue(undefined),
      on: vi.fn(),
      quit: vi.fn(),
    },
    BrowserWindow,
    ipcMain: { handle: vi.fn() },
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
    expect(electron.ipcMain.handle).toHaveBeenCalledWith('desktop:select-company', expect.any(Function));
  });
});
