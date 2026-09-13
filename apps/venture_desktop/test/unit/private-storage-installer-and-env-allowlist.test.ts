import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

import { CONNECTOR_CHILD_ENV_ALLOWLIST } from '../../src/application/release/connector-packaged-paths.js';

describe('electron-builder uninstall behavior — private business data must survive uninstall/upgrade', () => {
  const builderConfig = fs.readFileSync(path.resolve(process.cwd(), 'electron-builder.yml'), 'utf8');

  it('never deletes AppData (where connector-data/private-storage-locator.json live) on uninstall', () => {
    expect(builderConfig).toMatch(/deleteAppDataOnUninstall:\s*false/);
  });

  it('the custom installer script contains no filesystem delete targeting AppData/user data', () => {
    const script = fs.readFileSync(path.resolve(process.cwd(), 'build', 'installer.nsh'), 'utf8');
    expect(script).not.toMatch(/RMDir[^\n]*\$APPDATA/i);
    expect(script).not.toMatch(/Delete[^\n]*\$APPDATA/i);
  });
});

describe('CONNECTOR_CHILD_ENV_ALLOWLIST — private-storage env vars must reach the spawned Connector', () => {
  it('includes both private-storage vault env vars', () => {
    expect(CONNECTOR_CHILD_ENV_ALLOWLIST).toContain('VENTURE_PRIVATE_STORAGE_VAULT_ID');
    expect(CONNECTOR_CHILD_ENV_ALLOWLIST).toContain('VENTURE_PRIVATE_STORAGE_MARKER_PATH');
  });

  it('still includes the pre-existing VENTURE_DATABASE_PATH var (private storage reuses it, does not replace it)', () => {
    expect(CONNECTOR_CHILD_ENV_ALLOWLIST).toContain('VENTURE_DATABASE_PATH');
  });
});
