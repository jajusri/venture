import type { App } from 'electron';

export interface SingleInstanceResult {
  readonly acquired: boolean;
  readonly shouldQuit: boolean;
}

export function requestDesktopSingleInstance(app: Pick<App, 'requestSingleInstanceLock' | 'quit'>): SingleInstanceResult {
  const acquired = app.requestSingleInstanceLock();
  if (!acquired) {
    app.quit();
    return { acquired: false, shouldQuit: true };
  }
  return { acquired: true, shouldQuit: false };
}

export function bindSecondInstanceFocus(
  app: Pick<App, 'on'>,
  focusWindow: () => void,
): void {
  app.on('second-instance', () => {
    focusWindow();
  });
}
