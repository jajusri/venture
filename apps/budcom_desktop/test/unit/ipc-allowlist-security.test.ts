import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import {
  ALLOWED_IPC_CHANNELS,
  assertAllowedIpcChannel,
  assertBoundedIpcPayload,
  isPathContainedInRoot,
  MAX_IPC_PAYLOAD_BYTES,
  MAX_IPC_QUERY_LENGTH,
  validateChooseStorageModeInput,
  validateCompanyId,
  validateExportDirectory,
  validateLedgerQuery,
  validateSettingsInput,
  validateSyncOptions,
} from '../../src/application/ipc-allowlist.js';

const mintedDirs: string[] = [];

afterEach(() => {
  for (const dir of mintedDirs.splice(0)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

function mkTempDir(prefix: string): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), prefix));
  mintedDirs.push(dir);
  return dir;
}

describe('IPC security allowlist', () => {
  it('rejects unknown IPC channels', () => {
    expect(() => assertAllowedIpcChannel('desktop:evil-channel')).toThrow(/Blocked IPC channel/);
  });

  it('allows every registered invoke channel', () => {
    for (const channel of ALLOWED_IPC_CHANNELS) {
      expect(() => assertAllowedIpcChannel(channel)).not.toThrow();
    }
  });

  it('rejects malformed settings payloads', () => {
    expect(() => validateSettingsInput([])).toThrow(/object/i);
    expect(() => validateSettingsInput('bad')).toThrow(/object/i);
  });

  it('rejects malformed sync options', () => {
    expect(() => validateSyncOptions([])).toThrow(/object/i);
    expect(() => validateSyncOptions({ incremental: 'yes' })).toThrow(/boolean/i);
    expect(validateSyncOptions({ incremental: true })).toEqual({ incremental: true });
    expect(validateSyncOptions(undefined)).toEqual({ incremental: false });
  });

  it('rejects invalid company ids including traversal attempts', () => {
    expect(() => validateCompanyId('../etc/passwd')).toThrow(/invalid characters/i);
    expect(() => validateCompanyId('a'.repeat(200))).toThrow(/invalid characters/i);
    expect(validateCompanyId('estimation')).toBe('estimation');
  });

  it('bounds ledger query length and pagination', () => {
    const longQuery = 'x'.repeat(MAX_IPC_QUERY_LENGTH + 50);
    const result = validateLedgerQuery({ query: longQuery, page: 0, pageSize: 999 });
    expect(result.query).toHaveLength(MAX_IPC_QUERY_LENGTH);
    expect(result.page).toBe(1);
    expect(result.pageSize).toBe(100);
  });

  it('rejects oversized IPC payloads', () => {
    const oversized = [{ payload: 'x'.repeat(MAX_IPC_PAYLOAD_BYTES) }];
    expect(() => assertBoundedIpcPayload(oversized)).toThrow(/size limit/i);
    expect(() => assertBoundedIpcPayload([{ connectorPort: 8080 }])).not.toThrow();
  });
});

describe('owned export directory validation', () => {
  it('allows undefined export directory and contained child paths', () => {
    const root = mkTempDir('budcom-export-root-');
    const child = path.join(root, 'budcom-diagnostics-2026-07-25T00-00-00-000Z');
    expect(validateExportDirectory(undefined, root)).toBeUndefined();
    expect(validateExportDirectory(child, root)).toBe(path.resolve(child));
  });

  it('rejects path traversal and absolute paths outside owned root', () => {
    const root = mkTempDir('budcom-export-root-');
    expect(() => validateExportDirectory(path.join(root, '..', 'escape'), root)).toThrow(/within the approved root|app-owned/i);
    expect(() => validateExportDirectory('/tmp/evil', root)).toThrow(/within the approved root|app-owned/i);
  });

  it('rejects null bytes in export directory input', () => {
    const root = mkTempDir('budcom-export-root-');
    expect(() => validateExportDirectory('safe\u0000evil', root)).toThrow(/invalid characters/i);
  });

  it('detects path containment consistently', () => {
    const root = path.join(os.tmpdir(), 'budcom-owned');
    expect(isPathContainedInRoot(path.join(root, 'child'), root)).toBe(true);
    expect(isPathContainedInRoot(path.join(root, '..', 'outside'), root)).toBe(false);
  });
});

describe('renderer preload boundary characterization', () => {
  it('documents that preload exposes typed bridge only without generic invoke', () => {
    const preloadSource = fs.readFileSync(
      path.join(process.cwd(), 'src/preload/preload.ts'),
      'utf8',
    );
    expect(preloadSource).toContain('contextBridge.exposeInMainWorld');
    expect(preloadSource).not.toMatch(/exposeInMainWorld\([\s\S]*require/);
    expect(preloadSource).not.toMatch(/ipcRenderer\.invoke\([^'"]/);
  });

  it('documents main-process Electron hardening flags', () => {
    const mainSource = fs.readFileSync(path.join(process.cwd(), 'src/main/main.ts'), 'utf8');
    expect(mainSource).toContain('contextIsolation: true');
    expect(mainSource).toContain('nodeIntegration: false');
    expect(mainSource).toContain('sandbox: true');
    expect(mainSource).toContain('assertBoundedIpcPayload');
  });
});

// TD-033: confirmSwitch defaults false unless the caller explicitly sets true, so a legacy/stale
// renderer payload (or any payload that simply omits the field) can never accidentally bypass the
// switch-confirmation gate in desktop:choose-storage-mode.
describe('validateChooseStorageModeInput — TD-033 confirmSwitch parsing', () => {
  it('defaults confirmSwitch to false when omitted', () => {
    expect(validateChooseStorageModeInput({ mode: 'standard' })).toEqual({
      mode: 'standard',
      confirmSwitch: false,
    });
  });

  it('only a literal boolean true sets confirmSwitch — a truthy non-boolean does not', () => {
    expect(validateChooseStorageModeInput({ mode: 'standard', confirmSwitch: true })).toEqual({
      mode: 'standard',
      confirmSwitch: true,
    });
    expect(validateChooseStorageModeInput({ mode: 'standard', confirmSwitch: 'true' })).toEqual({
      mode: 'standard',
      confirmSwitch: false,
    });
    expect(validateChooseStorageModeInput({ mode: 'standard', confirmSwitch: 1 })).toEqual({
      mode: 'standard',
      confirmSwitch: false,
    });
  });

  it('carries confirmSwitch through the private-removable branch alongside the validated drive letter', () => {
    expect(
      validateChooseStorageModeInput({ mode: 'private-removable', driveLetter: 'E:\\', confirmSwitch: true }),
    ).toEqual({ mode: 'private-removable', driveLetter: 'E:\\', confirmSwitch: true });
  });
});
