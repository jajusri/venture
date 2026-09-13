import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const TRACKING_FILE = path.join(os.tmpdir(), 'venture-connector-test-tracked-dirs.jsonl');

function removeTrackedDirs(): void {
  let lines: string[];
  try {
    lines = fs.readFileSync(TRACKING_FILE, 'utf8').split('\n').filter((line) => line.trim());
  } catch {
    return; // Nothing was tracked this run (e.g. a filtered run that never called createTestContext).
  }
  for (const line of lines) {
    let dir: string | undefined;
    try {
      dir = (JSON.parse(line) as { dir?: string }).dir;
    } catch {
      continue; // A torn line from concurrent worker appends — skip, never fail teardown over it.
    }
    if (!dir) continue;
    try {
      fs.rmSync(dir, { recursive: true, force: true });
    } catch {
      // Best-effort — see test-context.ts's doc comment.
    }
  }
  try {
    fs.rmSync(TRACKING_FILE, { force: true });
  } catch {
    // Best-effort.
  }
}

/**
 * Vitest `globalSetup`: the default export runs once in the main process before any worker
 * starts, and — since it returns a function — that returned function runs once in the main
 * process after every `pool: 'forks'` worker has fully exited (vitest's combined setup/teardown
 * contract; there is no separate `globalTeardown` config key). Running setup-time cleanup first
 * clears any tracking file left behind by a previous run that was killed before its own teardown
 * ran; the real cleanup happens in the returned teardown, once every SQLite handle those workers
 * held open is guaranteed released by OS process exit — unlike an in-process `afterAll`, which
 * cannot delete a still-open file on Windows. See `test-context.ts`'s `createTestContext()` for
 * what gets tracked here — never a pattern-based sweep of unrelated OS-temp content.
 */
export default function globalSetup(): () => void {
  removeTrackedDirs();
  return () => {
    removeTrackedDirs();
  };
}
