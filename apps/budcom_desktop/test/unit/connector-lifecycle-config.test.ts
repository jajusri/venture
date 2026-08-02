import { afterEach, describe, expect, it } from 'vitest';

import {
  resolveConnectorLifecycleConfig,
  validateConnectorExecutable,
} from '../../src/application/connector-lifecycle-config.js';
import path from 'node:path';

describe('connector-lifecycle-config', () => {
  const originalLanAcknowledgement = process.env.BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED;

  afterEach(() => {
    if (originalLanAcknowledgement === undefined) {
      delete process.env.BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED;
    } else {
      process.env.BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED = originalLanAcknowledgement;
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
      resourcesPath: 'C:/Apps/Budcom Desktop/resources',
      connectorDatabaseDir: 'C:/Users/Tester/AppData/Roaming/@budcom/desktop/connector-data',
    });

    expect(config.childEnv?.BUDCOM_DATABASE_PATH).toBe('C:/Users/Tester/AppData/Roaming/@budcom/desktop/connector-data');
    expect(String(config.childEnv?.BUDCOM_DATABASE_PATH)).not.toContain('budcom-ledger.db');
    expect(config.connectorCwd?.replace(/\\/g, '/')).toBe('C:/Apps/Budcom Desktop/resources/connector');
    expect(config.childEnv?.ELECTRON_RUN_AS_NODE).toBeUndefined();
  });

  it('forces loopback host and child env binding for packaged startup', () => {
    delete process.env.BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED;
    const config = resolveConnectorLifecycleConfig({
      connectorBindMode: 'local-only',
      connectorBaseUrl: 'http://192.168.4.20:9090',
      connectorHost: '0.0.0.0',
      connectorPort: 9090,
      connectorExecutable: process.execPath,
    }, {
      isPackaged: true,
      resourcesPath: 'C:/Apps/Budcom Desktop/resources',
    });

    expect(config.connectorHost).toBe('127.0.0.1');
    expect(config.connectorBaseUrl).toBe('http://127.0.0.1:9090');
    expect(config.childEnv?.BUDCOM_CONNECTOR_HOST).toBe('127.0.0.1');
    expect(config.childEnv?.BUDCOM_CONNECTOR_PORT).toBe('9090');
  });

  it('honours a specific packaged LAN host only with explicit acknowledgement', () => {
    process.env.BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED = 'true';
    const config = resolveConnectorLifecycleConfig({
      connectorBindMode: 'trusted-lan',
      connectorBaseUrl: 'http://192.168.4.20:9090',
      connectorHost: '192.168.4.20',
      connectorPort: 9090,
      connectorExecutable: process.execPath,
    }, {
      isPackaged: true,
      resourcesPath: 'C:/Apps/Budcom Desktop/resources',
    });

    expect(config.connectorHost).toBe('192.168.4.20');
    expect(config.connectorBaseUrl).toBe('http://192.168.4.20:9090');
    expect(config.childEnv?.BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED).toBe('true');
  });

  it('trusted-LAN bind information flows to the child without any hardcoded address in source', () => {
    process.env.BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED = 'true';
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
      resourcesPath: 'C:/Apps/Budcom Desktop/resources',
    });

    expect(config.connectorHost).toBe(routeBackedHost);
    expect(config.childEnv?.BUDCOM_CONNECTOR_HOST).toBe(routeBackedHost);
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

    it('an explicit BUDCOM_CONNECTOR_EXECUTABLE override still wins in development mode', () => {
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
      expect(scriptArg.replace(/\\/g, '/')).toContain('connector/budcom_connector/dist/main.js');
    });
  });
});
