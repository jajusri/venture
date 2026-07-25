import { describe, expect, it } from 'vitest';

import {
  resolveConnectorLifecycleConfig,
  validateConnectorExecutable,
} from '../../src/application/connector-lifecycle-config.js';
import path from 'node:path';

describe('connector-lifecycle-config', () => {
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
    const config = resolveConnectorLifecycleConfig({
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
});
