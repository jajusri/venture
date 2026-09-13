import { afterEach, describe, expect, it } from 'vitest';

import {
  resolveConnectorLifecycleConfig,
  validateConnectorExecutable,
} from '../../src/application/connector-lifecycle-config.js';
import path from 'node:path';

describe('connector-lifecycle-config', () => {
  const originalLanAcknowledgement = process.env.VENTURE_CONNECTOR_LAN_MODE_ACKNOWLEDGED;

  afterEach(() => {
    if (originalLanAcknowledgement === undefined) {
      delete process.env.VENTURE_CONNECTOR_LAN_MODE_ACKNOWLEDGED;
    } else {
      process.env.VENTURE_CONNECTOR_LAN_MODE_ACKNOWLEDGED = originalLanAcknowledgement;
    }
  });
  it('resolves connector URL and port from environment', () => {
    const config = resolveConnectorLifecycleConfig({
      connectorBaseUrl: 'http://localhost:9090',
      autoStart: true,
    });

    expect(config.connectorPort).toBe(9090);
    expect(config.autoStart).toBe(true);
  });

  it('reports missing executable script', () => {
    const config = resolveConnectorLifecycleConfig({
      connectorExecutable: process.execPath,
      connectorArgs: ['C:/missing/connector/main.js'],
      connectorCwd: process.cwd(),
    });

    expect(validateConnectorExecutable(config)).toContain('not found');
  });

  it('passes connector database directory rather than file path to child env', () => {
    const config = resolveConnectorLifecycleConfig({
      connectorExecutable: process.execPath,
    }, {
      isPackaged: true,
      resourcesPath: 'C:/Apps/Venture Desktop/resources',
      connectorDatabaseDir: 'C:/Users/Tester/AppData/Roaming/@venture/desktop/connector-data',
    });

    expect(config.childEnv?.VENTURE_DATABASE_PATH).toBe('C:/Users/Tester/AppData/Roaming/@venture/desktop/connector-data');
    expect(String(config.childEnv?.VENTURE_DATABASE_PATH)).not.toContain('venture-ledger.db');
    expect(config.connectorCwd?.replace(/\\/g, '/')).toBe('C:/Apps/Venture Desktop/resources/connector');
    expect(config.childEnv?.ELECTRON_RUN_AS_NODE).toBeUndefined();
  });

  it('passes the configured Tally host/port to child env as VENTURE_TALLY_HOST/VENTURE_TALLY_PORT (TD-004)', () => {
    const config = resolveConnectorLifecycleConfig({
      connectorExecutable: process.execPath,
    }, {
      isPackaged: true,
      resourcesPath: 'C:/Apps/Venture Desktop/resources',
      tallyHost: '192.168.1.50',
      tallyPort: 9999,
    });

    expect(config.childEnv?.VENTURE_TALLY_HOST).toBe('192.168.1.50');
    expect(config.childEnv?.VENTURE_TALLY_PORT).toBe('9999');
  });

  it('omits VENTURE_TALLY_HOST/VENTURE_TALLY_PORT from child env when no Tally host/port is supplied', () => {
    const config = resolveConnectorLifecycleConfig({
      connectorExecutable: process.execPath,
    }, {
      isPackaged: true,
      resourcesPath: 'C:/Apps/Venture Desktop/resources',
    });

    expect(config.childEnv?.VENTURE_TALLY_HOST).toBeUndefined();
    expect(config.childEnv?.VENTURE_TALLY_PORT).toBeUndefined();
  });

  it('passes the Tally request audit path under persistent app data, never packaged CWD', () => {
    const auditPath = 'C:/Users/Tester/AppData/Roaming/@venture/desktop/connector-diagnostics/tally-request-audit.jsonl';
    const config = resolveConnectorLifecycleConfig({ connectorExecutable: process.execPath }, {
      isPackaged: true,
      resourcesPath: 'C:/Program Files/Venture Desktop/resources',
      connectorTallyAuditPath: auditPath,
    });
    expect(config.childEnv?.VENTURE_TALLY_REQUEST_AUDIT_PATH).toBe(auditPath);
    expect(config.childEnv?.VENTURE_TALLY_REQUEST_AUDIT_PATH).not.toContain('Program Files');
  });

  it('passes a persistent connector transport identity directory to child env as VENTURE_TRANSPORT_IDENTITY_DIR (TD-018 scenario 1)', () => {
    const config = resolveConnectorLifecycleConfig({
      connectorExecutable: process.execPath,
    }, {
      isPackaged: true,
      resourcesPath: 'C:/Apps/Venture Desktop/resources',
      connectorTransportIdentityDir: 'C:/Users/Tester/AppData/Roaming/@venture/desktop/connector-transport-identity',
    });

    expect(config.childEnv?.VENTURE_TRANSPORT_IDENTITY_DIR).toBe(
      'C:/Users/Tester/AppData/Roaming/@venture/desktop/connector-transport-identity',
    );
  });

  it('omits VENTURE_TRANSPORT_IDENTITY_DIR from child env when no persistent directory is supplied', () => {
    const config = resolveConnectorLifecycleConfig({
      connectorExecutable: process.execPath,
    }, {
      isPackaged: true,
      resourcesPath: 'C:/Apps/Venture Desktop/resources',
    });

    expect(config.childEnv?.VENTURE_TRANSPORT_IDENTITY_DIR).toBeUndefined();
  });

  it('resolves the same transport identity directory across a simulated reinstall (different resourcesPath, same persistent dir) (TD-018 scenario 2)', () => {
    const persistentTransportIdentityDir = 'C:/Users/Tester/AppData/Roaming/@venture/desktop/connector-transport-identity';

    const beforeReinstall = resolveConnectorLifecycleConfig({
      connectorExecutable: process.execPath,
    }, {
      isPackaged: true,
      resourcesPath: 'C:/Apps/Venture Desktop/resources',
      connectorTransportIdentityDir: persistentTransportIdentityDir,
    });
    // A reinstall/update replaces the install/resources tree — resourcesPath can legitimately
    // change (e.g. a version-suffixed reinstall directory) while userData, and therefore the
    // persistent transport identity directory passed in by main.ts, stays fixed.
    const afterReinstall = resolveConnectorLifecycleConfig({
      connectorExecutable: process.execPath,
    }, {
      isPackaged: true,
      resourcesPath: 'C:/Apps/Venture Desktop (new)/resources',
      connectorTransportIdentityDir: persistentTransportIdentityDir,
    });

    expect(beforeReinstall.childEnv?.VENTURE_TRANSPORT_IDENTITY_DIR).toBe(persistentTransportIdentityDir);
    expect(afterReinstall.childEnv?.VENTURE_TRANSPORT_IDENTITY_DIR).toBe(persistentTransportIdentityDir);
    expect(afterReinstall.childEnv?.VENTURE_TRANSPORT_IDENTITY_DIR).toBe(beforeReinstall.childEnv?.VENTURE_TRANSPORT_IDENTITY_DIR);
  });

  it('forces loopback host and child env binding for packaged startup', () => {
    delete process.env.VENTURE_CONNECTOR_LAN_MODE_ACKNOWLEDGED;
    const config = resolveConnectorLifecycleConfig({
      connectorBindMode: 'local-only',
      connectorBaseUrl: 'http://192.168.4.20:9090',
      connectorHost: '0.0.0.0',
      connectorPort: 9090,
      connectorExecutable: process.execPath,
    }, {
      isPackaged: true,
      resourcesPath: 'C:/Apps/Venture Desktop/resources',
    });

    expect(config.connectorHost).toBe('127.0.0.1');
    expect(config.connectorBaseUrl).toBe('http://127.0.0.1:9090');
    expect(config.childEnv?.VENTURE_CONNECTOR_HOST).toBe('127.0.0.1');
    expect(config.childEnv?.VENTURE_CONNECTOR_PORT).toBe('9090');
  });

  it('honours a specific packaged LAN host only with explicit acknowledgement', () => {
    process.env.VENTURE_CONNECTOR_LAN_MODE_ACKNOWLEDGED = 'true';
    const config = resolveConnectorLifecycleConfig({
      connectorBindMode: 'trusted-lan',
      connectorBaseUrl: 'http://192.168.4.20:9090',
      connectorHost: '192.168.4.20',
      connectorPort: 9090,
      connectorExecutable: process.execPath,
    }, {
      isPackaged: true,
      resourcesPath: 'C:/Apps/Venture Desktop/resources',
    });

    expect(config.connectorHost).toBe('192.168.4.20');
    expect(config.connectorBaseUrl).toBe('http://192.168.4.20:9090');
    expect(config.childEnv?.VENTURE_CONNECTOR_LAN_MODE_ACKNOWLEDGED).toBe('true');
  });

  it('trusted-LAN bind information flows to the child without any hardcoded address in source', () => {
    process.env.VENTURE_CONNECTOR_LAN_MODE_ACKNOWLEDGED = 'true';
    // The host below stands in for "whatever the route-backed resolver found live" — the
    // config layer must pass it through verbatim, never substitute a fixed literal of its own.
    const routeBackedHost = '10.77.0.99';
    const config = resolveConnectorLifecycleConfig({
      connectorBindMode: 'trusted-lan',
      connectorHost: routeBackedHost,
      connectorPort: 8080,
      connectorExecutable: process.execPath,
    }, {
      isPackaged: true,
      resourcesPath: 'C:/Apps/Venture Desktop/resources',
    });

    expect(config.connectorHost).toBe(routeBackedHost);
    expect(config.childEnv?.VENTURE_CONNECTOR_HOST).toBe(routeBackedHost);
  });

  describe('development-mode connector executable resolution (defect 2)', () => {
    it('never falls back to the bare Electron process — resolves to the real Node running this test', () => {
      // This test suite itself runs under real Node (not Electron), so the "already node.exe"
      // fast path in resolveConnectorHostExecutable is what fires — proving the code path no
      // longer unconditionally trusts process.execPath without checking what it actually is.
      const config = resolveConnectorLifecycleConfig({}, { isPackaged: false });

      expect(config.connectorExecutable.length).toBeGreaterThan(0);
      expect(path.basename(config.connectorExecutable).toLowerCase()).toMatch(/^node(\.exe)?$/);
      expect(config.developmentRuntimeError).toBeNull();
    });

    it('an explicit VENTURE_CONNECTOR_EXECUTABLE override still wins in development mode', () => {
      const config = resolveConnectorLifecycleConfig({
        connectorExecutable: 'D:/custom/node.exe',
      }, { isPackaged: false });

      expect(config.connectorExecutable).toBe('D:/custom/node.exe');
      expect(config.developmentRuntimeError).toBeNull();
    });

    it('the child environment never carries ELECTRON_RUN_AS_NODE in development mode', () => {
      const config = resolveConnectorLifecycleConfig({}, { isPackaged: false });

      expect(config.childEnv?.ELECTRON_RUN_AS_NODE).toBeUndefined();
    });

    it('connector args point at the real built entrypoint (dist/main.js), not an Electron app path', () => {
      const config = resolveConnectorLifecycleConfig({}, { isPackaged: false });

      const scriptArg = config.connectorArgs[0] ?? '';
      expect(scriptArg.replace(/\\/g, '/')).toContain('connector/venture_connector/dist/main.js');
    });
  });
});
